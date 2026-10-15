package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * レシピカード。作業指令台の「記録」でレシピを記録し、レシピ棚に並べると、作業指令台で注文できるようになる。
 * 未記録のカードは64枚まで重なり、記録したカードは重ならない。何もない所でシフト右クリックすると、記録を消す。
 * シフトを押している間は、作る物の絵が重なって見える。
 */
public class RecipeCardItem extends Item {
    public RecipeCardItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    /** 何もない所でシフト右クリック: 記録を消す。 */
    @Override
    public int getMaxStackSize(ItemStack stack) { return RecipeData.read(stack, null) == null ? 64 : 1; }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                                     net.minecraft.world.entity.player.Player player,
                                                                     net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || RecipeData.read(stack, level.registryAccess()) == null) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            RecipeData.clear(stack);
            player.displayClientMessage(Component.translatable("message.singulo.recipe_card.cleared"), true);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof RecipeCardItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        RecipeData data = RecipeData.read(stack, net.minecraft.core.RegistryAccess.EMPTY);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.singulo.recipe_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.singulo.recipe_card.output", data.output().getHoverName(), data.outputCount())
                .withStyle(ChatFormatting.AQUA));
        for (ItemStack e : data.extras()) {
            tooltip.add(Component.translatable("tooltip.singulo.recipe_card.extra", e.getHoverName(), e.getCount()).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (data.variants()) {
            tooltip.add(Component.translatable("tooltip.singulo.recipe_card.variants").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (data.processing()) {
            tooltip.add(Component.translatable("tooltip.singulo.recipe_card.processing").withStyle(ChatFormatting.DARK_AQUA));
            if (data.faces() != RecipeData.DEFAULT_FACES) {
                tooltip.add(Component.translatable("tooltip.singulo.recipe_card.faces",
                        Component.translatable("gui.singulo.recipe_encoder.face." + data.inputFace().getName()),
                        Component.translatable("gui.singulo.recipe_encoder.face." + data.outputFace().getName()),
                        Component.translatable("gui.singulo.recipe_encoder.face." + (data.fuelFace() == null ? "auto" : data.fuelFace().getName())))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            if (!data.fuel().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.singulo.recipe_card.fuel", data.fuel().getHoverName(), data.fuel().getCount())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        for (ItemCount c : data.inputs()) {
            tooltip.add(Component.literal(" ・").append(c.item.getHoverName()).append(" ×" + c.count).withStyle(ChatFormatting.GRAY));
        }
    }
}
