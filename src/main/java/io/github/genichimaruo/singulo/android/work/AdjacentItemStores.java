package io.github.genichimaruo.singulo.android.work;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** Per-owner caches; inventory quantities remain live and capability invalidation is handled by NeoForge. */
final class AdjacentItemStores {
    private final BlockEntity owner;
    private final EnumMap<Direction, BlockCapabilityCache<IItemHandler, Direction>> caches = new EnumMap<>(Direction.class);

    AdjacentItemStores(BlockEntity owner) { this.owner = owner; }
    void clear() { caches.clear(); }

    IItemHandler get(Direction direction) {
        if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) return null;
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
