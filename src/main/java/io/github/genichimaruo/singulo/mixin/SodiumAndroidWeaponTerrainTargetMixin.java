package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.genichimaruo.singulo.client.AndroidWeaponLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium owns the color/depth target instead of using ChunkSectionLayerGroup's. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass", remap = false)
abstract class SodiumAndroidWeaponTerrainTargetMixin {
    @Shadow public abstract boolean isTranslucent();

    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private void singulo$weaponTerrainTarget(CallbackInfoReturnable<RenderTarget> ci) {
        if (isTranslucent()) {
            var target = AndroidWeaponLight.terrainTarget();
            if (target != null) ci.setReturnValue(target);
        }
    }
}
