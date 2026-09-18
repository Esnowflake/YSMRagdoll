package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class CaptureBufferTest {
    @Test
    public void captureLayersCanUseVanillaDoubleConsumer() throws Exception {
        MultiBufferSource buffers = captureBuffers();
        emit(VertexMultiConsumer.create(buffers.getBuffer(null), buffers.getBuffer(null)));
    }

    @Test
    public void captureLayersCanUseVanillaMultipleConsumer() throws Exception {
        MultiBufferSource buffers = captureBuffers();
        emit(VertexMultiConsumer.create(new VertexConsumer[]{
                buffers.getBuffer(null), buffers.getBuffer(null), buffers.getBuffer(null)}));
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

    private static void emit(VertexConsumer consumer) {
        consumer.addVertex(1, 2, 3).setColor(255, 255, 255, 255).setUv(0, 0)
                .setUv1(0, 0).setUv2(240, 240).setNormal(0, 1, 0);
    }
}
