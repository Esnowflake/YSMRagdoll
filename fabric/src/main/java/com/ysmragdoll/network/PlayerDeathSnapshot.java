package com.ysmragdoll.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record PlayerDeathSnapshot(int entityId, UUID playerId, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ, float bodyYaw) {
    public static PlayerDeathSnapshot from(ServerPlayer player) {
        return new PlayerDeathSnapshot(player.getId(), player.getUUID(),
                player.getX(), player.getY(), player.getZ(), player.getDeltaMovement().x,
                player.getDeltaMovement().y, player.getDeltaMovement().z, player.getYRot());
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
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
    }
}
