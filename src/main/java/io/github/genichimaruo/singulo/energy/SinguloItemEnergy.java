package io.github.genichimaruo.singulo.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import io.github.genichimaruo.singulo.registry.SinguloComponents;

/** Native item energy; the legacy methods are only synchronous internal convenience calls. */
@SuppressWarnings("removal")
public final class SinguloItemEnergy extends ItemAccessEnergyHandler implements IEnergyStorage {
    public SinguloItemEnergy(ItemAccess access, int capacity) { super(access, SinguloComponents.ENERGY.get(), capacity); }
    public SinguloItemEnergy(ItemStack stack, int capacity) { this(ItemAccess.forStack(stack), capacity); }
    @Override public int getEnergyStored() { return getAmountAsInt(); }
    @Override public int getMaxEnergyStored() { return getCapacityAsInt(); }
    @Override public boolean canReceive() { return maxInsert > 0; }
    @Override public boolean canExtract() { return maxExtract > 0; }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = Transaction.openRoot()) { int got=insert(amount,tx); if (!simulate) tx.commit(); return got; }
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = Transaction.openRoot()) { int got=extract(amount,tx); if (!simulate) tx.commit(); return got; }
    }
}
