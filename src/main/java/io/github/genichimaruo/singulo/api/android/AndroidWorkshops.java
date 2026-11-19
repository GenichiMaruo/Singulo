package io.github.genichimaruo.singulo.api.android;

import io.github.genichimaruo.singulo.android.work.FluidSamples;
import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Discovery and material planning for the shared workshop. World queries require the server thread
 * and the authenticated console owner's UUID. Results are detached point-in-time snapshots. */
public final class AndroidWorkshops {
    private AndroidWorkshops() {}

    public static boolean isLocationCard(ItemStack stack) { return WorkLocationCardItem.isCard(stack); }

    /** The recorded dimension and coordinates. Reading a card grants no access to that location. */
    public static Optional<GlobalPos> readLocationCard(ItemStack stack) { return WorkLocationCardItem.recordedLocation(stack); }

    public enum Role { CONSOLE, SUPPLY, DELIVERY, RACK }
    public record Connection(BlockPos console, Role role) {
        public Connection { console = console.immutable(); }
    }

    /** Resolve a native console or reciprocally linked work block; checks only loaded positions. */
    public static Optional<Connection> resolve(ServerLevel level, BlockPos pos, UUID actor) {
        AndroidOrders.requireServerThread(level);
        Objects.requireNonNull(actor);
        if (!level.isLoaded(pos)) return Optional.empty();
        var block = level.getBlockEntity(pos);
        if (block == null || block.isRemoved()) return Optional.empty();
        if (block instanceof WorkConsoleBlockEntity console && actor.equals(console.owner()))
            return Optional.of(new Connection(pos, Role.CONSOLE));
        if (!(block instanceof WorkLinkedBlockEntity linked) || linked.console() == null) return Optional.empty();
        var console = AndroidOrders.console(level, linked.console());
        if (console == null || !actor.equals(console.owner()) || !console.hasWorkshopLink(pos)) return Optional.empty();
        Role role = switch (linked.linkKind()) {
            case SUPPLY -> Role.SUPPLY;
            case DELIVERY -> Role.DELIVERY;
            case RACK -> Role.RACK;
        };
        return Optional.of(new Connection(console.getBlockPos(), role));
    }

    /** One item/component combination or one fluid/chemical sample, counted in items or mB. */
    public record Quantity(ItemStack sample, long amount) {
        public Quantity { sample = FluidSamples.key(sample); }
        @Override public ItemStack sample() { return sample.copy(); }
    }
    private static List<Quantity> quantities(List<ItemCount> counts) {
        return counts.stream().filter(c -> c.count > 0).map(c -> new Quantity(c.item, c.count)).toList();
    }

    public record Stock(AndroidOrders.SnapshotState state, List<Quantity> available) {
        public Stock { available = List.copyOf(available); }
    }

    /** Extractable shared stock after reservations, excluding materials held in individual workers. */
    public static Stock stock(ServerLevel level, BlockPos pos, UUID actor) {
        Objects.requireNonNull(actor);
        var console = AndroidOrders.console(level, pos);
        if (console == null) return new Stock(AndroidOrders.SnapshotState.UNAVAILABLE, List.of());
        if (!actor.equals(console.owner())) return new Stock(AndroidOrders.SnapshotState.REJECTED, List.of());
        return new Stock(AndroidOrders.SnapshotState.AVAILABLE, quantities(console.available()));
    }

    public enum PlanState { MATERIALS_READY, MISSING_MATERIALS, CYCLE, PLAN_LIMIT, INVALID_REQUEST, REJECTED, UNAVAILABLE }
    public record Step(String mode, BlockPos location, Quantity outputPerCraft, long crafts) {
        public Step { location = location.immutable(); }
    }
    public record Preview(PlanState state, List<Step> steps, List<Quantity> fromStock,
                          List<Quantity> fromWorker, List<Quantity> missing) {
        public Preview {
            steps = List.copyOf(steps);
            fromStock = List.copyOf(fromStock);
            fromWorker = List.copyOf(fromWorker);
            missing = List.copyOf(missing);
        }
        private static Preview empty(PlanState state) { return new Preview(state, List.of(), List.of(), List.of(), List.of()); }
    }

    /** Preview a fresh AndroidOrders.submit request. Nothing is reserved. Worker availability,
     * machine setup and destination capacity are checked by the live order, and may change. */
    public static Preview preview(ServerLevel level, BlockPos pos, UUID actor, ItemStack target, long count, List<UUID> workers) {
        Objects.requireNonNull(actor);
        var console = AndroidOrders.console(level, pos);
        if (console == null) return Preview.empty(PlanState.UNAVAILABLE);
        if (!actor.equals(console.owner())) return Preview.empty(PlanState.REJECTED);
        if (target.isEmpty() || FluidSamples.amount(target) <= 0 || FluidSamples.key(target).isEmpty()
                || count < 1 || count > WorkConsoleBlockEntity.MAX_COUNT || workers.stream().anyMatch(Objects::isNull))
            return Preview.empty(PlanState.INVALID_REQUEST);
        var plan = console.previewExternalOrder(actor, List.copyOf(workers), FluidSamples.key(target), count);
        PlanState state = plan.limited ? PlanState.PLAN_LIMIT : plan.loop ? PlanState.CYCLE
                : plan.missing.isEmpty() ? PlanState.MATERIALS_READY : PlanState.MISSING_MATERIALS;
        var steps = plan.steps.stream().map(step -> {
            var recipe = step.entry().recipe();
            return new Step(recipe.mode(), step.entry().location(), new Quantity(recipe.output(), recipe.outputCount()), step.crafts());
        }).toList();
        return new Preview(state, steps, quantities(plan.fromStock), quantities(plan.fromWorker), quantities(plan.missing));
    }
}
