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

/** 在官方 YSM 2.6.5 最终提交网格前复制一次尸体快照。 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.ooOOo000OOO0ooO0oo0ooooO", remap = false)
abstract class Ysm265MeshCaptureMixin {
    private static final String DRAW_METHOD =
            "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/elfmcys/yesstevemodel/OOoOoooOOooO0o0000o0O0o0;[F[FIIIIFFFF)V";

    @Inject(method = DRAW_METHOD, at = @At("HEAD"), remap = false,
            cancellable = true, require = 0)
    private static void ysmragdoll$captureSnapshot(VertexConsumer consumer, PoseStack.Pose pose,
                                                    @Coerce Object mesh,
                                                    float[] boneTransforms, float[] secondaryState,
                                                    int textureIndex, int renderPartMask,
                                                    int packedLight, int packedOverlay,
                                                    float red, float green, float blue, float alpha,
                                                    CallbackInfo callback) {
        if (OpenYsmModelAdapter.onYsmMeshRender(pose, mesh, boneTransforms, secondaryState,
                textureIndex, renderPartMask, packedLight, packedOverlay,
                red, green, blue, alpha)) {
            callback.cancel();
        }
    }
}
