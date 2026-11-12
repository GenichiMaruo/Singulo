package io.github.genichimaruo.singulo.drone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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

    @Override public int getMaxStackSize(ItemStack stack) { return packed(stack) ? 1 : 16; }

    /** 箱の段階（梱包箱でなければ 0）。 */
    public static int tier(ItemStack stack) {
        return stack.getItem() instanceof DroneCrateItem crate ? crate.tier : 0;
    }

    public static int capacity(int tier) {
        return CAPACITY[Math.max(0, Math.min(3, tier))];
    }

    /** 液体カプセルか（液体のポートで使う箱）。 */
    public static boolean isFluid(ItemStack stack) {
        return stack.getItem() instanceof FluidCapsuleItem;
    }

    /**
     * 描くときの箱の番号: 梱包箱は段階（1〜3）、液体カプセルは段階 + 3（4〜6）。0 は箱なし。
     */
    public static int displayTier(ItemStack stack) {
        int t = tier(stack);
        return t > 0 && isFluid(stack) ? t + 3 : t;
    }

    /** その段階の箱で届く距離（ブロック）。 */
    public static int range(int tier) {
        return tier <= 0 ? 0 : (capacity(tier) - BASE_FE) / FE_PER_BLOCK;
    }

    /** その距離を送る電力。 */
    public static int cost(double distance) {
        return BASE_FE + (int) Math.ceil(distance * FE_PER_BLOCK);
    }

    /** 中身の入った（梱包済みの）箱か。液体カプセルは液体が入っていれば梱包済み。 */
    public static boolean packed(ItemStack stack) {
        if (stack.getItem() instanceof FluidCapsuleItem) {
            return !FluidCapsuleItem.fluid(stack).isEmpty();
        }
        return tier(stack) > 0 && stack.getTag() != null
                && !stack.getTag().getCompound("singulo:drone_contents").getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty();

    }

    /** 空の箱1個に中身を詰めた箱。 */
    public static ItemStack pack(ItemStack crate, List<ItemStack> contents) {
        ItemStack out = crate.copyWithCount(1);
        setContents(out, contents);
        return out;
    }

    public static NonNullList<ItemStack> contents(ItemStack stack) {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        var tag = stack.getTag();
        if (tag != null && tag.contains("singulo:drone_contents", net.minecraft.nbt.Tag.TAG_COMPOUND))
            net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("singulo:drone_contents"), list);
        return list;
    }

    /** 中身を入れ替える。空になったら、ほかの空の箱と重なるように部品を外す。 */
    public static void setContents(ItemStack stack, List<ItemStack> contents) {
        if (contents.stream().allMatch(ItemStack::isEmpty)) {
            if (stack.getTag() != null) {
                stack.getTag().remove("singulo:drone_contents");
                if (stack.getTag().isEmpty()) stack.setTag(null);
            }
        } else {
            NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(SLOTS, contents.size()); i++) list.set(i, contents.get(i).copy());
            stack.getOrCreateTag().put("singulo:drone_contents", net.minecraft.world.ContainerHelper.saveAllItems(new net.minecraft.nbt.CompoundTag(), list));
        }
    }

    /** 荷物にしてよい物（梱包済みの箱は入れ子にしない）。 */
    public static boolean mayCarry(ItemStack stack) {
        return !packed(stack);
    }

    /**
     * 空の箱はシュルカーボックスやバンドルに入れられるが、梱包済みの箱は入れられない（入れた入れ物をまた梱包できると、
     * 入れ子が際限なく深くなり、アイテムのデータが保存・送信できないほど大きくなる）。
     */
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return !packed(stack);
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
            // Inventory#add は、クリエイティブで入りきらないと残りを消してしまう。入った分だけ減らし、残りは箱に残す
            var inventory = new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory());
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    items.set(i, net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, items.get(i), false));
                }
            }
            setContents(crate, items);
            player.setItemInHand(hand, crate);
            level.playSound(null, player.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.7F, 1.3F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> lines, TooltipFlag flag) {
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
