package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

    /** その遺構の覚醒核を持って使うと、覚醒した番人を呼び出す（攻略後の再戦）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!GuardianCoreBlockEntity.isAwakeningCore(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && !sp.isSpectator()
                && level.getBlockEntity(pos) instanceof GuardianCoreBlockEntity core
                && core.awakenAwakened((ServerLevel) level, sp, stack) != null && !sp.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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
