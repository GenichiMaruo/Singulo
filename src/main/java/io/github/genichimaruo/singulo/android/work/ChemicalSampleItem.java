package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 化学物質の見本（{@link ChemicalSamples}）。作業指令台の見本の欄・注文の一覧で、Mekanism の化学物質を表す。名前は化学物質の名前、
 * 色は化学物質の色。手に入れる物ではない（クリエイティブのタブにも JEI にも出さない）。
 */
public class ChemicalSampleItem extends Item {
    public ChemicalSampleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = ChemicalSamples.id(stack);
        return id.isEmpty() ? super.getName(stack) : Chemicals.name(id);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long amount = ChemicalSamples.amount(stack);
        if (amount > 0) {
            tooltip.add(Component.translatable("tooltip.singulo.chemical_sample", amount).withStyle(ChatFormatting.GRAY));
        }
    }
}
