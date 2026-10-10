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
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.36F), 0.6F);

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
            if (cache.isSealed()) {
                // 番人を倒すまでは、攻略した（発見した）ことにならない
                player.displayClientMessage(Component.translatable("gui.singulo.cache.sealed"), true);
            } else {
                RuinDiscovery.record(player, cache.ruin(), pos);
                player.openMenu(cache);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** プレイヤーが置いた保管庫（壊して置き直したものも）は、中身が入らず再生もしない。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RuinCacheBlockEntity cache) {
            cache.markPlacedByPlayer();
        }
    }

    /** 中身のある保管庫は、縁から金色の光の粒がときどき立ちのぼる。封鎖中は赤い力場の粒が箱の周りを漂う。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(SEALED)) {
            if (random.nextInt(4) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(GOLD, pos.getX() + 0.5 + Math.cos(a) * 0.48, pos.getY() + 0.9, pos.getZ() + 0.5 + Math.sin(a) * 0.48,
                        0, 0.02, 0);
            }
            return;
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(FIELD, pos.getX() - 0.2 + random.nextDouble() * 1.4, pos.getY() + random.nextDouble() * 1.4,
                    pos.getZ() - 0.2 + random.nextDouble() * 1.4, 0, 0, 0);
        }
    }
}
