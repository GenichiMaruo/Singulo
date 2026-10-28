package io.github.genichimaruo.singulo.android.work;

import net.minecraft.core.BlockPos;

/**
 * 経路探しの回数を抑える。同じ行き先へは interval tick に1回だけ探す（たどり着けない場所へ毎 tick 探し直さない）。
 * 行き先が変わればすぐ探す。経路そのものは覚えない（ワールドが変われば、次の回にちゃんと探し直す）。
 */
public final class PathRetry {
    private BlockPos target;
    private long nextTick;

    /** いま destination への経路を探してよいか（よければ、次に探せる時刻を interval tick 先へ進める）。 */
    public boolean allow(BlockPos destination, long now, int interval) {
        if (!destination.equals(target) || now >= nextTick) {
            target = destination.immutable();
            nextTick = now + Math.max(1, interval);
            return true;
        }
        return false;
    }

    /** 新しい作業を始めた: 前の行き先の待ち時間を忘れる。 */
    public void reset() {
        target = null;
    }
}
