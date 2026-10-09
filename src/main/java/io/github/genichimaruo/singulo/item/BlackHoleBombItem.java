package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.reactor.BlackHoleBomb;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ブラックホール爆弾: 右クリックで投げる。当たった所に6秒ほど小さなブラックホールが開く。 */
public class BlackHoleBombItem extends SinguloItem {
    public BlackHoleBombItem(Properties properties, int stage) {
        super(properties.stacksTo(16), stage, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6F, 0.5F);
        if (!level.isClientSide) {
            BlackHoleBomb bomb = new BlackHoleBomb(level, player);
            bomb.setItem(stack.copyWithCount(1));
            bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
            level.addFreshEntity(bomb);
        }
        player.getCooldowns().addCooldown(this, 30);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
