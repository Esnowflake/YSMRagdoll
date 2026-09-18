package com.ysmragdoll.network;

import net.minecraft.network.FriendlyByteBuf;

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

    public boolean valid() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(radius) && radius > 0;
    }
}
