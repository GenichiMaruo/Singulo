package io.github.genichimaruo.singulo.energy;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Charging slots use an isolated native item location, preserving item replacements on commit. */
@SuppressWarnings("removal")
public final class ItemCharging {
    private ItemCharging() {}

    private static EnergyHandler capability(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var contents = NonNullList.of(ItemStack.EMPTY, stack.copy());
        return ItemAccess.forHandlerIndexStrict(new ItemStacksResourceHandler(contents), 0).getCapability(Capabilities.Energy.ITEM);
    }
    private static int accepts(EnergyHandler energy, int amount) {
        try (var tx = Transaction.openRoot()) { return energy.insert(amount, tx); }
    }
    public static boolean chargeable(ItemStack stack) {
        var e = capability(stack);
        return e != null && e.getCapacityAsLong() > 0 &&
                (e.getAmountAsLong() >= e.getCapacityAsLong() || accepts(e, 1) > 0);
    }
    public static boolean full(ItemStack stack) {
        var e = capability(stack);
        return e == null || e.getAmountAsLong() >= e.getCapacityAsLong() || accepts(e, 1) == 0;
    }
    public static float fraction(ItemStack stack) {
        var e = capability(stack);
        return e == null || e.getCapacityAsLong() <= 0 ? 0 : (float) ((double) e.getAmountAsLong() / e.getCapacityAsLong());
    }
    public static long tick(IItemHandlerModifiable items, int in, int out, long available) {
        ItemStack stack = items.getStackInSlot(in);
        if (stack.isEmpty()) return 0;
        if (full(stack)) {
            if (items.getStackInSlot(out).isEmpty()) {
                items.setStackInSlot(out, stack);
                items.setStackInSlot(in, ItemStack.EMPTY);
            }
            return 0;
        }
        if (available <= 0) return 0;
        var contents = NonNullList.of(ItemStack.EMPTY, stack.copy());
        var storage = new ItemStacksResourceHandler(contents);
        var access = ItemAccess.forHandlerIndexStrict(storage, 0);
        var energy = access.getCapability(Capabilities.Energy.ITEM);
        if (energy == null) return 0;
        int got;
        try (var tx = Transaction.openRoot()) {
            got = energy.insert((int) Math.min(Integer.MAX_VALUE, available), tx);
            tx.commit();
        }
        if (got > 0) items.setStackInSlot(in, storage.getResource(0).toStack(storage.getAmountAsInt(0)));
        return got;
    }
}
