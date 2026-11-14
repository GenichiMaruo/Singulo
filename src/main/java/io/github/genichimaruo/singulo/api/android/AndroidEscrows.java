package io.github.genichimaruo.singulo.api.android;

import io.github.genichimaruo.singulo.android.work.AndroidEscrowData;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Owner-authenticated, server-thread API for physically private single-step crafting inputs.
 * acceptedNow transfers ownership of the WHOLE offered list. Replays never transfer it again.
 * The caller must relinquish offered items only on acceptedNow, and persist the request ID.
 * This is synchronous in-memory atomic acceptance, NOT crash-atomic distributed persistence.
 */
public final class AndroidEscrows {
    private AndroidEscrows() { }
    public record Submission(boolean acceptedNow, AndroidOrders.Receipt receipt) { }
    public enum Phase { UNKNOWN, REJECTED, PREPARING, RUNNING, RETURNING, RECOVERABLE, COMPLETED, QUARANTINED }
    public record Snapshot(Phase phase, AndroidOrders.Receipt receipt, List<ItemStack> materials,
                           List<ItemStack> products, boolean workerPending) {
        public Snapshot { materials = copies(materials); products = copies(products); }
        @Override public List<ItemStack> materials() { return copies(materials); }
        @Override public List<ItemStack> products() { return copies(products); }
    }
    private static List<ItemStack> copies(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
    private static void check(ServerLevel level, UUID actor, UUID request) {
        Objects.requireNonNull(level); Objects.requireNonNull(actor); Objects.requireNonNull(request);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("AndroidEscrows requires the server thread");
    }
    public static Submission submit(ServerLevel level, BlockPos console, UUID actor, UUID request,
                                    ItemStack target, long count, List<ItemStack> offered, @Nullable UUID worker) {
        check(level, actor, request); Objects.requireNonNull(console); Objects.requireNonNull(target); Objects.requireNonNull(offered);
        if (!level.isLoaded(console) || !(level.getBlockEntity(console) instanceof WorkConsoleBlockEntity c) || c.isRemoved())
            return new Submission(false, AndroidOrders.result(request, AndroidOrders.State.UNAVAILABLE, "console_unavailable"));
        return c.submitEscrowed(actor, request, target, count, offered, worker);
    }
    /** Recovery/query does not require the original console to exist or be loaded. */
    public static Snapshot snapshot(ServerLevel level, UUID actor, UUID request) {
        check(level, actor, request);
        var data = AndroidEscrowData.get(level); var e = data.find(request);
        if (e == null) return new Snapshot(Phase.UNKNOWN, AndroidOrders.result(request, AndroidOrders.State.UNKNOWN, "escrow_unknown"), List.of(), List.of(), false);
        if (!actor.equals(e.owner)) return new Snapshot(Phase.REJECTED, AndroidOrders.result(request, AndroidOrders.State.REJECTED, "not_escrow_owner"), List.of(), List.of(), false);
        return new Snapshot(Phase.valueOf(e.state.name()), new AndroidOrders.Receipt(request, e.outcome, e.orderId, e.count, e.delivered, null),
                data.inputs(e), data.products(e), e.worker != null);
    }
    public static Snapshot cancel(ServerLevel level, UUID actor, UUID request) {
        check(level, actor, request);
        var data = AndroidEscrowData.get(level); var e = data.find(request);
        if (e != null && actor.equals(e.owner) && e.state == AndroidEscrowData.State.RUNNING) {
            if (level.isLoaded(e.console) && level.getBlockEntity(e.console) instanceof WorkConsoleBlockEntity c
                    && c.escrowConsoleIdentity().equals(e.consoleIdentity)) c.cancel(e.orderId, null);
            data.markTerminal(e, AndroidOrders.State.CANCELLED, e.delivered);
        }
        return snapshot(level, actor, request);
    }
    /** Standard per-stack extraction. The caller must ensure receiver capacity before real extraction.
     * Material refunds are blocked until termination AND the bound worker's actual return.
     * Full receivers should simulate and leave items here; no timeout discards them.
     */
    public static ItemStack claim(ServerLevel level, UUID actor, UUID request, boolean products, int slot, int amount, boolean simulate) {
        check(level, actor, request);
        var data = AndroidEscrowData.get(level); var e = data.find(request);
        if (e == null || !actor.equals(e.owner) || amount <= 0 || slot < 0
                || e.state == AndroidEscrowData.State.QUARANTINED || e.state == AndroidEscrowData.State.PREPARING
                || !products && (e.worker != null || e.state == AndroidEscrowData.State.RUNNING)) return ItemStack.EMPTY;
        if (!simulate) return data.claim(actor, request, products, slot, amount);
        var stacks = products ? data.products(e) : data.inputs(e);
        return slot >= stacks.size() ? ItemStack.EMPTY : stacks.get(slot).copyWithCount(Math.min(amount, stacks.get(slot).getCount()));
    }
    public static boolean acknowledge(ServerLevel level, UUID actor, UUID request) {
        check(level, actor, request);
        var data = AndroidEscrowData.get(level); var e = data.find(request);
        if (!data.acknowledge(actor, request)) return false;
        if (e != null && level.isLoaded(e.console) && level.getBlockEntity(e.console) instanceof WorkConsoleBlockEntity c
                && c.escrowConsoleIdentity().equals(e.consoleIdentity)) c.acknowledgeExternalOrder(actor, request);
        return true;
    }
}
