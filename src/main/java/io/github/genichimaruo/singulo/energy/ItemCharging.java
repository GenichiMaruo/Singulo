package io.github.genichimaruo.singulo.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 電力ストレージの装置（SMES・核パスタ積層蓄電庫）の充電の枠。「充電する物を入れる枠」の物を装置の電力で充電し、
 * 満タンになったら「充電済みの物が出てくる枠」へ移す。電力を受け取れる物（このMODのガントレット・スキャナー・充電器、
 * ほかのMODの電池や道具も）なら何でも入る。
 */
public final class ItemCharging {
    private ItemCharging() {}

    /** 充電の枠に入れられるか（電力を受け取れる物）。 */
    public static boolean chargeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return e != null && e.canReceive();
    }

    /** 満タン（またはもう受け取れない）か。 */
    public static boolean full(ItemStack stack) {
        IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return e == null || !e.canReceive() || e.getEnergyStored() >= e.getMaxEnergyStored();
    }

    /** 物の充電の割合（0〜1。電力を持たなければ 0）。 */
    public static float fraction(ItemStack stack) {
        IEnergyStorage e = stack.isEmpty() ? null : stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return e == null || e.getMaxEnergyStored() <= 0 ? 0 : (float) e.getEnergyStored() / e.getMaxEnergyStored();
    }

    /**
     * 1 tick ぶん: 枠 in の物へ、最大 available の電力を渡す。満タンなら、枠 out が空いていれば移す（空いていなければ待つ）。
     * 渡した電力を返す（装置はその分を減らす）。
     */
    public static long tick(IItemHandlerModifiable items, int in, int out, long available) {
        ItemStack stack = items.getStackInSlot(in);
        if (stack.isEmpty()) {
            return 0;
        }
        if (full(stack)) {
            if (items.getStackInSlot(out).isEmpty()) {
                items.setStackInSlot(out, stack);
                items.setStackInSlot(in, ItemStack.EMPTY);
            }
            return 0;
        }
        if (available <= 0) {
            return 0;
        }
        IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        int got = e.receiveEnergy((int) Math.min(Integer.MAX_VALUE, available), false);
        if (got > 0) {
            items.setStackInSlot(in, stack);                       // 中身（電力の記録）が変わったことを知らせる
        }
        return got;
    }
}
