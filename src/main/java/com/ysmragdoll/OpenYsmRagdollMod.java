package com.ysmragdoll;

import com.mojang.logging.LogUtils;
import com.ysmragdoll.network.RagdollNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.ysmragdoll.config.YsmRagdollConfig;
import com.ysmragdoll.client.ClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.slf4j.Logger;

/** 模组公共入口；此类不会引用任何仅客户端存在的 Minecraft 类。 */
@Mod(OpenYsmRagdollMod.MOD_ID)
public final class OpenYsmRagdollMod {
    public static final String MOD_ID = "ysmragdoll";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OpenYsmRagdollMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, YsmRagdollConfig.SPEC,
                "ysmragdoll-client.toml");
        RagdollNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::initialize);
        YsmRagdollLog.info("ysmragdoll 初始化，协议版本 2");
    }
}
