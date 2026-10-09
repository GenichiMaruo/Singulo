package io.github.genichimaruo.singulo.multiblock;

import java.util.ArrayDeque;
import java.util.HashSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Validated ring bounds and the continuous center line of its textured beam track. */
public record AcceleratorRing(BlockPos minimum, int side) {
    public record Point(double x, double z, double tangentX, double tangentZ) {}

    public double perimeter() {
        return 4 * (side - 2) + Math.PI;
    }

    /** Distance in blocks, wrapping seamlessly over all four half-block-radius bends. */
    public Point point(double distance) {
        double length = perimeter();
        double wrapped = distance - Math.floor(distance / length) * length;
        double straight = side - 2;
        double leg = straight + Math.PI / 4;
        int edge = Math.min(3, (int) (wrapped / leg));
        double along = wrapped - edge * leg;
        double x, z, tx, tz;
        if (along < straight) {
            x = 1 + along;
            z = .5;
            tx = 1;
            tz = 0;
        } else {
            double angle = -Math.PI / 2 + (along - straight) * 2;
            x = side - 1 + .5 * Math.cos(angle);
            z = 1 + .5 * Math.sin(angle);
            tx = -Math.sin(angle);
            tz = Math.cos(angle);
        }
        for (int i = 0; i < edge; i++) {
            double previousX = x, previousTX = tx;
            x = side - z;
            z = previousX;
            tx = -tz;
            tz = previousTX;
        }
        return new Point(x, z, tx, tz);
    }

    @Nullable
    public static AcceleratorRing find(Level level, BlockPos controller) {
        Direction direction = Structures.ringDirection(level, controller);
        if (direction == null) return null;
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(controller.relative(direction));
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE;
        while (!pending.isEmpty()) {
            BlockPos p = pending.removeFirst();
            if (!level.hasChunkAt(p) || visited.contains(p)
                    || !(level.getBlockState(p).getBlock() instanceof AcceleratorPartBlock)) continue;
            if (visited.size() >= 4 * (Structures.RING_MAX_SIDE - 1)) return null;
            visited.add(p);
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            for (Direction adjacent : Direction.Plane.HORIZONTAL) pending.add(p.relative(adjacent));
        }
        return visited.isEmpty() ? null
                : new AcceleratorRing(new BlockPos(minX, controller.getY(), minZ), maxX - minX + 1);
    }
}
