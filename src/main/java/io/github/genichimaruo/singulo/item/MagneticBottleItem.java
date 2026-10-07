package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 磁気瓶（段階4）。ストレンジレットに使うと、磁場で封じ込めて取り除き、ストレンジ物質 YIELD 個を回収する。
 * 使える回数は DURABILITY 回。
 */
public class MagneticBottleItem extends SinguloItem {
    public static final int DURABILITY = 8;
    public static final int YIELD = 4;

    public MagneticBottleItem(Properties properties, int stage) {
        super(properties.durability(DURABILITY), stage, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(SinguloBlocks.STRANGELET.get())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
            server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.5F);
            Block.popResource(server, pos, new ItemStack(SinguloBlocks.SIMPLE.get("strange_matter").get(), YIELD));
            Player player = context.getPlayer();
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                player.displayClientMessage(Component.translatable("message.singulo.strangelet_contained"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.magnetic_bottle", stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
    }
}
