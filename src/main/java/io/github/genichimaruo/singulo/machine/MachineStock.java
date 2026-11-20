package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** Machine-owned stock. Only ordinary stacks enter vanilla item codecs or leave an inventory slot. */
final class MachineStock {
    private MachineStock() {}

    static int limit(int normal) {
        int stacks = ServerConfig.SPEC.isLoaded() ? ServerConfig.MACHINE_SLOT_STACKS.get() : 1;
        return normal <= 1 ? normal : (int) Math.min(Integer.MAX_VALUE, (long) normal * stacks);
    }

    static int limit(ItemStack stack, boolean input) {
        return input && UsesHelper.hasUses(stack) ? 1 : limit(stack.getMaxStackSize());
    }

    static ItemStack visible(ItemStack stack) {
        return stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(stack.getMaxStackSize()) : stack;
    }

    static CompoundTag save(ItemStackHandler inventory) {
        var heads = new ItemStackHandler(inventory.getSlots());
        int[] extra = new int[heads.getSlots()];
        boolean reserved = false;
        for (int i = 0; i < heads.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i), head = visible(stack);
            heads.setStackInSlot(i, head);
            extra[i] = stack.getCount() - head.getCount();
            reserved |= extra[i] > 0;
        }
        CompoundTag tag = heads.serializeNBT();
        if (reserved) tag.putIntArray("extra", extra);
        return tag;
    }

    static ItemStackHandler read(CompoundTag tag) {
        var inventory = new ItemStackHandler(0);
        inventory.deserializeNBT(tag);
        int[] extra = tag.getIntArray("extra");
        for (int i = 0; i < Math.min(inventory.getSlots(), extra.length); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) stack.grow(io.github.genichimaruo.singulo.compat.Legacy.clamp(extra[i], 0, Integer.MAX_VALUE - stack.getCount()));
        }
        return inventory;
    }
}
