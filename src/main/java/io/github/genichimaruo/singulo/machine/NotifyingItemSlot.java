package io.github.genichimaruo.singulo.machine;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

/** Forwards vanilla's in-place stack edits to the handler's inventory-change callback. */
public class NotifyingItemSlot extends SlotItemHandler {
    private final int handlerSlot;
    public NotifyingItemSlot(IItemHandler handler, int slot, int x, int y) {
        super(handler, slot, x, y);
        this.handlerSlot = slot;
    }

    @Override
    public void setChanged() {
        // Vanilla merges/shrinks the live ItemStack, then calls Slot.setChanged().
        // SlotItemHandler inherits a dummy Container there, so the real handler
        // otherwise never hears about the edit and recipe caches remain stale.
        if (getItemHandler() instanceof IItemHandlerModifiable inventory) {
            inventory.setStackInSlot(handlerSlot, getItem());
        }
        super.setChanged();
    }
}
