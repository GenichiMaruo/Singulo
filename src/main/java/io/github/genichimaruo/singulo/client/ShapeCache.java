package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import java.util.LinkedHashMap;
import java.lang.ref.WeakReference;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * クライアントで、マルチブロックの形（向き・大きさ）を覚えておく。描画のたびに形を調べ直すと重いので、
 * 1秒（20 tick）ごとに確かめ直す。
 */
public final class ShapeCache {
    private ShapeCache() {}

    private record Entry(long checked, Blueprints.Kind kind, @Nullable Shapes.Found found) {}

    private static final Map<BlockPos, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Entry> eldest) { return size() > 4096; }
    };
    private static WeakReference<Level> lastLevel = new WeakReference<>(null);

    public static void clear() {
        CACHE.clear();
        lastLevel.clear();
    }

    @Nullable
    public static Shapes.Found get(Level level, BlockPos controller, Blueprints.Kind kind) {
        if (level != lastLevel.get()) {
            CACHE.clear();
            lastLevel = new WeakReference<>(level);
        }
        long now = level.getGameTime();
        Entry e = CACHE.get(controller);
        if (e == null || e.kind() != kind || now - e.checked() >= 20 || now < e.checked()) {
            e = new Entry(now, kind, Shapes.find(kind, level, controller));
            CACHE.put(controller.immutable(), e);
        }
        return e.found();
    }
}
