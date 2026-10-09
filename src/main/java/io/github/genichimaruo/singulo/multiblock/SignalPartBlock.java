package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 赤石信号を出す部品（炉心質量警報器）。信号の強さはコントローラが POWER に書き込む。LIT は見た目（光っているか）。
 */
public class SignalPartBlock extends PartBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SignalPartBlock(Properties properties, Role role) {
        super(properties.lightLevel(s -> s.getValue(LIT) ? 9 : 0), role);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, LIT);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new SignalPartBlock(p, role()));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}
