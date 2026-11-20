package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidPumpBlock extends BaseEntityBlock {
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new io.github.genichimaruo.singulo.client.FluidPumpParticles());
    }

    public static final DirectionProperty INTAKE = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) {
            SHAPES.put(dir, Shapes.or(
                    box(0, 5, 5, 16, 11, 11), box(5, 0, 5, 11, 16, 11), box(5, 5, 0, 11, 11, 16),
                    orientedBox(dir, 3, 3, 0, 13, 13, 3), orientedBox(dir, 2, 2, 3, 14, 14, 7),
                    orientedBox(dir, 3, 3, 9, 13, 13, 15), orientedBox(dir, 9, 11, 3, 16, 16, 7),
                    orientedBox(dir, 3, 11, 10, 8, 15, 13), orientedBox(dir, 2, 0, 3, 5, 2, 14),
                    orientedBox(dir, 11, 0, 3, 14, 2, 14)));
        }
    }

    private static VoxelShape orientedBox(Direction intake, int x, int y, int z, int endX, int endY, int endZ) {
        return switch (intake) {
            case NORTH -> box(x, y, z, endX, endY, endZ);
            case SOUTH -> box(16 - endX, y, 16 - endZ, 16 - x, endY, 16 - z);
            case EAST -> box(16 - endZ, y, x, 16 - z, endY, endX);
            case WEST -> box(z, y, 16 - endX, endZ, endY, 16 - x);
            case UP -> box(x, 16 - endZ, y, endX, 16 - z, endY);
            case DOWN -> box(x, z, 16 - endY, endX, endZ, 16 - y);
        };
    }

    public FluidPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(INTAKE, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(INTAKE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(INTAKE, ctx.getClickedFace().getOpposite()); }
    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(INTAKE, r.rotate(s.getValue(INTAKE))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return rotate(s, m.getRotation(s.getValue(INTAKE))); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPES.get(s.getValue(INTAKE)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FluidPumpBlockEntity(pos, state); }

    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SinguloBlockEntities.FLUID_PUMP.get(), FluidPumpBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof FluidPumpBlockEntity pump) pump.open(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FluidPumpBlockEntity pump) {
            for (ItemStack stack : drops) if (stack.is(asItem())) pump.copyContentsToItem(stack);
        }
        return drops;
    }
}
