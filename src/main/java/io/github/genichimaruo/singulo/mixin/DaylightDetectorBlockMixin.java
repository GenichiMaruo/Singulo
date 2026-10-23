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

    // Keep the native natural/inverted formula, then combine it with local noon. Do this
    // before vanilla writes POWER so a steady panel cannot produce transient redstone pulses.
    @Inject(method = "updateSignalStrength", at = @At("HEAD"), cancellable = true)
    private static void singulo$rayleighNoon(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        int daylight = RayleighSky.light(level, pos);
        if (daylight == 0) return;
        boolean inverted = state.getValue(DaylightDetectorBlock.INVERTED);
        int natural = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) - level.getSkyDarken();
        if (inverted) natural = 15 - natural;
        else if (natural > 0) {
            float angle = level.getSunAngle(1.0F);
            angle += ((angle < Math.PI ? 0 : (float) (Math.PI * 2)) - angle) * .2F;
            natural = Math.round(natural * net.minecraft.util.Mth.cos(angle));
        }
        natural = net.minecraft.util.Mth.clamp(natural, 0, 15);
        int signal = inverted ? Math.min(natural, 15 - daylight) : Math.max(natural, daylight);
        if (state.getValue(DaylightDetectorBlock.POWER) != signal) level.setBlock(pos, state.setValue(DaylightDetectorBlock.POWER, signal), 3);
        ci.cancel();
    }
}
