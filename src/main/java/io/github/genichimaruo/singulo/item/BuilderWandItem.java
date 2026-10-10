package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.command.SinguloCommands;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 組み立ての杖（クリエイティブ専用）。置いたコントローラに使うと、その向きのままマルチブロックを一瞬で組み立てる。
 * スニークして使うと大きさ（冷却塔の高さ・加速器の一辺）が切り替わる。
 */
public class BuilderWandItem extends Item {
    public BuilderWandItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        Blueprints.Kind kind = Blueprints.kindOf(state.getBlock());
        Player player = context.getPlayer();
        if (kind == null || player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            SinguloComponents.set(stack, SinguloComponents.HOLO_SIZE.get(), HoloProjectorItem.sizeIndex(stack) + 1);
            if (!context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("tooltip.singulo.builder_wand.size",
                        HoloProjectorItem.size(stack, kind)), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel() instanceof ServerLevel level && player.getAbilities().instabuild) {
            int size = HoloProjectorItem.size(stack, kind);
            int n = SinguloCommands.place(level, kind, context.getClickedPos(), HoloProjectorItem.backOf(state), size);
            player.displayClientMessage(Component.translatable("command.singulo.build.done",
                    Component.translatable("multiblock.singulo." + kind.id()), size, n), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.builder_wand.hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.creative_only").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
