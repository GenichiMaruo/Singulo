package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 触媒安定化剤（封印コンテナの時間錠から）。もう片方の手に触媒を持って使うと、その触媒の残りの寿命が2倍になる
 * （使った量と最大の寿命をともに2倍にするので、残りの割合＝電力の倍率は変わらない）。1つの触媒に1回まで。
 */
public class CatalystStabilizerItem extends SinguloItem {
    public CatalystStabilizerItem(Properties properties, int stage) {
        super(properties, stage, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stabilizer = player.getItemInHand(hand);
        ItemStack catalyst = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (CatalystHelper.tierOf(catalyst) <= 0 || !UsesHelper.hasUses(catalyst) || catalyst.getItem() instanceof CreativeCatalystItem) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.singulo.stabilizer.need_catalyst"), true);
            }
            return InteractionResultHolder.fail(stabilizer);
        }
        if (stabilized(catalyst)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.singulo.stabilizer.already"), true);
            }
            return InteractionResultHolder.fail(stabilizer);
        }
        if (!level.isClientSide) {
            stabilize(catalyst);
            if (!player.getAbilities().instabuild) {
                stabilizer.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.singulo.stabilizer.done"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        return InteractionResultHolder.sidedSuccess(stabilizer, level.isClientSide);
    }

    public static boolean stabilized(ItemStack catalyst) {
        return Boolean.TRUE.equals(catalyst.get(SinguloComponents.STABILIZED.get()));
    }

    /** 残りの寿命を2倍にする（残りの割合は変えない）。 */
    public static void stabilize(ItemStack catalyst) {
        UsesData d = UsesHelper.get(catalyst);
        catalyst.set(SinguloComponents.USES.get(), new UsesData(d.used() * 2, d.max() * 2, d.repairs()));
        catalyst.set(SinguloComponents.STABILIZED.get(), true);
    }
}
