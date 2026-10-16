package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

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
        s.set(SinguloComponents.FLUID_SAMPLE.get(), SimpleFluidContent.copyOf(fluid));
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
        SimpleFluidContent c = stack.get(SinguloComponents.FLUID_SAMPLE.get());
        return c == null ? FluidStack.EMPTY : c.copy();
    }

    /** 液体・化学物質の見本か（物ではなく、量で数える）。 */
    public static boolean liquid(ItemStack stack) {
        return is(stack) || ChemicalSamples.is(stack);
    }

    /** 数: 見本なら流体・化学物質の量（mB）、物なら個数。 */
    public static long amount(ItemStack stack) {
        return is(stack) ? fluid(stack).getAmount() : ChemicalSamples.is(stack) ? ChemicalSamples.amount(stack) : stack.getCount();
    }

    /** 数え方の目印（見本は量 1、物は1個）。 */
    public static ItemStack key(ItemStack stack) {
        return liquid(stack) ? withAmount(stack, 1) : stack.copyWithCount(1);
    }

    /** 量を変えた見本（物なら個数を変える。1〜64）。 */
    public static ItemStack withAmount(ItemStack stack, int amount) {
        if (ChemicalSamples.is(stack)) {
            return ChemicalSamples.of(ChemicalSamples.id(stack), Math.max(1, amount));
        }
        if (!is(stack)) {
            return stack.copyWithCount(Math.max(1, Math.min(64, amount)));
        }
        FluidStack f = fluid(stack);
        f.setAmount(Math.max(1, amount));
        return of(f);
    }

    /** 物を見本の欄に置くとき: バケツなど流体の入れ物なら中の流体の見本に、ケミカルタンク（Mekanism）なら中の化学物質の見本に変える。 */
    public static ItemStack fromContainer(ItemStack stack) {
        if (stack.isEmpty() || liquid(stack)) {
            return stack.copy();
        }
        FluidStack f = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        if (!f.isEmpty()) {
            return of(f);
        }
        var chem = Chemicals.contained(stack);
        return chem == null ? stack.copy() : ChemicalSamples.of(chem.getKey(), Math.min(1000, chem.getValue()));
    }
}
