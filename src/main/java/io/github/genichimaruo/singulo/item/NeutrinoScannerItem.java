package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.network.ScanPayload;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ニュートリノ・スキャナー（段階3）。ニュートリノは地面をほぼ素通りするので、使うと半径 RADIUS の中の鉱石（共通タグ c:ores、
 * 他modの鉱石も）と遺構のブロックの輪郭を、地形越しに SHOW_TICKS（10秒）浮かび上がらせる。1回に FE_PER_SCAN を使う。
 * スニークして電力を持つブロックを右クリックすると充電する。
 * <p>
 * 映る鉱石は感度の段階（1〜3）で決まる（{@link #oreTier}）。はじめは段階1で、石炭・銅・鉄・ラピス・レッドストーンなどだけ。
 * ニュートリノ感度モジュールで段階2（金・ダイヤモンド・エメラルド）、段階3（古代の残骸・ダイヤモンドのツルハシが要る鉱石）に上がる。
 */
public class NeutrinoScannerItem extends SinguloItem {
    public static final int CAPACITY = 200_000;
    public static final int FE_PER_SCAN = 10_000;
    public static final int RADIUS = 24;
    public static final int SHOW_TICKS = 200;
    public static final int MAX_MARKS = 600;
    public static final int MAX_TIER = 3;

    /** 見つけたもの。hidden は、感度が足りず映らなかった鉱石の数。 */
    public record Result(List<BlockPos> ores, List<BlockPos> ruins, int hidden) {}

    /** 鉱石が映るのに要る感度の段階。タグ singulo:scanner_tier_2・3 と、ダイヤモンドのツルハシが要るか（段階3）で決める。 */
    public static int oreTier(BlockState state) {
        if (state.is(SinguloTags.SCANNER_TIER_3) || state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }
        return state.is(SinguloTags.SCANNER_TIER_2) ? 2 : 1;
    }

    /** スキャナーの感度の段階。 */
    public static int tier(ItemStack stack) {
        return Math.max(1, Math.min(MAX_TIER, stack.getOrDefault(SinguloComponents.SCANNER_TIER.get(), 1)));
    }

    public NeutrinoScannerItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    public static IEnergyStorage energy(ItemStack stack) {
        return new ComponentEnergyStorage(stack, SinguloComponents.ENERGY.get(), CAPACITY);
    }

    /** 感度 tier で、鉱石と遺構のブロックを探す。 */
    public static Result scan(Level level, BlockPos center, int tier) {
        List<BlockPos> ores = new ArrayList<>();
        List<BlockPos> ruins = new ArrayList<>();
        int hidden = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            if (ores.size() + ruins.size() >= MAX_MARKS) {
                break;
            }
            if (center.distSqr(p) > RADIUS * RADIUS || !level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(Tags.Blocks.ORES)) {
                if (oreTier(s) <= tier) {
                    ores.add(p.immutable());
                } else {
                    hidden++;
                }
            } else if (s.is(SinguloTags.RUIN_BLOCKS)) {
                ruins.add(p.immutable());
            }
        }
        return new Result(ores, ruins, hidden);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            IEnergyStorage e = energy(stack);
            if (!sp.getAbilities().instabuild && e.extractEnergy(FE_PER_SCAN, true) < FE_PER_SCAN) {
                sp.displayClientMessage(Component.translatable("gauntlet.singulo.no_energy"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!sp.getAbilities().instabuild) {
                e.extractEnergy(FE_PER_SCAN, false);
            }
            Result found = scan(level, sp.blockPosition(), tier(stack));
            if (sp.connection.hasChannel(ScanPayload.TYPE)) {
                PacketDistributor.sendToPlayer(sp, new ScanPayload(found.ores(), found.ruins()));
            }
            var message = Component.translatable("message.singulo.neutrino_scan", found.ores().size(), found.ruins().size());
            if (found.hidden() > 0) {
                message.append(Component.translatable("message.singulo.neutrino_hidden", found.hidden()));
            }
            sp.displayClientMessage(message, true);
            ((ServerLevel) level).playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
            sp.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** スニークして電力を持つブロックを右クリックすると、そこから充電する。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK, context.getClickedPos(), context.getClickedFace());
        if (source == null || !source.canExtract()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            IEnergyStorage own = energy(context.getItemInHand());
            int got = source.extractEnergy(own.receiveEnergy(Integer.MAX_VALUE, true), false);
            own.receiveEnergy(got, false);
            player.displayClientMessage(Component.translatable("gauntlet.singulo.charged", got), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        return 0xB48CFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.energy", energy(stack).getEnergyStored(), CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.scanner_tier", tier(stack), MAX_TIER).withStyle(ChatFormatting.GRAY));
    }
}
