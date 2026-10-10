package io.github.genichimaruo.singulo.cable;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.genichimaruo.singulo.compat.Capabilities;

/**
 * エネルギーケーブル。隣のケーブル（種類を問わない）と、電力を扱うブロックに自動で接続する。
 * capacity は1 tick に運べる量、lossPerBlock は1ブロックごとの損失の割合。
 * 実際に電力が通っている道だけが光る（{@link CableBlockEntity} が通った面と量をクライアントへ送り、描画で光らせる）。
 */
public class CableBlock extends BaseEntityBlock {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<CableProfile.Straight> STRAIGHT =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("straight", CableProfile.Straight.class);

    static {
        CONNECTIONS.put(Direction.NORTH, BlockStateProperties.NORTH);
        CONNECTIONS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
        CONNECTIONS.put(Direction.EAST, BlockStateProperties.EAST);
        CONNECTIONS.put(Direction.WEST, BlockStateProperties.WEST);
        CONNECTIONS.put(Direction.UP, BlockStateProperties.UP);
        CONNECTIONS.put(Direction.DOWN, BlockStateProperties.DOWN);
    }

    private final long capacity;
    private final double lossPerBlock;
    private final CableProfile profile;

    public CableBlock(Properties properties, long capacity, double lossPerBlock, CableProfile profile) {
        super(properties);
        this.capacity = capacity;
        this.lossPerBlock = lossPerBlock;
        this.profile = profile;
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state.setValue(STRAIGHT, CableProfile.Straight.NONE));
    }

    public CableProfile profile() {
        return profile;
    }

    /** つながり方から、まっすぐ1本だけ通っているかを決める（中心の形を腕と同じ断面にするため）。 */
    static BlockState withStraight(BlockState state) {
        int n = 0;
        for (BooleanProperty p : CONNECTIONS.values()) {
            if (state.getValue(p)) {
                n++;
            }
        }
        CableProfile.Straight s = CableProfile.Straight.NONE;
        if (n == 2) {
            if (state.getValue(BlockStateProperties.NORTH) && state.getValue(BlockStateProperties.SOUTH)) {
                s = CableProfile.Straight.Z;
            } else if (state.getValue(BlockStateProperties.EAST) && state.getValue(BlockStateProperties.WEST)) {
                s = CableProfile.Straight.X;
            } else if (state.getValue(BlockStateProperties.UP) && state.getValue(BlockStateProperties.DOWN)) {
                s = CableProfile.Straight.Y;
            }
        }
        return state.setValue(STRAIGHT, s);
    }

    /** 1 tick に運べる量。Long.MAX_VALUE は上限なし。 */
    public long capacity() {
        return capacity;
    }

    public double lossPerBlock() {
        return lossPerBlock;
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(STRAIGHT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 見た目と同じ形: 断面を伸ばした腕と、中心（まっすぐなら腕と同じ断面、分かれ目なら断面を囲む箱）
        VoxelShape shape = profile.core(state.getValue(STRAIGHT));
        for (var e : CONNECTIONS.entrySet()) {
            if (state.getValue(e.getValue())) {
                shape = Shapes.or(shape, profile.arm(e.getKey()));
            }
        }
        return shape;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(dir), connects(context.getLevel(), context.getClickedPos(), dir));
        }
        return withStraight(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            cable.invalidateNetwork();
        }
        return withStraight(state.setValue(CONNECTIONS.get(dir), level instanceof Level l ? connects(l, pos, dir)
                : neighborState.getBlock() instanceof CableBlock));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // ブロックエンティティだけが変わった場合（装置の設置直後など）も接続を取り直す
        if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            cable.invalidateNetwork();
        }
        BlockState updated = state;
        for (Direction dir : Direction.values()) {
            updated = updated.setValue(CONNECTIONS.get(dir), connects(level, pos, dir));
        }
        updated = withStraight(updated);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            cable.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    static boolean connects(Level level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (level.getBlockState(other).getBlock() instanceof CableBlock) {
            return true;
        }
        return Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, other, dir.getOpposite()) != null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                io.github.genichimaruo.singulo.registry.SinguloBlockEntities.CABLE.get(), CableBlockEntity::serverTick);
    }
}
