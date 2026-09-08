package com.ysmragdoll.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** 注册客户端资源重载清理；设置界面只通过玩家绑定的快捷键进入。 */
@OnlyIn(Dist.CLIENT)
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void initialize() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                ClientBootstrap::onRegisterReloadListeners);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // YSM 资源重载时可能释放原来的纹理和网格。清除旧快照可以避免继续访问
        // 已失效的 GPU 资源；重新死亡后会从新资源生成新的独立快照。
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                ClientRagdollManager.clear("客户端资源重新加载"));
    }
}
