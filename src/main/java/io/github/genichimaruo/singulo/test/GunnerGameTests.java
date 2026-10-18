package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.GunnerAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 射撃型アンドロイド「シエル」（まだクリエイティブでだけ出す）: 出どころ・一覧に出さないこと・小型銃・レールガン。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class GunnerGameTests {
    private static final String HUGE = "huge";
    private static final String BATCH = "gunner";

    private GunnerGameTests() {}

    /** 動かない的（倒れにくいハスク）。 */
    private static Zombie dummy(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        return z;
    }

    /** 南（+Z）を向いた、動かない射撃型。 */
    private static GunnerAndroid gunner(GameTestHelper helper, BlockPos at) {
        GunnerAndroid g = SinguloEntities.GUNNER_ANDROID.get().create(helper.getLevel());
        g.setNoAi(true);
        g.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(at)));
        g.yBodyRot = 0;
        helper.getLevel().addFreshEntity(g);
        return g;
    }

    /** 射撃型は遺構の印刷台にも研究所にも出ない。スポーンエッグと設計メモリはあるが、クリエイティブタブには並べない。 */
    @GameTest(setupTicks = 20, template = "empty")
    public static void gunnerIsCreativeOnly(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.assertTrue(AndroidKind.GUNNER.origin == AndroidKind.Origin.HIDDEN, "射撃型が隠し型でない");
        helper.assertFalse(Set.of(AndroidKind.ruinKinds()).contains(AndroidKind.GUNNER), "射撃型が遺構の印刷台の型に入っている");
        helper.assertFalse(Set.of(AndroidKind.prototypes()).contains(AndroidKind.GUNNER), "射撃型が研究所の試作機の型に入っている");
        for (String id : Set.of("gunner_android_spawn_egg", AndroidKind.GUNNER.memoryId())) {
            helper.assertTrue(BuiltInRegistries.ITEM.get(Singulo.id(id)) != Items.AIR, "アイテムが登録されていない: " + id);
            helper.assertTrue(SinguloItems.HIDDEN.contains(id), "一覧に出さないアイテムに入っていない: " + id);
            helper.assertFalse(SinguloItems.TAB_ORDER.stream().anyMatch(i -> i.getId().getPath().equals(id)), "クリエイティブタブに並んでいる: " + id);
        }
        helper.succeed();
    }

    /**
     * 覚醒したファウンドリを倒すと、ときどき（25%）射撃型の設計メモリが落ちる。挑戦者の人数に関係なく、1回の撃破で最大1個。
     * 名前は紫（エピック）。
     */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_foundry", timeoutTicks = 60)
    public static void awakenedFoundryDropsGunnerMemory(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var memory = BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.GUNNER.memoryId()));
        helper.assertTrue(new net.minecraft.world.item.ItemStack(memory).getRarity() == net.minecraft.world.item.Rarity.EPIC,
                "射撃型の設計メモリの名前が紫（エピック）でない");
        var drop = io.github.genichimaruo.singulo.ruin.RuinBoss.class.getDeclaredMethod("dropAwakenedRewards", net.minecraft.server.level.ServerLevel.class);
        drop.setAccessible(true);
        int kills = 40, withMemory = 0;
        for (int i = 0; i < kills; i++) {
            var foundry = SinguloEntities.FOUNDRY_OVERSEER.get().create(helper.getLevel());
            foundry.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 7))));
            foundry.setNoAi(true);
            foundry.makeAwakened();
            helper.getLevel().addFreshEntity(foundry);
            drop.invoke(foundry, helper.getLevel());
            var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, foundry.getBoundingBox().inflate(6));
            int found = items.stream().filter(e -> e.getItem().is(memory)).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(found <= 1, "1回の撃破で射撃型の設計メモリが2個以上落ちた: " + found);
            withMemory += found;
            items.forEach(net.minecraft.world.entity.Entity::discard);
            foundry.discard();
        }
        helper.assertTrue(withMemory >= 1 && withMemory < kills, "射撃型の設計メモリの落ち方が珍しくない（または一度も落ちない）: " + withMemory + " / " + kills);
        helper.succeed();
    }

    /**
     * 珍しい型の強みと代償: バックパックを内蔵し（アップグレードなしで運べる欄が多い）、充電の満タンが1.5倍、護衛の範囲が2倍、作業が1.5倍速い。
     * その代わり、組立ステーションでの印刷の電力が3倍（工場式印刷ユニットで半分）。ほかの型は変わらない。
     */
    @GameTest(setupTicks = 20, template = "empty")
    public static void gunnerHasPerksAndCostsMore(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GunnerAndroid g = SinguloEntities.GUNNER_ANDROID.get().create(helper.getLevel());
        var blade = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        helper.assertTrue(g.packOpen() && !blade.packOpen(), "射撃型だけがバックパックを内蔵していない");
        helper.assertTrue(g.carryCapacity() == io.github.genichimaruo.singulo.android.RuinAndroid.CARRY_SLOTS
                + io.github.genichimaruo.singulo.android.RuinAndroid.PACK_SLOTS, "射撃型の運べる欄が増えない: " + g.carryCapacity());
        int base = io.github.genichimaruo.singulo.android.RuinAndroid.CHARGE_MAX;
        helper.assertTrue(g.maxCharge() == base * 3 / 2 && blade.maxCharge() == base, "射撃型の充電の満タンが1.5倍でない: " + g.maxCharge());
        helper.assertTrue(g.guardRange() == blade.guardRange() * 2, "射撃型の護衛の範囲が2倍でない: " + g.guardRange());
        helper.assertTrue(g.workTicks(40) == 27 && blade.workTicks(40) == 40, "射撃型の作業が速くない: " + g.workTicks(40));
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_STATION.get());
        io.github.genichimaruo.singulo.android.AndroidStationBlockEntity s = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, at);
        int fe = io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.PRINT_FE;
        s.items().setStackInSlot(io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.SLOT_MEMORY,
                new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.BLADE.memoryId()))));
        helper.assertTrue(s.printFe() == fe, "光刃型の印刷の電力が変わった: " + s.printFe());
        s.items().setStackInSlot(io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.SLOT_MEMORY,
                new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.GUNNER.memoryId()))));
        helper.assertTrue(s.printFe() == fe * 3, "射撃型の印刷の電力が3倍でない: " + s.printFe());
        s.items().setStackInSlot(io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.SLOT_PRINT_UNIT,
                new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_factory_print_unit"))));
        helper.assertTrue(s.printFe() == fe * 3 / 2, "工場式印刷ユニットで半分にならない: " + s.printFe());
        g.discard();
        blade.discard();
        helper.succeed();
    }

    /** レールガン: 狙いの線の上に並んだ相手をまとめて貫き、線から外れた相手には当たらない。 */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_pierce", timeoutTicks = 40)
    public static void railgunPiercesLine(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GunnerAndroid g = gunner(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 6));
        Zombie second = dummy(helper, new BlockPos(7, 1, 10));
        Zombie aside = dummy(helper, new BlockPos(11, 1, 8));
        helper.runAtTickTime(10, () -> {
            g.aimRail(first);
            int hit = g.fireRail(helper.getLevel());
            helper.assertTrue(hit == 2, "並んだ2体を貫かない: " + hit);
            helper.assertTrue(first.getHealth() < 200 && second.getHealth() < 200, "線の上の相手に当たらない");
            helper.assertTrue(aside.getHealth() == 200, "線から外れた相手に当たった");
            helper.succeed();
        });
    }

    /** レールガンの光線は壁で止まる（壁の向こうの相手には当たらない）。 */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_wall", timeoutTicks = 40)
    public static void railgunStopsAtWall(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GunnerAndroid g = gunner(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 6));
        Zombie behind = dummy(helper, new BlockPos(7, 1, 11));
        for (BlockPos p : BlockPos.betweenClosed(5, 1, 8, 9, 3, 8)) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.runAtTickTime(10, () -> {
            g.aimRail(first);
            int hit = g.fireRail(helper.getLevel());
            helper.assertTrue(hit == 1 && first.getHealth() < 200, "壁の手前の相手に当たらない: " + hit);
            helper.assertTrue(behind.getHealth() == 200, "壁の向こうの相手に当たった");
            helper.assertTrue(g.railTo().z < helper.absoluteVec(new Vec3(0, 0, 8.01)).z, "光線が壁で止まらない: " + g.railTo());
            helper.succeed();
        });
    }

    /** 戦いの動き（AI つき）: 護衛の射撃型が敵を見つけると、小型銃で撃ち、レールガンを組み上げて撃ち、しまうところまで進む。 */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_fight", timeoutTicks = 600)
    public static void gunnerFightsWithPistolAndRailgun(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        GunnerAndroid g = SinguloEntities.GUNNER_ANDROID.get().create(helper.getLevel());
        g.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 1)), 0, 0);
        g.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 2))));
        helper.getLevel().addFreshEntity(g);
        Zombie target = dummy(helper, new BlockPos(7, 1, 11));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            seen.add(g.state());
            target.setHealth(Math.max(target.getHealth(), 60));         // 倒しきらないように
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen.contains(GunnerAndroid.STATE_SHOT), "小型銃を撃たない: " + seen);
            for (int s : new int[]{GunnerAndroid.STATE_RAIL_ASSEMBLE, GunnerAndroid.STATE_RAIL_CHARGE, GunnerAndroid.STATE_RAIL_FIRE,
                    GunnerAndroid.STATE_RAIL_STOW}) {
                helper.assertTrue(seen.contains(s), "レールガンの動きが進まない（" + s + " がない）: " + seen);
            }
            helper.assertTrue(target.getHealth() < 200, "敵に当たらない");
            g.discard();
        });
    }

    /** 小型銃の3連射（5 tick おき）は、受けたあとの無敵時間に消されず、3発とも当たる。 */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_burst", timeoutTicks = 60)
    public static void pistolBurstHitsEveryShot(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GunnerAndroid g = gunner(helper, new BlockPos(7, 1, 2));
        Zombie target = dummy(helper, new BlockPos(7, 1, 7));
        float[] health = new float[4];
        helper.runAtTickTime(8, () -> health[0] = target.getHealth());         // 1発目（10 tick 目）より前に覚える
        for (int i = 0; i < GunnerAndroid.SHOT_TICKS.length; i++) {
            int shot = i;
            helper.runAtTickTime(10 + GunnerAndroid.SHOT_TICKS[i] - GunnerAndroid.SHOT_TICKS[0], () -> {
                helper.assertTrue(g.shoot(helper.getLevel(), target), "撃たない（" + (shot + 1) + "発目）");
                health[shot + 1] = target.getHealth();
                helper.assertTrue(health[shot + 1] < health[shot], (shot + 1) + "発目のダメージが入らない: " + health[shot] + " → " + health[shot + 1]);
            });
        }
        helper.runAtTickTime(40, helper::succeed);
    }

    /** 小型銃: 見通しがあれば撃って当て、壁があれば撃たない。 */
    @GameTest(setupTicks = 20, template = HUGE, batch = BATCH + "_pistol", timeoutTicks = 40)
    public static void pistolNeedsLineOfSight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GunnerAndroid g = gunner(helper, new BlockPos(7, 1, 2));
        Zombie target = dummy(helper, new BlockPos(7, 1, 7));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(g.shoot(helper.getLevel(), target), "見通しがあるのに撃たない");
            helper.assertTrue(target.getHealth() < 200, "光弾が当たらない");
            float after = target.getHealth();
            for (BlockPos p : BlockPos.betweenClosed(5, 1, 5, 9, 3, 5)) {
                helper.setBlock(p, Blocks.STONE);
            }
            target.invulnerableTime = 0;
            helper.assertFalse(g.shoot(helper.getLevel(), target), "壁ごしに撃った");
            helper.assertTrue(target.getHealth() == after, "壁ごしに当たった");
            helper.succeed();
        });
    }
}
