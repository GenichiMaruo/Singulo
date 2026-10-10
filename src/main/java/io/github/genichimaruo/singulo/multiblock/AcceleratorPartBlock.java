package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A completed square ring shares a continuous beam track across tubes and magnets. */
public class AcceleratorPartBlock extends PartBlock {
    public enum Segment implements StringRepresentable {
        NONE, NORTH, EAST, SOUTH, WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Segment> SEGMENT = EnumProperty.create("segment", Segment.class);

    public AcceleratorPartBlock(Properties properties, Role role) {
        super(properties, role);
        registerDefaultState(stateDefinition.any().setValue(SEGMENT, Segment.NONE));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            level.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(level, pos);
    }

    /** Bounded traversal also resets surviving pieces when the ring is broken. */
    public static void refresh(ServerLevel level, BlockPos start) {
        Set<BlockPos> parts = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int limit = 4 * (Structures.RING_MAX_SIDE - 1) + 1;
        while (!queue.isEmpty() && parts.size() < limit) {
            BlockPos p = queue.removeFirst();
            if (!level.hasChunkAt(p) || !isPart(level.getBlockState(p)) || !parts.add(p)) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                queue.add(p.relative(direction));
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        boolean formed = Structures.ringFrom(level, start) > 0;
        int minX = parts.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = parts.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = parts.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = parts.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        for (BlockPos p : parts) {
            BlockState previous = level.getBlockState(p);
            Segment segment = Segment.NONE;
            if (formed) {
                boolean west = p.getX() == minX, east = p.getX() == maxX;
                boolean north = p.getZ() == minZ, south = p.getZ() == maxZ;
                segment = north ? (west ? Segment.NORTH_WEST : east ? Segment.NORTH_EAST : Segment.NORTH)
                        : south ? (west ? Segment.SOUTH_WEST : east ? Segment.SOUTH_EAST : Segment.SOUTH)
                        : west ? Segment.WEST : Segment.EAST;
            }
            if (previous.getValue(SEGMENT) != segment) {
                level.setBlock(p, previous.setValue(SEGMENT, segment), Block.UPDATE_CLIENTS);
                // Continue clearing an oversized invalid component without an unbounded flood fill.
                if (!formed) {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos n = p.relative(direction);
                        if (level.hasChunkAt(n) && level.getBlockState(n).getBlock() instanceof AcceleratorPartBlock part
                                && level.getBlockState(n).getValue(SEGMENT) != Segment.NONE) {
                            level.scheduleTick(n, part, 1);
                        }
                    }
                }
            }
        }
    }

    private static boolean isPart(BlockState state) {
        return state.getBlock() instanceof AcceleratorPartBlock;
    }
}
