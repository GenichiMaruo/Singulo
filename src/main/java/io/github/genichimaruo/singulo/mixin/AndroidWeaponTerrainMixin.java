package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.genichimaruo.singulo.client.AndroidWeaponLight;
import io.github.genichimaruo.singulo.client.WeaponTerrainHook;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class AndroidWeaponTerrainMixin {
    /**
     * Keep the normal terrain draw, including a replacement chunk renderer, inside the selected target.
     * Optional: if another mod changes renderLevel so this matches nothing, the game still starts;
     * WeaponTerrainHook never sees a wrapped draw, warns once and AndroidWeaponLight keeps vanilla buffers.
     */
    @WrapOperation(method = "renderLevel", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void singulo$weaponTerrain(LevelRenderer renderer, RenderType type, double x, double y, double z,
                                       Matrix4f view, Matrix4f projection, Operation<Void> original) {
        WeaponTerrainHook.onTerrainDraw();
        boolean capturing = type == RenderType.translucent() && AndroidWeaponLight.beginTerrain();
        try {
            original.call(renderer, type, x, y, z, view, projection);
        } finally {
            // Normally already composited before AFTER_TRANSLUCENT_BLOCKS listeners run.
            if (capturing) AndroidWeaponLight.finishTerrain();
        }
    }
}
