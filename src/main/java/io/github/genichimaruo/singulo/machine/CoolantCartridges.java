package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * 冷媒カートリッジ（液体窒素 {@link #AMOUNT} mB 入り）と、空の冷媒カートリッジ。液体の容器として扱うので、
 * バケツと同じく、装置を右クリックすれば注げる・汲める（他MODの液体のパイプがなくても、液体窒素を運べる）。
 * 中身はいつも満タンか空で、半端には出し入れしない。
 */
public final class CoolantCartridges {
    /** 1個に詰まっている液体窒素（mB）。 */
    public static final int AMOUNT = 100;

    private CoolantCartridges() {}

    public static Item filled() {
        return BuiltInRegistries.ITEM.get(Singulo.id("coolant_cartridge"));
    }

    public static Item empty() {
        return BuiltInRegistries.ITEM.get(Singulo.id("empty_coolant_cartridge"));
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.is(filled());
    }

    public static boolean isEmptyCartridge(ItemStack stack) {
        return stack.is(empty());
    }

    private static FluidStack nitrogen() {
        return new FluidStack(SinguloFluids.get("liquid_nitrogen"), AMOUNT);
    }

    /** カートリッジ1個ぶんの液体の出し入れ（満タン ⇔ 空。半端な量は受け付けない）。 */
    public static final class Handler implements IFluidHandlerItem {
        private ItemStack container;

        public Handler(ItemStack container) {
            this.container = container;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return isFilled(container) ? nitrogen() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return AMOUNT;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid().isSame(SinguloFluids.get("liquid_nitrogen"));
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (!isEmptyCartridge(container) || container.getCount() != 1 || !isFluidValid(0, resource) || resource.getAmount() < AMOUNT) {
                return 0;
            }
            if (action.execute()) {
                container = new ItemStack(filled());
            }
            return AMOUNT;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return isFluidValid(0, resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (!isFilled(container) || container.getCount() != 1 || maxDrain < AMOUNT) {
                return FluidStack.EMPTY;
            }
            if (action.execute()) {
                container = new ItemStack(empty());
            }
            return nitrogen();
        }
    }
}
