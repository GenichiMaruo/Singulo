package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 地下アンドロイド研究所の、電力で見た目と明るさが変わるブロック（施設灯・非常灯・炉心）。
 * 研究所の中央動力炉を起動すると（{@link LabPower#restore}）、施設の中のものがまとめて powered になる。
 * 明るさはブロックの登録で、powered ごとに決める（施設灯は電力で光る、非常灯は電力がない間だけ光る）。
 * 設計図から作って置いたものは、placedPowered の状態で置かれる（施設灯・動力導管は点いた状態。非常灯は消えた電力＝赤く光る状態）。
 * 研究所の中のものは構造物として置かれるので、置いたときの状態は使われない。
 */
public class LabPoweredBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final boolean placedPowered;

    public LabPoweredBlock(Properties properties) {
        this(properties, false);
    }

    public LabPoweredBlock(Properties properties, boolean placedPowered) {
        super(properties);
        this.placedPowered = placedPowered;
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, placedPowered);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
