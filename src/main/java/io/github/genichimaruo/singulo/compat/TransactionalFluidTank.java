package io.github.genichimaruo.singulo.compat;

import java.util.Objects;
import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A tank owning its own native transactional state. Legacy machine-internal operations remain
 * available outside transactions; no legacy fill/drain is used to implement insert/extract.
 * Rollback restores fluid plus components, and notifications occur only after root commit.
 */
@SuppressWarnings("removal")
public class TransactionalFluidTank extends FluidTank implements ResourceHandler<FluidResource> {
    private final SnapshotJournal<FluidStack> journal = new SnapshotJournal<>() {
        @Override protected FluidStack createSnapshot() { return fluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack snapshot) { fluid = snapshot; }
        @Override protected void onRootCommit(FluidStack original) { onContentsChanged(); }
    };
    public TransactionalFluidTank(int capacity) { super(capacity); }
    public TransactionalFluidTank(int capacity, Predicate<FluidStack> validator) { super(capacity, validator); }
    private void checkIndex(int index) { Objects.checkIndex(index, 1); }
    private void requireNoTransaction() {
        if (journal.isInTransaction()) throw new IllegalStateException("Legacy fluid mutation during a transaction");
    }
    @Override public int size() { return 1; }
    @Override public FluidResource getResource(int index) { checkIndex(index); return FluidResource.of(fluid); }
    @Override public long getAmountAsLong(int index) { checkIndex(index); return fluid.getAmount(); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) {
        checkIndex(index); return resource.isEmpty() || isValid(index, resource) ? getCapacity() : 0;
    }
    @Override public boolean isValid(int index, FluidResource resource) {
        checkIndex(index); return !resource.isEmpty() && isFluidValid(resource.toStack(1));
    }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
        checkIndex(index); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource) || (!fluid.isEmpty() && !resource.matches(fluid))) return 0;
        int accepted = Math.min(amount, Math.max(0, getCapacity() - fluid.getAmount()));
        if (accepted > 0) {
            journal.updateSnapshots(tx);
            fluid = resource.toStack(fluid.getAmount() + accepted);
        }
        return accepted;
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
        checkIndex(index); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!resource.matches(fluid)) return 0;
        int taken = Math.min(amount, fluid.getAmount());
        if (taken > 0) {
            journal.updateSnapshots(tx);
            fluid = resource.toStack(fluid.getAmount() - taken);
        }
        return taken;
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        if (action.execute()) requireNoTransaction();
        return super.fill(resource, action);
    }
    @Override public FluidStack drain(int amount, FluidAction action) {
        if (action.execute()) requireNoTransaction();
        return super.drain(amount, action);
    }
    @Override public void setFluid(FluidStack fluid) { requireNoTransaction(); super.setFluid(fluid); }
    @Override public TransactionalFluidTank setCapacity(int capacity) {
        requireNoTransaction(); super.setCapacity(capacity); return this;
    }
    @Override public TransactionalFluidTank setValidator(Predicate<FluidStack> validator) {
        requireNoTransaction(); super.setValidator(validator); return this;
    }
}
