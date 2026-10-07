package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 封印コンソール。最終実験施設の中央にあり、壊せない。 */
public class SealConsoleBlock extends BaseEntityBlock {
    public static final MapCodec<SealConsoleBlock> CODEC = simpleCodec(SealConsoleBlock::new);

    public SealConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SealConsoleBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && !sp.isSpectator()
                && level.getBlockEntity(pos) instanceof SealConsoleBlockEntity console) {
            console.activate((ServerLevel) level, sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 封印の光がゆっくり昇る。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.1,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
        }
    }
}
