package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 雷ガラス（フルグライト）のでき方。雷が砂に落ちると、砂が一瞬で溶けて固まる（実在の閃電岩）。
 * 避雷針に落ちたときは、避雷針の下の砂がなる（避雷針を砂の上に立てておけば、雷雨のたびに少しずつ集まる）。
 * ケラウノス放電塔の人工の雷も同じように砂を溶かす（空から本物の雷は落とさない）。
 */
public final class Fulgurite {
    private Fulgurite() {}

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        strike(level, bolt.blockPosition());
    }

    /** strikeAt に雷が落ちた。避雷針ならその下、そうでなければ真下の砂を雷ガラス塊にする。作ったら true。 */
    public static boolean strike(ServerLevel level, BlockPos strikeAt) {
        BlockPos p = strikeAt;
        for (int i = 0; i < 2 && !level.getBlockState(p).is(Blocks.LIGHTNING_ROD) && level.getBlockState(p).isAir(); i++) {
            p = p.below();
        }
        // 避雷針（重ねてあってもよい）の根元へ
        while (level.getBlockState(p).is(Blocks.LIGHTNING_ROD)) {
            p = p.below();
        }
        BlockState base = level.getBlockState(p);
        if (!base.is(BlockTags.SAND)) {
            return false;
        }
        level.setBlockAndUpdate(p, SinguloBlocks.FULGURITE_BLOCK.get().defaultBlockState());
        level.playSound(null, p, io.github.genichimaruo.singulo.registry.SinguloSounds.get("fulgurite.form"), net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 20, 0.4, 0.2, 0.4, 0.2);
        return true;
    }

}
