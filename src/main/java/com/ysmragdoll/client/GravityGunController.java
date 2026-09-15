package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ysmragdoll.client.physics.PhysicsGrab;
import com.ysmragdoll.config.YsmRagdollConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.vecmath.Vector3f;

/** Local-only input and beam; never sends item interaction or physics packets. */
public final class GravityGunController {
    private static final double MIN_DISTANCE = 1.5;
    private static final double MAX_DISTANCE = 12.0;
    private static PhysicsGrab grab;
    private static double distance;
    private static boolean pressHandled;

    private GravityGunController() {}

    public static boolean isArmed() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        return mc.level != null && player != null && mc.screen == null && !mc.isPaused()
                && player.isAlive() && !player.isSpectator()
                && YsmRagdollConfig.GRAVITY_GUN_MODE.get()
                && player.getMainHandItem().is(Items.STICK);
    }

    /** One attempt per press, even if vanilla repeats the use action while held. */
    public static void start() {
        if (!isArmed() || pressHandled) return;
        pressHandled = true;
        Player player = Minecraft.getInstance().player;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = clippedTarget(player, eye, player.getViewVector(1.0F), MAX_DISTANCE);
        grab = ClientRagdollManager.grab(eye, end);
        if (grab != null) {
            Vector3f anchor = grab.anchor();
            distance = clampDistance(eye.distanceTo(new Vec3(anchor.x, anchor.y, anchor.z)));
        }
    }

    public static void update(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isArmed() || !mc.options.keyUse.isDown()) {
            release();
            pressHandled = false;
            return;
        }
        if (grab == null) return;
        if (!grab.isActive()) { release(); return; }
        Player player = mc.player;
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 anchor = clippedTarget(player, eye, player.getViewVector(partialTick), distance);
        grab.moveTo(new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z));
    }

    public static boolean scroll(double delta) {
        update(1.0F);
        if (grab == null || !grab.isActive()) return false;
        if (Double.isFinite(delta)) distance = clampDistance(distance + delta * 0.5);
        update(1.0F);
        return true;
    }

    static double clampDistance(double value) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, value));
    }

    private static Vec3 clippedTarget(Player player, Vec3 eye, Vec3 direction, double reach) {
        Vec3 end = eye.add(direction.scale(reach));
        HitResult hit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return end;
        return eye.add(direction.scale(Math.max(0, eye.distanceTo(hit.getLocation()) - 0.05)));
    }

    public static void release() {
        if (grab != null) { grab.close(); grab = null; }
    }

    static void render(PoseStack stack, Vec3 camera, float partialTick, MultiBufferSource buffers) {
        if (grab == null || !grab.isActive()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 direction = player.getViewVector(partialTick);
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        double hand = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 from = player.getEyePosition(partialTick).add(direction.scale(0.35))
                .add(right.scale(hand * 0.22)).add(0, -0.20, 0).subtract(camera);
        Vector3f anchor = grab.anchor();
        Vec3 to = new Vec3(anchor.x, anchor.y, anchor.z).add(
                YsmRagdollConfig.RENDER_OFFSET_X.get(), YsmRagdollConfig.RENDER_OFFSET_Y.get(),
                YsmRagdollConfig.RENDER_OFFSET_Z.get()).subtract(camera);
        Vec3 normal = to.subtract(from).normalize();
        if (normal.lengthSqr() < 1.0E-8) return;
        var consumer = buffers.getBuffer(RenderType.lines());
        var pose = stack.last();
        consumer.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(0.2F, 0.65F, 1.0F, 0.95F)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
        consumer.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(0.4F, 0.85F, 1.0F, 0.95F)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}
