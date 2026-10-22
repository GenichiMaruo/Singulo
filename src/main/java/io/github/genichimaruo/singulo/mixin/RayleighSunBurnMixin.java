package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Mob.class)
public abstract class RayleighSunBurnMixin {
    @Redirect(method = "isSunBurnTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean singulo$localDay(Level level) {
        Mob mob = (Mob) (Object) this;
        return level.isDay() || RayleighSky.sunlit(level, BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ()));
    }
}
