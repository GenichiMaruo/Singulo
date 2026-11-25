package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.data.PackBalance;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * アンドロイド用の無線充電器（Mk1〜Mk3）。持ち歩くと、充電の切れたアンドロイドが寄ってきて、ついて歩きながら充電する
 * （Mk1 は印刷台と同じ5秒で満タン）。電力を蓄えたブロック（蓄電池・発電機・ケーブルの先など）を右クリックすると、そこから充電する。
 *
 * <ul>
 * <li>Mk2: 蓄えられる量が5倍、充電が2倍速、離れたまま（8ブロック）充電できる。</li>
 * <li>Mk3: 蓄えられる量がさらに多く、充電が4倍速、16ブロック。さらに、16ブロック以内にいる自分のアンドロイドのうち充電が半分を
 *     切ったものへ、作業や戦闘を止めさせずに少しずつ給電する（使った分だけ充電器から減る）。</li>
 * </ul>
 */
public class AndroidChargerItem extends Item {
    /** アンドロイドを0から満タンにするのに使う電力。 */
    public static final int PER_ANDROID = 10_000;
    /** Mk1 の蓄えられる電力（満充電 {@link #PER_ANDROID} の30回分）。 */
    public static final int CAPACITY = 300_000;

    /** 段ごとの性能。capacity: 蓄えられる電力。pull: ブロックから1回の右クリックで受け取れる上限。speed: 充電の速さの倍率。range: 充電できる距離。 */
    public enum Tier {
        MK1(CAPACITY, 100_000, 1, 4, false, 0x78E1FF),
        MK2(1_500_000, 500_000, 2, 8, false, 0xB48CFF),
        MK3(8_000_000, 2_500_000, 4, 16, true, 0xFF9A4A);

        public final int capacity, pull, speed;
        public final double range;
        /** 近くの自分のアンドロイドへ、作業や戦闘を止めさせずに給電するか。 */
        public final boolean topUp;
        final int bar;

        Tier(int capacity, int pull, int speed, double range, boolean topUp, int bar) {
            this.capacity = capacity;
            this.pull = pull;
            this.speed = speed;
            this.range = range;
            this.topUp = topUp;
            this.bar = bar;
        }
    }

    /** Mk3 の給電: 充電がこの割合を下回ったアンドロイドへ、TOP_UP_INTERVAL tick ごとに給電する。 */
    public static final float TOP_UP_BELOW = 0.5F;
    public static final int TOP_UP_INTERVAL = 10;

    private final Tier tier;

    public AndroidChargerItem(Properties properties) {
        this(properties, Tier.MK1);
    }

    public AndroidChargerItem(Properties properties, Tier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof AndroidChargerItem c ? c.tier : Tier.MK1;
    }

    public static IEnergyStorage energy(ItemStack stack) {
        return new ComponentEnergyStorage(stack, SinguloComponents.ENERGY.get(), tierOf(stack).capacity);
    }

    /** プレイヤーの持ち物の中で、電力の残っている充電器（いちばん上の段のもの。なければ空）。 */
    public static ItemStack find(Player player) {
        int rate = PackBalance.value(ServerConfig.PORTABLE_CHARGE_PER_TICK);
        if (rate == 0) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack s : chargers(player)) {
            long minEnergy = Math.ceilDiv((long) RuinAndroid.CHARGER_FE_PER_TICK * tierOf(s).speed, rate);
            if (energy(s).getEnergyStored() >= minEnergy && (best.isEmpty() || tierOf(s).ordinal() > tierOf(best).ordinal())) {
                best = s;
            }
        }
        return best;
    }

    private static List<ItemStack> chargers(Player player) {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof AndroidChargerItem) out.add(s);
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AndroidChargerItem) out.add(off);
        return out;
    }

    /**
     * Mk3 の給電（持ち物の中で毎 tick）: 持ち物の最初の Mk3 だけが、TOP_UP_INTERVAL tick ごとに、範囲の自分のアンドロイドへ給電する
     * （何個持っていても速くならない）。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!tier.topUp || !(level instanceof ServerLevel server) || !(entity instanceof Player player)
                || level.getGameTime() % TOP_UP_INTERVAL != 0) {
            return;
        }
        ItemStack first = chargers(player).stream().filter(s -> tierOf(s).topUp).findFirst().orElse(ItemStack.EMPTY);
        if (first != stack) return;
        topUp(server, player, stack);
    }

    /** 範囲の自分のアンドロイドのうち、充電が TOP_UP_BELOW を下回ったものへ給電する。給電した数を返す。 */
    public static int topUp(ServerLevel level, Player player, ItemStack charger) {
        Tier t = tierOf(charger);
        int rate = PackBalance.value(ServerConfig.PORTABLE_CHARGE_PER_TICK);
        if (!t.topUp || rate == 0) return 0;
        int n = 0;
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, player.getBoundingBox().inflate(t.range),
                a -> a.isAlive() && a.printed() && a.isGuard() && player.getUUID().equals(a.owner())
                        && a.distanceToSqr(player) <= t.range * t.range && a.chargeFraction() < TOP_UP_BELOW
                        && !RuinAndroid.charging(a.state()))) {
            if (a.receiveWireless(charger, TOP_UP_INTERVAL, 1) > 0) n++;
        }
        return n;
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
        int want = Math.min(tier.pull, own.getMaxEnergyStored() - own.getEnergyStored());
        int got = source.extractEnergy(want, false);
        own.receiveEnergy(got, false);
        if (context.getPlayer() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.singulo.android_charger.charged", got, own.getEnergyStored(),
                    tier.capacity), true);
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
        return Math.round(13.0F * energy(stack).getEnergyStored() / tier.capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return tier.bar;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int stored = energy(stack).getEnergyStored();
        tooltip.add(Component.translatable("tooltip.singulo.energy", io.github.genichimaruo.singulo.energy.EnergyText.of(stored), io.github.genichimaruo.singulo.energy.EnergyText.of(tier.capacity)).withStyle(ChatFormatting.GRAY));
        if (io.github.genichimaruo.singulo.energy.EnergyText.abbreviated(tier.capacity)) {
            tooltip.add(io.github.genichimaruo.singulo.energy.EnergyText.hint());
        }
        int rate = PackBalance.value(ServerConfig.PORTABLE_CHARGE_PER_TICK);
        long perCharge = rate == 0 ? Long.MAX_VALUE : Math.ceilDiv((long) PackBalance.value(ServerConfig.ANDROID_BATTERY_CAPACITY), rate)
                * RuinAndroid.CHARGER_FE_PER_TICK;
        tooltip.add(Component.translatable("tooltip.singulo.android_charger.charges", stored / perCharge).withStyle(ChatFormatting.AQUA));
        if (tier != Tier.MK1) {
            tooltip.add(Component.translatable("tooltip.singulo.android_charger.reach", (int) tier.range, tier.speed).withStyle(ChatFormatting.GRAY));
        }
        if (tier.topUp) {
            tooltip.add(Component.translatable("tooltip.singulo.android_charger.top_up", (int) tier.range).withStyle(ChatFormatting.GOLD));
        }
    }
}
