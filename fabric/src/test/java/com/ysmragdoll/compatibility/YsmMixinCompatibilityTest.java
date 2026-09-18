package com.ysmragdoll.compatibility;

import com.ysmragdoll.platform.YsmPlatform;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Loads transformed classes without starting a window or initializing YSM native code. */
@EnabledIfSystemProperty(named = "ysmragdoll.compatibilityTest", matches = "true")
class YsmMixinCompatibilityTest {
    @Test
    void officialYsmRendererReceivesCaptureHook() throws Exception {
        assertTrue(FabricLoader.getInstance().isModLoaded("yes_steve_model"));
        Class<?> renderer = Class.forName(YsmPlatform.MESH_RENDERER, false, getClass().getClassLoader());
        assertTrue(Arrays.stream(renderer.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("ysmragdoll$capture")),
                "YSM mesh renderer must contain the injected capture callback");
    }

    @Test
    void vanillaTargetsReceiveInputAndExplosionHooks() throws Exception {
        for (String[] target : new String[][] {
                {"net.minecraft.client.Minecraft", "ysmragdoll$"},
                {"net.minecraft.client.MouseHandler", "ysmragdoll$scroll"},
                {"net.minecraft.world.level.Explosion", "ysmragdoll$exploded"}
        }) {
            Class<?> type = Class.forName(target[0], false, getClass().getClassLoader());
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains(target[1])), target[0]);
        }
    }
}
