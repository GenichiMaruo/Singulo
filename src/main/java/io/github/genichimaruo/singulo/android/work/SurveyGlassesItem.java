package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 測量メガネ。頭に掛けると（右クリックか、防具の頭の欄へ）、手に持った範囲カード・作業位置カードの記録した場所が、世界の中に枠で見える。
 * 作業中のアンドロイドの頭の上の、今していることの印も、掛けているときだけ見える。
 *
 * <p>掛ける高さはピクセル単位で上下に変えられる（持ってスニークして使うと、高さを決める画面が開く）。高さはメガネに残り、
 * 頭に掛けた模型を高さごとに切り替える（アイテムの絵の切り替え {@code singulo:height}）ので、ほかの人にも同じ高さで見える。
 */
public class SurveyGlassesItem extends Item implements Equipable {
    /** 掛ける高さの範囲（ピクセル。上が正）。 */
    public static final int MIN_HEIGHT = -4, MAX_HEIGHT = 4;
    private static final String HEIGHT_KEY = "glasses_height";

    public SurveyGlassesItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                io.github.genichimaruo.singulo.client.GlassesHeightScreen.open(hand);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /** 掛ける高さ（ピクセル。上が正）。 */
    public static int height(ItemStack stack) {
        var data = stack.getTag();
        int h = data == null ? 0 : data.getInt(HEIGHT_KEY);
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
    }

    public static void setHeight(ItemStack stack, int height) {
        int h = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
        var t = stack.getOrCreateTag();
        {
            if (h == 0) {
                t.remove(HEIGHT_KEY);
            } else {
                t.putInt(HEIGHT_KEY, h);
            }
        }
    }

    /** アイテムの絵の切り替えの値（高さ MIN〜MAX を 0〜1 に）。 */
    public static float heightPredicate(ItemStack stack) {
        return (height(stack) - MIN_HEIGHT) / (float) (MAX_HEIGHT - MIN_HEIGHT);
    }

    /** この人が測量メガネを掛けているか。 */
    public static boolean wearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SurveyGlassesItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.survey_glasses").withStyle(ChatFormatting.GRAY));
        int h = height(stack);
        if (h != 0) {
            tooltip.add(Component.translatable("tooltip.singulo.survey_glasses.height", (h > 0 ? "+" : "") + h).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.singulo.survey_glasses.adjust").withStyle(ChatFormatting.DARK_GRAY));
    }
}
