package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 搬入出ポートがいつも返す、電力・アイテム・液体の口。使われるたびに、つながっているコントローラの口を引いて渡す
 * （未形成・形が崩れている間は、何も受け取らず何も出さない空の口として振る舞う）。
 * <p>
 * 以前は未形成のときに口そのものを返さなかった（null）。Mekanism などのパイプやケーブルは、つなぐときに「口がない」と覚えると、
 * あとで口ができたことに気づかないことがある（読み込み直したとき・ポートがつながってからコントローラが形成済みになるまでの間など）。
 * いつも同じ口を返していれば、パイプは必ずつながり、形ができた時点から流れ始める。
 */
public final class PortProxies {
    private PortProxies() {}

    /** 電力の口。Singulo のケーブルとは long で受け渡す。 */
    public static final class Energy implements io.github.genichimaruo.singulo.energy.LongEnergyHandler {
        private final Supplier<net.neoforged.neoforge.transfer.energy.EnergyHandler> target;
        public Energy(Supplier<net.neoforged.neoforge.transfer.energy.EnergyHandler> target){this.target=target;}
        public long insertLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy insertion");var t=target.get();return t==null?0:io.github.genichimaruo.singulo.energy.LongEnergyHandler.insert(t,amount,tx);
        }
        public long extractLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy extraction");var t=target.get();return t==null?0:io.github.genichimaruo.singulo.energy.LongEnergyHandler.extract(t,amount,tx);
        }
        public long getAmountAsLong(){var t=target.get();return t==null?0:t.getAmountAsLong();}
        public long getCapacityAsLong(){var t=target.get();return t==null?0:t.getCapacityAsLong();}
        public boolean canExtract(){var t=target.get();if(t==null)return false;try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){return t.extract(1,tx)>0;}}
        public boolean canReceive(){var t=target.get();if(t==null)return false;try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){return t.insert(1,tx)>0;}}
    }

    /** アイテムの口。 */
    public static final class Items implements IItemHandler, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {
        private final Supplier<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> target;
        public Items(Supplier<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> target) { this.target = target; }
        private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> at(int slot) {
            var handler = target.get(); return handler == null || slot < 0 || slot >= handler.size() ? null : handler;
        }
        public int size() { var handler = target.get(); return handler == null ? 0 : handler.size(); }
        public net.neoforged.neoforge.transfer.item.ItemResource getResource(int slot) {
            var handler = at(slot); return handler == null ? net.neoforged.neoforge.transfer.item.ItemResource.EMPTY : handler.getResource(slot);
        }
        public long getAmountAsLong(int slot) { var handler = at(slot); return handler == null ? 0 : handler.getAmountAsLong(slot); }
        public long getCapacityAsLong(int slot, net.neoforged.neoforge.transfer.item.ItemResource resource) { var handler = at(slot); return handler == null ? 0 : handler.getCapacityAsLong(slot, resource); }
        public boolean isValid(int slot, net.neoforged.neoforge.transfer.item.ItemResource resource) { var handler = at(slot); return handler != null && handler.isValid(slot, resource); }
        public int insert(int slot, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var handler = at(slot); return handler == null ? 0 : handler.insert(slot, resource, amount, tx);
        }
        public int extract(int slot, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var handler = at(slot); return handler == null ? 0 : handler.extract(slot, resource, amount, tx);
        }
        public int getSlots() { return size(); }
        public ItemStack getStackInSlot(int slot) { return getResource(slot).toStack(getAmountAsInt(slot)); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var handler = at(slot); return handler == null ? stack : new io.github.genichimaruo.singulo.compat.ItemResourceView(handler).insertItem(slot, stack, simulate);
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var handler = at(slot); return handler == null ? ItemStack.EMPTY : new io.github.genichimaruo.singulo.compat.ItemResourceView(handler).extractItem(slot, amount, simulate);
        }
        public int getSlotLimit(int slot) { return getCapacityAsInt(slot, net.neoforged.neoforge.transfer.item.ItemResource.EMPTY); }
        public boolean isItemValid(int slot, ItemStack stack) { return !stack.isEmpty() && isValid(slot, net.neoforged.neoforge.transfer.item.ItemResource.of(stack)); }
    }

    /** 液体の口。 */
    public static final class Fluids implements IFluidHandler, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>, io.github.genichimaruo.singulo.pipe.PipeFluidFilter {
        private final Supplier<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>> target;

        public Fluids(Supplier<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>> target) {
            this.target = target;
        }

        public int size() { var t = target.get(); return t == null ? 0 : t.size(); }
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            var t = target.get(); return t == null || index < 0 || index >= t.size() ? net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY : t.getResource(index);
        }
        public long getAmountAsLong(int index) { var t = target.get(); return t == null || index < 0 || index >= t.size() ? 0 : t.getAmountAsLong(index); }
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            var t = target.get(); return t == null || index < 0 || index >= t.size() ? 0 : t.getCapacityAsLong(index, resource);
        }
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            var t = target.get(); return t != null && index >= 0 && index < t.size() && t.isValid(index, resource);
        }
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var t = target.get(); return t == null || index < 0 || index >= t.size() ? 0 : t.insert(index, resource, amount, tx);
        }
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var t = target.get(); return t == null || index < 0 || index >= t.size() ? 0 : t.extract(index, resource, amount, tx);
        }
        public int insert(net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var t = target.get(); return t == null ? 0 : t.insert(resource, amount, tx);
        }
        public int extract(net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            var t = target.get(); return t == null ? 0 : t.extract(resource, amount, tx);
        }
        @Override
        public boolean pipeAccepts(FluidStack fluid) {
            return !(target.get() instanceof io.github.genichimaruo.singulo.pipe.PipeFluidFilter filter) || filter.pipeAccepts(fluid);
        }

        @Override
        public int getTanks() {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null ? 0 : t.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null || tank < 0 || tank >= t.getTanks() ? FluidStack.EMPTY : t.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null || tank < 0 || tank >= t.getTanks() ? 0 : t.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t != null && tank >= 0 && tank < t.getTanks() && t.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null ? 0 : t.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null ? FluidStack.EMPTY : t.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler t = io.github.genichimaruo.singulo.compat.FluidResourceView.of(target.get());
            return t == null ? FluidStack.EMPTY : t.drain(maxDrain, action);
        }
    }
}
