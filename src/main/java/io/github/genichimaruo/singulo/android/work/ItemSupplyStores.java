package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/** A pickup point and its usable share of a physical inventory, accounting for sided extraction. */
final class ItemSupplyStores {
    record Source(BlockPos key, BlockPos point, IItemHandler handler) {}

    static List<Source> sources(WorkConsoleBlockEntity console, List<BlockPos> points) {
        Map<BlockPos, List<Source>> byStore = new LinkedHashMap<>();
        for (BlockPos point : points) {
            console.visitItemStoresAt(point, (key, handler) -> {
                List<Source> faces = byStore.computeIfAbsent(key, k -> new ArrayList<>());
                faces.add(new Source(key, point, handler));
            });
        }
        List<Source> out = new ArrayList<>();
        for (List<Source> faces : byStore.values()) {
            Source first = faces.getFirst();
            // QIO frequencies and AndroidSharedStorage explicitly present one complete network view.
            if (faces.size() == 1 || first.key().getX() <= Integer.MIN_VALUE + 1) out.add(first);
            else collectFaces(console, faces, out);
        }
        return out;
    }

    private static final class Slots {
        final Source face;
        final IntArrayList slots = new IntArrayList();
        long count;
        int priority;
        Slots(Source face) { this.face = face; }
    }

    private static void collectFaces(WorkConsoleBlockEntity console, List<Source> faces, List<Source> out) {
        BlockPos key = faces.getFirst().key();
        var level = console.getLevel();
        var block = level.isLoaded(key) ? level.getBlockEntity(key) : null;
        Map<Object, Slots> winners = new LinkedHashMap<>();
        for (Source face : faces) {
            var side = Direction.fromDelta(face.point().getX() - key.getX(), face.point().getY() - key.getY(),
                    face.point().getZ() - key.getZ());
            Map<Object, Slots> visible = new LinkedHashMap<>();
            IItemHandler handler = face.handler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack item = handler.getStackInSlot(i);
                if (item.isEmpty()) continue;
                // Vanilla sided wrappers remap indices. Singulo machines use the same indices on every face.
                // An opaque third-party capability has no physical slot IDs: use its best face per material.
                Object id;
                if (block instanceof WorldlyContainer inv && side != null && ExtractableItems.source(handler) instanceof SidedInvWrapper)
                    id = SidedInvWrapper.getSlot(inv, i, side);
                else if (block instanceof MachineBlockEntity) id = i;
                else id = WorkPlanner.Key.of(item);
                Slots slots = visible.computeIfAbsent(id, unused -> new Slots(face));
                slots.slots.add(i);
                slots.count += item.getCount();
                slots.priority = console.priority(face.point(), item, true);
            }
            visible.forEach((id, slots) -> winners.merge(id, slots, (a, b) ->
                    b.count > a.count || b.count == a.count && b.priority > a.priority ? b : a));
        }
        Map<Source, IntArrayList> selected = new LinkedHashMap<>();
        for (Slots winner : winners.values())
            selected.computeIfAbsent(winner.face, unused -> new IntArrayList()).addAll(winner.slots);
        selected.forEach((face, slots) -> {
            int[] indices = slots.toIntArray();
            java.util.Arrays.sort(indices);
            out.add(new Source(key, face.point(), new SlotView(face.handler(), indices)));
        });
    }

    /** A short-lived view for counting or picking up the slots attributed to this face. */
    private record SlotView(IItemHandler storage, int[] slots) implements IItemHandler {
        public int getSlots() { return slots.length; }
        public ItemStack getStackInSlot(int slot) { return storage.getStackInSlot(slots[slot]); }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return storage.extractItem(slots[slot], amount, simulate); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return storage.insertItem(slots[slot], stack, simulate); }
        public int getSlotLimit(int slot) { return storage.getSlotLimit(slots[slot]); }
        public boolean isItemValid(int slot, ItemStack stack) { return storage.isItemValid(slots[slot], stack); }
    }
}
