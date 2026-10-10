package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 番人の封印核。研究棟と封鎖培養施設のボス部屋の床にあり、壊せない（{@link GuardianCoreBlockEntity}）。 */
public class GuardianCoreBlock extends BaseEntityBlock {
    public static final MapCodec<GuardianCoreBlock> CODEC = simpleCodec(GuardianCoreBlock::new);

    public GuardianCoreBlock(Properties properties) {
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
        return new GuardianCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SinguloBlockEntities.GUARDIAN_CORE.get(),
                GuardianCoreBlockEntity::serverTick);
    }

    /** 封印の光が、ゆっくり脈打つように立ちのぼる。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * 0.4, pos.getY() + 1.05,
                    pos.getZ() + 0.5 + Math.sin(a) * 0.4, 0, 0.015, 0);
        }
    }
}
