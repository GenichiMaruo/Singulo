package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.genichimaruo.singulo.client.AndroidWeaponLight;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionLayerGroup.class)
abstract class AndroidWeaponTerrainTargetMixin {
    @Inject(method = "outputTarget", at = @At("HEAD"), cancellable = true)
    private void singulo$weaponTerrainTarget(CallbackInfoReturnable<RenderTarget> ci) {
        if ((Object) this == ChunkSectionLayerGroup.TRANSLUCENT) {
            var target = AndroidWeaponLight.terrainTarget();
            if (target != null) ci.setReturnValue(target);
        }
    }
}
