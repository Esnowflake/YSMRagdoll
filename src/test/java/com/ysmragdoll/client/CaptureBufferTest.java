package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotSame;

public class CaptureBufferTest {
    @Test
    public void captureLayersCanUseVanillaDoubleConsumer() throws Exception {
        MultiBufferSource buffers = captureBuffers();
        // The discard source ignores render types, so no renderer or GPU is needed.
        VertexConsumer combined = VertexMultiConsumer.create(
                buffers.getBuffer(null), buffers.getBuffer(null));
        emitVertex(combined);
    }

    @Test
    public void captureLayersCanUseVanillaMultipleConsumer() throws Exception {
        MultiBufferSource buffers = captureBuffers();
        VertexConsumer combined = VertexMultiConsumer.create(new VertexConsumer[]{
                buffers.getBuffer(null), buffers.getBuffer(null), buffers.getBuffer(null)});
        emitVertex(combined);
    }

    @Test
    public void repeatedRequestsDoNotShareDelegateIdentity() throws Exception {
        MultiBufferSource buffers = captureBuffers();
        assertNotSame(buffers.getBuffer(null), buffers.getBuffer(null));
    }

    private static MultiBufferSource captureBuffers() throws Exception {
        Field field = OpenYsmModelAdapter.class.getDeclaredField("DISCARDING_BUFFERS");
        field.setAccessible(true);
        return (MultiBufferSource) field.get(null);
    }

    private static void emitVertex(VertexConsumer consumer) {
        consumer.defaultColor(255, 255, 255, 255);
        consumer.unsetDefaultColor();
        consumer.vertex(1, 2, 3).color(255, 255, 255, 255).uv(0, 0)
                .overlayCoords(0, 0).uv2(240, 240).normal(0, 1, 0).endVertex();
        consumer.vertex(1, 2, 3, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0);
    }
}
