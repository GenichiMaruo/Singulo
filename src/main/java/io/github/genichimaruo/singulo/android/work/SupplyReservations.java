package io.github.genichimaruo.singulo.android.work;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Reservations outlive loaded consoles. Locations of shared storage networks already identify their contents server-wide. */
final class SupplyReservations extends SavedData {
    private record Store(String dimension, BlockPos pos) {}
    private record Owner(String dimension, BlockPos pos) {}
    private record Material(Store store, WorkPlanner.Key item) {}
    private record Access(Owner point, Material material) {}
    private final Map<Access, Long> pointTotals = new HashMap<>();
    private final Map<Owner, Map<Material, Long>> ownerTotals = new HashMap<>();
    private final Map<Material, Long> totals = new HashMap<>();
    private final Map<Owner, Map<Access, ItemCount>> owners = new HashMap<>();

    static SupplyReservations get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SupplyReservations::load, SupplyReservations::new, "singulo_android_supply_claims");
    }

    private static Store store(ServerLevel level, BlockPos pos) {
        String dimension = pos.getX() == Integer.MIN_VALUE || pos.getX() == Integer.MIN_VALUE + 1
                ? "" : level.dimension().location().toString();
        return new Store(dimension, pos);
    }

    private static Owner owner(ServerLevel level, BlockPos pos) {
        return new Owner(level.dimension().location().toString(), pos);
    }

    boolean hasForeign(ServerLevel level, BlockPos pos) {
        return owners.size() > 1 || !owners.isEmpty() && !owners.containsKey(owner(level, pos));
    }

    long total(ServerLevel level, BlockPos key, ItemStack item) {
        return totals.getOrDefault(new Material(store(level, key), WorkPlanner.Key.of(item)), 0L);
    }

    long foreign(ServerLevel level, BlockPos console, BlockPos key, ItemStack item) {
        Material material = new Material(store(level, key), WorkPlanner.Key.of(item));
        long total = totals.getOrDefault(material, 0L);
        if (total == 0) return 0;
        return total - ownerTotals.getOrDefault(owner(level, console), Map.of()).getOrDefault(material, 0L);
    }

    long at(ServerLevel level, BlockPos point, BlockPos key, ItemStack item) {
        return pointTotals.getOrDefault(new Access(owner(level, point), new Material(store(level, key), WorkPlanner.Key.of(item))), 0L);
    }

    void change(ServerLevel level, BlockPos console, BlockPos point, BlockPos key, ItemStack item, long amount) {
        if (amount != 0) change(owner(level, console), owner(level, point), store(level, key), item, amount);
    }

    private static <K> void adjust(Map<K, Long> map, K key, long delta) {
        if (map.merge(key, delta, Long::sum) == 0) map.remove(key);
    }

    private void change(Owner owner, Owner point, Store store, ItemStack item, long amount) {
        var material = new Material(store, WorkPlanner.Key.of(item));
        var access = new Access(point, material);
        var claims = owners.computeIfAbsent(owner, ignored -> new HashMap<>());
        var claim = claims.computeIfAbsent(access, ignored -> new ItemCount(item, 0));
        long delta = Math.max(-claim.count, amount);
        claim.count += delta;
        if (claim.count == 0) claims.remove(access);
        var local = ownerTotals.computeIfAbsent(owner, ignored -> new HashMap<>());
        adjust(local, material, delta);
        adjust(pointTotals, access, delta);
        adjust(totals, material, delta);
        if (claims.isEmpty()) {
            owners.remove(owner);
            ownerTotals.remove(owner);
        }
        setDirty();
    }

    void clear(ServerLevel level, BlockPos pos) {
        Owner owner = owner(level, pos);
        var claims = owners.remove(owner);
        if (claims == null) return;
        ownerTotals.remove(owner);
        claims.forEach((access, item) -> {
            adjust(pointTotals, access, -item.count);
            adjust(totals, access.material, -item.count);
        });
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        owners.forEach((owner, claims) -> claims.forEach((key, item) -> {
            var entry = item.save();
            entry.putString("dimension", owner.dimension);
            entry.put("console", NbtUtils.writeBlockPos(owner.pos));
            entry.put("point", NbtUtils.writeBlockPos(key.point.pos));
            entry.putString("store_dimension", key.material.store.dimension);
            entry.put("store", NbtUtils.writeBlockPos(key.material.store.pos));
            entries.add(entry);
        }));
        tag.put("claims", entries);
        return tag;
    }

    static SupplyReservations load(CompoundTag tag) {
        var data = new SupplyReservations();
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)t;
            ItemCount item = ItemCount.load(entry);
            var pos = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(entry, "console");
            var store = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(entry, "store");
            if (item.count > 0 && !item.item.isEmpty() && pos.isPresent() && store.isPresent()) {
                String dimension = entry.getString("dimension");
                var point = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(entry, "point").orElse(pos.get());
                data.change(new Owner(dimension, pos.get()), new Owner(dimension, point),
                        new Store(entry.getString("store_dimension"), store.get()), item.item, item.count);
            }
        }
        data.setDirty(false);
        return data;
    }
}
