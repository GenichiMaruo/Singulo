package io.github.genichimaruo.singulo.api.android;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
/**
 * Server-thread-only, AE2-independent API introduced in 0.18.0 development.
 * The caller must authenticate actor; this trusted Java API is not a network authentication boundary.
 * Only the console owner may use this API. Orders use existing supply inventories; no external
 * ingredients are accepted, escrowed or refunded. Completion means delivery to the console/linked
 * port, NOT insertion into a caller's network. Cancellation may leave produced/delivered items;
 * carried materials follow normal Singulo recovery. Persist request IDs until terminal acknowledgement.
 * Never reuse an acknowledged ID or automatically resubmit UNKNOWN/UNAVAILABLE (a replaced console
 * loses its local receipts). Fresh item crafting only. Empty workers selects eligible workers.
 */
public final class AndroidOrders {
    private AndroidOrders() { }
    public static final int MAX_RECEIPTS = 256;
    public enum State { RUNNING, COMPLETED, CANCELLED, FAILED, REJECTED, UNKNOWN, UNAVAILABLE }
    public record Receipt(UUID requestId, State state, int orderId, long requested, long delivered, @Nullable String reason) {
        public boolean terminal() { return state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED; }
    }
    public static Receipt result(UUID id, State state, String reason) {
        return new Receipt(Objects.requireNonNull(id), state, -1, 0, 0, reason);
    }
    @Nullable private static WorkConsoleBlockEntity console(ServerLevel level, BlockPos pos) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("AndroidOrders requires the server thread");
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WorkConsoleBlockEntity c && !c.isRemoved() ? c : null;
    }
    public static Receipt submit(ServerLevel level, BlockPos pos, UUID actor, UUID requestId,
                                 ItemStack target, long count, List<UUID> workers, @Nullable BlockPos destination) {
        Objects.requireNonNull(actor); Objects.requireNonNull(requestId); Objects.requireNonNull(target); Objects.requireNonNull(workers);
        var c = console(level, pos);
        return c == null ? result(requestId, State.UNAVAILABLE, "console_unavailable") : c.submitExternalOrder(actor, requestId, target, count, workers, destination);
    }
    public static Receipt status(ServerLevel level, BlockPos pos, UUID actor, UUID requestId) {
        Objects.requireNonNull(actor); Objects.requireNonNull(requestId);
        var c = console(level, pos);
        return c == null ? result(requestId, State.UNAVAILABLE, "console_unavailable") : c.externalOrderStatus(actor, requestId);
    }
    public static Receipt cancel(ServerLevel level, BlockPos pos, UUID actor, UUID requestId) {
        Objects.requireNonNull(actor); Objects.requireNonNull(requestId);
        var c = console(level, pos);
        return c == null ? result(requestId, State.UNAVAILABLE, "console_unavailable") : c.cancelExternalOrder(actor, requestId);
    }
    /** Forget a terminal receipt only after the caller durably records its outcome. */
    public static boolean acknowledge(ServerLevel level, BlockPos pos, UUID actor, UUID requestId) {
        Objects.requireNonNull(actor); Objects.requireNonNull(requestId);
        var c = console(level, pos);
        return c != null && c.acknowledgeExternalOrder(actor, requestId);
    }
}
