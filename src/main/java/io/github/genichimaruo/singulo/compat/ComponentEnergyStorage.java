package io.github.genichimaruo.singulo.compat;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** Forge energy exposed by a rechargeable, NBT-backed tool. */
public final class ComponentEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final SinguloComponents.Key<Integer> key;
    private final int capacity;
    public ComponentEnergyStorage(ItemStack stack, SinguloComponents.Key<Integer> key, int capacity) {
        this.stack = stack; this.key = key; this.capacity = capacity;
    }
    public int receiveEnergy(int max, boolean simulate) {
        int amount = Math.max(0, Math.min(max, capacity - getEnergyStored()));
        if (!simulate) SinguloComponents.set(stack, key, getEnergyStored() + amount);
        return amount;
    }
    public int extractEnergy(int max, boolean simulate) {
        int amount = Math.max(0, Math.min(max, getEnergyStored()));
        if (!simulate) SinguloComponents.set(stack, key, getEnergyStored() - amount);
        return amount;
    }
    public int getEnergyStored() { return SinguloComponents.getOrDefault(stack, key, 0); }
    public int getMaxEnergyStored() { return capacity; }
    public boolean canExtract() { return true; }
    public boolean canReceive() { return true; }
}
