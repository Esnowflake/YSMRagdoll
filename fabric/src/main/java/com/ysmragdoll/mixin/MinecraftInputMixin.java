package com.ysmragdoll.mixin;

import com.ysmragdoll.client.ClientRagdollEvents;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftInputMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void ysmragdoll$useItem(CallbackInfo callback) {
        if (ClientRagdollEvents.useItem()) callback.cancel();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void ysmragdoll$changeWorld(CallbackInfo callback) {
        ClientRagdollEvents.clear();
    }
}
