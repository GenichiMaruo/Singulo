package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 範囲カード。2つのブロックで囲った範囲（次元・2つの角）を1枚に記録する。未記録のカードは64枚まで重なる。
 * <ul>
 * <li>ブロックをシフト右クリック: 1つ目の角を記録する（重なっていれば1枚だけ分けて記録する）。もう一度ほかのブロックをシフト右クリックすると、
 * 2つ目の角を記録して範囲ができる。範囲ができたカードでもう一度シフト右クリックすると、1つ目の角から記録し直す。</li>
 * <li>何もない所でシフト右クリック: 記録を消して、未記録のカードに戻す。</li>
 * </ul>
 * 作業指令台・組立ステーションの「経路」で、必須経由地点の対象範囲と、その経由のあいだの進入禁止ゾーンに使う。測量メガネを掛けて持つと、範囲が見える。
 */
public class AreaCardItem extends Item {
    private static final String KEY = "area";
    /** 1辺の最大（ブロック）。 */
    public static final int MAX_SIZE = 128;

    public AreaCardItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            ItemStack held = context.getItemInHand();
            ItemStack card = held.getCount() > 1 ? held.split(1) : held;
            BlockPos first = first(card);
            if (first != null && second(card) == null && sameDimension(card, level)) {
                if (Math.abs(first.getX() - pos.getX()) >= MAX_SIZE || Math.abs(first.getY() - pos.getY()) >= MAX_SIZE
                        || Math.abs(first.getZ() - pos.getZ()) >= MAX_SIZE) {
                    player.displayClientMessage(Component.translatable("message.singulo.area_card.too_large", MAX_SIZE), true);
                    return InteractionResult.FAIL;
                }
                recordSecond(card, pos);
                BoundingBox box = box(card);
                player.displayClientMessage(Component.translatable("message.singulo.area_card.recorded", box.getXSpan(), box.getYSpan(),
                        box.getZSpan()), true);
            } else {
                recordFirst(card, level, pos);
                player.displayClientMessage(Component.translatable("message.singulo.area_card.first", pos.getX(), pos.getY(), pos.getZ()), true);
            }
            if (card != held && !player.getInventory().add(card)) {
                player.drop(card, false);
            }
            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6F, second(card) == null ? 1.4F : 1.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 何もない所でシフト右クリック: 記録を消す。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || first(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            clear(stack);
            player.displayClientMessage(Component.translatable("message.singulo.area_card.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 1つ目の角を記録する（2つ目は消す。記録したカードは重ならない）。 */
    public static void recordFirst(ItemStack card, Level level, BlockPos pos) {
        CompoundTag t = new CompoundTag();
        t.putLong("a", pos.asLong());
        t.putString("dim", level.dimension().location().toString());
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.put(KEY, t));
        card.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    /** 2つ目の角を記録する（範囲ができる）。 */
    public static void recordSecond(ItemStack card, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.getCompound(KEY).putLong("b", pos.asLong()));
    }

    /** 範囲を記録する（テストなどから。2つの角を一度に）。 */
    public static void record(ItemStack card, Level level, BlockPos a, BlockPos b) {
        recordFirst(card, level, a);
        recordSecond(card, b);
    }

    /** 記録を消して、未記録のカード（64枚まで重なる）に戻す。 */
    public static void clear(ItemStack card) {
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.remove(KEY));
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            card.remove(DataComponents.CUSTOM_DATA);
        }
        card.set(DataComponents.MAX_STACK_SIZE, card.getItem().getDefaultMaxStackSize());
    }

    @Nullable
    private static CompoundTag data(ItemStack card) {
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY) ? tag.getCompound(KEY) : null;
    }

    /** 1つ目の角（記録がなければ null）。 */
    @Nullable
    public static BlockPos first(ItemStack card) {
        CompoundTag t = data(card);
        return t == null || !t.contains("a") ? null : BlockPos.of(t.getLong("a"));
    }

    /** 2つ目の角（まだなければ null）。 */
    @Nullable
    public static BlockPos second(ItemStack card) {
        CompoundTag t = data(card);
        return t == null || !t.contains("b") ? null : BlockPos.of(t.getLong("b"));
    }

    /** 記録した範囲（2つの角がそろっていなければ null）。 */
    @Nullable
    public static BoundingBox box(ItemStack card) {
        BlockPos a = first(card), b = second(card);
        return a == null || b == null ? null : BoundingBox.fromCorners(a, b);
    }

    /** 記録した範囲が、この次元のものか。 */
    public static boolean sameDimension(ItemStack card, Level level) {
        CompoundTag t = data(card);
        return t != null && t.getString("dim").equals(level.dimension().location().toString());
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof AreaCardItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos a = first(stack), b = second(stack);
        if (a == null) {
            tooltip.add(Component.translatable("tooltip.singulo.area_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (b == null) {
            tooltip.add(Component.translatable("tooltip.singulo.area_card.first", a.getX(), a.getY(), a.getZ()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.singulo.area_card.next").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        BoundingBox box = BoundingBox.fromCorners(a, b);
        tooltip.add(Component.translatable("tooltip.singulo.area_card.area", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.singulo.area_card.size", box.getXSpan(), box.getYSpan(), box.getZSpan())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.singulo.area_card.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
