package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 設定カード: 装置の設定を写して、ほかの装置に貼る。スニークして装置に使うと写し、そのまま使うと貼る。
 * 写せるのは、装置の面の設定（搬入出と自動排出）と、Pリアクターのスピンの目標と投入間隔。
 */
public class SettingsCardItem extends SinguloItem {
    private static final String KEY = "settings";

    public SettingsCardItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
        if (player == null || be == null) {
            return InteractionResult.PASS;
        }
        String kind = kindOf(be);
        if (kind == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind);
            if (be instanceof MachineBlockEntity m) {
                tag.putInt("item_sides", m.itemSides().packed());
                tag.putIntArray("tank_sides", m.tankSidesPacked());
            } else if (be instanceof PenroseReactorBlockEntity r) {
                tag.putInt("spin_target", r.spinTargetIndex());
                tag.putInt("feed_interval", r.feedInterval());
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put(KEY, tag));
            player.displayClientMessage(Component.translatable("item.singulo.settings_card.copied"), true);
            return InteractionResult.SUCCESS;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? null : data.copyTag().getCompound(KEY);
        if (tag == null || tag.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.singulo.settings_card.empty"), true);
            return InteractionResult.FAIL;
        }
        if (!kind.equals(tag.getString("kind"))) {
            player.displayClientMessage(Component.translatable("item.singulo.settings_card.mismatch"), true);
            return InteractionResult.FAIL;
        }
        if (be instanceof MachineBlockEntity m) {
            m.pasteSides(tag.getInt("item_sides"), tag.getIntArray("tank_sides"));
        } else if (be instanceof PenroseReactorBlockEntity r) {
            r.setSpinTargetIndex(tag.getInt("spin_target"));
            r.setFeedInterval(tag.getInt("feed_interval"));
        }
        player.displayClientMessage(Component.translatable("item.singulo.settings_card.pasted"), true);
        return InteractionResult.SUCCESS;
    }

    /** 設定を写せる装置の種類（面の設定のある装置と、Pリアクター）。写せなければ null。 */
    private static String kindOf(BlockEntity be) {
        if (be instanceof MachineBlockEntity m) {
            return m.usesSideConfig() ? "machine" : null;
        }
        return be instanceof PenroseReactorBlockEntity ? "reactor" : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String kind = data == null ? "" : data.copyTag().getCompound(KEY).getString("kind");
        tooltip.add(Component.translatable(kind.isEmpty() ? "item.singulo.settings_card.blank" : "item.singulo.settings_card.holds." + kind)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.singulo.settings_card.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
