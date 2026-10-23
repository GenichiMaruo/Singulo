package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** 研究所の、前（facing）のある機械（サーバーラック）。電力が戻ると灯が瞬く（powered）。置くとこちらを向く。 */
public class LabFacingBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LabFacingBlock> CODEC = simpleCodec(LabFacingBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public LabFacingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 置いたもの（設計図から作ったもの）は動いている状態で置く。研究所の中のものは構造物として置かれ、電力が戻ると動く
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(POWERED, true);
    }
}
