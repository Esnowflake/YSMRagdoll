package com.ysmragdoll.event;

import com.ysmragdoll.OpenYsmRagdollMod;
import com.ysmragdoll.network.PlayerDeathSnapshot;
import com.ysmragdoll.network.RagdollNetwork;
import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 只在服务端创建权威死亡快照，客户端物理由接收方自行计算。 */
@Mod.EventBusSubscriber(modid = OpenYsmRagdollMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerDeathEvents {
    private ServerDeathEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        YsmRagdollLog.info("服务端捕获玩家死亡: " + player.getGameProfile().getName()
                + " / " + player.getUUID());
        RagdollNetwork.sendDeathSnapshot(player, PlayerDeathSnapshot.from(player));
    }
}
