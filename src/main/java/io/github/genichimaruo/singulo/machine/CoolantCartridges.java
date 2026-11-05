package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * 冷媒カートリッジ（液体窒素 {@link #AMOUNT} mB 入り）・液体ヘリウム・カートリッジ（{@link #HELIUM_AMOUNT} mB 入り）と、
 * 共通の空の冷媒カートリッジ。液体の容器として扱うので、バケツと同じく、装置を右クリックすれば注げる・汲める
 * （他MODの液体のパイプがなくても、液体窒素や液体ヘリウムを運べる）。中身はいつも満タンか空で、半端には出し入れしない。
 */
public final class CoolantCartridges {
    /** 1個に詰まっている液体窒素（mB）。 */
    public static final int AMOUNT = 100;
    /** 1個に詰まっている液体ヘリウム（mB）。 */
    public static final int HELIUM_AMOUNT = 250;

    private CoolantCartridges() {}

    public static Item filled() {
        return BuiltInRegistries.ITEM.get(Singulo.id("coolant_cartridge"));
    }

    public static Item helium() {
        return BuiltInRegistries.ITEM.get(Singulo.id("helium_cartridge"));
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

    /** 中身の液体（満タンのカートリッジだけ。空なら null）。 */
    @Nullable
    private static FluidStack contents(ItemStack stack) {
        if (stack.is(filled())) {
            return new FluidStack(SinguloFluids.get("liquid_nitrogen"), AMOUNT);
        }
        if (stack.is(helium())) {
            return new FluidStack(SinguloFluids.get("liquid_helium"), HELIUM_AMOUNT);
        }
        return null;
    }

    /** 空のカートリッジに fluid を詰めたときの量と、できるカートリッジ（詰められない液体なら null）。 */
    @Nullable
    private static FluidStack fillable(Fluid fluid) {
        if (fluid.isSame(SinguloFluids.get("liquid_nitrogen"))) {
            return new FluidStack(SinguloFluids.get("liquid_nitrogen"), AMOUNT);
        }
        if (fluid.isSame(SinguloFluids.get("liquid_helium"))) {
            return new FluidStack(SinguloFluids.get("liquid_helium"), HELIUM_AMOUNT);
        }
        return null;
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
            FluidStack in = contents(container);
            return in == null ? FluidStack.EMPTY : in;
        }

        @Override
        public int getTankCapacity(int tank) {
            FluidStack in = contents(container);
            return in == null ? AMOUNT : in.getAmount();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            FluidStack in = contents(container);
            return in != null ? stack.getFluid().isSame(in.getFluid()) : fillable(stack.getFluid()) != null;
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            FluidStack full = fillable(resource.getFluid());
            if (!isEmptyCartridge(container) || container.getCount() != 1 || full == null || resource.getAmount() < full.getAmount()) {
                return 0;
            }
            if (action.execute()) {
                container = new ItemStack(full.getFluid().isSame(SinguloFluids.get("liquid_helium")) ? helium() : filled());
            }
            return full.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return isFluidValid(0, resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            FluidStack in = contents(container);
            if (in == null || container.getCount() != 1 || maxDrain < in.getAmount()) {
                return FluidStack.EMPTY;
            }
            if (action.execute()) {
                container = new ItemStack(empty());
            }
            return in;
        }
    }
}
