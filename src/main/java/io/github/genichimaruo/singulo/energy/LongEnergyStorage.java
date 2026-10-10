package io.github.genichimaruo.singulo.energy;

import net.minecraftforge.energy.IEnergyStorage;

/**
 * 1 tick に int の上限（約 21 億 FE）を超える量をやり取りできる電力の口。
 * FE の口（IEnergyStorage）は int までなので、mod の中の装置どうし（ケーブル・リアクターなど）だけがこちらで受け渡す。
 * ほかの mod の装置へは、これまでどおり int の範囲で渡す。
 */
public interface LongEnergyStorage extends IEnergyStorage {
    long receiveLong(long amount, boolean simulate);

    default long extractLong(long amount, boolean simulate) {
        return extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate);
    }

    /** target が受け取った量。long の口なら上限なし、FE の口なら int の範囲で。 */
    static long receive(IEnergyStorage target, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (target instanceof LongEnergyStorage big) {
            return big.receiveLong(amount, simulate);
        }
        return target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate);
    }
}
