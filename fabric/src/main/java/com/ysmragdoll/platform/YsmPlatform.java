package com.ysmragdoll.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.util.Properties;

/** Version-specific renderer bindings are generated from the selected build target. */
public final class YsmPlatform {
    private static final Properties BINDINGS = loadBindings();
    public static final String RENDERER_REGISTRY = BINDINGS.getProperty("registry");
    public static final String MESH_RENDERER = BINDINGS.getProperty("mesh");

    private YsmPlatform() {}

    private static Properties loadBindings() {
        Properties result = new Properties();
        try (var stream = YsmPlatform.class.getResourceAsStream("/ysmragdoll-platform.properties")) {
            if (stream == null) throw new IllegalStateException("Missing YSM platform bindings");
            result.load(stream);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return result;
    }

    public static boolean loaded() {
        return FabricLoader.getInstance().isModLoaded("yes_steve_model");
    }

    public static Object modelState(Player player, ClassLoader loader) {
        // Fabric has no Forge capability; geometry matching uses the existing bone-signature fallback.
        return null;
    }
}
