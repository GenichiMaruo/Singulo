package io.github.genichimaruo.singulo.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Owned inventory with native rollback. Legacy calls remain available for synchronous machine logic.
 * Transactional mutations touch only the owned stacks; side effects run once after root commit.
 * This is deliberately not an adapter for arbitrary external IItemHandler implementations.
 */
@SuppressWarnings("removal")
public class TransactionalItemStackHandler extends ItemStackHandler implements ResourceHandler<ItemResource> {
    private final SnapshotJournal<List<ItemStack>> journal = new SnapshotJournal<>() {
        @Override protected List<ItemStack> createSnapshot() {
            List<ItemStack> copy = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) copy.add(stack.copy());
            return copy;
        }
        @Override protected void revertToSnapshot(List<ItemStack> snapshot) {
            if (snapshot.size() != stacks.size()) throw new IllegalStateException("Inventory resized during transaction");
            for (int i = 0; i < snapshot.size(); i++) stacks.set(i, snapshot.get(i).copy());
        }
        @Override protected void onRootCommit(List<ItemStack> original) {
            for (int i = 0; i < original.size(); i++) {
                if (!ItemStack.matches(original.get(i), stacks.get(i))) onContentsChanged(i);
            }
        }
    };
    public TransactionalItemStackHandler() { this(1); }
    public TransactionalItemStackHandler(int size) { super(size); }
    private void requireNoTransaction() {
        if (journal != null && journal.isInTransaction()) throw new IllegalStateException("Legacy item mutation during a transaction");
    }
    @Override public void setSize(int size) { requireNoTransaction(); super.setSize(size); }
    @Override public void setStackInSlot(int index, ItemStack stack) { requireNoTransaction(); super.setStackInSlot(index, stack); }
    @Override public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        if (!simulate) requireNoTransaction(); return super.insertItem(index, stack, simulate);
    }
    @Override public ItemStack extractItem(int index, int amount, boolean simulate) {
        if (!simulate) requireNoTransaction(); return super.extractItem(index, amount, simulate);
    }
    /** Internal owned-state update participating in the same caller transaction. */
    public void setStackInSlot(int index, ItemStack stack, TransactionContext transaction) {
        validateSlotIndex(index);
        if (ItemStack.matches(stacks.get(index), stack)) return;
        journal.updateSnapshots(transaction);
        stacks.set(index, stack.copy());
    }
    @Override public int size() { return getSlots(); }
    @Override public ItemResource getResource(int index) { return ItemResource.of(getStackInSlot(index)); }
    @Override public long getAmountAsLong(int index) { return getStackInSlot(index).getCount(); }
    @Override public long getCapacityAsLong(int index, ItemResource resource) {
        validateSlotIndex(index);
        if (resource.isEmpty()) return getSlotLimit(index);
        return isValid(index, resource) ? Math.min(getSlotLimit(index), resource.getMaxStackSize()) : 0;
    }
    @Override public boolean isValid(int index, ItemResource resource) {
        validateSlotIndex(index);
        return !resource.isEmpty() && isItemValid(index, resource.toStack());
    }
    @Override public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        validateSlotIndex(index);
        if (amount == 0 || !isValid(index, resource)) return 0;
        ItemStack existing = stacks.get(index);
        if (!existing.isEmpty() && !resource.matches(existing)) return 0;
        int accepted = (int) Math.min(amount, Math.max(0, getCapacityAsLong(index, resource) - existing.getCount()));
        if (accepted > 0) {
            journal.updateSnapshots(transaction);
            stacks.set(index, resource.toStack(existing.getCount() + accepted));
        }
        return accepted;
    }
    @Override public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        validateSlotIndex(index);
        ItemStack existing = stacks.get(index);
        if (amount == 0 || existing.isEmpty() || !resource.matches(existing)) return 0;
        int taken = Math.min(amount, existing.getCount());
        journal.updateSnapshots(transaction);
        stacks.set(index, existing.copyWithCount(existing.getCount() - taken));
        return taken;
    }
}