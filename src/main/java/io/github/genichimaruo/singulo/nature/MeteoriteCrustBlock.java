package io.github.genichimaruo.singulo.nature;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 隕石の溶融殻。隕石クレーターの中心の隕石を包む、焼けて溶けた黒い殻。
 * クレーターに最初からある殻（natural）だけが、宇宙から運ばれた微粒子を少しずつ表面に析出させ、
 * 隣の空いた所に星屑の結晶を育てる（紫水晶の芽のように。持ち帰って置いた殻では育たない）。
 */
public class MeteoriteCrustBlock extends Block {
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
    /** ランダムtick 1回あたり、結晶が育つ割合。 */
    static final int GROW_CHANCE = 4;

    public MeteoriteCrustBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NATURAL, false));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NATURAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(NATURAL, false);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(NATURAL);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROW_CHANCE) != 0) {
            return;
        }
        Direction dir = Direction.getRandom(random);
        BlockPos at = pos.relative(dir);
        BlockState there = level.getBlockState(at);
        boolean water = there.is(net.minecraft.world.level.block.Blocks.WATER) && there.getFluidState().getAmount() == 8;
        if (there.isAir() || water) {
            level.setBlockAndUpdate(at, SinguloBlocks.STARDUST_CLUSTER.get().defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, dir).setValue(AmethystClusterBlock.WATERLOGGED, water));
        }
    }
}
