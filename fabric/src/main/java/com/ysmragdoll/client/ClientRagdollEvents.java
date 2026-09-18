package com.ysmragdoll.client;

import net.minecraft.client.Minecraft;

public final class ClientRagdollEvents {
    private ClientRagdollEvents() {}

    public static void tick() {
        GravityGunController.update(1.0F);
        ClientRagdollManager.tick();
        Minecraft client = Minecraft.getInstance();
        boolean requested = false;
        while (RagdollKeyMappings.OPEN_SETTINGS.consumeClick()) requested = true;
        if (requested && !RagdollKeyMappings.OPEN_SETTINGS.isUnbound()
                && client.level != null && client.player != null && client.screen == null) {
            client.setScreen(new YsmRagdollSettingsScreen(null));
        }
    }

    public static boolean useItem() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) return false;
        if (GravityGunController.isArmed()) {
            GravityGunController.start();
            return true;
        }
        return client.player.getMainHandItem().isEmpty()
                && ClientRagdollManager.removeLookingAt(client.player);
    }

    public static void clear() {
        ClientPerformanceLogger.reset();
        ClientRagdollManager.clear("Fabric world/resource lifecycle");
    }
}
