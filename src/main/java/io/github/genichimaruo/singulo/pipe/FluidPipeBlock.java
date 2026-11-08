package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.cable.CableProfile;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.genichimaruo.singulo.compat.Capabilities;

/**
 * 液体パイプ。隣のパイプと、液体を扱うブロックと、水源に自動で接続する。形とモデルはケーブルと同じ作り。
 * 運ぶ液体やガスの種類を問わない。どの面から出し入れするかは、つないだ装置の面の設定で決まる。
 */
public class FluidPipeBlock extends BaseEntityBlock {
    /** 断面は6ドット角（tools/art16.py の CABLE_WIDTHS）。トポロジカル導線と同じ太さ。 */
    private static final CableProfile PROFILE = CableProfile.TOPOLOGICAL;

    public FluidPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : CableBlock.CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state.setValue(CableBlock.STRAIGHT, CableProfile.Straight.NONE));
    }



    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CableBlock.CONNECTIONS.values().forEach(builder::add);
        builder.add(CableBlock.STRAIGHT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = PROFILE.core(state.getValue(CableBlock.STRAIGHT));
        for (var e : CableBlock.CONNECTIONS.entrySet()) {
            if (state.getValue(e.getValue())) {
                shape = Shapes.or(shape, PROFILE.arm(e.getKey()));
            }
        }
        return shape;
    }

    private static BlockState connections(BlockState state, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(CableBlock.CONNECTIONS.get(d), connects(level, pos, d));
        }
        return CableBlock.withStraight(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.neighborChanged(neighborPos);
        }
        return CableBlock.withStraight(state.setValue(CableBlock.CONNECTIONS.get(dir), level instanceof Level l ? connects(l, pos, dir)
                : neighborState.getBlock() instanceof FluidPipeBlock));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // ブロックエンティティや面の設定だけが変わった場合も、接続を取り直す
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.neighborChanged(fromPos);
        }
        BlockState updated = connections(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    static boolean connects(Level level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (!level.isLoaded(other)) {
            return false;
        }
        BlockState state = level.getBlockState(other);
        return state.getBlock() instanceof FluidPipeBlock || FluidPipeNetwork.isSource(state)
                || Capabilities.get(level, Capabilities.FluidHandler.BLOCK, other, dir.getOpposite()) != null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }
}
