package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.machine.NeutrinoObservatoryBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * ニュートリノ感度モジュール（Mk2 は段階2、Mk3 は段階3）。もう片方の手にニュートリノ・スキャナーを持って使うか、
 * ニュートリノ観測所に使うと、その感度を上げる（使ったモジュールはなくなる）。感度が上がると、より深い層の鉱石が映る。
 */
public class ScannerModuleItem extends SinguloItem {
    private final int tier;

    public ScannerModuleItem(Properties properties, int stage, int tier) {
        super(properties, stage, false);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** 観測所に使う。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof NeutrinoObservatoryBlockEntity observatory)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (!context.getLevel().isClientSide && player != null) {
            if (observatory.raiseTier(tier)) {
                done(context.getLevel(), player, context.getItemInHand());
            } else {
                player.displayClientMessage(Component.translatable("message.singulo.scanner_module.already", observatory.tier()), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** もう片方の手のスキャナーに使う。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        ItemStack scanner = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(scanner.getItem() instanceof NeutrinoScannerItem)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.singulo.scanner_module.need_scanner"), true);
            }
            return InteractionResultHolder.fail(module);
        }
        int now = NeutrinoScannerItem.tier(scanner);
        if (now >= tier) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.singulo.scanner_module.already", now), true);
            }
            return InteractionResultHolder.fail(module);
        }
        if (!level.isClientSide) {
            scanner.set(SinguloComponents.SCANNER_TIER.get(), tier);
            done(level, player, module);
        }
        return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
    }

    private void done(Level level, Player player, ItemStack module) {
        if (!player.getAbilities().instabuild) {
            module.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.singulo.scanner_module.done", tier), true);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
