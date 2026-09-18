package com.ysmragdoll.mixin;

import com.ysmragdoll.network.ExplosionImpulseSnapshot;
import com.ysmragdoll.network.RagdollNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class ExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;
    @Shadow @Final private float radius;

    @Inject(method = "explode", at = @At("TAIL"))
    private void ysmragdoll$exploded(CallbackInfo callback) {
        if (level instanceof ServerLevel server && Float.isFinite(radius) && radius > 0) {
            RagdollNetwork.sendExplosion(server, new ExplosionImpulseSnapshot(x, y, z, radius));
        }
    }
}
