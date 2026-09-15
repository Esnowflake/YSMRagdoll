package com.ysmragdoll.client;

import com.ysmragdoll.OpenYsmRagdollMod;
import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端布娃娃的更新、绘制、手动删除和设置入口。 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = OpenYsmRagdollMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRagdollEvents {
    private ClientRagdollEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        GravityGunController.update(1.0F);
        ClientRagdollManager.tick();
        Minecraft minecraft = Minecraft.getInstance();
        // Drain clicks even while a GUI is open: a queued press must never open the
        // settings after leaving chat, the controls menu, or a previous settings screen.
        boolean requested = false;
        while (RagdollKeyMappings.OPEN_SETTINGS.consumeClick()) {
            requested = true;
        }
        if (requested && !RagdollKeyMappings.OPEN_SETTINGS.isUnbound()
                && minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
            minecraft.setScreen(new YsmRagdollSettingsScreen(null));
            YsmRagdollLog.info("通过快捷键打开设置页面");
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        long startedAt = System.nanoTime();
        ClientRagdollManager.render(event.getPoseStack(), event.getCamera().getPosition(),
                event.getPartialTick());
        ClientIntensiveLogger.recordFrame();
        ClientPerformanceLogger.recordFrame(System.nanoTime() - startedAt,
                ClientRagdollManager.ragdollCount(), ClientRagdollManager.physicsRagdollCount());
    }

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && GravityGunController.isArmed()) {
            if (event.getHand() == InteractionHand.MAIN_HAND) GravityGunController.start();
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null || event.getHand() != InteractionHand.MAIN_HAND
                || !event.isUseItem() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        if (ClientRagdollManager.removeLookingAt(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (GravityGunController.scroll(event.getScrollDelta())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPerformanceLogger.reset();
        ClientRagdollManager.clear("客户端断开连接");
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientPerformanceLogger.reset();
            ClientRagdollManager.clear("客户端世界卸载或切换维度");
        }
    }
}
