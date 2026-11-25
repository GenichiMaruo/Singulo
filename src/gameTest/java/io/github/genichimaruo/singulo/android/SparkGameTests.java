package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/** 放電型アンドロイド「エクレール」の隠し型としての扱い・強み・技・給電と連携・設計メモリの入手。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class SparkGameTests {
    private static final String HUGE = "huge";

    private SparkGameTests() {}

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 14, 0, 14)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** 動かない的（倒れにくいハスク）。 */
    private static Zombie dummy(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        return z;
    }

    /** 南（+Z）を向いた、動かない放電型。 */
    private static SparkAndroid spark(GameTestHelper helper, BlockPos at) {
        SparkAndroid s = SinguloEntities.SPARK_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        s.setNoAi(true);
        s.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at)), 0, 0);
        s.yBodyRot = 0;
        helper.getLevel().addFreshEntity(s);
        return s;
    }

    /** 同じ持ち主の護衛にする（ステーションに二人を登録する）。 */
    private static void team(GameTestHelper helper, RuinAndroid... androids) {
        UUID owner = UUID.randomUUID();
        BlockPos home = helper.absolutePos(new BlockPos(7, 0, 7));
        helper.setBlock(new BlockPos(7, 0, 7), SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = helper.getBlockEntity(new BlockPos(7, 0, 7), AndroidStationBlockEntity.class);
        var registry = helper.getLevel().registryAccess();
        var saved = station.saveWithoutMetadata(registry);
        saved.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        for (int i = 0; i < androids.length; i++) {
            saved.store("android" + i, net.minecraft.core.UUIDUtil.CODEC, androids[i].getUUID());
        }
        saved.putInt("mode", RuinAndroid.MODE_FOLLOW);
        io.github.genichimaruo.singulo.test.harness.TestNbt.load(station, saved, registry);
        for (RuinAndroid a : androids) {
            a.makeGuard(owner, home, RuinAndroid.MODE_FOLLOW, 0);
        }
    }

    /** 放電型は射撃型・刀型と同じ隠し型: 遺構にも研究所にも出ず、スポーンエッグと設計メモリは一覧に出さず、メモリの名前は紫。 */
    @GameTest(template = "empty")
    public static void sparkIsHiddenLikeCielAndRouge(GameTestHelper helper) {
        helper.assertTrue(AndroidKind.SPARK.origin == AndroidKind.Origin.HIDDEN, "放電型が隠し型でない");
        helper.assertTrue(AndroidKind.SPARK.printCost == AndroidKind.KATANA.printCost, "放電型の印刷の倍率が刀型と違う");
        helper.assertFalse(Set.of(AndroidKind.ruinKinds()).contains(AndroidKind.SPARK), "放電型が遺構の印刷台の型に入っている");
        helper.assertFalse(Set.of(AndroidKind.prototypes()).contains(AndroidKind.SPARK), "放電型が研究所の試作機の型に入っている");
        for (String id : Set.of("spark_android_spawn_egg", AndroidKind.SPARK.memoryId())) {
            helper.assertTrue(BuiltInRegistries.ITEM.getValue(Singulo.id(id)) != Items.AIR, "アイテムが登録されていない: " + id);
            helper.assertTrue(SinguloItems.HIDDEN.contains(id), "一覧に出さないアイテムに入っていない: " + id);
            helper.assertFalse(SinguloItems.TAB_ORDER.stream().anyMatch(i -> i.getId().getPath().equals(id)), "クリエイティブタブに並んでいる: " + id);
        }
        helper.assertTrue(new ItemStack(BuiltInRegistries.ITEM.getValue(Singulo.id(AndroidKind.SPARK.memoryId()))).getRarity() == Rarity.EPIC,
                "放電型の設計メモリの名前が紫（エピック）でない");
        helper.succeed();
    }

    /** 珍しい型の強み: バックパック内蔵・内蔵バッテリー1個（満タン1.5倍）・作業1.5倍速。 */
    @GameTest(template = "empty")
    public static void sparkHasRareStrengths(GameTestHelper helper) {
        SparkAndroid s = spark(helper, new BlockPos(2, 1, 2));
        RuinAndroid blade = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        helper.assertTrue(s.builtInPack(), "バックパックを内蔵していない");
        helper.assertTrue(s.maxCharge() * 2 == blade.maxCharge() * 3, "充電の満タンが1.5倍でない");
        helper.assertTrue(s.workTicks(30) == 20, "作業が1.5倍速でない");
        s.discard();
        helper.succeed();
    }

    /** アークは近くの相手へ跳び移る（最大3体）。遠すぎる相手と、壁の向こうの相手には跳ばない。 */
    @GameTest(template = HUGE, batch = "spark_arc")
    public static void arcChainsAcrossNearbyFoes(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 7));
        Zombie second = dummy(helper, new BlockPos(10, 1, 7));
        Zombie walled = dummy(helper, new BlockPos(13, 1, 9));
        Zombie far = dummy(helper, new BlockPos(1, 1, 13));
        for (int y = 1; y <= 3; y++) {
            for (int z = 7; z <= 11; z++) helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
        }
        int hits = s.arc(helper.getLevel(), first);
        helper.assertTrue(hits == 2, "壁と距離で止まらず、跳んだ数がおかしい: " + hits);
        helper.assertTrue(first.getHealth() < 200 && second.getHealth() < 200, "連鎖した相手に当たっていない");
        helper.assertTrue(200 - second.getHealth() < 200 - first.getHealth(), "跳ぶたびに弱まらない");
        helper.assertTrue(walled.getHealth() == 200 && far.getHealth() == 200, "壁の向こうか遠くの相手に当たった");
        helper.assertTrue(s.arcPath().size() == 3, "アークの道筋が指先と2体になっていない: " + s.arcPath().size());
        helper.succeed();
    }

    /** 掌底放電は前の近い相手に当たって押し返す。後ろの相手には当たらない。 */
    @GameTest(template = HUGE, batch = "spark_palm")
    public static void palmStrikesAndPushesBack(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(7, 1, 6));
        Zombie front = dummy(helper, new BlockPos(7, 1, 7));
        Zombie behind = dummy(helper, new BlockPos(7, 1, 5));
        front.setNoAi(false);
        int hits = s.palm(helper.getLevel());
        helper.assertTrue(hits == 1, "前の相手にだけ当たらない: " + hits);
        helper.assertTrue(front.getHealth() < 200 && behind.getHealth() == 200, "当たった相手がおかしい");
        helper.assertTrue(front.getDeltaMovement().z > 0.3, "前へ押し返していない: " + front.getDeltaMovement());
        helper.succeed();
    }

    /** 電場は中の相手に少しずつ当たって動きを鈍らせる。外の相手には当たらない。張ったあとは時間で消える。 */
    @GameTest(template = HUGE, batch = "spark_field", timeoutTicks = 140)
    public static void fieldWearsDownAndSlowsFoesInside(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(7, 1, 7));
        Zombie inside = dummy(helper, new BlockPos(9, 1, 8));
        Zombie outside = dummy(helper, new BlockPos(13, 1, 13));
        s.plantField();
        helper.startSequence()
                .thenIdle(SparkAndroid.FIELD_PULSE * 3 + 2)
                .thenExecute(() -> {
                    helper.assertTrue(inside.getHealth() <= 200 - SparkAndroid.FIELD_DAMAGE * 2, "電場の中の相手が削れていない: " + inside.getHealth());
                    helper.assertTrue(inside.hasEffect(MobEffects.SLOWNESS), "電場の中の相手が鈍っていない");
                    helper.assertTrue(outside.getHealth() == 200, "電場の外の相手に当たった");
                })
                .thenWaitUntil(() -> helper.assertTrue(s.fieldLeft() == 0, "電場が消えない"))
                .thenSucceed();
    }

    /** 天雷は範囲の見えている相手すべてに落ちる。範囲の外と、壁の向こうの相手には落ちない。 */
    @GameTest(template = HUGE, batch = "spark_tenrai")
    public static void tenraiStrikesEveryVisibleFoeInRange(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(7, 1, 7));
        Zombie a = dummy(helper, new BlockPos(3, 1, 7));
        Zombie b = dummy(helper, new BlockPos(11, 1, 9));
        Zombie c = dummy(helper, new BlockPos(7, 1, 12));
        Zombie hidden = dummy(helper, new BlockPos(7, 1, 1));
        for (int x = 5; x <= 9; x++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
        }
        int hits = s.strike(helper.getLevel());
        helper.assertTrue(hits == 3, "見えている3体に落ちない: " + hits);
        for (Zombie z : new Zombie[]{a, b, c}) {
            helper.assertTrue(z.getHealth() <= 200 - SparkAndroid.TENRAI_DAMAGE, "天雷が当たっていない相手がいる");
        }
        helper.assertTrue(hidden.getHealth() == 200, "壁の向こうの相手に落ちた");
        helper.assertTrue(s.boltTargets().size() == 3, "落ちた先の数が描画に送られていない");
        helper.succeed();
    }

    /** AI つきで、相手に向かってアークを放ち、溜めてから天雷を落とす。 */
    @GameTest(template = HUGE, batch = "spark_ai", timeoutTicks = 400)
    public static void combatReachesArcAndTenrai(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(7, 1, 2));
        Zombie a = dummy(helper, new BlockPos(6, 1, 8));
        dummy(helper, new BlockPos(8, 1, 8));
        s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        s.setTarget(a);
        s.setNoAi(false);
        s.readyAllMovesForTest();
        boolean[] seen = new boolean[2];
        helper.onEachTick(() -> {
            if (s.state() == SparkAndroid.STATE_TENRAI_CHARGE) seen[0] = true;
            if (s.state() == SparkAndroid.STATE_TENRAI_STRIKE) {
                // AI がその tick の技を処理したあと、stateTicks が1進む。
                boolean released = !s.boltTargets().isEmpty();
                helper.assertTrue(released == (s.stateTicks > SparkAndroid.STRIKE_HIT), "振り下ろしと落雷の時刻がずれている");
                seen[1] |= released;
            }
            if (s.getTarget() == null && a.isAlive()) s.setTarget(a);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(seen[0] && seen[1], "天雷の溜めから落雷まで進まない"))
                .thenWaitUntil(() -> helper.assertTrue(s.state() == SparkAndroid.STATE_ARC || s.arcFlash() > 0, "アークを放たない"))
                .thenSucceed();
    }

    /** 給電: 仲間へ分けた分だけ自分が減る（電力は増えも消えもしない）。自分は半分を残し、満ちている仲間には分けない。 */
    @GameTest(template = HUGE, batch = "spark_supply")
    public static void supplySharesPowerWithoutCreatingIt(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(5, 1, 7));
        RuinAndroid ally = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        ally.setNoAi(true);
        ally.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(9, 1, 7))), 0, 0);
        helper.getLevel().addFreshEntity(ally);
        team(helper, s, ally);
        s.setCharge(s.maxCharge());
        ally.setCharge(ally.maxCharge() / 4);
        int before = s.charge() + ally.charge();
        int given = s.supply(helper.getLevel());
        helper.assertTrue(given > 0, "電力の少ない仲間に分けない");
        helper.assertTrue(s.charge() + ally.charge() == before, "給電で電力の合計が変わった");
        s.setCharge(s.maxCharge() / 2);
        helper.assertTrue(s.supply(helper.getLevel()) == 0, "自分が半分しかないのに分けた");
        s.setCharge(s.maxCharge());
        ally.setCharge(ally.maxCharge());
        helper.assertTrue(s.supply(helper.getLevel()) == 0, "満ちている仲間に分けた");
        helper.succeed();
    }

    /** 電磁加速: レールガンを組み上げるシエルに電力を注ぎ足すと、溜めが短くなる。 */
    @GameTest(template = HUGE, batch = "spark_rail", timeoutTicks = 100)
    public static void boostShortensCielsRailCharge(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(4, 1, 4));
        GunnerAndroid gun = SinguloEntities.GUNNER_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        gun.setNoAi(true);
        gun.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 4))), 0, 0);
        helper.getLevel().addFreshEntity(gun);
        team(helper, s, gun);
        Zombie foe = dummy(helper, new BlockPos(7, 1, 12));
        gun.setTarget(foe);
        gun.setState(GunnerAndroid.STATE_RAIL_ASSEMBLE);
        int before = s.charge();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(gun.boosted() && gun.boostedBy(s), "シエルに電力を注ぎ足さない"))
                .thenExecute(() -> {
                    helper.assertTrue(gun.railCharge() == GunnerAndroid.RAIL_CHARGE - SparkAndroid.RAIL_BOOST_TICKS, "溜めが短くならない");
                    helper.assertTrue(s.linkKind() == SparkAndroid.LINK_RAIL, "電磁加速の放電が描画に送られていない");
                    helper.assertTrue(s.charge() < before, "注ぎ足しに電力を使っていない");
                })
                .thenSucceed();
    }

    /** 刀への電荷: 紅一閃を構えるルージュの刀に電荷を与えると、遅延斬撃が強くなる。 */
    @GameTest(template = HUGE, batch = "spark_blade", timeoutTicks = 100)
    public static void chargeEmpowersRougesDelayedCut(GameTestHelper helper) {
        floor(helper);
        SparkAndroid s = spark(helper, new BlockPos(4, 1, 4));
        KatanaAndroid rouge = SinguloEntities.KATANA_ANDROID.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        rouge.setNoAi(true);
        rouge.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 4))), 0, 0);
        helper.getLevel().addFreshEntity(rouge);
        team(helper, s, rouge);
        rouge.setState(KatanaAndroid.STATE_IAI_STANCE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rouge.sparkChargedBy(s), "紅一閃の刀に電荷を与えない"))
                .thenExecute(() -> {
                    Zombie foe = dummy(helper, new BlockPos(10, 1, 10));
                    rouge.cutAlong(helper.getLevel(), Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, 7))),
                            Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, 13))));
                    float afterPass = foe.getHealth();
                    rouge.releaseCuts(helper.getLevel());
                    helper.assertTrue(afterPass - foe.getHealth() >= KatanaAndroid.IAI_DAMAGE + SparkAndroid.BLADE_BONUS - 0.01F,
                            "電荷の上乗せが遅延斬撃に入っていない: " + (afterPass - foe.getHealth()));
                })
                .thenSucceed();
    }

    /** 設計メモリ: 覚醒した大蛇の撃破ごとの抽選は25%（境界を含まない）。当たると放電とともにメモリが1個残り、組立ステーションは放電型と認識する。 */
    @GameTest(template = "empty")
    public static void leviathanLeavesSparkMemory(GameTestHelper helper) {
        helper.assertTrue(ErgosphereLeviathan.awardsSparkMemory(0.2499F), "25%未満で当たらない");
        helper.assertFalse(ErgosphereLeviathan.awardsSparkMemory(0.25F), "25%ちょうどで当たった");
        var leviathan = SinguloEntities.ERGOSPHERE_LEVIATHAN.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        leviathan.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        var item = leviathan.dropSparkMemory(helper.getLevel());
        helper.assertTrue(item.isAlive() && item.getItem().getCount() == 1, "メモリが1個残らない");
        helper.assertTrue(AndroidStationBlockEntity.memoryKind(item.getItem()) == AndroidKind.SPARK, "組立ステーションが放電型と認識しない");
        item.discard();
        helper.succeed();
    }
}
