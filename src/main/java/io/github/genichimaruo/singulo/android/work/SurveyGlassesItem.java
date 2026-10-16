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
 */
public class SurveyGlassesItem extends Item implements Equipable {
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
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /** この人が測量メガネを掛けているか。 */
    public static boolean wearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SurveyGlassesItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.survey_glasses").withStyle(ChatFormatting.GRAY));
    }
}
