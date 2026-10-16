package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 耐圧殻。地下アンドロイド研究所の全体を包む（部屋の壁と合わせて二重）。ボス部屋の壁も耐圧殻。
 * 封じられている（sealed）間は壊せない。研究所のボスを倒すと封じが解け（{@link LabPower#unseal}）、
 * 黒曜石より硬いが掘れるようになる。爆発ではいつも壊れない。
 */
public class PressureHullBlock extends Block {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public PressureHullBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(SEALED) ? 0 : super.getDestroyProgress(state, player, level, pos);
    }
}
