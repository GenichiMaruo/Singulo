package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.item.SinguloBlockItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * カバーの枠。ケーブル・パイプを右クリックすると取り付き、枠にブロックをはめ込むとそのブロックの見た目になる（{@link ConduitCover}）。
 * ほかの場所では、枠だけのブロック（{@link CoverFrameBlock}）として置け、あとからケーブル・パイプを入れられる。
 * 取り付け・はめ込みはケーブル・パイプの側（{@link ConduitCover#use}）で、ケーブルを入れるのは置いた枠の側で扱う。
 */
public class CoverFrameItem extends SinguloBlockItem {
    public CoverFrameItem(net.minecraft.world.level.block.Block block, Properties properties, int stage) {
        super(block, properties, stage);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.cover_frame.fit").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.cover_frame.inlay").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.cover_frame.remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
