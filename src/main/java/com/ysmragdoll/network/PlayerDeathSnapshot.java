package com.ysmragdoll.network;

import com.ysmragdoll.client.ClientRagdollManager;
import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 服务端广播的最小死亡状态；模型本身仍由各客户端的 OpenYSM 缓存提供。 */
public record PlayerDeathSnapshot(int entityId, UUID playerId,
                                  double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  float bodyYaw) {
    public static PlayerDeathSnapshot from(ServerPlayer player) {
        return new PlayerDeathSnapshot(player.getId(), player.getUUID(),
                player.getX(), player.getY(), player.getZ(),
                player.getDeltaMovement().x, player.getDeltaMovement().y,
                player.getDeltaMovement().z, player.getYRot());
    }

    public static void encode(PlayerDeathSnapshot message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeUUID(message.playerId);
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
        buffer.writeDouble(message.velocityX);
        buffer.writeDouble(message.velocityY);
        buffer.writeDouble(message.velocityZ);
        buffer.writeFloat(message.bodyYaw);
    }

    public static PlayerDeathSnapshot decode(FriendlyByteBuf buffer) {
        return new PlayerDeathSnapshot(buffer.readVarInt(), buffer.readUUID(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat());
    }

    public static void handle(PlayerDeathSnapshot message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            YsmRagdollLog.info("客户端收到死亡快照: " + message.playerId()
                    + "，实体编号=" + message.entityId());
            ClientRagdollManager.onPlayerDeath(message);
        }));
        context.setPacketHandled(true);
    }
}
