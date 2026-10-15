package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;


/**
 * 流体の見本: 作業の計算・予約・画面で、液体やガスを物と同じように扱うための目印（{@link FluidSampleItem}）。
 * 見本の物は1個で、流体の種類と量（mB）を持つ。数を数えるとき（{@link ItemCount}）は、量 1 の見本を目印にして、数を mB にする。
 */
public final class FluidSamples {
    private FluidSamples() {}

    /** 流体の見本（量つき）。空の流体なら空。 */
    public static ItemStack of(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(SinguloItems.FLUID_SAMPLE.get());
        s.getOrCreateTag().put("singulo:fluid_sample", fluid.writeToNBT(new net.minecraft.nbt.CompoundTag()));
        return s;
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FluidSampleItem;
    }

    /** 見本の流体（見本でなければ空）。 */
    public static FluidStack fluid(ItemStack stack) {
        if (!is(stack)) {
            return FluidStack.EMPTY;
        }
        return stack.getTag() == null ? FluidStack.EMPTY : FluidStack.loadFluidStackFromNBT(stack.getTag().getCompound("singulo:fluid_sample"));
    }

    /** 数: 見本なら流体の量（mB）、物なら個数。 */
    public static long amount(ItemStack stack) {
        return is(stack) ? fluid(stack).getAmount() : stack.getCount();
    }

    /** 数え方の目印（見本は量 1、物は1個）。 */
    public static ItemStack key(ItemStack stack) {
        return is(stack) ? withAmount(stack, 1) : stack.copyWithCount(1);
    }

    /** 量を変えた見本（物なら個数を変える。1〜64）。 */
    public static ItemStack withAmount(ItemStack stack, int amount) {
        if (!is(stack)) {
            return stack.copyWithCount(Math.max(1, Math.min(64, amount)));
        }
        FluidStack f = fluid(stack);
        f.setAmount(Math.max(1, amount));
        return of(f);
    }

    /** 物を見本の欄に置くとき: バケツなど流体の入れ物なら、中の流体の見本に変える。 */
    public static ItemStack fromContainer(ItemStack stack) {
        if (stack.isEmpty() || is(stack)) {
            return stack.copy();
        }
        FluidStack f = net.minecraftforge.fluids.FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        return f.isEmpty() ? stack.copy() : of(f);
    }
}
