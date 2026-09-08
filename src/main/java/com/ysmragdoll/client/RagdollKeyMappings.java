package com.ysmragdoll.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ysmragdoll.OpenYsmRagdollMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 注册可在 Minecraft 控制设置中重新绑定的设置快捷键。 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = OpenYsmRagdollMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RagdollKeyMappings {
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.ysmragdoll.open_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.ysmragdoll");

    private RagdollKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }
}
