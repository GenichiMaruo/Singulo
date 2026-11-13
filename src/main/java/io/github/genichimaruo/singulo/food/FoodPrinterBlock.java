package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** フードプリンター（形はモデルと同じ: 台と本体 1〜15、天板まで 14、上に食材の入口）。 */
public class FoodPrinterBlock extends SimpleMachineBlock<FoodPrinterBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 14, 15), Block.box(5, 14, 5, 11, 16, 11));

    public FoodPrinterBlock(Properties properties) {
        super(properties, SinguloBlockEntities.FOOD_PRINTER, FoodPrinterBlockEntity::new, FoodPrinterBlockEntity::serverTick,
                FoodPrinterBlockEntity::clientTick);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
