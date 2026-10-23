package io.github.genichimaruo.singulo.genesis;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 創世炉の核。動き出したら（種火から先）、サバイバルでは壊せない。 */
public class GenesisCoreBlock extends BaseEntityBlock {

    public GenesisCoreBlock(Properties properties) {
        super(properties);
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GenesisCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, SinguloBlockEntities.GENESIS_CORE.get(), GenesisCoreBlockEntity::clientTick);
        }
        return createTickerHelper(type, SinguloBlockEntities.GENESIS_CORE.get(), GenesisCoreBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof io.github.genichimaruo.singulo.item.HoloProjectorItem
                || stack.getItem() instanceof io.github.genichimaruo.singulo.item.BuilderWandItem) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof GenesisCoreBlockEntity core)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && !sp.isSpectator()) {
            if (!stack.isEmpty() && core.useItem(sp, stack)) return InteractionResult.CONSUME;
            core.useEmpty(sp);
        }
        return InteractionResult.CONSUME;
    }

    /** 動き出した炉は、宇宙が生まれるまでつるはしでは崩せない（創造モードなら壊せる）。宇宙を生んだ後は壊して運べる。 */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof GenesisCoreBlockEntity core
                && core.stage() >= GenesisCoreBlockEntity.KINDLING && core.stage() < GenesisCoreBlockEntity.COMPLETE) {
            return 0;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** 宇宙を生んだ炉は、記憶を宿した核と、出来上がっていた物を落とす。 */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof GenesisCoreBlockEntity core && core.remembersUniverse()) {
            drops = new java.util.ArrayList<>(drops);
            drops.replaceAll(s -> s.is(asItem()) ? core.memoryItem(s) : s);
            if (!core.output().isEmpty()) {
                drops.add(core.output().copy());
            }
        }
        return drops;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 1.05,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
        }
    }
}
