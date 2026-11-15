package io.github.genichimaruo.singulo.compat;

import io.github.genichimaruo.singulo.api.android.AndroidStorageCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * One-way native transactional storage -> synchronous legacy consumer adapter.
 * Each operation owns its root transaction: simulation aborts, successful mutation commits.
 * This is not a legacy -> transactional adapter; it may not be called within an existing transaction.
 * The underlying handler's identity/access is managed by the caller's capability cache.
 */
@SuppressWarnings("removal")
public final class ItemResourceView implements IItemHandler {
    private final ResourceHandler<ItemResource> handler;
    public ItemResourceView(ResourceHandler<ItemResource> handler) { this.handler = java.util.Objects.requireNonNull(handler); }
    public static @Nullable IItemHandler of(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : new ItemResourceView(handler);
    }
    /** Ordinary platform inventory only; does not expose Android-private storage to generic machines. */
    public static @Nullable IItemHandler find(Level level, BlockPos pos, @Nullable Direction side) {
        return of(level.getCapability(Capabilities.Item.BLOCK, pos, side));
    }
    /** Android consumers explicitly opt into the private integration before platform storage fallback. */
    public static @Nullable IItemHandler findAndroid(Level level, BlockPos pos, @Nullable Direction side) {
        var shared = level.getCapability(AndroidStorageCapabilities.SHARED_ITEMS, pos, side);
        return shared != null ? shared : find(level, pos, side);
    }
    @Override public int getSlots() { return handler.size(); }
    @Override public ItemStack getStackInSlot(int slot) {
        var resource = handler.getResource(slot);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (inserted < 0 || inserted > stack.getCount()) throw new IllegalStateException("Invalid transactional insertion result");
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        var resource = handler.getResource(slot);
        if (resource.isEmpty()) return ItemStack.EMPTY;
        try (var tx = Transaction.openRoot()) {
            int taken = handler.extract(slot, resource, Math.min(amount, resource.getMaxStackSize()), tx);
            if (taken < 0 || taken > Math.min(amount, resource.getMaxStackSize())) throw new IllegalStateException("Invalid transactional extraction result");
            if (!simulate) tx.commit();
            return resource.toStack(taken);
        }
    }
    @Override public int getSlotLimit(int slot) { return handler.getCapacityAsInt(slot, ItemResource.EMPTY); }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return !stack.isEmpty() && handler.isValid(slot, ItemResource.of(stack));
    }
}
