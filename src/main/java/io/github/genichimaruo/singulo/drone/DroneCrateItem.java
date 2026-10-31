package io.github.genichimaruo.singulo.drone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * 梱包箱（3段階）。空の箱は重なり、梱包すると中身（9枠）を持つ1個だけの箱になる。段階が上がるほどバッテリーが大きく、遠くへ送れる。
 * 発送の電力は梱包のときにポートが払い、その便で使い切る（箱には電力をためない）。梱包済みの箱は右クリックで開梱できる。
 */
public class DroneCrateItem extends Item {
    public static final int SLOTS = 9;
    /** 発送の電力 = BASE_FE + 直線距離 × FE_PER_BLOCK。 */
    public static final int BASE_FE = 2_000, FE_PER_BLOCK = 50;
    /** 段階ごとのバッテリー容量（射程を決める）。 */
    private static final int[] CAPACITY = {0, 10_000, 40_000, 160_000};

    private final int tier;

    public DroneCrateItem(int tier, Properties properties) {
        super(properties.stacksTo(16));
        this.tier = tier;
    }

    /** 箱の段階（梱包箱でなければ 0）。 */
    public static int tier(ItemStack stack) {
        return stack.getItem() instanceof DroneCrateItem crate ? crate.tier : 0;
    }

    public static int capacity(int tier) {
        return CAPACITY[Math.max(0, Math.min(3, tier))];
    }

    /** その段階の箱で届く距離（ブロック）。 */
    public static int range(int tier) {
        return tier <= 0 ? 0 : (capacity(tier) - BASE_FE) / FE_PER_BLOCK;
    }

    /** その距離を送る電力。 */
    public static int cost(double distance) {
        return BASE_FE + (int) Math.ceil(distance * FE_PER_BLOCK);
    }

    /** 中身の入った（梱包済みの）箱か。 */
    public static boolean packed(ItemStack stack) {
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        return tier(stack) > 0 && c != null && c.nonEmptyItems().iterator().hasNext();
    }

    /** 空の箱1個に中身を詰めた箱。 */
    public static ItemStack pack(ItemStack crate, List<ItemStack> contents) {
        ItemStack out = crate.copyWithCount(1);
        setContents(out, contents);
        return out;
    }

    /** 中身（9枠。空の枠は EMPTY）。 */
    public static NonNullList<ItemStack> contents(ItemStack stack) {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        if (c != null) {
            c.copyInto(list);
        }
        return list;
    }

    /** 中身を入れ替える。空になったら、ほかの空の箱と重なるように部品を外す。 */
    public static void setContents(ItemStack stack, List<ItemStack> contents) {
        if (contents.stream().allMatch(ItemStack::isEmpty)) {
            // 元の値に戻す（remove だと「外した」という差分が残り、新しい空の箱と重ならない）
            stack.remove(DataComponents.CONTAINER);
            stack.set(DataComponents.MAX_STACK_SIZE, stack.getItem().components().get(DataComponents.MAX_STACK_SIZE));
            return;
        }
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    /** 荷物にしてよい物（梱包済みの箱は入れ子にしない）。 */
    public static boolean mayCarry(ItemStack stack) {
        return !packed(stack);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return true;
    }

    /** 梱包済みの箱を右クリック: 中身を持ち物へ移す（入りきらない分は箱に残す）。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!packed(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            NonNullList<ItemStack> items = contents(stack);
            ItemStack crate = stack.copyWithCount(1);
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    player.getInventory().add(items.get(i));         // 入った分だけ減る
                }
            }
            setContents(crate, items);
            player.setItemInHand(hand, crate);
            level.playSound(null, player.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.7F, 1.3F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.singulo.drone_crate.range", range(tier)).withStyle(ChatFormatting.AQUA));
        if (!packed(stack)) {
            return;
        }
        List<ItemStack> shown = new ArrayList<>();
        for (ItemStack s : contents(stack)) {
            if (!s.isEmpty()) {
                shown.add(s);
            }
        }
        for (int i = 0; i < Math.min(5, shown.size()); i++) {
            lines.add(Component.translatable("tooltip.singulo.drone_crate.item", shown.get(i).getHoverName(), shown.get(i).getCount())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (shown.size() > 5) {
            lines.add(Component.translatable("tooltip.singulo.drone_crate.more", shown.size() - 5).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.singulo.drone_crate.unpack").withStyle(ChatFormatting.DARK_GRAY));
    }
}
