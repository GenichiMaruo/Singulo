package io.github.genichimaruo.singulo.pipe;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import io.github.genichimaruo.singulo.compat.FluidResourceView;
import io.github.genichimaruo.singulo.compat.LegacyBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Powered outlets push to adjacent handlers and retain any undelivered fluid. */
public final class FluidPumpBlockEntity extends LegacyBlockEntity implements DeviceMenu.Host {
    private static final Direction[] FACES = Direction.values();
    private final PowerInput energy = new PowerInput();
    // Only populated when loading a pump saved by the buffered implementation.
    private int legacyFe;
    @Nullable private Transfer transfer;
    private final EnumMap<Direction, BlockCapabilityCache<ResourceHandler<FluidResource>, Direction>> neighbours = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Outlet> outlets = new EnumMap<>(Direction.class);
    // An external tank can accept less during execution than it advertised. Keep the balance until delivered.
    private FluidStack pending = FluidStack.EMPTY;
    private int closedMask, nextFace;
    private int prepaidMilliFe;
    private long lastDelivery = Long.MIN_VALUE;
    private long movedThisTick, spentThisTick;
    private int tickCredit = -1;
    private boolean busy;

    public FluidPumpBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FLUID_PUMP.get(), pos, state);
        for (Direction face : FACES) outlets.put(face, new Outlet(face));
    }

    public PowerInput energy() { return energy; }

    /** FE is accepted against completed fluid transfers in the caller's transaction. */
    public final class PowerInput implements EnergyHandler, IEnergyStorage {
        @Override public int insert(int offer, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(offer);
            return offer == 0 ? 0 : pump(offer, tx);
        }
        @Override public int extract(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }
        @Override public long getAmountAsLong() { return 0; }
        @Override public long getCapacityAsLong() { return 0; }
        @Override public int receiveEnergy(int offer, boolean simulate) {
            if (offer <= 0) return 0;
            try (var tx = Transaction.openRoot()) {
                int accepted = insert(offer, tx);
                if (!simulate) tx.commit();
                return accepted;
            }
        }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return false; }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
    }

    private record PumpState(FluidStack pending, int legacyFe, int nextFace, int prepaidMilliFe,
                             int tickCredit, long lastDelivery, long movedThisTick, long spentThisTick) {}
    private final SnapshotJournal<PumpState> journal = new SnapshotJournal<>() {
        @Override protected PumpState createSnapshot() {
            return new PumpState(pending.copy(), legacyFe, nextFace, prepaidMilliFe, tickCredit, lastDelivery, movedThisTick, spentThisTick);
        }
        @Override protected void revertToSnapshot(PumpState s) {
            pending = s.pending; legacyFe = s.legacyFe; nextFace = s.nextFace; prepaidMilliFe = s.prepaidMilliFe;
            tickCredit = s.tickCredit; lastDelivery = s.lastDelivery; movedThisTick = s.movedThisTick; spentThisTick = s.spentThisTick;
        }
        @Override protected void onRootCommit(PumpState before) { setChanged(); }
    };
    private static final class Transfer {
        final long limit;
        long moved;
        Transfer(long limit) { this.limit = limit; }
        int remaining() { return (int) Math.min(Integer.MAX_VALUE, limit - moved); }
    }

    private record WorldPosition(ServerLevel level, BlockPos pos) {}
    // Two pumps in the same transaction must not take one source twice. This map
    // stays populated only until abort or commit; it does not persist across ticks.
    private static final Map<WorldPosition, SnapshotJournal<Boolean>> WORLD_CLAIMS = new ConcurrentHashMap<>();

    private record WorldSource(WorldPosition key, BlockState state, BlockState replacement, FluidResource fluid) {
        boolean take(TransactionContext tx) {
            ServerLevel level = key.level;
            BlockPos pos = key.pos;
            if (!level.isLoaded(pos) || level.getBlockState(pos) != state) return false;
            var claim = new SnapshotJournal<Boolean>() {
                @Override protected Boolean createSnapshot() { return false; }
                @Override protected void revertToSnapshot(Boolean before) { WORLD_CLAIMS.remove(key, this); }
                @Override protected void onRootCommit(Boolean before) {
                    if (WORLD_CLAIMS.get(key) != this) return;
                    try {
                        // Don't write to a changed or unloaded block; on ordinary server
                        // ticks no world mutation can interleave with the synchronous transfer.
                        if (level.isLoaded(pos) && level.getBlockState(pos) == state) {
                            level.setBlockAndUpdate(pos, replacement);
                        }
                    } finally {
                        WORLD_CLAIMS.remove(key, this);
                    }
                }
            };
            // No speculative writes: even setBlock(flags=0) can notify shapes or schedule ticks.
            claim.updateSnapshots(tx);
            return WORLD_CLAIMS.putIfAbsent(key, claim) == null;
        }
    }

    private @Nullable WorldSource worldSource() {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos pos = worldPosition.relative(intake());
        WorldPosition key = new WorldPosition(server, pos);
        if (WORLD_CLAIMS.containsKey(key) || !server.isLoaded(pos) || server.getBlockEntity(pos) != null) return null;
        BlockState state = server.getBlockState(pos);
        var fluid = state.getFluidState();
        if (!fluid.isSource()) return null;
        BlockState replacement;
        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) replacement = Blocks.AIR.defaultBlockState();
        else if (state.getBlock() instanceof SimpleWaterloggedBlock && state.hasProperty(BlockStateProperties.WATERLOGGED))
            replacement = state.setValue(BlockStateProperties.WATERLOGGED, false);
        else return null;
        return new WorldSource(key, state, replacement, FluidResource.of(fluid.getType()));
    }
    private PumpPowerCost price() {
        return PumpPowerCost.current(level.getGameTime() == lastDelivery ? tickCredit : -1, prepaidMilliFe);
    }

    public Direction intake() { return getBlockState().getValue(FluidPumpBlock.INTAKE); }

    public boolean open(Direction face) {
        return face != intake() && (closedMask & (1 << face.get3DDataValue())) == 0;
    }

    @Nullable public Outlet outlet(@Nullable Direction face) {
        return face != null && open(face) ? outlets.get(face) : null;
    }

    private @Nullable ResourceHandler<FluidResource> neighbour(Direction face) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos pos = worldPosition.relative(face);
        if (!server.isLoaded(pos)) return null;
        var cache = neighbours.get(face);
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, server, pos, face.getOpposite(), () -> !isRemoved(), () -> {});
            neighbours.put(face, cache);
        }
        return cache.getCapability();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidPumpBlockEntity pump) {
        if (pump.legacyFe > 0 || pump.prepaidMilliFe > 0 && pump.price().affordable(0) > 0) {
            try (var tx = Transaction.openRoot()) {
                pump.pump(0, tx);
                tx.commit();
            }
        }
    }

    private void settle(long amount, PumpPowerCost price, int charged) {
        long now = level.getGameTime();
        if (now != lastDelivery) {
            movedThisTick = 0;
            spentThisTick = 0;
        }
        int remainder = price.remainingCredit(amount, charged);
        prepaidMilliFe = price.fixedPrice() ? remainder : 0;
        tickCredit = price.fixedPrice() ? -1 : remainder;
        movedThisTick += amount;
        spentThisTick += charged;
        legacyFe -= Math.min(legacyFe, charged);
        lastDelivery = now;
    }

    private int pump(int offered, TransactionContext tx) {
        if (transfer != null || busy || isRemoved() || !(level instanceof ServerLevel)) return 0;
        PumpPowerCost price = price();
        long limit = price.affordable((long) offered + legacyFe);
        if (limit == 0 || pending.isEmpty() && neighbour(intake()) == null && worldSource() == null) return 0;
        journal.updateSnapshots(tx);
        Transfer work = new Transfer(limit);
        transfer = work;
        try {
            int start = nextFace;
            for (int i = 0; i < FACES.length && work.remaining() > 0; i++) {
                int index = (start + i) % FACES.length;
                Direction face = FACES[index];
                if (!open(face)) continue;
                var target = neighbour(face);
                if (target == null || target instanceof Outlet) continue;
                Outlet output = outlets.get(face);
                BlockPos owner = FluidPipeNetwork.ownerOf(level, worldPosition.relative(face));
                if (owner.equals(output.sourceOwner())) continue;
                for (int tank = 0; tank < output.size() && work.remaining() > 0; tank++) {
                    FluidResource fluid = output.getResource(tank);
                    if (!fluid.isEmpty() && output.sendTo(target, fluid, work.remaining(), tx) > 0) nextFace = (index + 1) % FACES.length;
                    if (!pending.isEmpty()) break;
                }
            }
            int charged = price.charge(work.moved);
            int fromOffer = Math.max(0, charged - legacyFe);
            if (work.moved > 0) settle(work.moved, price, charged);
            return fromOffer;
        } finally { transfer = null; }
    }

    private int draw(FluidResource fluid, int limit, TransactionContext tx) {
        if (limit <= 0) return 0;
        if (pending.isEmpty()) {
            var source = neighbour(intake());
            if (source != null) return source.extract(fluid, limit, tx);
            var world = worldSource();
            if (world == null || !world.fluid.equals(fluid) || limit < net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME) return 0;
            journal.updateSnapshots(tx);
            if (!world.take(tx)) return 0;
            pending = fluid.toStack(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME);
        }
        if (!fluid.equals(FluidResource.of(pending))) return 0;
        journal.updateSnapshots(tx);
        int taken = Math.min(limit, pending.getAmount());
        pending.shrink(taken);
        return taken;
    }

    public final class Outlet implements IFluidHandler, ResourceHandler<FluidResource> {
        private final Direction face;
        private final FluidResourceView synchronous = new FluidResourceView(this);
        Outlet(Direction face) { this.face = face; }
        private boolean available() { return !busy && !isRemoved() && level instanceof ServerLevel && open(face); }

        BlockPos sourceOwner() {
            BlockPos source = worldPosition.relative(intake());
            return level.isLoaded(source) ? FluidPipeNetwork.ownerOf(level, source) : source;
        }

        @Override public int size() {
            if (!available()) return 0;
            if (!pending.isEmpty()) return 1;
            busy = true;
            try {
                var source = neighbour(intake());
                return source != null ? source.size() : worldSource() == null ? 0 : 1;
            } finally { busy = false; }
        }
        @Override public FluidResource getResource(int index) {
            if (!available()) return FluidResource.EMPTY;
            busy = true;
            try {
                if (!pending.isEmpty()) return index == 0 ? FluidResource.of(pending) : FluidResource.EMPTY;
                var source = neighbour(intake());
                if (source != null) return index < source.size() ? source.getResource(index) : FluidResource.EMPTY;
                var world = worldSource();
                return world != null && index == 0 ? world.fluid : FluidResource.EMPTY;
            } finally { busy = false; }
        }
        @Override public long getAmountAsLong(int index) {
            if (!available()) return 0;
            busy = true;
            try {
                if (!pending.isEmpty()) return index == 0 ? pending.getAmount() : 0;
                var source = neighbour(intake());
                if (source != null) return index < source.size() ? source.getAmountAsLong(index) : 0;
                return index == 0 && worldSource() != null ? net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME : 0;
            } finally { busy = false; }
        }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return getAmountAsLong(index); }
        @Override public boolean isValid(int index, FluidResource resource) { return false; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (!resource.equals(getResource(index))) return 0;
            return extract(resource, amount, tx);
        }
        @Override public int extract(FluidResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!available() || transfer != null || amount == 0) return 0;
            busy = true;
            try {
                PumpPowerCost price = price();
                int requested;
                try (var probe = Transaction.open(tx)) { requested = draw(resource, amount, probe); }
                if (requested <= 0) return 0;
                EnergyHandler supplier = null;
                int offered = 0;
                for (Direction side : FACES) {
                    if (price.affordable((long) legacyFe + offered) >= requested) break;
                    BlockPos pos = worldPosition.relative(side);
                    if (!level.isLoaded(pos)) continue;
                    var cap = level.getCapability(Capabilities.Energy.BLOCK, pos, side.getOpposite());
                    if (cap == null || cap instanceof PowerInput) continue;
                    int quote;
                    try (var probe = Transaction.open(tx)) { quote = cap.extract(Integer.MAX_VALUE, probe); }
                    if (quote > offered) { supplier = cap; offered = quote; }
                }
                int limit = (int) Math.min(requested, price.affordable((long) legacyFe + offered));
                if (limit <= 0) return 0;
                try (var work = Transaction.open(tx)) {
                    int taken = draw(resource, limit, work);
                    if (taken <= 0 || taken > limit) return 0;
                    int charged = price.charge(taken);
                    int due = Math.max(0, charged - legacyFe);
                    if (due > 0 && (supplier == null || supplier.extract(due, work) != due)) return 0;
                    journal.updateSnapshots(work);
                    settle(taken, price, charged);
                    work.commit();
                    return taken;
                }
            } finally { busy = false; }
        }

        int sendTo(ResourceHandler<FluidResource> target, FluidResource fluid, int max, TransactionContext tx) {
            if (!available() || transfer == null || !PipeFluidFilter.accepts(target, fluid.toStack(1))) return 0;
            int limit = Math.min(max, transfer.remaining());
            if (limit <= 0) return 0;
            if (target instanceof FluidPipeBlockEntity.Inlet inlet) return inlet.route(fluid, limit, tx, this);
            busy = true;
            try {
                int want;
                try (var probe = Transaction.open(tx)) {
                    int drawn = draw(fluid, limit, probe);
                    want = target.insert(fluid, drawn, probe);
                }
                if (want <= 0 || want > limit) return 0;
                try (var work = Transaction.open(tx)) {
                    if (draw(fluid, want, work) != want || target.insert(fluid, want, work) != want) return 0;
                    work.commit();
                    transfer.moved += want;
                    return want;
                }
            } finally { busy = false; }
        }

        @Override public int getTanks() { return size(); }
        @Override public FluidStack getFluidInTank(int tank) { return synchronous.getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return getCapacityAsInt(tank, FluidResource.EMPTY); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(int amount, FluidAction action) { return synchronous.drain(amount, action); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return synchronous.drain(stack, action); }
    }

    private long recent(long amount) {
        return lastDelivery >= level.getGameTime() - 1 ? amount : 0;
    }

    public void open(ServerPlayer player) {
        DeviceMenu.open(player, this, DeviceMenu.Kind.FLUID_PUMP, new ItemStackHandler(0), i -> switch (i) {
            case DeviceMenu.Pump.INTAKE -> intake().get3DDataValue();
            case DeviceMenu.Pump.CLOSED -> closedMask;
            case DeviceMenu.Pump.FLOW -> (int) recent(movedThisTick);
            case DeviceMenu.Pump.FLOW + 1 -> (int) (recent(movedThisTick) >>> 32);
            case DeviceMenu.Pump.POWER -> (int) recent(spentThisTick);
            case DeviceMenu.Pump.POWER + 1 -> (int) (recent(spentThisTick) >>> 32);
            case DeviceMenu.Pump.ACTIVE -> lastDelivery != Long.MIN_VALUE && level.getGameTime() - lastDelivery < 20 ? 1 : 0;
            default -> 0;
        }, this);
    }

    @Override public boolean onButton(ServerPlayer player, int id) {
        if (id < 0 || id >= FACES.length || Direction.from3DDataValue(id) == intake() || isRemoved()
                || level != player.level() || level.getBlockEntity(worldPosition) != this
                || !Blueprints.withinMenuReach(player, worldPosition)) return false;
        closedMask ^= 1 << id;
        setChanged();
        refreshOutlets();
        return true;
    }

    private void refreshOutlets() {
        if (level == null) return;
        level.invalidateCapabilities(worldPosition);
        if (!level.isClientSide()) {
            BlockState state = getBlockState();
            level.updateNeighborsAt(worldPosition, state.getBlock());
            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("closed", closedMask);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        savePumpContents(tag, registries);
        if (level != null && lastDelivery == level.getGameTime()) {
            tag.putLong("deliveryTick", lastDelivery);
            tag.putLong("movedThisTick", movedThisTick);
            tag.putLong("spentThisTick", spentThisTick);
            tag.putInt("tickCredit", tickCredit);
        }
    }

    private void savePumpContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (legacyFe > 0) tag.putInt("energy", legacyFe);
        tag.putInt("prepaidMilliFe", prepaidMilliFe);
        tag.putInt("closed", closedMask);
        if (!pending.isEmpty()) tag.put("pending", io.github.genichimaruo.singulo.compat.NbtCompat.save(pending, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastDelivery = tag.contains("deliveryTick") ? tag.getLongOr("deliveryTick", 0L) : Long.MIN_VALUE;
        movedThisTick = Math.max(0L, tag.getLongOr("movedThisTick", 0L));
        spentThisTick = Math.max(0L, tag.getLongOr("spentThisTick", 0L));
        tickCredit = Math.clamp(tag.getIntOr("tickCredit", 0), -1, PumpPowerCost.FLOW_SPAN - 1);
        legacyFe = Math.max(0, tag.getIntOr("energy", 0));
        prepaidMilliFe = Math.clamp(tag.getIntOr("prepaidMilliFe", 0), 0, PumpPowerCost.MILLI_FE - 1);
        pending = FluidStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.getCompoundOrEmpty("pending")).result().orElse(FluidStack.EMPTY);
        int savedMask = tag.getIntOr("closed", 0) & 63;
        if (closedMask != savedMask) {
            closedMask = savedMask;
            refreshOutlets();
        }
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level == null || legacyFe == 0 && prepaidMilliFe == 0 && closedMask == 0 && pending.isEmpty()) return;
        var data = new CompoundTag();
        savePumpContents(data, level.registryAccess());
        components.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(getType(), data));
    }
}
