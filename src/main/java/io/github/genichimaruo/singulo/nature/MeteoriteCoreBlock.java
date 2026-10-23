package io.github.genichimaruo.singulo.nature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 隕石の核。隕石クレーターの隕石の芯に1つだけ埋まっている、隕鉄と星屑が溶け合った塊。隕石コンパスはこれを指す。
 * 掘り出す（壊れる）と、その場所を覚え、コンパスはもう指さない。
 */
public class MeteoriteCoreBlock extends Block {

    public MeteoriteCoreBlock(Properties properties) {
        super(properties);
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            MeteorCores.get(server).markMined(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
