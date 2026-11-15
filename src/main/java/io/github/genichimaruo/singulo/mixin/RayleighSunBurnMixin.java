package io.github.genichimaruo.singulo.mixin;
import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Extend the environment's burn permission, preserving vanilla rain, brightness and random checks. */
@Mixin(Mob.class)
public abstract class RayleighSunBurnMixin {
    @ModifyExpressionValue(method="isSunBurnTick",at=@At(value="INVOKE",target="Ljava/lang/Boolean;booleanValue()Z"))
    private boolean singulo$localDay(boolean original) {
        Mob mob=(Mob)(Object)this;
        return original || RayleighSky.sunlit(mob.level(),BlockPos.containing(mob.getX(),mob.getEyeY(),mob.getZ()));
    }
}
