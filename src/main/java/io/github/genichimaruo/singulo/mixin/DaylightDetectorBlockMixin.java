package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 点いているレイリー散乱パネルの下の日照センサーは、ずっと真昼の値を出す（{@link RayleighSky}）。 */
@Mixin(DaylightDetectorBlock.class)
public abstract class DaylightDetectorBlockMixin {
    @Shadow
    private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
        throw new AssertionError();
    }

    /** Vanilla omits the ticker entirely in dimensions without skylight. */
    @Inject(method = "getTicker", at = @At("RETURN"), cancellable = true)
    private <T extends BlockEntity> void singulo$skylessTicker(Level level, BlockState state, BlockEntityType<T> type,
            CallbackInfoReturnable<BlockEntityTicker<T>> ci) {
        if (!level.isClientSide && !level.dimensionType().hasSkyLight() && type == BlockEntityType.DAYLIGHT_DETECTOR) {
            ci.setReturnValue((l, p, s, be) -> {
                if (l.getGameTime() % 20L == 0L) updateSignalStrength(s, l, p);
            });
        }
    }

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
