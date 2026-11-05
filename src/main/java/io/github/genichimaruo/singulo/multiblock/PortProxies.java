package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import java.util.function.Supplier;
import javax.annotation.Nullable;
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
    public static final class Energy implements LongEnergyStorage {
        private final Supplier<IEnergyStorage> target;

        public Energy(Supplier<IEnergyStorage> target) {
            this.target = target;
        }

        @Override
        public long receiveLong(long amount, boolean simulate) {
            IEnergyStorage t = target.get();
            return t == null ? 0 : LongEnergyStorage.receive(t, amount, simulate);
        }

        @Override
        public long extractLong(long amount, boolean simulate) {
            IEnergyStorage t = target.get();
            if (t == null) {
                return 0;
            }
            return t instanceof LongEnergyStorage big ? big.extractLong(amount, simulate)
                    : t.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            IEnergyStorage t = target.get();
            return t == null ? 0 : t.receiveEnergy(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            IEnergyStorage t = target.get();
            return t == null ? 0 : t.extractEnergy(amount, simulate);
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage t = target.get();
            return t == null ? 0 : t.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage t = target.get();
            return t == null ? 0 : t.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            IEnergyStorage t = target.get();
            return t != null && t.canExtract();
        }

        @Override
        public boolean canReceive() {
            IEnergyStorage t = target.get();
            return t != null && t.canReceive();
        }
    }

    /** アイテムの口。 */
    public static final class Items implements IItemHandler {
        private final Supplier<IItemHandler> target;

        public Items(Supplier<IItemHandler> target) {
            this.target = target;
        }

        @Nullable
        private IItemHandler at(int slot) {
            IItemHandler t = target.get();
            return t == null || slot < 0 || slot >= t.getSlots() ? null : t;
        }

        @Override
        public int getSlots() {
            IItemHandler t = target.get();
            return t == null ? 0 : t.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler t = at(slot);
            return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler t = at(slot);
            return t == null ? stack : t.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler t = at(slot);
            return t == null ? ItemStack.EMPTY : t.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler t = at(slot);
            return t == null ? 0 : t.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler t = at(slot);
            return t != null && t.isItemValid(slot, stack);
        }
    }

    /** 液体の口。 */
    public static final class Fluids implements IFluidHandler {
        private final Supplier<IFluidHandler> target;

        public Fluids(Supplier<IFluidHandler> target) {
            this.target = target;
        }

        @Override
        public int getTanks() {
            IFluidHandler t = target.get();
            return t == null ? 0 : t.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler t = target.get();
            return t == null || tank < 0 || tank >= t.getTanks() ? FluidStack.EMPTY : t.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler t = target.get();
            return t == null || tank < 0 || tank >= t.getTanks() ? 0 : t.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler t = target.get();
            return t != null && tank >= 0 && tank < t.getTanks() && t.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler t = target.get();
            return t == null ? 0 : t.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler t = target.get();
            return t == null ? FluidStack.EMPTY : t.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler t = target.get();
            return t == null ? FluidStack.EMPTY : t.drain(maxDrain, action);
        }
    }
}
