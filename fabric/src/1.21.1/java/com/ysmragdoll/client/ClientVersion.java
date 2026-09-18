package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public final class ClientVersion {
    private ClientVersion() {}

    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    public static Checkbox checkbox(Font font, int x, Component label, boolean selected,
                                     Consumer<Boolean> changed) {
        return Checkbox.builder(Component.empty(), font).pos(x, 0).selected(selected)
                .tooltip(Tooltip.create(label))
                .onValueChange((box, value) -> changed.accept(value)).build();
    }

    public static void background(Screen screen, GuiGraphics graphics, int x, int y, float tick) {
        graphics.fill(0, 0, screen.width, screen.height, 0x88000000);
    }

    public static void lineVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, Vec3 normal) {
        consumer.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(51, 255, 204, 255)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
