package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FleeSunGoal.class)
public abstract class RayleighFleeSunMixin {
    @Shadow @Final protected PathfinderMob mob;
    @Redirect(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean singulo$localDay(Level level) {
        return level.isDay() || RayleighSky.sunlit(level, mob.blockPosition());
    }
}
