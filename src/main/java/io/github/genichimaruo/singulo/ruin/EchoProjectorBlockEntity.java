package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 残響投影器（描画のためだけにある。番人へ伸びる投影の光とレンズの回転は EchoProjectorRenderer が描く）。 */
public class EchoProjectorBlockEntity extends BlockEntity {
    public EchoProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ECHO_PROJECTOR.get(), pos, state);
    }
}
