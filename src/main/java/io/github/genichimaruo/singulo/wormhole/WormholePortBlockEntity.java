package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * ワームホール・ポート（段階5）。ワームホールの口から PORT_RANGE ブロック以内に置くと、向こう側の口の近くにある
 * 同じポート番号（0〜127）の、働いているポートの隣の装置と直結する。このポートにつないだケーブル・パイプからは、向こう側の装置が
 * そのまま隣にあるように見える（エネルギーは向こうの装置へ流れ、アイテムと液体は向こうの入れ物のスロットとして見える）。
 * つながるのは、手前の口と対になった口の近くのポートだけ。
 * 1つの口で働けるポートは喉の大きさで決まる数（口に近い順）まで。通せる量も喉の大きさで決まり、いちばん大きい喉では上限なし
 * （mod のケーブルからは 1 tick に int を超えて送れる）。番号は0〜127（画面の7つの丸のオンオフ）。
 */
public class WormholePortBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    private static final Map<Level, Set<WormholePortBlockEntity>> LOADED = new WeakHashMap<>();
    /** 転送中に転送を呼び返さないための印（サーバーは1スレッド）。 */
    private static boolean busy;

    private final IEnergyStorage energy = new Energy();
    private final IItemHandler items = new Items();
    private final IFluidHandler fluids = new Fluids();
    /** ポート番号。向こう側の同じ番号のポートとつながる。 */
    private int channel;
    private long cacheTick = -1;
    @Nullable
    private WormholeMouthBlockEntity cachedMouth;
    /** 手前の口の上限の数に入っていて、働けるか。 */
    private boolean active;
    private List<IEnergyStorage> energyTargets = List.of();
    private List<IItemHandler> itemTargets = List.of();
    private List<IFluidHandler> fluidTargets = List.of();

    public WormholePortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORMHOLE_PORT.get(), pos, state);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public IItemHandler items() {
        return items;
    }

    public IFluidHandler fluids() {
        return fluids;
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        int c = Math.floorMod(channel, io.github.genichimaruo.singulo.machine.DeviceMenu.Port.MAX_CHANNEL + 1);
        if (c != this.channel) {
            this.channel = c;
            cacheTick = -1;
            setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            Set<WormholePortBlockEntity> set = LOADED.get(level);
            if (set != null) {
                set.remove(this);
            }
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ 向こう側

    /** 1 tick に1回、手前の口と、向こう側のポートの隣の入れ物を調べ直す。 */
    private void refresh() {
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if (now == cacheTick) {
            return;
        }
        cacheTick = now;
        cachedMouth = WormholeMouthBlockEntity.near(level, worldPosition);
        active = cachedMouth != null && cachedMouth.portActive(this);
        List<IEnergyStorage> e = new ArrayList<>();
        List<IItemHandler> it = new ArrayList<>();
        List<IFluidHandler> f = new ArrayList<>();
        WormholeMouthBlockEntity remote = active ? cachedMouth.partner() : null;
        if (remote != null && remote.getLevel() instanceof ServerLevel other) {
            Set<WormholePortBlockEntity> ports = LOADED.get(other);
            if (ports != null) {
                for (WormholePortBlockEntity port : ports) {
                    if (port == this || port.isRemoved() || port.channel != channel || !remote.portActive(port)) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        BlockPos n = port.worldPosition.relative(dir);
                        BlockEntity be = other.getBlockEntity(n);
                        if (be instanceof WormholePortBlockEntity || be instanceof WormholeMouthBlockEntity) {
                            continue;
                        }
                        add(e, other, Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
                        add(it, other, Capabilities.ItemHandler.BLOCK, n, dir.getOpposite());
                        add(f, other, Capabilities.FluidHandler.BLOCK, n, dir.getOpposite());
                    }
                }
            }
        }
        energyTargets = e;
        itemTargets = it;
        fluidTargets = f;
    }

    private static <T> void add(List<T> list, Level level, net.minecraftforge.common.capabilities.Capability<T> cap, BlockPos pos, Direction side) {
        T t = Capabilities.get(level, cap, pos, side);
        if (t != null && !list.contains(t)) {
            list.add(t);
        }
    }

    /** 働いている（上限の数に入っている）ときだけ、手前の口。 */
    @Nullable
    private WormholeMouthBlockEntity mouth() {
        refresh();
        return active ? cachedMouth : null;
    }

    public boolean connected() {
        refresh();
        return active && !(energyTargets.isEmpty() && itemTargets.isEmpty() && fluidTargets.isEmpty());
    }

    /** 働いているか（口に近い順で上限の数に入り、口に対がある）。 */
    public boolean active() {
        refresh();
        return active && cachedMouth.partner() != null;
    }

    /** 働いている間は光る（状態の LIT）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WormholePortBlockEntity port) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0) {
            return;
        }
        boolean lit = port.active();
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private int state() {
        if (cachedMouth == null) {
            return io.github.genichimaruo.singulo.machine.DeviceMenu.Port.STATE_NO_MOUTH;
        }
        if (!active) {
            return io.github.genichimaruo.singulo.machine.DeviceMenu.Port.STATE_OVER_LIMIT;
        }
        return cachedMouth.partner() == null ? io.github.genichimaruo.singulo.machine.DeviceMenu.Port.STATE_NO_PARTNER
                : io.github.genichimaruo.singulo.machine.DeviceMenu.Port.STATE_LINKED;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        refresh();
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.WORMHOLE_PORT, new net.minecraftforge.items.ItemStackHandler(0), i -> {
            refresh();
            return switch (i) {
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.STATE -> state();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.SIZE -> cachedMouth == null ? 0 : cachedMouth.size();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.ENERGY -> energyTargets.size();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.ITEMS -> itemTargets.size();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.FLUIDS -> fluidTargets.size();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.CHANNEL -> channel;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.RANK -> cachedMouth == null ? 0 : cachedMouth.rankOf(this);
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Port.LIMIT -> cachedMouth == null ? 0 : cachedMouth.portLimit();
                default -> 0;
            };
        }, (p, id) -> onButton(id));
    }

    /** 画面のボタン: 7つの丸（ビット）のオンオフで番号（0〜127）を決める。 */
    boolean onButton(int id) {
        int bit = id - io.github.genichimaruo.singulo.machine.DeviceMenu.Port.BUTTON_BIT;
        if (bit < 0 || bit >= io.github.genichimaruo.singulo.machine.DeviceMenu.Port.BITS) {
            return false;
        }
        setChannel(channel ^ (1 << bit));
        return true;
    }

    /** 読み込まれているポート（口が順位をつけるのに使う）。 */
    static List<WormholePortBlockEntity> loaded(Level level) {
        Set<WormholePortBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    // ------------------------------------------------------------------ エネルギー（いちばん大きい喉では上限なし）

    private final class Energy implements io.github.genichimaruo.singulo.energy.LongEnergyStorage {
        @Override
        public long receiveLong(long amount, boolean simulate) {
            WormholeMouthBlockEntity m = mouth();
            if (busy || m == null || amount <= 0) {
                return 0;
            }
            long budget = Math.min(amount, m.energyBudget());
            long sent = 0;
            busy = true;
            try {
                for (IEnergyStorage t : energyTargets) {
                    if (sent >= budget) {
                        break;
                    }
                    if (t.canReceive()) {
                        sent += io.github.genichimaruo.singulo.energy.LongEnergyStorage.receive(t, budget - sent, simulate);
                    }
                }
            } finally {
                busy = false;
            }
            if (!simulate) {
                m.useEnergy(sent);
            }
            return sent;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public long extractLong(long amount, boolean simulate) {
            WormholeMouthBlockEntity m = mouth();
            if (busy || m == null || amount <= 0) {
                return 0;
            }
            long budget = Math.min(amount, m.energyBudget());
            long got = 0;
            busy = true;
            try {
                for (IEnergyStorage t : energyTargets) {
                    if (got >= budget) {
                        break;
                    }
                    if (t.canExtract()) {
                        long want = budget - got;
                        got += t instanceof io.github.genichimaruo.singulo.energy.LongEnergyStorage big
                                ? big.extractLong(want, simulate)
                                : t.extractEnergy((int) Math.min(Integer.MAX_VALUE, want), simulate);
                    }
                }
            } finally {
                busy = false;
            }
            if (!simulate) {
                m.useEnergy(got);
            }
            return got;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return (int) extractLong(amount, simulate);
        }

        @Override
        public int getEnergyStored() {
            refresh();
            long sum = 0;
            for (IEnergyStorage t : energyTargets) {
                sum += t.getEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }

        @Override
        public int getMaxEnergyStored() {
            refresh();
            long sum = 0;
            for (IEnergyStorage t : energyTargets) {
                sum += t.getMaxEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }

        @Override
        public boolean canExtract() {
            return mouth() != null && !energyTargets.isEmpty();
        }

        @Override
        public boolean canReceive() {
            return mouth() != null && !energyTargets.isEmpty();
        }
    }

    // ------------------------------------------------------------------ アイテム（向こうの入れ物のスロットを並べて見せる）

    private final class Items implements IItemHandler {
        /** スロット番号 → (入れ物, その中の番号)。 */
        @Nullable
        private IItemHandler handler(int slot, int[] local) {
            for (IItemHandler h : itemTargets) {
                if (slot < h.getSlots()) {
                    local[0] = slot;
                    return h;
                }
                slot -= h.getSlots();
            }
            return null;
        }

        @Override
        public int getSlots() {
            if (mouth() == null) {
                return 0;
            }
            int n = 0;
            for (IItemHandler h : itemTargets) {
                n += h.getSlots();
            }
            return n;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            refresh();
            int[] local = new int[1];
            IItemHandler h = handler(slot, local);
            return h == null ? ItemStack.EMPTY : h.getStackInSlot(local[0]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            WormholeMouthBlockEntity m = mouth();
            int[] local = new int[1];
            IItemHandler h = handler(slot, local);
            if (busy || m == null || h == null || stack.isEmpty()) {
                return stack;
            }
            int budget = Math.min(stack.getCount(), m.itemBudget());
            if (budget <= 0) {
                return stack;
            }
            busy = true;
            ItemStack rest;
            try {
                rest = h.insertItem(local[0], stack.copyWithCount(budget), simulate);
            } finally {
                busy = false;
            }
            int moved = budget - rest.getCount();
            if (!simulate) {
                m.useItems(moved);
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            WormholeMouthBlockEntity m = mouth();
            int[] local = new int[1];
            IItemHandler h = handler(slot, local);
            if (busy || m == null || h == null) {
                return ItemStack.EMPTY;
            }
            int budget = Math.min(amount, m.itemBudget());
            if (budget <= 0) {
                return ItemStack.EMPTY;
            }
            busy = true;
            ItemStack out;
            try {
                out = h.extractItem(local[0], budget, simulate);
            } finally {
                busy = false;
            }
            if (!simulate) {
                m.useItems(out.getCount());
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            refresh();
            int[] local = new int[1];
            IItemHandler h = handler(slot, local);
            return h == null ? 0 : h.getSlotLimit(local[0]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            refresh();
            int[] local = new int[1];
            IItemHandler h = handler(slot, local);
            return h != null && h.isItemValid(local[0], stack);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("channel", channel);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        channel = tag.getInt("channel");
    }

    // ------------------------------------------------------------------ 液体

    private final class Fluids implements IFluidHandler {
        @Nullable
        private IFluidHandler handler(int tank, int[] local) {
            for (IFluidHandler h : fluidTargets) {
                if (tank < h.getTanks()) {
                    local[0] = tank;
                    return h;
                }
                tank -= h.getTanks();
            }
            return null;
        }

        @Override
        public int getTanks() {
            if (mouth() == null) {
                return 0;
            }
            int n = 0;
            for (IFluidHandler h : fluidTargets) {
                n += h.getTanks();
            }
            return n;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            refresh();
            int[] local = new int[1];
            IFluidHandler h = handler(tank, local);
            return h == null ? FluidStack.EMPTY : h.getFluidInTank(local[0]);
        }

        @Override
        public int getTankCapacity(int tank) {
            refresh();
            int[] local = new int[1];
            IFluidHandler h = handler(tank, local);
            return h == null ? 0 : h.getTankCapacity(local[0]);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            refresh();
            int[] local = new int[1];
            IFluidHandler h = handler(tank, local);
            return h != null && h.isFluidValid(local[0], stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            WormholeMouthBlockEntity m = mouth();
            if (busy || m == null || resource.isEmpty()) {
                return 0;
            }
            int budget = Math.min(resource.getAmount(), m.fluidBudget());
            int filled = 0;
            busy = true;
            try {
                for (IFluidHandler h : fluidTargets) {
                    if (filled >= budget) {
                        break;
                    }
                    filled += h.fill(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(resource, budget - filled), action);
                }
            } finally {
                busy = false;
            }
            if (action.execute()) {
                m.useFluid(filled);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            WormholeMouthBlockEntity m = mouth();
            if (busy || m == null || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int budget = Math.min(resource.getAmount(), m.fluidBudget());
            int drained = 0;
            busy = true;
            try {
                for (IFluidHandler h : fluidTargets) {
                    if (drained >= budget) {
                        break;
                    }
                    drained += h.drain(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(resource, budget - drained), action).getAmount();
                }
            } finally {
                busy = false;
            }
            if (action.execute()) {
                m.useFluid(drained);
            }
            return drained == 0 ? FluidStack.EMPTY : io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(resource, drained);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            refresh();
            for (IFluidHandler h : fluidTargets) {
                FluidStack peek = h.drain(maxDrain, FluidAction.SIMULATE);
                if (!peek.isEmpty()) {
                    return drain(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(peek, maxDrain), action);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
