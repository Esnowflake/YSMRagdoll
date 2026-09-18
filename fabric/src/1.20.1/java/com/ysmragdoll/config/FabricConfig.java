package com.ysmragdoll.config;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;

public final class FabricConfig {
    private FabricConfig() {}
    public static void register() {
        ForgeConfigRegistry.INSTANCE.register("ysmragdoll", ModConfig.Type.CLIENT,
                YsmRagdollConfig.SPEC, "ysmragdoll-client.toml");
    }
}
