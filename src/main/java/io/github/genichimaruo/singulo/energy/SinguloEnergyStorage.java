package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Native transactional storage with synchronous helpers for machine-owned tick logic. */
@SuppressWarnings("removal")
public class SinguloEnergyStorage extends SimpleEnergyHandler implements IEnergyStorage {
    private final Runnable onChange;
    private final int baseCapacity, baseExtract;
    private double fraction;

    public SinguloEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
        this.baseCapacity = capacity;
        this.baseExtract = maxExtract;
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

    /** Keep sub-FE generation across ticks and chunk unloads. External FE transfers stay whole. */
    public int generateRate(double rate) {
        if (!(rate > 0) || energy >= capacity) return 0;
        double limited = Math.min(Integer.MAX_VALUE, rate);
        int whole = (int) limited;
        fraction += limited - whole;
        if (fraction >= 1) {
            fraction--;
            whole++;
        }
        int before = energy;
        generate(whole);
        if (energy == before) onChange.run();
        return energy - before;
    }

    public void saveGeneration(net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("energy", energy);
        if (fraction > 0) tag.putDouble("generation_fraction", fraction);
    }

    public void loadGeneration(net.minecraft.nbt.CompoundTag tag) {
        restoreEnergy(tag.getIntOr("energy", 0));
        double saved = tag.getDoubleOr("generation_fraction", 0.0D);
        fraction = saved > 0 && saved < 1 ? saved : 0;
    }

    public void setEnergy(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }

    /** Allow one configured production tick to be stored and exported, including after an idle tick. */
    public double prepareGeneration(double rate) {
        double limited = Math.clamp(rate, 0.0, Integer.MAX_VALUE);
        int whole = (int) Math.ceil(limited);
        capacity = Math.max(baseCapacity, whole);
        maxExtract = Math.max(baseExtract, whole);
        return limited;
    }

    public void setReceiveLimit(int limit) { maxInsert = limit; }

    public void pushToNeighbors(Level level, BlockPos pos, int maxPerFace) {
        if (energy <= 0 || maxPerFace <= 0) return;
        var output = new NeighborEnergyOutput(level, () -> Math.min(energy, maxPerFace), sent -> consume((int) sent));
        output.pushFrom(pos, neighbor -> true);
        output.sendSurplusToPumps();
    }

    public int exportLimit() { return maxExtract; }

    /** Downgrading never destroys stored energy. Reception stays blocked until surplus is spent. */
    public void resizeCapacity(int value) { capacity = Math.max(1, value); }
    public void restoreEnergy(int value) { energy = Math.max(0, value); }
}
