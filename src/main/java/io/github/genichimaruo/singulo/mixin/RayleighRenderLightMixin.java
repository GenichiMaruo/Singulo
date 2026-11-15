package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class RayleighRenderLightMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void singulo$localNoon(LevelRenderer.BrightnessGetter getter, BlockAndLightGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> ci) {
        if (!state.emissiveRendering(level, pos)) ci.setReturnValue(RayleighLighting.pack(level, pos, ci.getReturnValue()));
    }
}
