package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Per-part buoyancy using submerged AABB volume, with fluid cells cached for one client tick. */
final class FluidBuoyancy {
    private final Map<Long, Cell> cells = new HashMap<>();
    private final Vector3f offset = new Vector3f();
    private BlockGetter level;
    private Predicate<BlockPos> loaded = position -> false;

    void beginTick(BlockGetter level, Predicate<BlockPos> loaded, Vector3f offset) {
        this.level = level;
        this.loaded = loaded;
        this.offset.set(offset);
        cells.clear();
    }

    void clear() {
        level = null;
        loaded = position -> false;
        cells.clear();
    }

    boolean apply(RigidBody body, float seconds, boolean grabbed) {
        if (level == null || body.getInvMass() <= 0) return false;
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        body.getAabb(min, max);
        min.sub(offset);
        max.sub(offset);
        Wetness wetness = sample(min, max);
        apply(body, wetness, seconds, grabbed);
        return wetness.fraction > 0;
    }

    Wetness sample(Vector3f min, Vector3f max) {
        if (level == null) return new Wetness(0, 0);
        double volume = (double) (max.x - min.x) * (max.y - min.y) * (max.z - min.z);
        if (volume <= 0) return new Wetness(0, 0);
        double submerged = 0;
        double lava = 0;
        BlockPos start = BlockPos.containing(min.x, min.y, min.z);
        BlockPos end = BlockPos.containing(max.x, max.y, max.z);
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            Cell cell = cells.computeIfAbsent(pos.asLong(), key -> read(pos));
            if (cell.height <= 0) continue;
            double overlap = Math.max(0, Math.min(max.x, pos.getX() + 1) - Math.max(min.x, pos.getX()))
                    * Math.max(0, Math.min(max.z, pos.getZ() + 1) - Math.max(min.z, pos.getZ()))
                    * Math.max(0, Math.min(max.y, pos.getY() + cell.height) - Math.max(min.y, pos.getY()));
            submerged += overlap;
            if (cell.lava) lava += overlap;
        }
        return new Wetness((float) Math.min(1, submerged / volume), (float) Math.min(1, lava / volume));
    }

    private Cell read(BlockPos pos) {
        if (!loaded.test(pos)) return new Cell(0, false);
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty()) return new Cell(0, false);
        return new Cell(Math.max(0, Math.min(1, state.getHeight(level, pos))),
                state.is(FluidTags.LAVA) || state.getType().isSame(Fluids.LAVA));
    }

    static void apply(RigidBody body, Wetness wetness, float seconds, boolean grabbed) {
        if (wetness.fraction <= 0 || seconds <= 0 || body.getInvMass() <= 0) return;
        body.activate(true);
        // 80% immersion balances gravity. Scaling by mass keeps small hands and heavy torsos compatible.
        Vector3f impulse = new Vector3f(0, 9.81F * 1.25F * wetness.fraction * seconds, 0);
        if (!grabbed) {
            float drag = 2.5F * wetness.fraction + 2.5F * wetness.lavaFraction;
            Vector3f velocity = body.getLinearVelocity(new Vector3f());
            velocity.scale((float) Math.expm1(-drag * seconds));
            impulse.add(velocity);
            Vector3f angular = body.getAngularVelocity(new Vector3f());
            angular.scale((float) Math.exp(-0.6F * drag * seconds));
            body.setAngularVelocity(angular);
        }
        impulse.scale(1 / body.getInvMass());
        body.applyCentralImpulse(impulse);
    }

    record Wetness(float fraction, float lavaFraction) {}
    private record Cell(float height, boolean lava) {}
}
