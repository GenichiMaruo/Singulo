package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

import java.util.List;

/**
 * 液体キャニスター（段階1）。どの液体・ガスでも {@link #CAPACITY} mB まで入る容器。バケツと同じく、装置を右クリックすると
 * 汲む（装置の出口のタンクから）か注ぐ（入口のタンクへ）。水素・ヘリウム・液体ヘリウムなどを、液体のパイプの mod がなくても運べる。
 * <p>
 * 中身は物の情報（{@link SinguloComponents#CANISTER_FLUID}）に持つ。空のもの（と、中身も量も同じもの）は重なる。
 * 重ねたままでは出し入れしない（右クリックでは1個だけ取り分けて出し入れする）。
 */
public class FluidCanisterItem extends SinguloItem {
    public static final int CAPACITY = 1000;
    public static final int STACK = 16;

    public FluidCanisterItem(Properties properties, int stage) {
        super(properties.stacksTo(STACK), stage, false);
    }

    /** 液体の出し入れ（NeoForge の物の液体容器）。 */
    public static FluidHandlerItemStack handler(ItemStack stack) {
        return new FluidHandlerItemStack(SinguloComponents.CANISTER_FLUID, stack, CAPACITY);
    }

    /** 中身（空なら空）。 */
    public static FluidStack fluid(ItemStack stack) {
        return stack.getOrDefault(SinguloComponents.CANISTER_FLUID.get(), SimpleFluidContent.EMPTY).copy();
    }

    /** テストやコマンド用: 中身を入れたキャニスター。 */
    public static ItemStack filled(ItemStack canister, FluidStack fluid) {
        ItemStack s = canister.copyWithCount(1);
        s.set(SinguloComponents.CANISTER_FLUID.get(), SimpleFluidContent.copyOf(fluid.copyWithAmount(Math.min(CAPACITY, fluid.getAmount()))));
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack f = fluid(stack);
        return f.isEmpty() ? super.getName(stack) : Component.translatable("item.singulo.fluid_canister.filled", f.getHoverName());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !fluid(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * fluid(stack).getAmount() / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return color(fluid(stack));
    }

    /** 目盛りの色（Singulo の液体は決めた色、水と溶岩はそれらしい色、ほかは水色）。 */
    static int color(FluidStack f) {
        if (f.getFluid().isSame(Fluids.WATER)) {
            return 0x3F76E4;
        }
        if (f.getFluid().isSame(Fluids.LAVA)) {
            return 0xFF6A00;
        }
        var id = BuiltInRegistries.FLUID.getKey(f.getFluid());
        for (GeneratedContent.FluidDef def : GeneratedContent.FLUIDS) {
            if (id.getNamespace().equals(io.github.genichimaruo.singulo.Singulo.MODID) && id.getPath().equals(def.id())) {
                return def.color() & 0xFFFFFF;
            }
        }
        return 0x9AD0FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FluidStack f = fluid(stack);
        tooltip.add(f.isEmpty() ? Component.translatable("tooltip.singulo.fluid_canister.empty", CAPACITY).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.singulo.fluid_canister.amount", f.getHoverName(), f.getAmount(), CAPACITY)
                        .withStyle(ChatFormatting.GRAY));
    }
}
