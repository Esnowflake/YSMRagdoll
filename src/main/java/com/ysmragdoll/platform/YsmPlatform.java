package com.ysmragdoll.platform;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

/** Forge-specific access to YSM; shared rendering never links Fabric to Forge APIs. */
public final class YsmPlatform {
    public static final String RENDERER_REGISTRY = "com.elfmcys.yesstevemodel.OOoO00ooO00OOO00O0o0000O";
    public static final String MESH_RENDERER = "com.elfmcys.yesstevemodel.ooOOo000OOO0ooO0oo0ooooO";

    private YsmPlatform() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("yes_steve_model");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object modelState(Player player, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> provider = Class.forName(
                "com.elfmcys.yesstevemodel.O0OooOo0oOOoOoOoOooO000o", false, loader);
        Field field = provider.getDeclaredField("Oo0Oo0o00O00Oo0OOoOOoooo");
        field.setAccessible(true);
        Object handle = field.get(null);
        return handle instanceof Capability capability
                ? player.getCapability(capability).resolve().orElse(null) : null;
    }
}
