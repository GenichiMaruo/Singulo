package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Invalidate before queued light work or neighbor updates, only for changes affecting light. */
@Mixin(LevelChunk.class)
public abstract class RayleighChunkLightMixin {
    @Shadow @Final private Level level;
    @Redirect(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LightEngine;hasDifferentLightProperties(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean singulo$invalidate(BlockGetter view, BlockPos pos, BlockState before, BlockState after) {
        boolean changed = LightEngine.hasDifferentLightProperties(view, pos, before, after);
        if (changed) RayleighSky.changed(level, pos);
        return changed;
    }
}
