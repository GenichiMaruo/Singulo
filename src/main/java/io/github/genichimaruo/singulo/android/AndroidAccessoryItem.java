package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * アンドロイドの装飾。装備のページのとなりの「装飾」のページで、頭・背中/腰・手の枠に着ける。戦い・作業には影響しない。
 * 染料と一緒に作業台で染められる（革の防具と同じ。タグ minecraft:dyeable）。手に持つ物は、待機している間だけ持つ。
 */
public class AndroidAccessoryItem extends Item {
    /** 着ける所。 */
    public static final int HEAD = 0, BACK = 1, HAND = 2;

    private final int part;
    private final String style;
    private final int defaultColor;

    public AndroidAccessoryItem(Properties properties, int part, String style, int defaultColor) {
        super(properties.stacksTo(1));
        this.part = part;
        this.style = style;
        this.defaultColor = defaultColor;
    }

    public int part() {
        return part;
    }

    /** 見た目の種類（描き方を決める名前。リボン、ベレー帽 など）。 */
    public String style() {
        return style;
    }

    /** 染めた色（染めていなければ、もとの色）。 */
    public int color(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, defaultColor) & 0xFFFFFF;
    }

    public static boolean isAccessory(ItemStack stack, int part) {
        return stack.getItem() instanceof AndroidAccessoryItem a && a.part == part;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_accessory." + part).withStyle(ChatFormatting.GRAY));
    }
}
