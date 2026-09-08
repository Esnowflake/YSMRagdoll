package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.config.YsmRagdollConfig;
import com.ysmragdoll.network.PlayerDeathSnapshot;
import com.ysmragdoll.network.ExplosionImpulseSnapshot;
import com.ysmragdoll.client.physics.ClientPhysicsWorld;
import com.ysmragdoll.client.physics.PhysicsRagdoll;
import com.ysmragdoll.client.physics.YsmSkeletonExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 管理独立模型快照及其客户端物理生命周期。
 *
 * <p>此类是“死亡网络快照、YSM 绘制快照和 JBullet 刚体”之间的所有权边界：
 * 每具布娃娃持有一份可写骨骼数组和一个可选物理对象；共享物理世界只在至少一具
 * 布娃娃启用物理时存在。数量上限、存在时间、手动清除、世界卸载和资源重载最终
 * 都通过这里释放约束和刚体，避免跨世界保留 YSM 的旧 GPU 资源。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientRagdollManager {
    private static final Deque<StaticRagdoll> RAGDOLLS = new ArrayDeque<>();
    private static final Deque<PendingRagdoll> PENDING = new ArrayDeque<>();
    private static final Map<String, Long> RECENT_DEATHS = new HashMap<>();
    private static final long DEATH_DEDUPLICATION_WINDOW_MILLIS = 1500L;
    private static final int MAX_CAPTURE_RETRIES = 40;
    private static final int CAPTURE_RETRY_INTERVAL_TICKS = 2;
    private static final List<PhysicsRagdoll> ACTIVE_PHYSICS = new ArrayList<>();
    private static final Deque<PendingExplosion> RECENT_EXPLOSIONS = new ArrayDeque<>();
    private static final long EXPLOSION_REPLAY_WINDOW_MILLIS = 1000L;
    private static ClientPhysicsWorld physicsWorld;

    private ClientRagdollManager() {
    }

    public static int ragdollCount() {
        return RAGDOLLS.size();
    }

    public static int physicsRagdollCount() {
        return ACTIVE_PHYSICS.size();
    }

    public enum TestSpawnResult {
        CREATED, STATIC_CREATED, NO_PLAYER, DISABLED, CAPTURE_FAILED
    }

    /** One GUI click creates one local snapshot; never send a death packet or deduplicate it. */
    public static TestSpawnResult createFromCurrentPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.level == null || player == null || player.isDeadOrDying() || player.isSpectator()) {
            return TestSpawnResult.NO_PLAYER;
        }
        if (YsmRagdollConfig.MAX_RAGDOLLS.get() <= 0) {
            return TestSpawnResult.DISABLED;
        }
        Vec3 velocity = player.getDeltaMovement();
        PlayerDeathSnapshot snapshot = new PlayerDeathSnapshot(player.getId(), player.getUUID(),
                player.getX(), player.getY(), player.getZ(), velocity.x, velocity.y, velocity.z,
                player.yBodyRot);
        var captured = OpenYsmModelAdapter.capture(player, snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.bodyYaw());
        if (captured.isEmpty() || !OpenYsmModelAdapter.hasSnapshot(captured.get())) {
            // No delayed retry: otherwise repeated clicks can produce unexpected later spawns.
            return TestSpawnResult.CAPTURE_FAILED;
        }
        StaticRagdoll ragdoll = promote(new PendingRagdoll(snapshot, captured.get(), null,
                0, System.currentTimeMillis()));
        if (ragdoll == null) return TestSpawnResult.DISABLED;
        initializePhysics(ragdoll, false);
        YsmRagdollLog.info("手动创建测试布娃娃: 玩家=" + player.getUUID()
                + ", 当前数量=" + RAGDOLLS.size() + ", 物理=" + (ragdoll.physics != null));
        return ragdoll.physics == null ? TestSpawnResult.STATIC_CREATED : TestSpawnResult.CREATED;
    }

    /** Shared death/test initialization; test objects do not inherit past explosions. */
    private static void initializePhysics(StaticRagdoll ragdoll, boolean replayExplosions) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ragdoll.physicsAttempted || minecraft.level == null) return;
        OpenYsmModelAdapter.physicsView(ragdoll.model).ifPresent(view -> {
            ragdoll.physicsAttempted = true;
            if (physicsWorld == null) physicsWorld = new ClientPhysicsWorld();
            ragdoll.physics = PhysicsRagdoll.create(physicsWorld, ragdoll.snapshot, view);
            if (ragdoll.physics != null) {
                ACTIVE_PHYSICS.add(ragdoll.physics);
                ragdoll.physics.writePose();
                ragdoll.physics.updateChunkLoaded(minecraft.level);
                if (replayExplosions && ragdoll.physics.isChunkLoaded()) {
                    long now = System.currentTimeMillis();
                    for (PendingExplosion explosion : RECENT_EXPLOSIONS) {
                        if (explosion.expiresAtMillis() > now) applyExplosion(ragdoll.physics, explosion.snapshot());
                    }
                }
            }
        });
    }

    static String intensiveState() {
        StringBuilder state = new StringBuilder(512)
                .append("ragdolls=").append(RAGDOLLS.size())
                .append(" physics=").append(ACTIVE_PHYSICS.size());
        int index = 0;
        for (StaticRagdoll ragdoll : RAGDOLLS) {
            state.append(" | ragdoll[").append(index++).append("] player=")
                    .append(ragdoll.snapshot().playerId()).append(' ');
            if (ragdoll.physics == null) {
                state.append("physics=pending ");
            } else {
                state.append(ragdoll.physics.intensiveState()).append(' ');
            }
            state.append(OpenYsmModelAdapter.describeBonePose(ragdoll.model()));
        }
        return state.toString();
    }

    public static void onPlayerDeath(PlayerDeathSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || YsmRagdollConfig.MAX_RAGDOLLS.get() == 0) {
            return;
        }
        Entity entity = minecraft.level.getEntity(snapshot.entityId());
        Player player = entity instanceof Player found ? found : minecraft.level.getPlayerByUUID(snapshot.playerId());
        if (player == null) {
            YsmRagdollLog.warn("死亡玩家已离开客户端追踪范围: " + snapshot.playerId());
            return;
        }
        String deathKey = deathKey(snapshot);
        long now = System.currentTimeMillis();
        RECENT_DEATHS.entrySet().removeIf(entry ->
                now - entry.getValue() > DEATH_DEDUPLICATION_WINDOW_MILLIS);
        Long previousDeath = RECENT_DEATHS.put(deathKey, now);
        if (previousDeath != null && now - previousDeath <= DEATH_DEDUPLICATION_WINDOW_MILLIS) {
            YsmRagdollLog.warn("忽略短时间内重复的死亡快照: " + deathKey);
            return;
        }

        OpenYsmModelAdapter.capture(player, snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.bodyYaw()).ifPresent(model -> {
            PendingRagdoll pending = new PendingRagdoll(snapshot, model, player,
                    0, System.currentTimeMillis());
            if (OpenYsmModelAdapter.hasSnapshot(model)) {
                promote(pending);
            } else {
                PENDING.addLast(pending);
                YsmRagdollLog.warn("模型捕获失败，暂不创建布娃娃，进入有限重试: 玩家="
                        + snapshot.playerId());
            }
        });
    }

    public static void tick() {
        processPending();
        long currentTimeMillis = System.currentTimeMillis();
        RECENT_EXPLOSIONS.removeIf(explosion -> explosion.expiresAtMillis() <= currentTimeMillis);
        int limit = YsmRagdollConfig.MAX_RAGDOLLS.get();
        while (RAGDOLLS.size() > limit) {
            removeOldest("数量上限降低");
        }
        if (!YsmRagdollConfig.MANUAL_REMOVAL.get()) {
            int lifetime = YsmRagdollConfig.LIFETIME_SECONDS.get();
            if (lifetime <= 10000) {
                long now = System.currentTimeMillis();
                Iterator<StaticRagdoll> iterator = RAGDOLLS.iterator();
                while (iterator.hasNext()) {
                    StaticRagdoll ragdoll = iterator.next();
                    if (now - ragdoll.createdAtMillis >= lifetime * 1000L) {
                        iterator.remove();
                        dispose(ragdoll);
                    }
                }
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || RAGDOLLS.isEmpty()) {
            releaseWorldIfUnused();
            return;
        }
        for (StaticRagdoll ragdoll : RAGDOLLS) {
            initializePhysics(ragdoll, true);
        }
        if (physicsWorld != null && !ACTIVE_PHYSICS.isEmpty()) {
            for (PhysicsRagdoll physics : ACTIVE_PHYSICS) {
                physics.updateChunkLoaded(minecraft.level);
            }
            physicsWorld.tick(minecraft.level, minecraft.player, ACTIVE_PHYSICS);
        }
    }

    private static void trimForNewRagdoll() {
        int limit = YsmRagdollConfig.MAX_RAGDOLLS.get();
        while (!RAGDOLLS.isEmpty() && RAGDOLLS.size() >= limit) {
            removeOldest("为新布娃娃腾出数量上限");
        }
    }

    /** 处理捕获失败的暂存对象；成功后才转入正式列表。 */
    private static void processPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        // 每个客户端 tick 只处理队首一个等待对象，并将未完成对象放回队尾，
        // 防止多个死亡快照在同一帧同时进入 YSM 渲染器。
        PendingRagdoll pending = PENDING.pollFirst();
        if (pending == null) {
            return;
        }
        pending.ageTicks++;
        if (pending.ageTicks > MAX_CAPTURE_RETRIES) {
            YsmRagdollLog.warn("YSM 模型在有限重试窗口内仍未捕获，丢弃布娃娃: "
                    + pending.snapshot.playerId());
            return;
        }
        boolean complete = false;
        if (pending.ageTicks % CAPTURE_RETRY_INTERVAL_TICKS == 0) {
            Player carrier = pending.player;
            if (carrier == null || !carrier.isAlive()) {
                Entity entity = minecraft.level.getEntity(pending.snapshot.entityId());
                carrier = entity instanceof Player found ? found
                        : minecraft.level.getPlayerByUUID(pending.snapshot.playerId());
                pending.player = carrier;
            }
            complete = carrier != null && OpenYsmModelAdapter.retryCapture(pending.model, carrier,
                    pending.snapshot.x(), pending.snapshot.y(), pending.snapshot.z(),
                    pending.snapshot.bodyYaw());
        }
        if (complete) {
            promote(pending);
        } else {
            PENDING.addLast(pending);
        }
    }

    private static StaticRagdoll promote(PendingRagdoll pending) {
        if (YsmRagdollConfig.MAX_RAGDOLLS.get() <= 0) {
            return null;
        }
        trimForNewRagdoll();
        StaticRagdoll ragdoll = new StaticRagdoll(pending.snapshot, pending.model, pending.createdAtMillis);
        RAGDOLLS.addLast(ragdoll);
        YsmRagdollLog.info("创建布娃娃快照: 玩家=" + pending.snapshot.playerId()
                + ", 位置=" + pending.snapshot.x() + "," + pending.snapshot.y()
                + "," + pending.snapshot.z());
        return ragdoll;
    }

    private static String deathKey(PlayerDeathSnapshot snapshot) {
        return snapshot.playerId() + ":" + snapshot.entityId() + ":"
                + Double.doubleToLongBits(snapshot.x()) + ":"
                + Double.doubleToLongBits(snapshot.y()) + ":"
                + Double.doubleToLongBits(snapshot.z());
    }

    private static void removeOldest(String reason) {
        StaticRagdoll removed = RAGDOLLS.pollFirst();
        if (removed != null) {
            dispose(removed);
            YsmRagdollLog.info("清除最早的布娃娃: " + removed.snapshot().playerId() + ", 原因=" + reason);
        }
    }

    public static void clear(String reason) {
        int count = RAGDOLLS.size();
        for (StaticRagdoll ragdoll : RAGDOLLS) {
            dispose(ragdoll);
        }
        RAGDOLLS.clear();
        PENDING.clear();
        RECENT_DEATHS.clear();
        RECENT_EXPLOSIONS.clear();
        releaseWorldIfUnused();
        YsmSkeletonExtractor.clearCache();
        if (count > 0) {
            YsmRagdollLog.info("清理全部静态布娃娃: 数量=" + count + ", 原因=" + reason);
        }
    }

    /** 接收服务端爆炸并立即作用于当前尸体，同时短暂缓存以覆盖爆炸后才完成的死亡快照。 */
    public static void onExplosion(ExplosionImpulseSnapshot snapshot) {
        long expiresAt = System.currentTimeMillis() + EXPLOSION_REPLAY_WINDOW_MILLIS;
        RECENT_EXPLOSIONS.addLast(new PendingExplosion(snapshot, expiresAt));
        int affected = 0;
        for (PhysicsRagdoll physics : ACTIVE_PHYSICS) {
            if (physics.isChunkLoaded() && applyExplosion(physics, snapshot)) {
                affected++;
            }
        }
        YsmRagdollLog.info("客户端收到爆炸冲击: 中心=" + snapshot.x() + "," + snapshot.y()
                + "," + snapshot.z() + ", 威力=" + snapshot.radius() + ", 受影响布娃娃=" + affected);
    }

    private static boolean applyExplosion(PhysicsRagdoll physics,
                                          ExplosionImpulseSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        try {
            return physics.applyExplosion(minecraft.level, minecraft.player,
                    snapshot.x(), snapshot.y(), snapshot.z(), snapshot.radius());
        } catch (RuntimeException | LinkageError exception) {
            YsmRagdollLog.warn("应用客户端爆炸冲击失败，已跳过当前布娃娃: " + exception);
            return false;
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || RAGDOLLS.isEmpty()) {
            return;
        }
        if (physicsWorld != null && !ACTIVE_PHYSICS.isEmpty()) {
            if (minecraft.isPaused()) {
                physicsWorld.resetFrameClock();
            } else {
                physicsWorld.simulateFrame(ACTIVE_PHYSICS);
            }
        }
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer debugLines = YsmRagdollConfig.SHOW_COLLISION_BOXES.get()
                ? buffers.getBuffer(RenderType.lines()) : null;
        double renderOffsetX = YsmRagdollConfig.RENDER_OFFSET_X.get();
        double renderOffsetY = YsmRagdollConfig.RENDER_OFFSET_Y.get();
        double renderOffsetZ = YsmRagdollConfig.RENDER_OFFSET_Z.get();
        for (StaticRagdoll ragdoll : RAGDOLLS) {
            if (ragdoll.physics != null) {
                ragdoll.physics.updateChunkLoaded(minecraft.level);
                if (!ragdoll.physics.isChunkLoaded()) {
                    continue;
                }
            } else if (!minecraft.level.hasChunkAt(BlockPos.containing(
                    ragdoll.snapshot().x(), ragdoll.snapshot().y(), ragdoll.snapshot().z()))) {
                continue;
            }
            PlayerDeathSnapshot snapshot = ragdoll.snapshot();
            Vec3 displacement = ragdoll.physics == null ? Vec3.ZERO : ragdoll.physics.renderDisplacement();
            double renderX = snapshot.x() + displacement.x + renderOffsetX;
            double renderY = snapshot.y() + displacement.y + renderOffsetY;
            double renderZ = snapshot.z() + displacement.z + renderOffsetZ;
            poseStack.pushPose();
            poseStack.translate(renderX - camera.x, renderY - camera.y, renderZ - camera.z);
            Vec3 lightSample = ragdoll.physics == null
                    ? new Vec3(renderX, renderY + 1.0, renderZ)
                    : ragdoll.physics.center().add(0.0, 0.65, 0.0);
            int light = LevelRenderer.getLightColor(minecraft.level,
                    BlockPos.containing(lightSample));
            OpenYsmModelAdapter.render(ragdoll.model(), snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.bodyYaw(), partialTick, poseStack, buffers, light);
            poseStack.popPose();
            if (debugLines != null && ragdoll.physics != null) {
                ragdoll.physics.renderDebug(poseStack, camera, debugLines);
            }
        }
        buffers.endBatch();
    }

    public static boolean removeLookingAt(Player player) {
        if (!YsmRagdollConfig.MANUAL_REMOVAL.get() || RAGDOLLS.isEmpty()) {
            return false;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(direction.scale(5.0));
        HitResult blockHit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maximum = blockHit.getType() == HitResult.Type.MISS
                ? 5.0 : eye.distanceTo(blockHit.getLocation()) + 0.05;

        StaticRagdoll selected = null;
        double nearest = maximum;
        for (StaticRagdoll ragdoll : RAGDOLLS) {
            PlayerDeathSnapshot snapshot = ragdoll.snapshot();
            Vec3 center = ragdoll.physics == null
                    ? new Vec3(snapshot.x(), snapshot.y() + 0.9, snapshot.z())
                    : ragdoll.physics.center();
            double radius = ragdoll.physics == null ? 0.75 : ragdoll.physics.selectionRadius();
            double hit = raySphere(eye, direction, center, radius);
            if (hit >= 0 && hit < nearest) {
                selected = ragdoll;
                nearest = hit;
            }
        }
        if (selected == null) {
            return false;
        }
        RAGDOLLS.remove(selected);
        dispose(selected);
        YsmRagdollLog.info("玩家空手右键清除布娃娃: " + selected.snapshot().playerId());
        return true;
    }

    private static double raySphere(Vec3 eye, Vec3 direction, Vec3 center, double radius) {
        Vec3 offset = eye.subtract(center);
        double projection = offset.dot(direction);
        double discriminant = projection * projection - offset.lengthSqr() + radius * radius;
        if (discriminant < 0) return -1;
        double root = Math.sqrt(discriminant);
        double near = -projection - root;
        return near >= 0 ? near : Math.max(-1, -projection + root);
    }

    private static void dispose(StaticRagdoll ragdoll) {
        if (ragdoll.physics != null) {
            ACTIVE_PHYSICS.remove(ragdoll.physics);
            ragdoll.physics.dispose();
            ragdoll.physics = null;
        }
        releaseWorldIfUnused();
    }

    private static void releaseWorldIfUnused() {
        if (physicsWorld != null && ACTIVE_PHYSICS.isEmpty()) {
            physicsWorld.clear();
            physicsWorld = null;
        }
    }

    private static final class StaticRagdoll {
        private final PlayerDeathSnapshot snapshot;
        private final OpenYsmModelAdapter.CapturedModel model;
        private final long createdAtMillis;
        private PhysicsRagdoll physics;
        private boolean physicsAttempted;

        private StaticRagdoll(PlayerDeathSnapshot snapshot,
                              OpenYsmModelAdapter.CapturedModel model,
                              long createdAtMillis) {
            this.snapshot = snapshot;
            this.model = model;
            this.createdAtMillis = createdAtMillis;
        }

        private PlayerDeathSnapshot snapshot() {
            return snapshot;
        }

        private OpenYsmModelAdapter.CapturedModel model() {
            return model;
        }
    }

    private static final class PendingRagdoll {
        private final PlayerDeathSnapshot snapshot;
        private final OpenYsmModelAdapter.CapturedModel model;
        private Player player;
        private int ageTicks;
        private final long createdAtMillis;

        private PendingRagdoll(PlayerDeathSnapshot snapshot,
                               OpenYsmModelAdapter.CapturedModel model,
                               Player player, int ageTicks,
                               long createdAtMillis) {
            this.snapshot = snapshot;
            this.model = model;
            this.player = player;
            this.ageTicks = ageTicks;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private record PendingExplosion(ExplosionImpulseSnapshot snapshot, long expiresAtMillis) {
    }
}
