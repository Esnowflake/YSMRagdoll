package com.ysmragdoll.network;

import com.ysmragdoll.OpenYsmRagdollMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道允许远端缺失。这样未安装本模组的客户端仍可加入服务器，
 * 只有声明了该通道的客户端才会处理布娃娃死亡快照。
 */
public final class RagdollNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(OpenYsmRagdollMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            // 允许完全未安装本模组的端加入，但拒绝已安装旧协议的端，避免收到未知包编号。
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .simpleChannel();

    private RagdollNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(PlayerDeathSnapshot.class, 0)
                .encoder(PlayerDeathSnapshot::encode)
                .decoder(PlayerDeathSnapshot::decode)
                .consumerMainThread(PlayerDeathSnapshot::handle)
                .add();
        CHANNEL.messageBuilder(ExplosionImpulseSnapshot.class, 1)
                .encoder(ExplosionImpulseSnapshot::encode)
                .decoder(ExplosionImpulseSnapshot::decode)
                .consumerMainThread(ExplosionImpulseSnapshot::handle)
                .add();
    }

    public static void sendDeathSnapshot(ServerPlayer player, PlayerDeathSnapshot snapshot) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), snapshot);
    }

    /**
     * 向爆炸所在维度广播一次冲击。客户端只计算自己持有的布娃娃，包体只有中心和威力。
     * 广播整个维度可保证暂时远离尸体的观察者回来后仍保留正确的物理结果。
     */
    public static void sendExplosion(net.minecraft.server.level.ServerLevel level,
                                     ExplosionImpulseSnapshot snapshot) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), snapshot);
    }
}
