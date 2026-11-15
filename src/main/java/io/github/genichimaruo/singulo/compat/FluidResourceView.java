package io.github.genichimaruo.singulo.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Native transactional storage -> synchronous legacy consumer only. Never wraps legacy storage. */
@SuppressWarnings("removal")
public class FluidResourceView implements IFluidHandler, io.github.genichimaruo.singulo.pipe.PipeFluidFilter {
    private final ResourceHandler<FluidResource> handler;
    public FluidResourceView(ResourceHandler<FluidResource> handler) { this.handler = java.util.Objects.requireNonNull(handler); }
    public static @Nullable IFluidHandler of(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : handler instanceof io.github.genichimaruo.singulo.pipe.PipeSplitTanks ? new Split(handler) : new FluidResourceView(handler);
    }
    public static @Nullable IFluidHandler find(Level level, BlockPos pos, @Nullable Direction side) {
        return of(level.getCapability(Capabilities.Fluid.BLOCK, pos, side));
    }
    private static final class Split extends FluidResourceView implements io.github.genichimaruo.singulo.pipe.PipeSplitTanks {
        Split(ResourceHandler<FluidResource> handler) { super(handler); }
    }
    @Override public boolean pipeAccepts(FluidStack fluid) {
        return !(handler instanceof io.github.genichimaruo.singulo.pipe.PipeFluidFilter filter) || filter.pipeAccepts(fluid);
    }
    @Override public int getTanks() { return handler.size(); }
    @Override public FluidStack getFluidInTank(int tank) { return handler.getResource(tank).toStack(handler.getAmountAsInt(tank)); }
    @Override public int getTankCapacity(int tank) { return handler.getCapacityAsInt(tank, FluidResource.EMPTY); }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return !stack.isEmpty() && handler.isValid(tank, FluidResource.of(stack)); }
    @Override public int fill(FluidStack stack, FluidAction action) {
        if (stack.isEmpty()) return 0;
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(stack), stack.getAmount(), tx);
            checkAmount(inserted, stack.getAmount());
            if (action.execute()) tx.commit();
            return inserted;
        }
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action) {
        if (stack.isEmpty()) return FluidStack.EMPTY;
        var resource = FluidResource.of(stack);
        try (var tx = Transaction.openRoot()) {
            int taken = handler.extract(resource, stack.getAmount(), tx);
            checkAmount(taken, stack.getAmount());
            if (action.execute()) tx.commit();
            return resource.toStack(taken);
        }
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < handler.size(); i++) {
                var resource = handler.getResource(i);
                if (resource.isEmpty()) continue;
                // The unrestricted overload may combine indices holding this same resource, never mix fluids.
                int taken = handler.extract(resource, maxDrain, tx);
                checkAmount(taken, maxDrain);
                if (taken > 0) {
                    if (action.execute()) tx.commit();
                    return resource.toStack(taken);
                }
            }
            return FluidStack.EMPTY;
        }
    }
    private static void checkAmount(int amount, int limit) {
        if (amount < 0 || amount > limit) throw new IllegalStateException("Invalid transactional fluid transfer result");
    }
}
