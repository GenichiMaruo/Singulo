package io.github.genichimaruo.singulo.android;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** How many items in each cargo stack the guard picked up and may deposit automatically. */
final class AndroidCargoInventory extends SimpleContainer {
    private final int[] pickups = new int[RuinAndroid.PACK_START + RuinAndroid.PACK_SLOTS];
    private final int[] lastSize = new int[pickups.length];
    private boolean addingByDrag;

    AndroidCargoInventory() {
        super(RuinAndroid.INVENTORY_SIZE);
    }

    int pickups(int slot) {
        int size = getItem(slot).getCount();
        // Treat withdrawals from a mixed stack as removing pickups first. Later additions are personal.
        pickups[slot] = Math.max(0, pickups[slot] - Math.max(0, lastSize[slot] - size));
        lastSize[slot] = size;
        return pickups[slot];
    }

    void growCargo(int slot, int amount, int pickedUp) {
        int previous = pickups(slot);
        getItem(slot).grow(amount);
        setPickups(slot, previous + pickedUp);
    }

    void setPickups(int slot, int count) {
        lastSize[slot] = getItem(slot).getCount();
        pickups[slot] = io.github.genichimaruo.singulo.compat.Legacy.clamp(count, 0, lastSize[slot]);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= RuinAndroid.CARRY_START && slot < pickups.length && pickups[slot] > 0 && getItem(slot) != stack) {
            ItemStack old = getItem(slot);
            // Vanilla drag distribution writes a new, larger stack even though the old items have stayed in place.
            pickups[slot] = addingByDrag && stack.getCount() >= old.getCount() && ItemStack.isSameItemSameTags(old, stack)
                    ? pickups(slot) : 0;
        }
        super.setItem(slot, stack);
    }

    void addByDrag(Runnable distribute) {
        addingByDrag = true;
        try {
            distribute.run();
        } finally {
            addingByDrag = false;
        }
    }

    @Override
    public void setChanged() {
        for (int slot = RuinAndroid.CARRY_START; slot < pickups.length; slot++) {
            if (pickups[slot] > 0) pickups(slot);
        }
        super.setChanged();
    }

    int[] savePickups() {
        for (int slot = RuinAndroid.CARRY_START; slot < pickups.length; slot++) pickups(slot);
        return pickups.clone();
    }

    void loadPickups(int[] saved) {
        for (int slot = RuinAndroid.CARRY_START; slot < pickups.length; slot++) {
            setPickups(slot, slot < saved.length ? saved[slot] : 0);
        }
    }
}
