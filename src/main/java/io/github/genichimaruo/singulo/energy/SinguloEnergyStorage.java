package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

/** 変更通知と直接の出し入れができる電力の入れ物。 */
public class SinguloEnergyStorage extends EnergyStorage {
    private final Runnable onChange;

    public SinguloEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int r = super.receiveEnergy(toReceive, simulate);
        if (r > 0 && !simulate) {
            onChange.run();
        }
        return r;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int r = super.extractEnergy(toExtract, simulate);
        if (r > 0 && !simulate) {
            onChange.run();
        }
        return r;
    }

    /** 装置自身が電力を使う（受け入れ・取り出しの上限を無視する）。 */
    public boolean consume(int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        onChange.run();
        return true;
    }

    /** 発電機が電力を生む。入り切らなかった分は捨てる。 */
    public void generate(int amount) {
        int before = energy;
        energy = Math.min(capacity, energy + amount);
        if (energy != before) {
            onChange.run();
        }
    }

    public void setEnergy(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
