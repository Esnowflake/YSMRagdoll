package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Receives auxiliary YSM layers without submitting vertices to the world buffer. */
final class DiscardingVertexConsumer implements VertexConsumer {
    public VertexConsumer vertex(double x, double y, double z) { return this; }
    public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
    public VertexConsumer uv(float u, float v) { return this; }
    public VertexConsumer overlayCoords(int u, int v) { return this; }
    public VertexConsumer uv2(int u, int v) { return this; }
    public VertexConsumer normal(float x, float y, float z) { return this; }
    public void endVertex() {}
    public void defaultColor(int red, int green, int blue, int alpha) {}
    public void unsetDefaultColor() {}
}
