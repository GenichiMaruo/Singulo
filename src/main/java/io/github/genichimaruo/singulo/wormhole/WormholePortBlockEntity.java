package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * ワームホール・ポート（段階5）。ワームホールの口から PORT_RANGE ブロック以内に置くと、向こう側の口の近くにある
 * 同じポート番号（0〜127）の、働いているポートの隣の装置と直結する。このポートにつないだケーブル・パイプからは、向こう側の装置が
 * そのまま隣にあるように見える（エネルギーは向こうの装置へ流れ、アイテムと液体は向こうの入れ物のスロットとして見える）。
 * つながるのは、手前の口と対になった口の近くのポートだけ。
 * 1つの口で働けるポートは喉の大きさで決まる数（口に近い順）まで。通せる量も喉の大きさで決まり、いちばん大きい喉では上限なし
 * （mod のケーブルからは 1 tick に int を超えて送れる）。番号は0〜127（画面の7つの丸のオンオフ）。
 */
public class WormholePortBlockEntity extends io.github.genichimaruo.singulo.compat.LegacyBlockEntity implements AbstractMachineBlock.MenuOpener {
    private static final Map<Level, Set<WormholePortBlockEntity>> LOADED = new WeakHashMap<>();

    /** Release runtime references when this level closes. */
    public static void forgetLevel(Level level) { LOADED.remove(level); }
    /** 転送中に転送を呼び返さないための印（サーバーは1スレッド）。 */
    private static boolean busy;

    private final Energy energy = new Energy();
    private final Items items = new Items();
    private final Fluids fluids = new Fluids();
    /** ポート番号。向こう側の同じ番号のポートとつながる。 */
    private int channel;
    private long cacheTick = -1;
    @Nullable
    private WormholeMouthBlockEntity cachedMouth;
    /** 手前の口の上限の数に入っていて、働けるか。 */
    private boolean active;
    private List<net.neoforged.neoforge.transfer.energy.EnergyHandler> energyTargets = List.of();
    private List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> itemTargets = List.of();
    private List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>> fluidTargets = List.of();

    public WormholePortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORMHOLE_PORT.get(), pos, state);
    }

    public io.github.genichimaruo.singulo.energy.LongEnergyHandler energy() {
        return energy;
    }

    public IItemHandler items() {
        return items;
    }

    public Fluids fluids() {
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
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
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
        if (level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (now == cacheTick) {
            return;
        }
        cacheTick = now;
        cachedMouth = WormholeMouthBlockEntity.near(level, worldPosition);
        active = cachedMouth != null && cachedMouth.portActive(this);
        List<net.neoforged.neoforge.transfer.energy.EnergyHandler> e = new ArrayList<>();
        List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> it = new ArrayList<>();
        List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>> f = new ArrayList<>();
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
                        add(e, other, Capabilities.Energy.BLOCK, n, dir.getOpposite());
                        add(it, other, Capabilities.Item.BLOCK, n, dir.getOpposite());
                        add(f, other, Capabilities.Fluid.BLOCK, n, dir.getOpposite());
                    }
                }
            }
        }
        energyTargets = e;
        itemTargets = it;
        fluidTargets = f;
    }

    private static <T> void add(List<T> list, Level level, BlockCapability<T, Direction> cap, BlockPos pos, Direction side) {
        T t = level.getCapability(cap, pos, side);
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
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.WORMHOLE_PORT, new net.neoforged.neoforge.items.ItemStackHandler(0), i -> {
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

    private final class Energy implements io.github.genichimaruo.singulo.energy.LongEnergyHandler {
        public long insertLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy insertion");
            WormholeMouthBlockEntity m=mouth();if(busy||m==null||amount==0)return 0;
            long budget=Math.min(amount,m.energyBudget()),sent=0;busy=true;
            try{for(var target:energyTargets){if(sent>=budget)break;sent+=io.github.genichimaruo.singulo.energy.LongEnergyHandler.insert(target,budget-sent,tx);}}
            finally{busy=false;}m.useEnergy(sent,tx);return sent;
        }
        public long extractLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy extraction");
            WormholeMouthBlockEntity m=mouth();if(busy||m==null||amount==0)return 0;
            long budget=Math.min(amount,m.energyBudget()),got=0;busy=true;
            try{for(var target:energyTargets){if(got>=budget)break;got+=io.github.genichimaruo.singulo.energy.LongEnergyHandler.extract(target,budget-got,tx);}}
            finally{busy=false;}m.useEnergy(got,tx);return got;
        }
        public long getAmountAsLong(){refresh();long sum=0;for(var t:energyTargets){long n=t.getAmountAsLong();sum=Long.MAX_VALUE-sum<n?Long.MAX_VALUE:sum+n;}return sum;}
        public long getCapacityAsLong(){refresh();long sum=0;for(var t:energyTargets){long n=t.getCapacityAsLong();sum=Long.MAX_VALUE-sum<n?Long.MAX_VALUE:sum+n;}return sum;}
        public boolean canReceive(){return mouth()!=null;}
        public boolean canExtract(){return mouth()!=null;}
    }

    // ------------------------------------------------------------------ アイテム（向こうの入れ物のスロットを並べて見せる）


    private final class Items implements IItemHandler, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {
        private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> handler(int index,int[] local) {
            if(index < 0) return null;
            for(var h:itemTargets) { if(index<h.size()){local[0]=index;return h;} index-=h.size(); }
            return null;
        }
        public int size(){if(mouth()==null)return 0;return itemTargets.stream().mapToInt(net.neoforged.neoforge.transfer.ResourceHandler::size).sum();}
        public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index){refresh();int[] local={0};var h=handler(index,local);return h==null?net.neoforged.neoforge.transfer.item.ItemResource.EMPTY:h.getResource(local[0]);}
        public long getAmountAsLong(int index){refresh();int[] local={0};var h=handler(index,local);return h==null?0:h.getAmountAsLong(local[0]);}
        public long getCapacityAsLong(int index,net.neoforged.neoforge.transfer.item.ItemResource resource){refresh();int[] local={0};var h=handler(index,local);return h==null?0:h.getCapacityAsLong(local[0],resource);}
        public boolean isValid(int index,net.neoforged.neoforge.transfer.item.ItemResource resource){refresh();int[] local={0};var h=handler(index,local);return h!=null&&h.isValid(local[0],resource);}
        public int insert(int index,net.neoforged.neoforge.transfer.item.ItemResource resource,int amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource,amount);
            var m=mouth();int[] local={0};var h=handler(index,local);if(busy||m==null||h==null)return 0;
            int budget=Math.min(amount,m.itemBudget());if(budget<=0)return 0;busy=true;
            try{int moved=h.insert(local[0],resource,budget,tx);m.useItems(moved,tx);return moved;}finally{busy=false;}
        }
        public int extract(int index,net.neoforged.neoforge.transfer.item.ItemResource resource,int amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource,amount);
            var m=mouth();int[] local={0};var h=handler(index,local);if(busy||m==null||h==null)return 0;
            int budget=Math.min(amount,m.itemBudget());if(budget<=0)return 0;busy=true;
            try{int moved=h.extract(local[0],resource,budget,tx);m.useItems(moved,tx);return moved;}finally{busy=false;}
        }
        public int getSlots(){return size();}
        public ItemStack getStackInSlot(int index){return getResource(index).toStack(getAmountAsInt(index));}
        public ItemStack insertItem(int index,ItemStack stack,boolean simulate){return new io.github.genichimaruo.singulo.compat.ItemResourceView(this).insertItem(index,stack,simulate);}
        public ItemStack extractItem(int index,int amount,boolean simulate){return new io.github.genichimaruo.singulo.compat.ItemResourceView(this).extractItem(index,amount,simulate);}
        public int getSlotLimit(int index){return getCapacityAsInt(index,net.neoforged.neoforge.transfer.item.ItemResource.EMPTY);}
        public boolean isItemValid(int index,ItemStack stack){return !stack.isEmpty()&&isValid(index,net.neoforged.neoforge.transfer.item.ItemResource.of(stack));}
    }
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> transferItems(){return items;}

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("channel", channel);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getIntOr("channel", 0);
    }

    // ------------------------------------------------------------------ 液体

    public final class Fluids implements IFluidHandler,
            net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>,
            io.github.genichimaruo.singulo.pipe.PipeFluidFilter {
        private final IFluidHandler legacy = new io.github.genichimaruo.singulo.compat.FluidResourceView(this);
        public boolean pipeAccepts(FluidStack fluid) {
            refresh();
            for (var h : fluidTargets) if (!(h instanceof io.github.genichimaruo.singulo.pipe.PipeFluidFilter filter) || filter.pipeAccepts(fluid)) return true;
            return false;
        }
        private record Slot(net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> handler, int local) {}
        private Slot slot(int index) {
            if (index < 0 || mouth() == null) return null;
            for (var h : fluidTargets) { if (index < h.size()) return new Slot(h, index); index -= h.size(); }
            return null;
        }
        public int size() { if (mouth() == null) return 0; return fluidTargets.stream().mapToInt(net.neoforged.neoforge.transfer.ResourceHandler::size).sum(); }
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            var s = slot(index); return s == null ? net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY : s.handler.getResource(s.local);
        }
        public long getAmountAsLong(int index) { var s = slot(index); return s == null ? 0 : s.handler.getAmountAsLong(s.local); }
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource fluid) { var s = slot(index); return s == null ? 0 : s.handler.getCapacityAsLong(s.local, fluid); }
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource fluid) { var s = slot(index); return s != null && s.handler.isValid(s.local, fluid); }
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource fluid, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) { return transfer(index, fluid, amount, tx, true); }
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource fluid, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) { return transfer(index, fluid, amount, tx, false); }
        public int insert(net.neoforged.neoforge.transfer.fluid.FluidResource fluid, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) { return transfer(-1, fluid, amount, tx, true); }
        public int extract(net.neoforged.neoforge.transfer.fluid.FluidResource fluid, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) { return transfer(-1, fluid, amount, tx, false); }
        private int transfer(int index, net.neoforged.neoforge.transfer.fluid.FluidResource fluid, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx, boolean insert) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(fluid, amount);
            var mouth = mouth();
            if (busy || mouth == null || amount == 0) return 0;
            int limit = Math.min(amount, mouth.fluidBudget()), moved = 0;
            Slot selected = index < 0 ? null : slot(index);
            if (index >= 0 && selected == null) return 0;
            busy = true;
            try {
                if (selected != null) {
                    moved = insert ? selected.handler.insert(selected.local, fluid, limit, tx) : selected.handler.extract(selected.local, fluid, limit, tx);
                } else {
                    for (var h : fluidTargets) {
                        if (moved >= limit) break;
                        moved += insert ? h.insert(fluid, limit - moved, tx) : h.extract(fluid, limit - moved, tx);
                    }
                }
                if (moved > 0) mouth.useFluid(moved, tx);
                return moved;
            } finally { busy = false; }
        }
        public int getTanks() { return size(); }
        public FluidStack getFluidInTank(int index) { return legacy.getFluidInTank(index); }
        public int getTankCapacity(int index) { return legacy.getTankCapacity(index); }
        public boolean isFluidValid(int index, FluidStack stack) { return legacy.isFluidValid(index, stack); }
        public int fill(FluidStack stack, FluidAction action) { return legacy.fill(stack, action); }
        public FluidStack drain(FluidStack stack, FluidAction action) { return legacy.drain(stack, action); }
        public FluidStack drain(int amount, FluidAction action) { return legacy.drain(amount, action); }
    }
}
