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
    private final EnumMap<Direction, BlockCapabilityCache<IItemHandler, Direction>> sharedCaches = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>, Direction>> nativeCaches = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Object> seen = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IItemHandler> views = new EnumMap<>(Direction.class);

    AdjacentItemStores(BlockEntity owner) {
        this.owner = owner;
    }

    /** 覚えた入れ物を忘れる（持ち主が外された・戻ったとき）。 */
    void clear() {
        seen.clear();
        views.clear();
        sharedCaches.clear();
        nativeCaches.clear();
    }

    /** direction の隣の入れ物（なければ null）。 */
    IItemHandler get(Direction direction) {
        if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) {
            return null;
        }
        var sharedCache = sharedCaches.get(direction);
        var nativeCache = nativeCaches.get(direction);
        if (sharedCache == null || sharedCache.level() != level) {
            sharedCache = BlockCapabilityCache.create(io.github.genichimaruo.singulo.api.android.AndroidStorageCapabilities.SHARED_ITEMS,
                level, owner.getBlockPos().relative(direction), direction.getOpposite(),
                () -> !owner.isRemoved() && owner.getLevel() == level, () -> {});
            nativeCache = BlockCapabilityCache.create(Capabilities.Item.BLOCK,
                level, owner.getBlockPos().relative(direction), direction.getOpposite(),
                () -> !owner.isRemoved() && owner.getLevel() == level, () -> {});
            sharedCaches.put(direction, sharedCache);
            nativeCaches.put(direction, nativeCache);
            seen.remove(direction);
            views.remove(direction);
        }
        var shared = sharedCache.getCapability();
        var nativeHandler = shared == null ? nativeCache.getCapability() : null;
        Object identity = shared != null ? shared : nativeHandler;
        if (identity == null || identity != seen.get(direction)) {
            seen.put(direction, identity);
            IItemHandler handler = shared != null ? shared : io.github.genichimaruo.singulo.compat.ItemResourceView.of(nativeHandler);
            views.put(direction, androidView(level, owner.getBlockPos().relative(direction), direction.getOpposite(), handler));
        }
        return views.get(direction);
    }

    /**
     * pos の入れ物を、アンドロイドの素材置き場として見たもの（AndroidStorageView なら専用の見え方）。
     * どちらも、取り出せない欄は空に見せる（{@link ExtractableItems}）。
     */
    static IItemHandler androidView(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Direction side, IItemHandler handler) {
        if (handler != null && level.getBlockEntity(pos) instanceof AndroidStorageView view) {
            return ExtractableItems.of(view.androidItems(side));
        }
        return ExtractableItems.of(handler);
    }
}
