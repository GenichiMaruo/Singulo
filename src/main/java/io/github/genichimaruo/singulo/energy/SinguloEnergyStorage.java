package io.github.genichimaruo.singulo.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.EnergyStorage;

/** 変更通知と直接の出し入れができる電力の入れ物。 */
public class SinguloEnergyStorage extends EnergyStorage {
    private final Runnable onChange;
    private final int baseCapacity, baseExtract;
    private double fraction;

    public SinguloEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
        this.baseCapacity = capacity;
        this.baseExtract = maxExtract;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (energy >= capacity || toReceive <= 0) return 0;
        int r = super.receiveEnergy(toReceive, simulate);
        if (r > 0 && !simulate) {
            onChange.run();
        }
        return r;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (toExtract <= 0) return 0;
        int r = super.extractEnergy(toExtract, simulate);
        if (r > 0 && !simulate) {
            onChange.run();
        }
        return r;
    }

    /** 装置自身が電力を使う（受け入れ・取り出しの上限を無視する）。 */
    public boolean consume(int amount) {
        if (amount < 0 || energy < amount) {
            return false;
        }
        energy -= amount;
        onChange.run();
        return true;
    }

    /** 発電機が電力を生む。入り切らなかった分は捨てる。 */
    public void generate(int amount) {
        int before = energy;
        energy = (int) Math.max(before, Math.min(capacity, (long) energy + amount));
        if (energy != before) {
            onChange.run();
        }
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
        restoreEnergy(tag.getInt("energy"));
        double saved = tag.getDouble("generation_fraction");
        fraction = saved > 0 && saved < 1 ? saved : 0;
    }

    public void setEnergy(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }

    /** Allow one configured production tick to be stored and exported, including after an idle tick. */
    public double prepareGeneration(double rate) {
        double limited = io.github.genichimaruo.singulo.compat.Legacy.clamp(rate, 0.0, Integer.MAX_VALUE);
        int whole = (int) Math.ceil(limited);
        capacity = Math.max(baseCapacity, whole);
        maxExtract = Math.max(baseExtract, whole);
        return limited;
    }

    public void setReceiveLimit(int limit) { maxReceive = limit; }

    public void pushToNeighbors(Level level, BlockPos pos, int maxPerFace) {
        if (energy <= 0 || maxPerFace <= 0) return;
        var output = new NeighborEnergyOutput(level, () -> Math.min(energy, maxPerFace), sent -> consume((int) sent));
        output.pushFrom(pos, neighbor -> true);
        output.sendSurplusToPumps();
    }

    public int exportLimit() { return maxExtract; }

    /** Downgrading never destroys stored energy. Reception stays blocked until surplus is spent. */
    public void resizeCapacity(int value) { capacity = Math.max(1, value); }

    /** Restore pre-existing charge even if a removed upgrade or migration reduced the capacity. */
    public void restoreEnergy(int value) { energy = Math.max(0, value); }
}
