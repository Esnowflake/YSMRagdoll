package com.ysmragdoll.client;

import com.ysmragdoll.network.RagdollNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ClientBootstrap implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RagdollKeyMappings.register();
        RagdollNetwork.registerClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientRagdollEvents.tick());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long start = System.nanoTime();
            ClientRagdollManager.render(context.matrixStack(), context.camera().getPosition(),
                    ClientVersion.partialTick());
            ClientIntensiveLogger.recordFrame();
            ClientPerformanceLogger.recordFrame(System.nanoTime() - start,
                    ClientRagdollManager.ragdollCount(), ClientRagdollManager.physicsRagdollCount());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRagdollEvents.clear());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    public ResourceLocation getFabricId() { return RagdollNetwork.id("reload"); }
                    public void onResourceManagerReload(ResourceManager manager) {
                        ClientRagdollEvents.clear();
                    }
                });
    }
}
