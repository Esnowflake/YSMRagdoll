package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 只维护布娃娃附近的 Minecraft 方块碰撞体，并按方块状态增量复用。
 *
 * <p>缓存逐 tick 扫描每具布娃娃周围的有限区域，不加载远处区块；一个复杂
 * {@code VoxelShape} 会拆成多个静态盒刚体。检测到方块刚放进布娃娃内部后，额外
 * 保留二十 tick 的放置保护并持续尝试把整套刚体推出，避免尸体永久卡在新方块中。</p>
 */
final class BlockCollisionCache {
    private static final int HORIZONTAL_RADIUS = 3;
    private static final int VERTICAL_RADIUS = 4;
    private static final int PLACEMENT_PROTECTION_TICKS = 20;

    private final ClientPhysicsWorld physicsWorld;
    private final Map<Long, Entry> entries = new HashMap<>();
    private final Set<Long> required = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private final List<AABB> newlyFilledBoxes = new ArrayList<>();
    private final List<AABB> protectedBoxes = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final List<PlacementProtection> placementProtections = new ArrayList<>();
    private final Vector3f worldOffset = new Vector3f();

    BlockCollisionCache(ClientPhysicsWorld physicsWorld, Vector3f initialWorldOffset) {
        this.physicsWorld = physicsWorld;
        this.worldOffset.set(initialWorldOffset);
    }

    void update(Level level, Iterable<PhysicsRagdoll> ragdolls) {
        required.clear();
        visited.clear();
        newlyFilledBoxes.clear();
        Predicate<BlockPos> loaded = level == null ? position -> false : level::hasChunkAt;
        for (PhysicsRagdoll ragdoll : ragdolls) {
            if (!ragdoll.isChunkLoaded()) continue;
            net.minecraft.world.phys.Vec3 center = ragdoll.center().subtract(
                    worldOffset.x, worldOffset.y, worldOffset.z);
            BlockPos origin = BlockPos.containing(center);
            scanRegion(level, origin, loaded);
        }
        entries.entrySet().removeIf(entry -> {
            if (required.contains(entry.getKey())) {
                return false;
            }
            remove(entry.getValue());
            return true;
        });
        for (AABB box : newlyFilledBoxes) {
            placementProtections.add(new PlacementProtection(box, PLACEMENT_PROTECTION_TICKS));
        }
        if (!placementProtections.isEmpty()) {
            protectedBoxes.clear();
            for (PlacementProtection protection : placementProtections) {
                protectedBoxes.add(protection.box);
            }
            for (PhysicsRagdoll ragdoll : ragdolls) {
                if (ragdoll.isChunkLoaded()) ragdoll.ejectAboveNewBlocks(protectedBoxes);
            }
            placementProtections.removeIf(PlacementProtection::tickExpired);
        }
    }

    /** Shared coverage is queried once per update, before any state/shape lookup. */
    void scanRegion(BlockGetter level, BlockPos origin, Predicate<BlockPos> loaded) {
        for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
            for (int y = -VERTICAL_RADIUS; y <= VERTICAL_RADIUS; y++) {
                for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    long key = cursor.asLong();
                    if (!visited.add(key) || !loaded.test(cursor)) continue;
                    required.add(key);
                    refresh(level, cursor);
                }
            }
        }
    }

    /** Refresh one loaded position, including empty and environment-dependent shapes. */
    void refresh(BlockGetter level, BlockPos position) {
        long key = position.asLong();
        BlockState state = level.getBlockState(position);
        Entry current = entries.get(key);
        if (current != null && current.state.equals(state) && !state.getBlock().hasDynamicShape()) return;
        List<AABB> boxes = state.getCollisionShape(level, position, CollisionContext.empty()).toAabbs();
        if (current != null && current.boxes.equals(boxes)) {
            if (!current.state.equals(state)) {
                entries.put(key, new Entry(state, current.boxes, current.bodies));
            }
            return;
        }
        if (current != null) remove(current);
        entries.put(key, create(position, state, boxes));
        if (current != null) {
            for (AABB box : boxes) {
                newlyFilledBoxes.add(box.move(position).move(worldOffset.x, worldOffset.y, worldOffset.z));
            }
        }
    }

    void clear() {
        for (Entry entry : entries.values()) {
            remove(entry);
        }
        entries.clear();
        required.clear();
        visited.clear();
        newlyFilledBoxes.clear();
        protectedBoxes.clear();
        placementProtections.clear();
    }

    private Entry create(BlockPos position, BlockState state, List<AABB> boxes) {
        List<RigidBody> bodies = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            float halfX = (float) Math.max(0.001, box.getXsize() * 0.5);
            float halfY = (float) Math.max(0.001, box.getYsize() * 0.5);
            float halfZ = (float) Math.max(0.001, box.getZsize() * 0.5);
            BoxShape shape = new BoxShape(new Vector3f(halfX, halfY, halfZ));
            shape.setMargin(0.01F);
            Transform transform = new Transform();
            transform.setIdentity();
            transform.origin.set((float) (position.getX() + (box.minX + box.maxX) * 0.5),
                    (float) (position.getY() + (box.minY + box.maxY) * 0.5),
                    (float) (position.getZ() + (box.minZ + box.maxZ) * 0.5));
            transform.origin.add(worldOffset);
            RigidBody body = new RigidBody(new RigidBodyConstructionInfo(0.0F,
                    new DefaultMotionState(transform), shape, new Vector3f()));
            body.setFriction(0.9F);
            body.setRestitution(0.0F);
            physicsWorld.addStaticBody(body);
            bodies.add(body);
        }
        return new Entry(state, List.copyOf(boxes), bodies);
    }

    private void remove(Entry entry) {
        for (RigidBody body : entry.bodies) {
            physicsWorld.removeBody(body);
        }
    }

    /** 设置变化时同步平移已缓存的方块碰撞体，新缓存也会使用更新后的偏移。 */
    void translateWorld(Vector3f delta) {
        if (delta.lengthSquared() < 1.0E-10F) return;
        worldOffset.add(delta);
        placementProtections.clear();
        for (Entry entry : entries.values()) {
            for (RigidBody body : entry.bodies) {
                Transform transform = body.getWorldTransform(new Transform());
                transform.origin.add(delta);
                body.setWorldTransform(transform);
                if (body.getMotionState() != null) {
                    body.getMotionState().setWorldTransform(transform);
                }
                physicsWorld.updateBodyAabb(body);
            }
        }
    }

    private record Entry(BlockState state, List<AABB> boxes, List<RigidBody> bodies) {
    }

    private static final class PlacementProtection {
        private final AABB box;
        private int remainingTicks;

        private PlacementProtection(AABB box, int remainingTicks) {
            this.box = box;
            this.remainingTicks = remainingTicks;
        }

        private boolean tickExpired() {
            return --remainingTicks <= 0;
        }
    }
}
