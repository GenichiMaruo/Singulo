package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 点いているレイリー散乱パネルの下の日照センサーは、ずっと真昼の値を出す（{@link RayleighSky}）。 */
@Mixin(DaylightDetectorBlock.class)
public abstract class DaylightDetectorBlockMixin {
    @Inject(method = "updateSignalStrength", at = @At("HEAD"), cancellable = true)
    private static void singulo$rayleighNoon(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!RayleighSky.litAbove(level, pos)) {
            return;
        }
        int signal = RayleighSky.noonSignal(state.getValue(DaylightDetectorBlock.INVERTED));
        if (state.getValue(DaylightDetectorBlock.POWER) != signal) {
            level.setBlock(pos, state.setValue(DaylightDetectorBlock.POWER, signal), 3);
        }
        ci.cancel();
    }
}
