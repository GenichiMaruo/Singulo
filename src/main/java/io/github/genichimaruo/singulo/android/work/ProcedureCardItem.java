package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 手順カード。作業指令台の「記録」（手順モード）で、投入・取り出し・採掘の手順（最大8つ）と、できる物を記録する。
 * 場所はカードの中に持つので、レシピ棚では行の左の作業位置カードがなくても並べられる。
 * ほかはレシピカードと同じ（重なり方、記録の消し方、シフトでの絵）。
 */
public class ProcedureCardItem extends RecipeCardItem {
    public ProcedureCardItem(Properties properties) {
        super(properties);
    }

    public static boolean isProcedureCard(ItemStack stack) {
        return stack.getItem() instanceof ProcedureCardItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RecipeData data = context.registries() == null ? null : RecipeData.read(stack, context.registries());
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.singulo.procedure_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.singulo.recipe_card.output", data.output().getHoverName(), data.output().getCount())
                .withStyle(ChatFormatting.AQUA));
        int n = 1;
        for (ProcedureStep s : data.steps()) {
            tooltip.add(Component.literal(" " + n++ + ". ").append(describe(s)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 手順の説明（「投入: 鉄鉱石 ×8（12, 64, -3）」など）。 */
    public static Component describe(ProcedureStep s) {
        Component what = s.item().isEmpty() ? Component.translatable("tooltip.singulo.procedure_card.product")
                : Component.empty().append(s.item().getHoverName()).append(" ×" + s.item().getCount());
        return Component.translatable("tooltip.singulo.procedure_card." + ProcedureStep.KEYS[s.kind()], what, where(s));
    }

    public static String where(ProcedureStep s) {
        BlockPos p = s.pos();
        String a = p.getX() + ", " + p.getY() + ", " + p.getZ();
        if (s.corner() != null && !s.corner().equals(p)) {
            BlockPos c = s.corner();
            a += " ～ " + c.getX() + ", " + c.getY() + ", " + c.getZ();
        }
        return a;
    }
}
