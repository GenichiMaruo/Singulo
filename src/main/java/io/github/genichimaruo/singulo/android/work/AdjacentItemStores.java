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

    AdjacentItemStores(BlockEntity owner) {
        this.owner = owner;
    }

    /** 覚えた入れ物を忘れる（持ち主が外された・戻ったとき）。 */
    void clear() {
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
        return cache.getCapability();
    }
}
