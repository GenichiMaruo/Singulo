package io.github.genichimaruo.singulo.android.work;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 作業のブロックの隣（6方向）の入れ物を覚えておく（毎回探し直さない）。入れ物が置かれた・壊されたときの付け替えは NeoForge に任せる。
 * 覚えるのは入れ物そのものなので、中身の数はいつも今のもの。
 */
final class AdjacentItemStores {
    private final BlockEntity owner;
    private final EnumMap<Direction, BlockCapabilityCache<IItemHandler, Direction>> caches = new EnumMap<>(Direction.class);
    /** アンドロイド用の見え方（入れ物の窓口が同じ間は使い回す。窓口が変わったら見え方を引き直す）。 */
    private final EnumMap<Direction, IItemHandler> seen = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IItemHandler> views = new EnumMap<>(Direction.class);

    AdjacentItemStores(BlockEntity owner) {
        this.owner = owner;
    }

    /** 覚えた入れ物を忘れる（持ち主が外された・戻ったとき）。 */
    void clear() {
        seen.clear();
        views.clear();
        caches.clear();
    }

    /** direction の隣の入れ物（なければ null）。 */
    IItemHandler get(Direction direction) {
        if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) {
            return null;
        }
        var cache = caches.get(direction);
        if (cache == null || cache.level() != level) {
            cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level,
                    owner.getBlockPos().relative(direction), direction.getOpposite(),
                    () -> !owner.isRemoved() && owner.getLevel() == level, () -> {});
            caches.put(direction, cache);
        }
        IItemHandler handler = cache.getCapability();
        if (handler == null || handler != seen.get(direction)) {
            seen.put(direction, handler);
            views.put(direction, androidView(level, owner.getBlockPos().relative(direction), direction.getOpposite(), handler));
        }
        return views.get(direction);
    }

    /** pos の入れ物を、アンドロイドの素材置き場として見たもの（AndroidStorageView なら専用の見え方、ほかは handler のまま）。 */
    static IItemHandler androidView(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Direction side, IItemHandler handler) {
        if (handler != null && level.getBlockEntity(pos) instanceof AndroidStorageView view) {
            return view.androidItems(side);
        }
        return handler;
    }
}
