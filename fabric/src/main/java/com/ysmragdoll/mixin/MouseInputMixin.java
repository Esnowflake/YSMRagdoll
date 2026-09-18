package com.ysmragdoll.mixin;

import com.ysmragdoll.client.GravityGunController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseInputMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ysmragdoll$scroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().getWindow() && client.screen == null
                && GravityGunController.scroll(vertical)) callback.cancel();
    }
}
