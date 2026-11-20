package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.genichimaruo.singulo.client.AndroidWeaponLight;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class AndroidWeaponTerrainMixin {
    /** Keep the normal terrain draw, including a replacement chunk renderer, inside the selected target. */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"))
    private void singulo$weaponTerrain(LevelRenderer renderer, RenderType type, PoseStack pose, double x, double y, double z,
                                       Matrix4f projection, Operation<Void> original) {
        boolean capturing = type == RenderType.translucent() && AndroidWeaponLight.beginTerrain();
        try {
            original.call(renderer, type, pose, x, y, z, projection);
        } finally {
            // Normally already composited before AFTER_TRANSLUCENT_BLOCKS listeners run.
            if (capturing) AndroidWeaponLight.finishTerrain();
        }
    }
}
