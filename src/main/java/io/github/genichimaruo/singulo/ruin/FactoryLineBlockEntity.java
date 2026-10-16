package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 製造ライン（描画のためだけにある。ファウンドリへ伸びる給電の光は FactoryLineRenderer が描く）。 */
public class FactoryLineBlockEntity extends BlockEntity {
    public FactoryLineBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FACTORY_LINE.get(), pos, state);
    }
}
