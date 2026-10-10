package io.github.genichimaruo.singulo.gravity;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 重力パネル（低重力・高重力）。電力が届いている（POWERED）パネルに乗ると、次に着地するまで重力が変わる
 * （{@link GravityEffects}）。電力は重力パネル受電器から、つながったパネルを伝って届く（{@link GravityReceiverBlockEntity}）。
 * <p>
 * 見た目は隣の同じパネルとつながる: 6方向の接続（同じ種類のパネルが隣にあるか）を状態に持ち、
 * つながっていない辺にだけ縁取りを重ねる（ブロックの状態定義は tools/gen_data.py の gravity_panel_models）。
 */
public class GravityPanelBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);

    static {
        CONNECTIONS.put(Direction.NORTH, BlockStateProperties.NORTH);
        CONNECTIONS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
        CONNECTIONS.put(Direction.EAST, BlockStateProperties.EAST);
        CONNECTIONS.put(Direction.WEST, BlockStateProperties.WEST);
        CONNECTIONS.put(Direction.UP, BlockStateProperties.UP);
        CONNECTIONS.put(Direction.DOWN, BlockStateProperties.DOWN);
    }

    /** 重力の種類。 */
    public enum Kind {
        LOW, HIGH
    }

    private final Kind kind;

    public GravityPanelBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        BlockState state = stateDefinition.any().setValue(POWERED, false);
        for (BooleanProperty p : CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    public Kind kind() {
        return kind;
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(POWERED);
    }

    /** 見た目がつながる相手（同じ種類のパネル）。 */
    private boolean joins(BlockState other) {
        return other.getBlock() instanceof GravityPanelBlock p && p.kind == kind;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(dir), joins(level.getBlockState(pos.relative(dir))));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(CONNECTIONS.get(dir), joins(neighborState));
    }
}
