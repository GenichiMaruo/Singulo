package io.github.genichimaruo.singulo.mixin;

import net.minecraft.client.renderer.block.FluidStateModelSet;
import org.spongepowered.asm.mixin.Final;
import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.client.GlassStairsWater;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 水に浸かったガラスの階段（0.21.0。{@link GlassStairsWater}）: バニラの水をいったん受け取り、ガラスの段にかかる部分を除いて描く。
 * 受け取るために同じ描き方をもう一度呼ぶので、その間はここを素通りする（チャンクは複数のスレッドで作るので、スレッドごとの印）。
 */
@Mixin(FluidRenderer.class)
public abstract class GlassStairsWaterMixin {
    @Unique
    private static final ThreadLocal<Boolean> singulo$capturing = ThreadLocal.withInitial(() -> false);

    @Shadow @Final private FluidStateModelSet fluidModels;

    @Shadow
    public abstract void tesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState);

    @Shadow
    private int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void singulo$glassStairsWater(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState,
                                          FluidState fluidState, CallbackInfo ci) {
        if (!(blockState.getBlock() instanceof GlassJoin.Shaped) || singulo$capturing.get()) {
            return;
        }
        GlassStairsWater.Capture capture = new GlassStairsWater.Capture();
        singulo$capturing.set(true);
        try {
            tesselate(level, pos, layer -> capture, blockState, fluidState);
        } finally {
            singulo$capturing.set(false);
        }
        var model = fluidModels.get(fluidState);
        GlassStairsWater.finish(level, pos, output.getBuilder(model.layer()), blockState, fluidState, capture, getLightCoords(level, pos), model);
        ci.cancel();
    }
}
