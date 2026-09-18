package com.ysmragdoll;

import com.mojang.logging.LogUtils;
import com.ysmragdoll.config.FabricConfig;
import com.ysmragdoll.network.PlayerDeathSnapshot;
import com.ysmragdoll.network.RagdollNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class OpenYsmRagdollMod implements ModInitializer {
    public static final String MOD_ID = "ysmragdoll";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        FabricConfig.register();
        RagdollNetwork.register();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                RagdollNetwork.sendDeathSnapshot(player, PlayerDeathSnapshot.from(player));
            }
        });
        YsmRagdollLog.info("YSM Ragdoll Fabric initialized");
    }
}
