package io.github.genichimaruo.singulo.android.work;

import com.google.common.math.LongMath;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** One operation's view of the material actually accessible through each pickup point. */
final class WorkshopSupplies {
    record Material(BlockPos store, WorkPlanner.Key item) {}
    record Source(Material material, BlockPos point, ItemCount stock, @Nullable IItemHandler items, @Nullable LiquidStores liquids) {
        long take(RuinAndroid worker, long wanted) {
            ItemStack sample = stock.item;
            if (liquids != null) {
                long taken = liquids.drain(sample, Math.min(wanted, worker.fillLiquid(sample, wanted, true)), false);
                long stored = worker.fillLiquid(sample, taken, false);
                if (stored < taken) liquids.fill(sample, taken - stored, false);
                return stored;
            }
            long left = wanted;
            for (int slot = 0; slot < items.getSlots() && left > 0; slot++) {
                while (left > 0 && stock.matches(items.getStackInSlot(slot))) {
                    ItemStack taken = items.extractItem(slot, (int)Math.min(left, sample.getMaxStackSize()), false);
                    if (taken.isEmpty()) break;
                    ItemStack rest = worker.addToCarry(taken);
                    left -= taken.getCount() - rest.getCount();
                    if (!rest.isEmpty()) {
                        ItemStack unreturned = ItemHandlerHelper.insertItemStacked(items, rest, false);
                        if (!unreturned.isEmpty()) worker.spawnAtLocation(unreturned);
                        return wanted - left;
                    }
                }
            }
            return wanted - left;
        }
    }

    private final Map<BlockPos, List<Source>> byPoint = new LinkedHashMap<>();
    private record AtPoint(BlockPos point, WorkPlanner.Key item) {}
    private final Map<AtPoint, List<Source>> byItem = new HashMap<>();
    private final Map<Material, Long> counts = new HashMap<>();
    private final SupplyReservations claims;
    private final ServerLevel level;

    WorkshopSupplies(WorkConsoleBlockEntity console, List<BlockPos> points, boolean items, boolean liquids) {
        level = (ServerLevel)console.getLevel();
        claims = SupplyReservations.get(level);
        if (items) for (var store : ItemSupplyStores.sources(console, points)) {
            var stacks = new LinkedHashMap<WorkPlanner.Key, ItemCount>();
            for (int slot = 0; slot < store.handler().getSlots(); slot++) {
                var stack = store.handler().getStackInSlot(slot);
                if (!stack.isEmpty())
                    stacks.computeIfAbsent(WorkPlanner.Key.of(stack), k -> new ItemCount(stack, 0)).count += stack.getCount();
            }
            stacks.forEach((key, stock) -> add(new Source(new Material(store.key(), key), store.point(), stock, store.handler(), null)));
        }
        if (liquids) for (var supplies : LiquidStores.supplies(console, points).values()) for (var store : supplies) {
            var material = new Material(store.key(), WorkPlanner.Key.of(store.material().item));
            add(new Source(material, store.point(), store.material(), null, store.store()));
        }
    }

    private void add(Source source) {
        byPoint.computeIfAbsent(source.point, k -> new ArrayList<>()).add(source);
        byItem.computeIfAbsent(new AtPoint(source.point, source.material.item), k -> new ArrayList<>()).add(source);
        counts.merge(source.material, source.stock.count, LongMath::saturatedAdd);
    }

    List<Source> at(BlockPos point) {
        return byPoint.getOrDefault(point, List.of());
    }

    List<Source> matching(BlockPos point, ItemCount need) {
        return need.alt != null ? at(point).stream().filter(s -> need.matches(s.stock.item)).toList()
                : byItem.getOrDefault(new AtPoint(point, WorkPlanner.Key.of(need.item)), List.of());
    }

    long free(Source source) {
        long total = counts.get(source.material) - claims.total(level, source.material.store, source.stock.item);
        long atPoint = source.stock.count - claims.at(level, source.point, source.material.store, source.stock.item);
        return Math.max(0, Math.min(atPoint, total));
    }

    Map<WorkPlanner.Key, Long> foreignClaims(BlockPos console) {
        Map<WorkPlanner.Key, Long> reserved = new HashMap<>();
        var seen = new java.util.HashSet<Material>();
        for (var sources : byPoint.values()) for (var source : sources) {
            if (!seen.add(source.material)) continue;
            long foreign = claims.foreign(level, console, source.material.store, source.stock.item);
            if (foreign > 0) reserved.merge(source.material.item, Math.min(counts.get(source.material), foreign), LongMath::saturatedAdd);
        }
        return reserved;
    }
}
