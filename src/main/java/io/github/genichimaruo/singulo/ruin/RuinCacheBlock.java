package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

/**
 * 遺構保管庫。壊せず（持ち帰って自動化されないように）、開くと中身の再生を確かめる。
 * sealed のあいだは力場で封鎖され、開けない（最終実験施設は守護機を倒すまで）。
 */
public class RuinCacheBlock extends BaseEntityBlock {
    public static final MapCodec<RuinCacheBlock> CODEC = simpleCodec(RuinCacheBlock::new);
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    private static final DustParticleOptions FIELD = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.35F), 1.0F);

    public RuinCacheBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RuinCacheBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RuinCacheBlockEntity cache) {
            cache.refillIfDue((ServerLevel) level, level.getGameTime());
            RuinDiscovery.record(player, cache.ruin(), pos);
            if (cache.isSealed()) {
                player.displayClientMessage(Component.translatable("gui.singulo.cache.sealed"), true);
            } else {
                player.openMenu(cache);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 封鎖中は赤い力場の粒が箱の周りを漂う。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(SEALED)) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(FIELD, pos.getX() - 0.2 + random.nextDouble() * 1.4, pos.getY() + random.nextDouble() * 1.4,
                    pos.getZ() - 0.2 + random.nextDouble() * 1.4, 0, 0, 0);
        }
    }
}
