package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** 盤を回転・鏡映しても、組み込まれた計器の種類は維持する。 */
public class InstrumentPanelBlock extends LabDecorBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 2);

    public InstrumentPanelBlock(Properties properties) {
        super(properties, Shapes.block(), null, null);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

}
