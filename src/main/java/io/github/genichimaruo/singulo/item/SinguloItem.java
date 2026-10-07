package io.github.genichimaruo.singulo.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 段階の表示と、未実装の装置の注記を持つアイテム。 */
public class SinguloItem extends Item {
    private final int stage;
    private final boolean planned;

    public SinguloItem(Properties properties, int stage, boolean planned) {
        super(properties);
        this.stage = stage;
        this.planned = planned;
    }

    public int stage() {
        return stage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        addStageLine(tooltip, stage);
        if (planned) {
            tooltip.add(Component.translatable("tooltip.singulo.planned").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static void addStageLine(List<Component> tooltip, int stage) {
        tooltip.add(Component.translatable("tooltip.singulo.stage", stage,
                Component.translatable("stage.singulo." + stage)).withStyle(ChatFormatting.AQUA));
    }
}
