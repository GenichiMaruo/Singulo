package io.github.genichimaruo.singulo.android.work;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Resumable exact area search. Large empty work areas cannot monopolize one server tick. */
public final class WorkAreaSearch {
    public static final int PER_TICK = 2048;
    private BoundingBox box;
    private int order, x, y, z, used;
    private long tick = Long.MIN_VALUE;
    private BlockPos origin, best;
    private double distance;
    private boolean pending;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public boolean pending() { return pending; }
    public void reset() { pending = false; box = null; best = null; }

    /** order: 0 nearest, 1 highest layer first, -1 lowest layer first, 2 any match.
     * The predicate receives a temporary mutable position and must not retain it. */
    public BlockPos find(BoundingBox area, int order, BlockPos from, long now, Predicate<BlockPos> matches) {
        if (now != tick) { tick = now; used = 0; }
        if (!pending || !same(area) || this.order != order) {
            box = new BoundingBox(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
            this.order = order; origin = from.immutable(); best = null; distance = Double.MAX_VALUE;
            x = box.minX(); z = box.minZ(); y = order == 1 ? box.maxY() : box.minY(); pending = true;
        }
        while (used < PER_TICK - 1) {
            used++;
            BlockPos at = cursor.set(x, y, z);
            if (matches.test(at)) {
                double d = at.distSqr(origin);
                if (d < distance) { best = at.immutable(); distance = d; }
                if (order == 2) { pending = false; return best; }
            }
            if (++x <= box.maxX()) continue;
            x = box.minX();
            if (++z <= box.maxZ()) continue;
            z = box.minZ();
            if (best != null && (order == 1 || order == -1)) { return finish(matches); }
            y += order == 1 ? -1 : 1;
            if (y < box.minY() || y > box.maxY()) return finish(matches);
        }
        return null;
    }

    private BlockPos finish(Predicate<BlockPos> matches) {
        pending = false;
        // Another worker may have removed this candidate during the preceding scan ticks.
        if (best != null) { used++; if (!matches.test(best)) return null; }
        return best;
    }

    private boolean same(BoundingBox other) {
        return box != null && box.minX() == other.minX() && box.maxX() == other.maxX()
                && box.minY() == other.minY() && box.maxY() == other.maxY()
                && box.minZ() == other.minZ() && box.maxZ() == other.maxZ();
    }
}
