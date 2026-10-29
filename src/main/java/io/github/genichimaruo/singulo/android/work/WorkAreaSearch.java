package io.github.genichimaruo.singulo.android.work;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 作業範囲の中を、何 tick かに分けて探す（広い範囲でも、1 tick に {@link #PER_TICK} か所までしか見ない）。
 * 見つかるまで同じ範囲・同じ順番で呼び続けると、前の続きから探す。途中なら {@link #pending()} が true で、結果は null。
 */
public final class WorkAreaSearch {
    /** 1 tick に調べる場所の数の上限（1つの作業者が、空の広い範囲で tick を食いつぶさないように）。 */
    public static final int PER_TICK = 2048;

    /** 探す順番。 */
    public enum Order {
        /** 範囲全体から、いちばん近いもの。 */
        NEAREST,
        /** 上の層から。その層で見つかれば、その層でいちばん近いもの（上から崩す採掘）。 */
        TOP_DOWN,
        /** 下の層から。その層で見つかれば、その層でいちばん近いもの（根元から切る伐採）。 */
        BOTTOM_UP,
        /** どれでもよい（最初に見つかったもの）。 */
        ANY
    }

    private BoundingBox box;
    private Order order;
    private BlockPos origin;
    private BlockPos best;
    private double bestDistance;
    private boolean pending;
    /** いま調べている場所。 */
    private int x, y, z;
    /** この tick に調べた数と、その tick。 */
    private int used;
    private long tick = Long.MIN_VALUE;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** 探している途中か（まだ結果が出ていない）。 */
    public boolean pending() {
        return pending;
    }

    /** 探すのをやめる（次の find は最初から）。 */
    public void reset() {
        pending = false;
        box = null;
        best = null;
    }

    /**
     * area の中で matches に合う場所を、order の順で探す。見つかればその場所、見つからないまま調べ終えたか、
     * この tick の上限に達したら null（途中なら {@link #pending()} が true）。
     * matches に渡す場所は使い回すので、とっておかないこと。
     */
    public BlockPos find(BoundingBox area, Order order, BlockPos from, long now, Predicate<BlockPos> matches) {
        if (now != tick) {
            tick = now;
            used = 0;
        }
        if (!pending || !sameBox(area) || this.order != order) {
            start(area, order, from);
        }
        while (used < PER_TICK - 1) {
            used++;
            BlockPos at = cursor.set(x, y, z);
            if (matches.test(at)) {
                double d = at.distSqr(origin);
                if (d < bestDistance) {
                    best = at.immutable();
                    bestDistance = d;
                }
                if (order == Order.ANY) {
                    pending = false;
                    return best;
                }
            }
            if (++x <= box.maxX()) {
                continue;
            }
            x = box.minX();
            if (++z <= box.maxZ()) {
                continue;
            }
            z = box.minZ();
            // 1つの層を見終えた。層ごとに探す順番なら、見つかった層で終える
            if (best != null && (order == Order.TOP_DOWN || order == Order.BOTTOM_UP)) {
                return finish(matches);
            }
            y += order == Order.TOP_DOWN ? -1 : 1;
            if (y < box.minY() || y > box.maxY()) {
                return finish(matches);
            }
        }
        return null;
    }

    private void start(BoundingBox area, Order order, BlockPos from) {
        box = new BoundingBox(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
        this.order = order;
        origin = from.immutable();
        best = null;
        bestDistance = Double.MAX_VALUE;
        x = box.minX();
        z = box.minZ();
        y = order == Order.TOP_DOWN ? box.maxY() : box.minY();
        pending = true;
    }

    private boolean sameBox(BoundingBox other) {
        return box != null && box.minX() == other.minX() && box.maxX() == other.maxX()
                && box.minY() == other.minY() && box.maxY() == other.maxY()
                && box.minZ() == other.minZ() && box.maxZ() == other.maxZ();
    }

    private BlockPos finish(Predicate<BlockPos> matches) {
        pending = false;
        // 何 tick かかけて探している間に、ほかの作業者が見つけた場所を片付けたかもしれないので、もう一度確かめる
        if (best != null) {
            used++;
            if (!matches.test(best)) {
                return null;
            }
        }
        return best;
    }
}
