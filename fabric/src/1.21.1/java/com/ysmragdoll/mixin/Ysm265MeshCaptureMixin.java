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
@Mixin(targets = "com.elfmcys.yesstevemodel.o0oOOOOooOOo0OOo00oo00O0", remap = false)
abstract class Ysm265MeshCaptureMixin {
    @Inject(method = {
            "OO0ooo0OooOoO0OoO0ooOO0o(Lnet/minecraft/class_4588;Lnet/minecraft/class_4587$class_4665;Lcom/elfmcys/yesstevemodel/OOO0o0oOO00oO0ooO0OoO0oO;[F[FIIIIFFFF)V",
            "OO0ooo0OooOoO0OoO0ooOO0o(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/elfmcys/yesstevemodel/OOO0o0oOO00oO0ooO0OoO0oO;[F[FIIIIFFFF)V"
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
