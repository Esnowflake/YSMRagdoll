package com.ysmragdoll.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class RagdollKeyMappings {
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.ysmragdoll.open_settings", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.ysmragdoll");

    private RagdollKeyMappings() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS);
    }
}
