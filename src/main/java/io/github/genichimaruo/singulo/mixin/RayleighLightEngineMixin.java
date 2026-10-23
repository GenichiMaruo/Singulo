package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.lighting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelLightEngine.class)
public abstract class RayleighLightEngineMixin implements RayleighLightAccess {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Unique private LayerLightEventListener singulo$sky;
    @Override public Level singulo$level() { return levelHeightAccessor instanceof Level level ? level : null; }

    @Inject(method = "getLayerListener", at = @At("RETURN"), cancellable = true)
    private void singulo$sky(LightLayer layer, CallbackInfoReturnable<LayerLightEventListener> ci) {
        Level level = singulo$level();
        if (layer == LightLayer.SKY && level != null) {
            if (singulo$sky == null) singulo$sky = new RayleighLightListener(level, ci.getReturnValue());
            ci.setReturnValue(singulo$sky);
        }
    }
    @Inject(method = "getRawBrightness", at = @At("RETURN"), cancellable = true)
    private void singulo$noon(BlockPos pos, int darken, CallbackInfoReturnable<Integer> ci) {
        Level level = singulo$level();
        if (level != null) ci.setReturnValue(Math.max(ci.getReturnValue(), RayleighSky.light(level, pos)));
    }
    @Inject(method = "checkBlock", at = @At("HEAD"))
    private void singulo$changed(BlockPos pos, CallbackInfo ci) {
        Level level = singulo$level();
        if (level != null) RayleighSky.changed(level, pos);
    }
    @Inject(method = "setLightEnabled", at = @At("HEAD"))
    private void singulo$chunk(ChunkPos pos, boolean enabled, CallbackInfo ci) {
        Level level = singulo$level();
        if (level != null) RayleighSky.chunkChanged(level, pos);
    }
}
