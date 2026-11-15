package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Native transactional storage with synchronous helpers for machine-owned tick logic. */
@SuppressWarnings("removal")
public class SinguloEnergyStorage extends SimpleEnergyHandler implements IEnergyStorage {
    private final Runnable onChange;

    public SinguloEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
    }

    @Override protected void onEnergyChanged(int previousAmount) { onChange.run(); }
    @Override public int getEnergyStored() { return getAmountAsInt(); }
    @Override public int getMaxEnergyStored() { return getCapacityAsInt(); }
    @Override public boolean canReceive() { return maxInsert > 0; }
    @Override public boolean canExtract() { return maxExtract > 0; }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = Transaction.openRoot()) {
            int result = insert(amount, tx);
            if (!simulate) tx.commit();
            return result;
        }
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = Transaction.openRoot()) {
            int result = extract(amount, tx);
            if (!simulate) tx.commit();
            return result;
        }
    }

    /** Internal machine consumption ignores the external extraction limit. */
    public boolean consume(int amount) {
        if (amount < 0 || energy < amount) return false;
        if (amount != 0) set(energy - amount);
        return true;
    }
    public void generate(int amount) {
        int next = (int) Math.max(energy, Math.min(capacity, (long) energy + amount));
        if (next != energy) set(next);
    }
    /** Loading state does not emit a dirty notification. */
    public void setEnergy(int value) { energy = Math.max(0, Math.min(capacity, value)); }
    /** Downgrading never destroys surplus charge. */
    public void resizeCapacity(int value) { capacity = Math.max(1, value); }
    public void restoreEnergy(int value) { energy = Math.max(0, value); }
}
