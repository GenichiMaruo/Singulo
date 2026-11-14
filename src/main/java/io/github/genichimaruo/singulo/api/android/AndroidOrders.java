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
    public enum SnapshotState { AVAILABLE, REJECTED, UNAVAILABLE }
    /** Point-in-time external API receipts only; historyCount counts terminal receipts, not UI orders.
     * Remaining capacity is global ledger space, not a reservation or promise that submit will succeed.
     */
    public record ConsoleSnapshot(SnapshotState state, List<Receipt> receipts, int activeCount,
                                  int historyCount, int receiptCapacity, int remainingReceiptCapacity,
                                  long maxRequestCount) {
        public ConsoleSnapshot { Objects.requireNonNull(state); receipts = List.copyOf(receipts); }
    }
    public static ConsoleSnapshot snapshot(ServerLevel level, BlockPos pos, UUID actor) {
        Objects.requireNonNull(actor);
        var c = console(level, pos);
        return c == null ? new ConsoleSnapshot(SnapshotState.UNAVAILABLE, List.of(), 0, 0,
                MAX_RECEIPTS, 0, WorkConsoleBlockEntity.MAX_COUNT) : c.externalOrderSnapshot(actor);
    }
    /** Loaded linked recipes only. Empty AVAILABLE is distinct from inaccessible/unloaded console.
     * This view may be incomplete while linked racks are unloaded. It never loads their chunks.
     * Material alternatives are descriptive, not a promise that an arbitrary candidate will craft.
     */
    public record RecipeSnapshot(SnapshotState state, List<AndroidRecipe> recipes) {
        public RecipeSnapshot { Objects.requireNonNull(state); recipes = List.copyOf(recipes); }
    }
    public static RecipeSnapshot recipes(ServerLevel level, BlockPos pos, UUID actor) {
        Objects.requireNonNull(actor);
        var c = console(level, pos);
        if (c == null) return new RecipeSnapshot(SnapshotState.UNAVAILABLE, List.of());
        if (!actor.equals(c.owner())) return new RecipeSnapshot(SnapshotState.REJECTED, List.of());
        var out = new java.util.ArrayList<AndroidRecipe>();
        for (var entry : c.recipes()) {
            var recipe = entry.recipe();
            var products = recipe.outputs().stream().map(s -> new AndroidRecipe.Product(
                    io.github.genichimaruo.singulo.android.work.FluidSamples.key(s),
                    io.github.genichimaruo.singulo.android.work.FluidSamples.amount(s),
                    io.github.genichimaruo.singulo.android.work.FluidSamples.liquid(s))).toList();
            var materials = new java.util.ArrayList<AndroidRecipe.Material>();
            for (var input : recipe.inputs()) {
                materials.add(new AndroidRecipe.Material(input.item, input.count,
                        input.alt == null ? List.of(input.item) : java.util.Arrays.asList(input.alt.getItems()),
                        input.alt == null ? "" : io.github.genichimaruo.singulo.android.work.ItemCount.key(input.alt),
                        io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(input.item), false,
                        io.github.genichimaruo.singulo.android.work.FluidSamples.liquid(input.item)));
            }
            if (!recipe.fuel().isEmpty()) {
                var fuel = recipe.fuel();
                materials.add(new AndroidRecipe.Material(
                        io.github.genichimaruo.singulo.android.work.FluidSamples.key(fuel),
                        io.github.genichimaruo.singulo.android.work.FluidSamples.amount(fuel), List.of(fuel), "",
                        io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(fuel), true,
                        io.github.genichimaruo.singulo.android.work.FluidSamples.liquid(fuel)));
            }
            out.add(new AndroidRecipe(recipe.mode(), entry.location(), products, materials, recipe.fuelFill()));
        }
        return new RecipeSnapshot(SnapshotState.AVAILABLE, out);
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
    /** Initial/retry material reservations use only sourceEndpoint. NOT escrow: other consumers
     * and cancellation returns follow normal inventory rules. Only a single ordinary crafting
     * step without catalysts/fuel/liquids is supported; other plans are explicitly rejected.
     */
    public static Receipt submitFromEndpoint(ServerLevel level, BlockPos pos, UUID actor, UUID requestId,
                                            ItemStack target, long count, List<UUID> workers,
                                            @Nullable BlockPos destination, BlockPos sourceEndpoint) {
        Objects.requireNonNull(actor); Objects.requireNonNull(requestId); Objects.requireNonNull(target);
        Objects.requireNonNull(workers); Objects.requireNonNull(sourceEndpoint);
        var c = console(level, pos);
        return c == null ? result(requestId, State.UNAVAILABLE, "console_unavailable")
                : c.submitExternalOrder(actor, requestId, target, count, workers, destination, sourceEndpoint);
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
