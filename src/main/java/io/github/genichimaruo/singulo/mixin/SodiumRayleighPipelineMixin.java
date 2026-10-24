package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.SodiumRayleighPipeline;
import java.util.EnumMap;
import net.caffeinemc.mods.sodium.client.model.light.*;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider", remap = false)
public abstract class SodiumRayleighPipelineMixin {
    @Shadow @Final private EnumMap<LightMode, LightPipeline> lighters;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void singulo$separateSky(LightDataAccess cache, CallbackInfo ci) {
        lighters.replaceAll((mode, pipeline) -> new SodiumRayleighPipeline(cache, mode, pipeline));
    }
}
