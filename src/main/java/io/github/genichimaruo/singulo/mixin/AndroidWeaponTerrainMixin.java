package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.textures.GpuSampler;
import io.github.genichimaruo.singulo.client.AndroidWeaponLight;
import io.github.genichimaruo.singulo.client.WeaponTerrainHook;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class AndroidWeaponTerrainMixin {
    /**
     * Preserve the vanilla terrain pass; redirect only the translucent target.
     * Optional: the target is a compiler-generated lambda. If it matches nothing, the game still starts;
     * WeaponTerrainHook never sees a wrapped draw, warns once and AndroidWeaponLight keeps vanilla buffers.
     */
    @WrapOperation(method = "lambda$addMainPass$0", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    private void singulo$weaponTerrain(ChunkSectionsToRender sections, ChunkSectionLayerGroup group,
                                       GpuSampler sampler, Operation<Void> original) {
        WeaponTerrainHook.onTerrainDraw();
        boolean capturing = group == ChunkSectionLayerGroup.TRANSLUCENT && AndroidWeaponLight.beginTerrain();
        try {
            original.call(sections, group, sampler);
        } finally {
            if (capturing) AndroidWeaponLight.finishTerrain();
        }
    }
}
