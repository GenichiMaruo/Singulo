package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * アンドロイド用の無線充電器。持ち歩くと、追従モードのアンドロイドが寄ってきて、ついて歩きながら充電する
 * （印刷台と同じ5秒で満タン）。満充電の充電器で、アンドロイドを約30回満タンにできる。
 * 電力を蓄えたブロック（蓄電池・発電機・ケーブルの先など）を右クリックすると、そこから充電する。
 */
public class AndroidChargerItem extends Item {
    /** 蓄えられる電力（アンドロイドの満充電 {@link #PER_ANDROID} の30回分）。 */
    public static final int CAPACITY = 300_000;
    /** アンドロイドを0から満タンにするのに使う電力。 */
    public static final int PER_ANDROID = 10_000;
    /** ブロックから1回の右クリックで受け取れる上限。 */
    static final int PULL = 100_000;

    public AndroidChargerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static IEnergyStorage energy(ItemStack stack) {
        return new ComponentEnergyStorage(stack, SinguloComponents.ENERGY.get(), CAPACITY);
    }

    /** プレイヤーの持ち物の中で、電力の残っている充電器（なければ空）。 */
    public static ItemStack find(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof AndroidChargerItem && energy(s).getEnergyStored() > 0) {
                return s;
            }
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AndroidChargerItem && energy(off).getEnergyStored() > 0) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        IEnergyStorage source = context.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, context.getClickedPos(),
                context.getClickedFace());
        if (source == null || !source.canExtract()) {
            return InteractionResult.PASS;
        }
        IEnergyStorage own = energy(context.getItemInHand());
        int want = Math.min(PULL, own.getMaxEnergyStored() - own.getEnergyStored());
        int got = source.extractEnergy(want, false);
        own.receiveEnergy(got, false);
        if (context.getPlayer() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.singulo.android_charger.charged", got, own.getEnergyStored(),
                    CAPACITY), true);
        }
        if (got > 0) {
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack).getEnergyStored() / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x78E1FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int stored = energy(stack).getEnergyStored();
        tooltip.add(Component.translatable("tooltip.singulo.energy", io.github.genichimaruo.singulo.energy.EnergyText.of(stored), io.github.genichimaruo.singulo.energy.EnergyText.of(CAPACITY)).withStyle(ChatFormatting.GRAY));
        if (io.github.genichimaruo.singulo.energy.EnergyText.abbreviated(CAPACITY)) {
            tooltip.add(io.github.genichimaruo.singulo.energy.EnergyText.hint());
        }
        tooltip.add(Component.translatable("tooltip.singulo.android_charger.charges", stored / PER_ANDROID).withStyle(ChatFormatting.AQUA));
    }
}
