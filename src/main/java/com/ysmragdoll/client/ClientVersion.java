package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Minecraft 1.20.1 client API bindings, shared by Forge and Fabric. */
public final class ClientVersion {
    private ClientVersion() {}

    public static float partialTick() {
        return Minecraft.getInstance().getFrameTime();
    }

    public static Checkbox checkbox(Font font, int x, Component label, boolean selected,
                                     Consumer<Boolean> changed) {
        return new Checkbox(x, 0, 20, 20, label, selected, false) {
            @Override
            public void onPress() {
                super.onPress();
                changed.accept(selected());
            }
        };
    }

    public static void background(Screen screen, GuiGraphics graphics, int x, int y, float tick) {
        screen.renderBackground(graphics);
    }

    public static void lineVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, Vec3 normal) {
        consumer.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(51, 255, 204, 255)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}
