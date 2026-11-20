package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.genichimaruo.singulo.block.MixedGlassSlab;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerExplosion.class)
abstract class MixedGlassExplosionMixin implements MixedGlassSlab.ExplosionHits {
    @Unique private int singulo$sampleHalf;
    @Unique private Map<BlockPos, Integer> singulo$glassDamage;

    // The sixth double in the 26.1.2 ray loop is its current sample Y, after the three
    // direction components, their length and the sample X. Vanilla steps only 0.3 blocks.
    @ModifyExpressionValue(method = "calculateExplodedPositions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState singulo$sampleGlass(BlockState state, @Local BlockPos pos, @Local(ordinal = 5) double y) {
        if (state.getBlock() instanceof MixedGlassSlab) {
            boolean top = MixedGlassSlab.topAt(pos, y);
            singulo$sampleHalf = top ? MixedGlassSlab.TOP_HIT : MixedGlassSlab.BOTTOM_HIT;
            return MixedGlassSlab.half(state, top);
        }
        singulo$sampleHalf = 0;
        return state;
    }

    @ModifyExpressionValue(method = "calculateExplodedPositions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;shouldBlockExplode(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z"))
    private boolean singulo$recordGlassHit(boolean hit, @Local BlockPos pos) {
        if (hit && singulo$sampleHalf != 0) {
            if (singulo$glassDamage == null) singulo$glassDamage = new HashMap<>();
            singulo$glassDamage.merge(pos, singulo$sampleHalf, (a, b) -> a | b);
        }
        return hit;
    }

    @Override public int singulo$glassHits(BlockPos pos) {
        return singulo$glassDamage == null ? 0 : singulo$glassDamage.getOrDefault(pos, 0);
    }

}
