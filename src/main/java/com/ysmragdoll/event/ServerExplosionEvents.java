package com.ysmragdoll.event;

import com.ysmragdoll.OpenYsmRagdollMod;
import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.network.ExplosionImpulseSnapshot;
import com.ysmragdoll.network.RagdollNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.Mod;

/**
 * 捕获 Forge 通用爆炸结算事件。TNT、苦力怕以及复用原版 Explosion 流程的模组爆炸
 * 都会经过这里；完全绕过 Forge 爆炸事件的自定义伤害实现无法自动识别。
 */
@Mod.EventBusSubscriber(modid = OpenYsmRagdollMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerExplosionEvents {
    private static boolean radiusFallbackLogged;

    private ServerExplosionEvents() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            Explosion explosion = event.getExplosion();
            float radius = explosionRadius(explosion);
            if (!Float.isFinite(radius) || radius <= 0.0F) {
                return;
            }
            Vec3 position = explosion.getPosition();
            RagdollNetwork.sendExplosion(level, new ExplosionImpulseSnapshot(
                    position.x, position.y, position.z, radius));
            String source = explosion.getExploder() == null
                    ? "无实体来源" : explosion.getExploder().getType().toString();
            YsmRagdollLog.info("服务端捕获爆炸: 中心=" + position + ", 威力=" + radius
                    + ", 来源=" + source);
        } catch (RuntimeException | LinkageError exception) {
            // 布娃娃是附加视觉功能，任何兼容问题都不能中断原版爆炸和服务端 tick。
            YsmRagdollLog.warn("处理爆炸冲击失败，已跳过本次布娃娃响应: " + exception);
        }
    }

    private static float explosionRadius(Explosion explosion) {
        try {
            Float radius = ObfuscationReflectionHelper.getPrivateValue(
                    Explosion.class, explosion, "f_46017_");
            if (radius != null && Float.isFinite(radius) && radius > 0.0F) {
                return radius;
            }
        } catch (RuntimeException | LinkageError exception) {
            if (!radiusFallbackLogged) {
                radiusFallbackLogged = true;
                YsmRagdollLog.warn("无法读取 Explosion 原始威力，将使用受影响范围后备值: "
                        + exception);
            }
        }
        // 即使 Forge 映射或第三方核心修改导致反射失败，爆炸也只能降低精度，绝不能崩服。
        double farthestSquared = 0.0;
        Vec3 center = explosion.getPosition();
        for (net.minecraft.core.BlockPos block : explosion.getToBlow()) {
            farthestSquared = Math.max(farthestSquared,
                    center.distanceToSqr(Vec3.atCenterOf(block)));
        }
        return farthestSquared > 0.0 ? (float) Math.sqrt(farthestSquared) : 4.0F;
    }
}
