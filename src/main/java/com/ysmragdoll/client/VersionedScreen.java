package com.ysmragdoll.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class VersionedScreen extends Screen {
    protected VersionedScreen(Component title) { super(title); }

    protected abstract boolean scrollContent(double x, double y, double delta);

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return scrollContent(x, y, delta) || super.mouseScrolled(x, y, delta);
    }
}
