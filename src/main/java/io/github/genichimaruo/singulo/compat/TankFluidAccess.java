package io.github.genichimaruo.singulo.compat;

import java.util.Objects;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Sided policy over explicitly owned native tanks, not an arbitrary legacy handler adapter. */
@SuppressWarnings("removal")
public interface TankFluidAccess extends IFluidHandler, ResourceHandler<FluidResource> {
    TransactionalFluidTank tankAt(int index);
    default boolean allowsInsert(int index) { return true; }
    boolean allowsExtract(int index);
    @Override default int size() { return getTanks(); }
    @Override default FluidResource getResource(int index) { Objects.checkIndex(index, size()); return tankAt(index).getResource(0); }
    @Override default long getAmountAsLong(int index) { Objects.checkIndex(index, size()); return tankAt(index).getAmountAsLong(0); }
    @Override default boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        return !resource.isEmpty() && isFluidValid(index, resource.toStack(1));
    }
    @Override default long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        return resource.isEmpty() || isValid(index, resource) ? getTankCapacity(index) : 0;
    }
    @Override default int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size()); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!allowsInsert(index) || !isValid(index, resource)) return 0;
        int room = (int) Math.max(0, getCapacityAsLong(index, resource) - getAmountAsLong(index));
        return tankAt(index).insert(0, resource, Math.min(amount, room), transaction);
    }
    @Override default int insert(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int inserted = 0;
        // Fill matching tanks before empty ones, avoiding unnecessary fragmentation.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < size() && inserted < amount; i++) {
            var stored = getResource(i);
            if (pass == 0 ? stored.equals(resource) : stored.isEmpty())
                inserted += insert(i, resource, amount - inserted, transaction);
        }
        return inserted;
    }
    @Override default int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size()); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return allowsExtract(index) ? tankAt(index).extract(0, resource, amount, transaction) : 0;
    }
}
