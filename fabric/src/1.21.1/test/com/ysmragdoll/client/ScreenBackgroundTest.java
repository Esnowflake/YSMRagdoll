package com.ysmragdoll.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ScreenBackgroundTest {
    private static final class TestScreen extends VersionedScreen {
        int rendered;

        TestScreen() {
            super(Component.empty());
            addRenderableOnly((graphics, x, y, tick) -> rendered++);
        }

        @Override
        protected boolean scrollContent(double x, double y, double delta) {
            return false;
        }
    }

    @Test
    void automaticBackgroundNeedsNeitherMinecraftNorGraphics() {
        // Vanilla background requires a renderer and applies blur; ours must do neither.
        assertDoesNotThrow(() -> new TestScreen().renderBackground(null, 0, 0, 0));
    }

    @Test
    void vanillaWidgetPassDoesNotRedrawOrBlurTheContent() {
        TestScreen screen = new TestScreen();
        assertDoesNotThrow(() -> screen.render(null, 0, 0, 0));
        assertEquals(1, screen.rendered);
    }
}
