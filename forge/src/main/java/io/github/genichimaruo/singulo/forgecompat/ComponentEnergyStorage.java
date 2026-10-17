package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** Forge 版: NeoForge の ComponentEnergyStorage と同じ形で使える、アイテムのデータ部品に電力をためる入れ物。 */
public class ComponentEnergyStorage implements IEnergyStorage {
    protected final ItemStack parent;
    protected final DataComponentType<Integer> energyComponent;
    protected final int capacity;
    protected final int maxReceive;
    protected final int maxExtract;

    public ComponentEnergyStorage(ItemStack parent, DataComponentType<Integer> energyComponent, int capacity, int maxReceive, int maxExtract) {
        this.parent = parent;
        this.energyComponent = energyComponent;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    public ComponentEnergyStorage(ItemStack parent, DataComponentType<Integer> energyComponent, int capacity, int maxTransfer) {
        this(parent, energyComponent, capacity, maxTransfer, maxTransfer);
    }

    public ComponentEnergyStorage(ItemStack parent, DataComponentType<Integer> energyComponent, int capacity) {
        this(parent, energyComponent, capacity, capacity);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!canReceive() || toReceive <= 0) {
            return 0;
        }
        int stored = getEnergyStored();
        int received = Math.min(capacity - stored, Math.min(maxReceive, toReceive));
        if (!simulate && received > 0) {
            setEnergy(stored + received);
        }
        return received;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!canExtract() || toExtract <= 0) {
            return 0;
        }
        int stored = getEnergyStored();
        int extracted = Math.min(stored, Math.min(maxExtract, toExtract));
        if (!simulate && extracted > 0) {
            setEnergy(stored - extracted);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        int stored = parent.getOrDefault(energyComponent, 0);
        return Math.max(0, Math.min(stored, capacity));
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return maxReceive > 0;
    }

    protected void setEnergy(int energy) {
        parent.set(energyComponent, Math.max(0, Math.min(energy, capacity)));
    }
}
