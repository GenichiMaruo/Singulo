package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 作業位置カード。未記録のカードは64枚まで重なる。
 * <ul>
 * <li>ブロックをシフト右クリック: その位置（次元・座標・ブロックの種類）を記録する。重なっていれば1枚だけ分けて記録し、記録したカードは重ならない。</li>
 * <li>何もない所でシフト右クリック: 記録を消して、未記録のカードに戻す。</li>
 * </ul>
 * レシピ棚の行の左に置くと、その行のレシピを作る場所になる。作業指令台を記録したカードで資材口・納品口・レシピ棚を
 * 右クリックすると、その指令台につなぐ（もう一度で外す）。
 */
public class WorkLocationCardItem extends Item {
    private static final String KEY = "work_location";

    public WorkLocationCardItem(Properties properties) {
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
            Block block = level.getBlockState(pos).getBlock();
            ItemStack held = context.getItemInHand();
            ItemStack card = held.getCount() > 1 ? held.split(1) : held;
            record(card, level, pos, block);
            if (card != held && !player.getInventory().add(card)) {
                player.drop(card, false);
            }
            player.displayClientMessage(Component.translatable("message.singulo.work_location.recorded",
                    block.getName(), pos.getX(), pos.getY(), pos.getZ()), true);
            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 何もない所でシフト右クリック: 記録を消す。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || pos(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            clear(stack);
            player.displayClientMessage(Component.translatable("message.singulo.work_location.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 記録する（記録したカードは重ならない）。 */
    public static void record(ItemStack card, Level level, BlockPos pos, Block block) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("dim", level.dimension().location().toString());
        t.putString("block", BuiltInRegistries.BLOCK.getKey(block).toString());
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.put(KEY, t));
        card.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    /** 記録を消して、未記録のカード（64枚まで重なる）に戻す。 */
    public static void clear(ItemStack card) {
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.remove(KEY));
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            card.remove(DataComponents.CUSTOM_DATA);
        }
        // 「重ならない」を外す（取り除くのではなく元の値に戻す。取り除くと、新しいカードと違う物になって重ならない）
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

    /** 記録した位置（記録がなければ null）。 */
    @Nullable
    public static BlockPos pos(ItemStack card) {
        CompoundTag t = data(card);
        return t == null ? null : BlockPos.of(t.getLong("pos"));
    }

    /** 記録した位置が、この次元のものか。 */
    public static boolean sameDimension(ItemStack card, Level level) {
        CompoundTag t = data(card);
        return t != null && t.getString("dim").equals(level.dimension().location().toString());
    }

    /** 記録したブロックの種類（記録がなければ空気）。 */
    public static Block block(ItemStack card) {
        CompoundTag t = data(card);
        if (t == null) {
            return Blocks.AIR;
        }
        ResourceLocation id = ResourceLocation.tryParse(t.getString("block"));
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof WorkLocationCardItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pos = pos(stack);
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.singulo.work_location.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.singulo.work_location.block", block(stack).getName(), pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.singulo.work_location.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
