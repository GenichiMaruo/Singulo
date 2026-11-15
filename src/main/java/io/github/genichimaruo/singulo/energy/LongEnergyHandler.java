package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Native long-sized transactional extension used by Singulo's high-power network. */
public interface LongEnergyHandler extends EnergyHandler, LongEnergyStorage {
    @Override default long receiveLong(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            long result = insertLong(amount,tx); if (!simulate) tx.commit(); return result;
        }
    }
    @Override default long extractLong(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            long result = extractLong(amount,tx); if (!simulate) tx.commit(); return result;
        }
    }
    @Override default int receiveEnergy(int amount, boolean simulate) { return (int) receiveLong(amount,simulate); }
    @Override default int extractEnergy(int amount, boolean simulate) { return (int) extractLong(amount,simulate); }
    @Override default int getEnergyStored() { return getAmountAsInt(); }
    @Override default int getMaxEnergyStored() { return getCapacityAsInt(); }
    long insertLong(long amount, TransactionContext transaction);
    long extractLong(long amount, TransactionContext transaction);
    @Override default int insert(int amount, TransactionContext transaction) { return Math.toIntExact(insertLong(amount, transaction)); }
    @Override default int extract(int amount, TransactionContext transaction) { return Math.toIntExact(extractLong(amount, transaction)); }
    static long insert(EnergyHandler handler, long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy insertion");
        return handler instanceof LongEnergyHandler large ? large.insertLong(amount, transaction)
                : handler.insert((int) Math.min(Integer.MAX_VALUE, amount), transaction);
    }
    static long extract(EnergyHandler handler, long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy extraction");
        return handler instanceof LongEnergyHandler large ? large.extractLong(amount, transaction)
                : handler.extract((int) Math.min(Integer.MAX_VALUE, amount), transaction);
    }
}
