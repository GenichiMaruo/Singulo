package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.block.MixedGlassSlab;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class MixedGlassExplosionMixin implements MixedGlassSlab.ExplosionHits {
    @Shadow @Final private Level level;
    @Shadow @Final private float radius;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;
    @Shadow public abstract List<BlockPos> getToBlow();
    @Unique private int singulo$sampleHalf;
    @Unique private Map<BlockPos, Integer> singulo$glassDamage;

    // The sixth double in the 1.21.1 ray loop is its current sample Y, after the three
    // direction components, their length and the sample X. Vanilla steps only 0.3 blocks.
    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState singulo$sampleGlass(BlockState state, @Local BlockPos pos, @Local(ordinal = 5) double y) {
        if (state.getBlock() instanceof MixedGlassSlab) {
            boolean top = MixedGlassSlab.topAt(pos, y);
            singulo$sampleHalf = top ? MixedGlassSlab.TOP_HIT : MixedGlassSlab.BOTTOM_HIT;
            return MixedGlassSlab.half(state, top);
        }
        singulo$sampleHalf = 0;
        return state;
    }

    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;shouldBlockExplode(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z"))
    private boolean singulo$recordGlassHit(boolean hit, @Local BlockPos pos) {
        if (hit && singulo$sampleHalf != 0) {
            if (singulo$glassDamage == null) singulo$glassDamage = new HashMap<>();
            singulo$glassDamage.merge(pos, singulo$sampleHalf, (a, b) -> a | b);
        }
        return hit;
    }

    @Override public void singulo$addLootDecay(net.minecraft.world.level.storage.loot.LootParams.Builder loot) {
        if (blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY)
            loot.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, radius);
    }

    @Override public int singulo$glassHits(BlockPos pos) {
        return singulo$glassDamage == null ? 0 : singulo$glassDamage.getOrDefault(pos, 0);
    }

    @Inject(method = "finalizeExplosion", at = @At("TAIL"))
    private void singulo$syncSurvivingSlabs(boolean particles, CallbackInfo ci) {
        if (!level.isClientSide && singulo$glassDamage != null) {
            // The explosion packet can only remove whole blocks. Partial hits use the ordinary block update.
            getToBlow().removeIf(pos -> {
                int hits = singulo$glassHits(pos);
                return (hits == MixedGlassSlab.TOP_HIT || hits == MixedGlassSlab.BOTTOM_HIT)
                        && level.getBlockState(pos).getBlock() instanceof GlassJoin.PairSlab;
            });
        }
    }
}
