package io.github.genichimaruo.singulo.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 箱いっぱいでない部品。当たり判定と選択枠は見た目と同じ形にする（tools/cosmic_assets.py のモデルと同じ値）。 */
public class ShapedPartBlock extends PartBlock {
    private final VoxelShape shape;

    public ShapedPartBlock(Properties properties, Role role, VoxelShape shape) {
        super(properties, role);
        this.shape = shape;
    }



    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
