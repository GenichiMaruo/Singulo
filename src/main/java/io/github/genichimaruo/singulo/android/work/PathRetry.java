package io.github.genichimaruo.singulo.android.work;

import net.minecraft.core.BlockPos;

/** Rate limits path construction without retaining a path or assuming the world is unchanged. */
public final class PathRetry {
    private BlockPos target;
    private long nextTick;

    public boolean allow(BlockPos destination, long now, int interval) {
        if (!destination.equals(target) || now >= nextTick) {
            target = destination.immutable();
            nextTick = now + Math.max(1, interval);
            return true;
        }
        return false;
    }

    public void reset() { target = null; }
}
