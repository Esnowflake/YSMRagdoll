package com.ysmragdoll.network;

import com.ysmragdoll.client.ClientRagdollManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RagdollNetwork {
    private static final ResourceLocation DEATH = id("death_v2");
    private static final ResourceLocation EXPLOSION = id("explosion_v2");

    private RagdollNetwork() {}
    public static ResourceLocation id(String path) { return new ResourceLocation("ysmragdoll", path); }
    public static void register() {}

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(DEATH, (client, handler, buffer, sender) -> {
            var snapshot = PlayerDeathSnapshot.decode(buffer);
            client.execute(() -> ClientRagdollManager.onPlayerDeath(snapshot));
        });
        ClientPlayNetworking.registerGlobalReceiver(EXPLOSION, (client, handler, buffer, sender) -> {
            var snapshot = ExplosionImpulseSnapshot.decode(buffer);
            if (snapshot.valid()) client.execute(() -> ClientRagdollManager.onExplosion(snapshot));
        });
    }

    private static void death(ServerPlayer player, PlayerDeathSnapshot message) {
        if (!ServerPlayNetworking.canSend(player, DEATH)) return;
        var buffer = PacketByteBufs.create();
        PlayerDeathSnapshot.encode(message, buffer);
        ServerPlayNetworking.send(player, DEATH, buffer);
    }

    public static void sendDeathSnapshot(ServerPlayer player, PlayerDeathSnapshot message) {
        death(player, message);
        for (var observer : PlayerLookup.tracking(player)) {
            if (observer != player) death(observer, message);
        }
    }

    public static void sendExplosion(ServerLevel level, ExplosionImpulseSnapshot message) {
        for (var player : PlayerLookup.world(level)) {
            if (!ServerPlayNetworking.canSend(player, EXPLOSION)) continue;
            var buffer = PacketByteBufs.create();
            ExplosionImpulseSnapshot.encode(message, buffer);
            ServerPlayNetworking.send(player, EXPLOSION, buffer);
        }
    }
}
