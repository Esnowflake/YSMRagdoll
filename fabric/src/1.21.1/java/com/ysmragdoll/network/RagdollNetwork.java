package com.ysmragdoll.network;

import com.ysmragdoll.client.ClientRagdollManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RagdollNetwork {
    private RagdollNetwork() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ysmragdoll", path); }

    public record Death(PlayerDeathSnapshot value) implements CustomPacketPayload {
        public static final Type<Death> TYPE = new Type<>(id("death_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Death> CODEC = StreamCodec.of(
                (buffer, message) -> PlayerDeathSnapshot.encode(message.value, buffer),
                buffer -> new Death(PlayerDeathSnapshot.decode(buffer)));
        public Type<Death> type() { return TYPE; }
    }

    public record Blast(ExplosionImpulseSnapshot value) implements CustomPacketPayload {
        public static final Type<Blast> TYPE = new Type<>(id("explosion_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Blast> CODEC = StreamCodec.of(
                (buffer, message) -> ExplosionImpulseSnapshot.encode(message.value, buffer),
                buffer -> new Blast(ExplosionImpulseSnapshot.decode(buffer)));
        public Type<Blast> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Death.TYPE, Death.CODEC);
        PayloadTypeRegistry.playS2C().register(Blast.TYPE, Blast.CODEC);
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(Death.TYPE, (message, context) ->
                context.client().execute(() -> ClientRagdollManager.onPlayerDeath(message.value)));
        ClientPlayNetworking.registerGlobalReceiver(Blast.TYPE, (message, context) -> {
            if (message.value.valid()) context.client().execute(() ->
                    ClientRagdollManager.onExplosion(message.value));
        });
    }

    private static void death(ServerPlayer player, PlayerDeathSnapshot value) {
        if (ServerPlayNetworking.canSend(player, Death.TYPE)) ServerPlayNetworking.send(player, new Death(value));
    }

    public static void sendDeathSnapshot(ServerPlayer player, PlayerDeathSnapshot message) {
        death(player, message);
        for (var observer : PlayerLookup.tracking(player)) {
            if (observer != player) death(observer, message);
        }
    }

    public static void sendExplosion(ServerLevel level, ExplosionImpulseSnapshot message) {
        for (var player : PlayerLookup.world(level)) {
            if (ServerPlayNetworking.canSend(player, Blast.TYPE)) ServerPlayNetworking.send(player, new Blast(message));
        }
    }
}
