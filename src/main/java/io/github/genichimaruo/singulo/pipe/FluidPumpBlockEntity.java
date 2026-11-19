package io.github.genichimaruo.singulo.pipe;

import net.minecraftforge.energy.IEnergyStorage;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraftforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** Powered outlets push to adjacent handlers and retain any undelivered fluid. */
public final class FluidPumpBlockEntity extends BlockEntity implements DeviceMenu.Host {
    private static final Direction[] FACES = Direction.values();
    private final PowerInput energy = new PowerInput();
    // Only populated when loading a pump saved by the buffered implementation.
    private int legacyFe;
    @Nullable private Transfer transfer;
    private final EnumMap<Direction, BlockCapabilityCache<IFluidHandler, Direction>> neighbours = new EnumMap<>(Direction.class);
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

    public IEnergyStorage energy() { return energy; }

    /** FE is accepted synchronously, against completed fluid transfers. */
    public final class PowerInput implements IEnergyStorage {
        @Override public int receiveEnergy(int offer, boolean simulate) {
            return offer <= 0 ? 0 : pump(offer, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return false; }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
    }

    private record FluidKey(Fluid fluid, DataComponentMap components) {}

    private static final class Transfer {
        final FluidAction action;
        final long limit;
        long moved;
        // A simulated delivery cannot mutate tanks or pipe budgets. Record those reservations for this offer.
        final Map<FluidKey, Integer> drawn;
        final Map<BlockPos, FluidStack> filled;
        final Map<Object, Long> pipeUsage;

        Transfer(long limit, FluidAction action) {
            this.limit = limit;
            this.action = action;
            drawn = action.simulate() ? new HashMap<>() : Map.of();
            filled = action.simulate() ? new HashMap<>() : Map.of();
            pipeUsage = action.simulate() ? new IdentityHashMap<>() : Map.of();
        }

        int remaining() { return (int) Math.min(Integer.MAX_VALUE, limit - moved); }
    }
    private PumpPowerCost price() {
        return PumpPowerCost.current(level.getGameTime() == lastDelivery ? tickCredit : -1, prepaidMilliFe);
    }

    public Direction intake() { return getBlockState().getValue(FluidPumpBlock.INTAKE); }

    public boolean open(Direction face) {
        return face != intake() && (closedMask & (1 << face.get3DDataValue())) == 0;
    }

    @Nullable public IFluidHandler outlet(@Nullable Direction face) {
        return face != null && open(face) ? outlets.get(face) : null;
    }

    @Nullable
    private IFluidHandler neighbour(Direction face) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos pos = worldPosition.relative(face);
        if (!server.isLoaded(pos)) return null;
        var cache = neighbours.get(face);
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, server, pos, face.getOpposite(), () -> !isRemoved(), () -> {});
            neighbours.put(face, cache);
        }
        IFluidHandler handler = cache.getCapability();
        if (handler == null && face == intake()) {
            var state = server.getBlockState(pos);
            if (state.getFluidState().isSource() && state.getBlock() instanceof BucketPickup pickup)
                return new BucketPickupHandlerWrapper(null, pickup, server, pos);
        }
        return handler;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidPumpBlockEntity pump) {
        if (pump.legacyFe > 0 || pump.prepaidMilliFe > 0 && pump.price().affordable(0) > 0)
            pump.pump(0, FluidAction.EXECUTE);
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
        setChanged();
    }

    private int pump(int offered, FluidAction action) {
        if (transfer != null || busy || isRemoved() || !(level instanceof ServerLevel)) return 0;
        PumpPowerCost price = price();
        long limit = price.affordable((long) offered + legacyFe);
        if (limit == 0 || pending.isEmpty() && neighbour(intake()) == null) return 0;
        Transfer work = new Transfer(limit, action);
        transfer = work;
        try {
            int start = nextFace;
            for (int i = 0; i < FACES.length && work.remaining() > 0; i++) {
                int index = (start + i) % FACES.length;
                Direction face = FACES[index];
                if (!open(face)) continue;
                IFluidHandler target = neighbour(face);
                if (target == null || target instanceof Outlet) continue;
                Outlet output = outlets.get(face);
                BlockPos owner = FluidPipeNetwork.ownerOf(level, worldPosition.relative(face));
                if (owner.equals(output.sourceOwner())) continue;
                int tanks = output.getTanks();
                for (int tank = 0; tank < tanks && work.remaining() > 0; tank++) {
                    FluidStack fluid = output.getFluidInTank(tank);
                    if (!fluid.isEmpty() && output.sendTo(owner, target, fluid, work.remaining()) > 0 && action.execute())
                        nextFace = (index + 1) % FACES.length;
                    if (!pending.isEmpty()) break;
                }
            }
            int charged = price.charge(work.moved);
            int fromOffer = Math.max(0, charged - legacyFe);
            if (work.moved > 0 && action.execute()) settle(work.moved, price, charged);
            return fromOffer;
        } finally { transfer = null; }
    }

    private FluidStack draw(@Nullable FluidStack type, int limit, FluidAction action) {
        if (limit <= 0) return FluidStack.EMPTY;
        if (!pending.isEmpty()) {
            if (type != null && !FluidStack.isSameFluidSameComponents(type, pending)) return FluidStack.EMPTY;
            int n = Math.min(limit, pending.getAmount());
            FluidStack result = pending.copyWithAmount(n);
            if (action.execute()) pending.shrink(n);
            return result;
        }
        IFluidHandler source = neighbour(intake());
        if (source == null) return FluidStack.EMPTY;
        return type == null ? source.drain(limit, action) : source.drain(type.copyWithAmount(limit), action);
    }

    final class Outlet implements IFluidHandler {
        private final Direction face;
        Outlet(Direction face) { this.face = face; }
        private boolean available() { return !busy && !isRemoved() && level instanceof ServerLevel && open(face); }

        BlockPos sourceOwner() {
            BlockPos source = worldPosition.relative(intake());
            return level.isLoaded(source) ? FluidPipeNetwork.ownerOf(level, source) : source;
        }

        @Override public int getTanks() {
            if (!available()) return 0;
            if (!pending.isEmpty()) return 1;
            busy = true;
            try {
                IFluidHandler source = neighbour(intake());
                return source == null ? 0 : source.getTanks();
            } finally { busy = false; }
        }
        @Override public FluidStack getFluidInTank(int tank) {
            if (!available() || tank < 0) return FluidStack.EMPTY;
            busy = true;
            try {
                IFluidHandler source = pending.isEmpty() ? neighbour(intake()) : null;
                FluidStack fluid = !pending.isEmpty() && tank == 0 ? pending
                        : source != null && tank < source.getTanks() ? source.getFluidInTank(tank) : FluidStack.EMPTY;
                return fluid.copy();
            } finally { busy = false; }
        }
        @Override public int getTankCapacity(int tank) {
            if (!available() || tank < 0) return 0;
            if (!pending.isEmpty()) return tank == 0 ? pending.getAmount() : 0;
            busy = true;
            try {
                IFluidHandler source = neighbour(intake());
                return source != null && tank < source.getTanks() ? source.getTankCapacity(tank) : 0;
            } finally { busy = false; }
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(int amount, FluidAction action) { return pull(null, amount, action); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return stack.isEmpty() ? FluidStack.EMPTY : pull(stack, stack.getAmount(), action);
        }

        private FluidStack pull(@Nullable FluidStack type, int amount, FluidAction action) {
            if (!available() || transfer != null || amount <= 0) return FluidStack.EMPTY;
            busy = true;
            try {
                PumpPowerCost price = price();
                FluidStack requested = draw(type, amount, FluidAction.SIMULATE);
                if (requested.isEmpty()) return FluidStack.EMPTY;
                // A pull-only consumer can pay at the moment of extraction from an adjacent FE source.
                IEnergyStorage supplier = null;
                int offered = 0;
                for (Direction side : FACES) {
                    if (price.affordable((long) legacyFe + offered) >= requested.getAmount()) break;
                    BlockPos pos = worldPosition.relative(side);
                    if (!level.isLoaded(pos)) continue;
                    var cap = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, pos, side.getOpposite());
                    if (cap == null || !cap.canExtract()) continue;
                    int quote = cap.extractEnergy(Integer.MAX_VALUE, true);
                    if (quote > offered) { supplier = cap; offered = quote; }
                }
                long limit = price.affordable((long) legacyFe + offered);
                FluidStack result = draw(requested, (int) Math.min(limit, requested.getAmount()), FluidAction.SIMULATE);
                if (action.simulate() || result.isEmpty()) return result;
                if (pending.isEmpty()) {
                    pending = draw(result, result.getAmount(), FluidAction.EXECUTE).copy();
                    if (pending.isEmpty()) return FluidStack.EMPTY;
                    setChanged();
                }
                int deliver = Math.min(result.getAmount(), pending.getAmount());
                int due = Math.max(0, price.charge(deliver) - legacyFe);
                if (due > 0) {
                    int paid = supplier.extractEnergy(due, false);
                    if (paid < due) {
                        deliver = (int) Math.min(deliver, price.affordable((long) legacyFe + paid));
                        int unused = paid - Math.max(0, price.charge(deliver) - legacyFe);
                        if (unused > 0 && supplier.canReceive()) supplier.receiveEnergy(unused, false);
                    }
                }
                if (deliver <= 0) return FluidStack.EMPTY;
                result = draw(result, deliver, FluidAction.EXECUTE);
                settle(result.getAmount(), price, price.charge(result.getAmount()));
                return result;
            } finally { busy = false; }
        }

        Map<Object, Long> pipeReservations() { return transfer.pipeUsage; }

        int sendTo(BlockPos owner, IFluidHandler target, FluidStack fluid, int max) {
            if (!available() || transfer == null || !PipeFluidFilter.accepts(target, fluid)) return 0;
            if (target instanceof FluidPipeBlockEntity.Inlet inlet) {
                return inlet.route(fluid.copyWithAmount(Math.min(max, transfer.remaining())), transfer.action, this);
            }
            busy = true;
            try {
                int limit = Math.min(max, transfer.remaining());
                if (transfer.action.simulate()) return simulateDelivery(owner, target, fluid, limit);
                FluidStack offer = draw(fluid, limit, FluidAction.SIMULATE);
                if (offer.isEmpty()) return 0;
                int want = Math.min(offer.getAmount(), target.fill(offer, FluidAction.SIMULATE));
                if (want <= 0) return 0;
                if (pending.isEmpty()) {
                    pending = draw(fluid, want, FluidAction.EXECUTE).copy();
                    if (pending.isEmpty()) return 0;
                    setChanged();
                }
                FluidStack batch = pending.copyWithAmount(Math.min(want, pending.getAmount()));
                int sent = io.github.genichimaruo.singulo.compat.Legacy.clamp(target.fill(batch, FluidAction.EXECUTE), 0, batch.getAmount());
                pending.shrink(sent);
                transfer.moved += sent;
                return sent;
            } finally { busy = false; }
        }

        private int simulateDelivery(BlockPos owner, IFluidHandler target, FluidStack fluid, int limit) {
            FluidStack filled = transfer.filled.get(owner);
            if (filled != null && !FluidStack.isSameFluidSameComponents(filled, fluid)) return 0;
            int filledAmount = filled == null ? 0 : filled.getAmount();
            int room = target.fill(fluid.copyWithAmount((int) Math.min(Integer.MAX_VALUE, (long) filledAmount + limit)), FluidAction.SIMULATE) - filledAmount;
            if (room <= 0) return 0;
            FluidKey key = new FluidKey(fluid.getFluid(), fluid.getComponents());
            int alreadyDrawn = transfer.drawn.getOrDefault(key, 0);
            int request = (int) Math.min(Integer.MAX_VALUE, (long) alreadyDrawn + Math.min(limit, room));
            int total = draw(fluid, request, FluidAction.SIMULATE).getAmount();
            int sent = io.github.genichimaruo.singulo.compat.Legacy.clamp(total - alreadyDrawn, 0, Math.min(limit, room));
            if (sent > 0) {
                transfer.drawn.put(key, alreadyDrawn + sent);
                transfer.filled.put(owner, fluid.copyWithAmount(filledAmount + sent));
                transfer.moved += sent;
            }
            return sent;
        }
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
        if (!level.isClientSide) {
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
        if (!pending.isEmpty()) tag.put("pending", pending.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastDelivery = tag.contains("deliveryTick") ? tag.getLong("deliveryTick") : Long.MIN_VALUE;
        movedThisTick = Math.max(0L, tag.getLong("movedThisTick"));
        spentThisTick = Math.max(0L, tag.getLong("spentThisTick"));
        tickCredit = io.github.genichimaruo.singulo.compat.Legacy.clamp(tag.getInt("tickCredit"), -1, PumpPowerCost.FLOW_SPAN - 1);
        legacyFe = Math.max(0, tag.getInt("energy"));
        prepaidMilliFe = io.github.genichimaruo.singulo.compat.Legacy.clamp(tag.getInt("prepaidMilliFe"), 0, PumpPowerCost.MILLI_FE - 1);
        pending = FluidStack.parseOptional(registries, tag.getCompound("pending"));
        int savedMask = tag.getInt("closed") & 63;
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
        BlockEntity.addEntityType(data, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
    }
}
