package com.ysmragdoll.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

abstract class VersionedScreen extends Screen {
    protected VersionedScreen(Component title) { super(title); }

    protected abstract boolean scrollContent(double x, double y, double delta);

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        // Screen.render calls this after our content; the shared screen owns its background.
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollContent(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
}
