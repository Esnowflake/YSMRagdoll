package com.ysmragdoll.network;

import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.client.ClientRagdollManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端广播的最小爆炸状态；方块遮挡、距离衰减和刚体冲量由客户端计算。 */
public record ExplosionImpulseSnapshot(double x, double y, double z, float radius) {
    public static void encode(ExplosionImpulseSnapshot message, FriendlyByteBuf buffer) {
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
        buffer.writeFloat(message.radius);
    }

    public static ExplosionImpulseSnapshot decode(FriendlyByteBuf buffer) {
        return new ExplosionImpulseSnapshot(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat());
    }

    public static void handle(ExplosionImpulseSnapshot message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (!Double.isFinite(message.x) || !Double.isFinite(message.y)
                    || !Double.isFinite(message.z) || !Float.isFinite(message.radius)
                    || message.radius <= 0.0F) {
                YsmRagdollLog.warn("忽略非法爆炸快照: " + message);
                return;
            }
            ClientRagdollManager.onExplosion(message);
        }));
        context.setPacketHandled(true);
    }
}
