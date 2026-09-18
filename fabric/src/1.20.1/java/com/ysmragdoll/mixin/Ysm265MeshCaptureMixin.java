package com.ysmragdoll.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ysmragdoll.client.OpenYsmModelAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.oo0o0o0O0o0o00OOOOOooOOo", remap = false)
abstract class Ysm265MeshCaptureMixin {
    // YSM names are not mapped, but Minecraft descriptors differ in dev and production.
    @Inject(method = {
            "OoOo0OooO0OOO0Oo00000o00(Lnet/minecraft/class_4588;Lnet/minecraft/class_4587$class_4665;Lcom/elfmcys/yesstevemodel/oO0OoOoO0000oOOO0OO0O0oo;[F[FIIIIFFFF)V",
            "OoOo0OooO0OOO0Oo00000o00(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/elfmcys/yesstevemodel/oO0OoOoO0000oOOO0OO0O0oo;[F[FIIIIFFFF)V"
    }, at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void ysmragdoll$capture(VertexConsumer consumer, PoseStack.Pose pose,
                                         @Coerce Object mesh, float[] bones, float[] secondary,
                                         int texture, int mask, int light, int overlay,
                                         float red, float green, float blue, float alpha,
                                         CallbackInfo callback) {
        if (OpenYsmModelAdapter.onYsmMeshRender(pose, mesh, bones, secondary,
                texture, mask, light, overlay, red, green, blue, alpha)) callback.cancel();
    }
}
