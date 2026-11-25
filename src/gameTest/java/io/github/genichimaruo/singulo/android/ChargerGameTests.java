package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/** アンドロイド用無線充電器 Mk1〜Mk3 の段ごとの性能と、Mk3 の給電。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ChargerGameTests {
    private ChargerGameTests() {}

    /** 上の段ほど多く蓄え、速く、遠くから充電する。給電は Mk3 だけ。 */
    @GameTest(template = "empty")
    public static void tiersGrowInCapacitySpeedAndReach(GameTestHelper helper) {
        AndroidChargerItem.Tier[] tiers = {
                AndroidChargerItem.tierOf(new ItemStack(SinguloItems.ANDROID_CHARGER.get())),
                AndroidChargerItem.tierOf(new ItemStack(SinguloItems.ANDROID_CHARGER_2.get())),
                AndroidChargerItem.tierOf(new ItemStack(SinguloItems.ANDROID_CHARGER_3.get())),
        };
        helper.assertTrue(tiers[0] == AndroidChargerItem.Tier.MK1 && tiers[1] == AndroidChargerItem.Tier.MK2
                && tiers[2] == AndroidChargerItem.Tier.MK3, "段が登録と合っていない");
        for (int i = 1; i < tiers.length; i++) {
            helper.assertTrue(tiers[i].capacity > tiers[i - 1].capacity && tiers[i].speed > tiers[i - 1].speed
                    && tiers[i].range > tiers[i - 1].range, "上の段が強くない: " + tiers[i]);
        }
        helper.assertTrue(!tiers[0].topUp && !tiers[1].topUp && tiers[2].topUp, "給電するのが Mk3 だけでない");
        ItemStack mk3 = new ItemStack(SinguloItems.ANDROID_CHARGER_3.get());
        helper.assertTrue(AndroidChargerItem.energy(mk3).getMaxEnergyStored() == AndroidChargerItem.Tier.MK3.capacity, "Mk3 の容量が違う");
        helper.succeed();
    }

    /** 持ち物にいくつあっても、いちばん上の段の充電器を使う。 */
    @GameTest(template = "empty")
    public static void ownerUsesTheBestCharger(GameTestHelper helper) {
        ServerPlayer player = TestScope.player(helper);
        try {
            ItemStack mk1 = new ItemStack(SinguloItems.ANDROID_CHARGER.get()), mk2 = new ItemStack(SinguloItems.ANDROID_CHARGER_2.get());
            AndroidChargerItem.energy(mk1).receiveEnergy(100_000, false);
            AndroidChargerItem.energy(mk2).receiveEnergy(100_000, false);
            player.getInventory().setItem(0, mk1);
            player.getInventory().setItem(1, mk2);
            helper.assertTrue(AndroidChargerItem.find(player) == mk2, "上の段の充電器を選ばない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** Mk3 の給電: 範囲の自分のアンドロイドで、充電が半分を切ったものにだけ給電し、使った電力より多くは増やさない。 */
    @GameTest(template = "empty")
    public static void mk3TopsUpLowAndroidsWithoutCreatingPower(GameTestHelper helper) {
        ServerPlayer player = TestScope.player(helper);
        try {
            player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))), 0, 0);
            ItemStack mk3 = new ItemStack(SinguloItems.ANDROID_CHARGER_3.get());
            AndroidChargerItem.energy(mk3).receiveEnergy(1_000_000, false);
            RuinAndroid low = android(helper, new BlockPos(4, 1, 4), player);
            RuinAndroid full = android(helper, new BlockPos(5, 1, 2), player);
            RuinAndroid stranger = android(helper, new BlockPos(2, 1, 5), null);
            low.setCharge(low.maxCharge() / 4);
            full.setCharge(full.maxCharge());
            stranger.setCharge(stranger.maxCharge() / 4);
            int lowBefore = low.charge(), strangerBefore = stranger.charge();
            int energyBefore = AndroidChargerItem.energy(mk3).getEnergyStored();
            int helped = AndroidChargerItem.topUp(helper.getLevel(), player, mk3);
            int gained = low.charge() - lowBefore;
            int spent = energyBefore - AndroidChargerItem.energy(mk3).getEnergyStored();
            helper.assertTrue(helped == 1 && gained > 0, "充電の少ない自分のアンドロイドに給電しない: " + helped + " " + gained);
            helper.assertTrue(stranger.charge() == strangerBefore, "ほかの人のアンドロイドに給電した");
            int rate = io.github.genichimaruo.singulo.data.PackBalance.value(io.github.genichimaruo.singulo.generated.ServerConfig.PORTABLE_CHARGE_PER_TICK);
            helper.assertTrue((long) gained * RuinAndroid.CHARGER_FE_PER_TICK <= (long) spent * rate, "使った電力より多く増えた");
            helper.assertTrue(low.state() == RuinAndroid.STATE_IDLE, "給電で今していることを止めた");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** 満タン近くでは、満タンを超える分を充電器から取らない。 */
    @GameTest(template = "empty")
    public static void wirelessChargeStopsAtFull(GameTestHelper helper) {
        ItemStack mk3 = new ItemStack(SinguloItems.ANDROID_CHARGER_3.get());
        AndroidChargerItem.energy(mk3).receiveEnergy(1_000_000, false);
        RuinAndroid a = android(helper, new BlockPos(2, 1, 2), null);
        a.setCharge(a.maxCharge() - 1);
        int before = AndroidChargerItem.energy(mk3).getEnergyStored();
        a.receiveWireless(mk3, 100, 4);
        int spent = before - AndroidChargerItem.energy(mk3).getEnergyStored();
        helper.assertTrue(a.charge() == a.maxCharge(), "満タンにならない");
        helper.assertTrue(spent <= RuinAndroid.CHARGER_FE_PER_TICK, "満タンを超える分まで充電器から取った: " + spent);
        helper.succeed();
    }

    private static RuinAndroid android(GameTestHelper helper, BlockPos at, ServerPlayer owner) {
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        a.setNoAi(true);
        a.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at)), 0, 0);
        helper.getLevel().addFreshEntity(a);
        a.makeGuard(owner == null ? java.util.UUID.randomUUID() : owner.getUUID(), helper.absolutePos(at), RuinAndroid.MODE_FOLLOW, 0);
        return a;
    }
}
