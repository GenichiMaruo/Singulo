package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 流体の見本（{@link FluidSamples}）。作業指令台の見本の欄・注文の一覧で、液体やガスを表す。名前は流体の名前、色は流体の色。
 * 手に入れる物ではない（クリエイティブのタブにも JEI にも出さない）。
 */
public class FluidSampleItem extends Item {
    public FluidSampleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack f = FluidSamples.fluid(stack);
        return f.isEmpty() ? super.getName(stack) : f.getHoverName();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidStack f = FluidSamples.fluid(stack);
        if (!f.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.singulo.fluid_sample", f.getAmount()).withStyle(ChatFormatting.GRAY));
        }
    }
}
