package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.SodiumRayleighView;
import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumRayleighSliceMixin implements SodiumRayleighView {
    @Shadow @Final private ClientLevel level;
    @Unique private RayleighSky.LightSnapshot singulo$sunlight;
    @Unique private long singulo$epoch;

    @Inject(method = "copyData", at = @At("RETURN"))
    private void singulo$capture(@Coerce Object context, CallbackInfo ci) {
        singulo$sunlight = RayleighSky.snapshot(level);
        singulo$epoch++;
    }

    @Override public RayleighSky.LightSnapshot singulo$sunlight() { return singulo$sunlight; }
    @Override public long singulo$lightEpoch() { return singulo$epoch; }
}
