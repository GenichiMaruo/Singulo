package io.github.genichimaruo.singulo.android.work;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** Legacy capability cache: observe replacement/unload and Forge's explicit invalidation. */
final class AdjacentItemStores {
    private static final class Entry {
        final BlockEntity target;
        final LazyOptional<IItemHandler> capability;
        boolean valid = true;
        Entry(BlockEntity target, Direction side) {
            this.target = target;
            capability = target.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            capability.addListener(ignored -> valid = false);
        }
    }
    private final BlockEntity owner;
    private final EnumMap<Direction, Entry> caches = new EnumMap<>(Direction.class);
    AdjacentItemStores(BlockEntity owner) { this.owner = owner; }
    void clear() { caches.clear(); }
    IItemHandler get(Direction direction) {
        if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) return null;
        var pos = owner.getBlockPos().relative(direction);
        if (!level.hasChunkAt(pos)) { caches.remove(direction); return null; }
        var target = level.getBlockEntity(pos);
        if (target == null || target.isRemoved()) { caches.remove(direction); return null; }
        var cached = caches.get(direction);
        if (cached == null || cached.target != target || !cached.valid) {
            cached = new Entry(target, direction.getOpposite());
            caches.put(direction, cached);
        }
        return cached.capability.orElse(null);
    }
}
