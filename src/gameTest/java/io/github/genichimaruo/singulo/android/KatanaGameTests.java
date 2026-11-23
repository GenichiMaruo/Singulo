package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 刀型アンドロイド「ルージュ」の隠し型としての扱い・強みと代償・紅一閃・戦いの流れ。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class KatanaGameTests {
    private static final String HUGE = "huge";
    private static final String BATCH = "katana";

    private KatanaGameTests() {}

    private static GunnerAndroid partner(GameTestHelper helper, KatanaAndroid rouge, BlockPos at) {
        var gun = SinguloEntities.GUNNER_ANDROID.get().create(helper.getLevel());
        var owner = java.util.UUID.randomUUID();
        var home = helper.absolutePos(new BlockPos(7, 0, 7));
        helper.setBlock(new BlockPos(7, 0, 7), SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = (AndroidStationBlockEntity) helper.getBlockEntity(new BlockPos(7, 0, 7));
        var registry = helper.getLevel().registryAccess();
        var saved = station.saveWithoutMetadata();
        saved.putUUID("owner", owner);
        saved.putUUID("android0", rouge.getUUID());
        saved.putUUID("android1", gun.getUUID());
        saved.putInt("mode", RuinAndroid.MODE_FOLLOW);
        station.loadWithComponents(saved, registry);
        rouge.makeGuard(owner, home, RuinAndroid.MODE_FOLLOW, 0);
        gun.makeGuard(owner, home, RuinAndroid.MODE_FOLLOW, 0);
        { var pos = Vec3.atBottomCenterOf(helper.absolutePos(at)); gun.moveTo(pos.x, pos.y, pos.z, 0, 0); }
        helper.getLevel().addFreshEntity(gun);
        rouge.setNoAi(false);
        return gun;
    }

    @GameTest(template = HUGE, batch = "paired_inspection", timeoutTicks = 750)
    public static void pairInspectsAndExchangesNods(GameTestHelper helper) {
        floor(helper);
        var rouge = katana(helper, new BlockPos(7, 1, 7));
        var gun = partner(helper, rouge, new BlockPos(10, 1, 7));
        int ammo = gun.ammo();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rouge.state() == KatanaAndroid.STATE_MAINTENANCE
                        && gun.gesture() == GunnerAndroid.GESTURE_INSPECT, "隣で弾倉の点検を始めるのを待つ"))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(gun.gesture() == GunnerAndroid.GESTURE_INSPECT && gun.ammo() == ammo,
                        "弾数を変えずに相手の手入れを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(rouge.gesture() == RuinAndroid.GESTURE_NOD
                        && gun.gesture() == RuinAndroid.GESTURE_NOD, "片付けが終わった二人の会釈を待つ"))
                .thenSucceed();
    }

    @GameTest(template = HUGE, batch = "cross_aim", timeoutTicks = 140)
    public static void crossAimWaitsUntilRougeSheathes(GameTestHelper helper) {
        floor(helper);
        var rouge = katana(helper, new BlockPos(2, 1, 9));
        var gun = partner(helper, rouge, new BlockPos(9, 1, 2));
        var foe = dummy(helper, new BlockPos(9, 1, 9));
        rouge.setTarget(foe);
        gun.setTarget(foe);
        rouge.setState(KatanaAndroid.STATE_IAI_STANCE);
        gun.setState(GunnerAndroid.STATE_RAIL_ASSEMBLE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(gun.covering(rouge), "同じ相手への交差照準を待つ"))
                .thenWaitUntil(() -> helper.assertTrue(rouge.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                        && rouge.stateTicks == KatanaAndroid.IAI_SHEATHE_SEAT - 2, "残心の納刀を待つ"))
                .thenExecute(() -> helper.assertTrue(gun.state() == GunnerAndroid.STATE_RAIL_CHARGE, "納刀前に発射した"))
                .thenWaitUntil(() -> helper.assertTrue(gun.state() == GunnerAndroid.STATE_RAIL_FIRE, "納刀に合わせた貫通射撃を待つ"))
                .thenExecute(() -> helper.assertTrue(foe.getHealth() <= 160, "二刀の斬撃と貫通射撃が両方当たらなかった"))
                .thenSucceed();
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

    /** 南（+Z）を向いた、動かない刀型。 */
    private static KatanaAndroid katana(GameTestHelper helper, BlockPos at) {
        KatanaAndroid k = SinguloEntities.KATANA_ANDROID.get().create(helper.getLevel());
        k.setNoAi(true);
        { var pos = Vec3.atBottomCenterOf(helper.absolutePos(at)); k.moveTo(pos.x, pos.y, pos.z, 0, 0); }
        k.yBodyRot = 0;
        helper.getLevel().addFreshEntity(k);
        return k;
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 14, 0, 14)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** 歩行だけを止めて、実際に技で届いた距離を通常の接近と区別する。 */
    private static KatanaAndroid stationaryFighter(GameTestHelper helper, BlockPos at, Zombie target) {
        KatanaAndroid k = katana(helper, at);
        k.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        k.setTarget(target);
        k.setNoAi(false);
        return k;
    }

    /** 刀型は射撃型と同じ隠し型: 遺構の印刷台にも研究所にも出ない。スポーンエッグと設計メモリはあるが一覧に出さず、メモリの名前は紫。 */
    @GameTest(template = "empty")
    public static void katanaIsHiddenLikeGunner(GameTestHelper helper) {
        helper.assertTrue(AndroidKind.KATANA.origin == AndroidKind.Origin.HIDDEN, "刀型が隠し型でない");
        helper.assertTrue(AndroidKind.KATANA.printCost == AndroidKind.GUNNER.printCost, "刀型の印刷の倍率が射撃型と違う");
        helper.assertFalse(Set.of(AndroidKind.ruinKinds()).contains(AndroidKind.KATANA), "刀型が遺構の印刷台の型に入っている");
        helper.assertFalse(Set.of(AndroidKind.prototypes()).contains(AndroidKind.KATANA), "刀型が研究所の試作機の型に入っている");
        for (String id : Set.of("katana_android_spawn_egg", AndroidKind.KATANA.memoryId())) {
            helper.assertTrue(BuiltInRegistries.ITEM.get(Singulo.id(id)) != Items.AIR, "アイテムが登録されていない: " + id);
            helper.assertTrue(SinguloItems.HIDDEN.contains(id), "一覧に出さないアイテムに入っていない: " + id);
            helper.assertFalse(SinguloItems.TAB_ORDER.stream().anyMatch(i -> i.getId().getPath().equals(id)), "クリエイティブタブに並んでいる: " + id);
        }
        helper.assertTrue(new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.KATANA.memoryId()))).getRarity() == Rarity.EPIC,
                "刀型の設計メモリの名前が紫（エピック）でない");
        helper.succeed();
    }

    /** 珍しい型の強みと代償: バックパックを内蔵し、充電の満タンが1.5倍、作業が1.5倍速い。印刷の電力は3倍。 */
    @GameTest(template = "empty")
    public static void katanaHasBuiltInPackAndCostsMore(GameTestHelper helper) {
        KatanaAndroid k = SinguloEntities.KATANA_ANDROID.get().create(helper.getLevel());
        var blade = SinguloEntities.BLADE_ANDROID.get().create(helper.getLevel());
        helper.assertTrue(k.builtInPack() && k.packOpen() && !blade.packOpen(), "刀型がバックパックを内蔵していない");
        helper.assertFalse(k.hasBackpack(), "内蔵のバックパックが、アップグレードの背負い物として見えている");
        helper.assertTrue(k.carryCapacity() == RuinAndroid.CARRY_SLOTS + RuinAndroid.PACK_SLOTS, "刀型の運べる欄が増えない: " + k.carryCapacity());
        helper.assertTrue(k.maxCharge() == RuinAndroid.CHARGE_MAX * 3 / 2 && blade.maxCharge() == RuinAndroid.CHARGE_MAX,
                "刀型の充電の満タンが1.5倍でない: " + k.maxCharge());
        helper.assertTrue(k.workTicks(40) == 27, "刀型の作業が速くない: " + k.workTicks(40));
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity s = (AndroidStationBlockEntity) helper.getBlockEntity(at);
        s.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY,
                new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.KATANA.memoryId()))));
        helper.assertTrue(s.printFe() == AndroidStationBlockEntity.PRINT_FE * 3, "刀型の印刷の電力が3倍でない: " + s.printFe());
        k.discard();
        blade.discard();
        helper.succeed();
    }

    /**
     * 紅一閃: 駆け抜ける通り道は相手の少し先まで。通り道の上の相手だけ浅く斬って覚え、残心で深い斬撃が入る。
     * 通り道から外れた相手には当たらない。
     */
    @GameTest(template = HUGE, batch = BATCH + "_iai", timeoutTicks = 40)
    public static void iaiCutsAlongPathThenReleases(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 6));
        Zombie aside = dummy(helper, new BlockPos(11, 1, 6));
        helper.runAtTickTime(10, () -> {
            Vec3 to = k.planDash(helper.getLevel(), first);
            helper.assertTrue(to.z > first.getZ() + 1, "相手の向こうまで駆け抜けない: " + to);
            helper.assertTrue(to.distanceTo(k.position()) <= KatanaAndroid.IAI_RANGE + 1e-6, "駆け抜ける距離が上限を超えた");
            int hit = k.cutAlong(helper.getLevel(), k.pathFrom(), k.pathTo());
            helper.assertTrue(hit == 1 && k.markedForTest(first) && !k.markedForTest(aside), "通り道の上の相手だけを斬らない: " + hit);
            float afterPass = first.getHealth();
            helper.assertTrue(afterPass < 200 && afterPass > 200 - KatanaAndroid.IAI_DAMAGE, "駆け抜けの傷が浅くない: " + afterPass);
            helper.assertTrue(k.cutAlong(helper.getLevel(), k.pathFrom(), k.pathTo()) == 0, "同じ相手を二度斬った");
            int released = k.releaseCuts(helper.getLevel());
            helper.assertTrue(released == 1 && first.getHealth() < afterPass - KatanaAndroid.IAI_DAMAGE + 1, "残心の斬撃が入らない");
            helper.assertTrue(aside.getHealth() == 200, "通り道から外れた相手に当たった");
            helper.assertTrue(k.markedCount() == 0, "斬撃のあとも、斬った相手を覚えている");
            helper.succeed();
        });
    }

    /** 紅一閃は壁を突き抜けない: 通り道は壁の手前で止まる。 */
    @GameTest(template = HUGE, batch = BATCH + "_wall", timeoutTicks = 40)
    public static void iaiStopsBeforeWall(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 2));
        Zombie target = dummy(helper, new BlockPos(7, 1, 7));
        for (BlockPos p : BlockPos.betweenClosed(4, 1, 9, 10, 3, 9)) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.runAtTickTime(10, () -> {
            Vec3 to = k.planDash(helper.getLevel(), target);
            helper.assertTrue(to.z < helper.absoluteVec(new Vec3(0, 0, 9)).z, "壁を突き抜けて駆け抜ける: " + to);
            helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = BATCH + "_step_reach", timeoutTicks = 110)
    public static void katanaCutsAtThreeAndStepsFromTen(GameTestHelper helper) {
        floor(helper);
        Zombie near = dummy(helper, new BlockPos(7, 1, 5));
        Zombie far = dummy(helper, new BlockPos(7, 1, 12));
        Zombie covered = dummy(helper, new BlockPos(9, 1, 12));
        KatanaAndroid k = stationaryFighter(helper, new BlockPos(7, 1, 2), near);
        Vec3 start = k.position();
        near.setPos(start.add(0, 0, 3));
        far.setPos(start.add(0, 0, 10));
        covered.setPos(start.add(1.8, 0, 10));
        for (BlockPos p : BlockPos.betweenClosed(8, 1, 10, 8, 3, 12)) helper.setBlock(p, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_CUT && near.getHealth() < 200,
                        "3ブロック先の敵に袈裟斬りが当たるのを待つ"))
                .thenExecute(() -> {
                    near.discard();
                    k.setTarget(far);
                })
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_STEP, "10ブロックからの踏み込みを待つ"))
                .thenExecute(() -> helper.assertTrue(k.position().distanceTo(start) < 0.05 && far.getHealth() == 200,
                        "技を始める前に射程を詰めたか、遠くから敵に当たった"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_STEP && far.getHealth() < 200,
                        "踏み込んでから、前にいる敵を実際に斬るのを待つ"))
                .thenExecute(() -> {
                    double travel = k.getZ() - start.z;
                    helper.assertTrue(travel > 6 && travel < 10, "踏み込みの命中位置が敵の手前でない: " + travel);
                    helper.assertTrue(k.distanceTo(covered) < KatanaAndroid.STEP_REACH && !k.hasLineOfSight(covered)
                            && covered.getHealth() == 200, "射程に入った壁の向こうの敵を巻き込んだ");
                    k.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = HUGE, batch = BATCH + "_iai_reach", timeoutTicks = 220)
    public static void katanaIaiHitsFromFourteenBlocks(GameTestHelper helper) {
        floor(helper);
        Zombie foe = dummy(helper, new BlockPos(10, 1, 12));
        KatanaAndroid k = stationaryFighter(helper, new BlockPos(1, 1, 1), foe);
        Vec3 start = k.position(), forward = new Vec3(0.6, 0, 0.8);
        foe.setPos(start.add(forward.scale(13.9)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_STANCE, "遠間から紅一閃の構えに入るのを待つ"))
                .thenExecute(() -> helper.assertTrue(k.distanceTo(foe) > 13.8 && foe.getHealth() == 200,
                        "紅一閃の構えに入る前に接近または命中した"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN && k.markedForTest(foe),
                        "紅一閃で遠間の敵を斬り抜けるのを待つ"))
                .thenExecute(() -> {
                    double length = k.pathFrom().distanceTo(k.pathTo());
                    helper.assertTrue(length > 15.9 && length <= 16.001
                            && k.position().subtract(start).dot(forward) > 14, "駆け抜けの経路と実際の到達位置が違う: " + length);
                    helper.assertTrue(foe.getHealth() < 200 && foe.getHealth() > 185, "駆け抜けの浅い斬撃が入っていない");
                })
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                                && k.stateTicks >= KatanaAndroid.IAI_RELEASE && !k.markedForTest(foe) && foe.getHealth() < 185,
                        "離れた位置から斬り抜けた相手にも残心の斬撃が入るのを待つ"))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 抜いた刀は、敵がいなくなって少したつと腰の鞘へ納める。 */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void katanaSheathesWhenCalm(GameTestHelper helper) {
        KatanaAndroid k = katana(helper, new BlockPos(3, 1, 3));
        k.drawForTest();
        Set<Integer> seen = new java.util.HashSet<>();
        helper.onEachTick(() -> seen.add(k.state()));
        helper.succeedWhen(() -> {
            helper.assertFalse(k.drawn(), "敵がいないのに刀を納めない");
            helper.assertTrue(seen.contains(KatanaAndroid.STATE_SHEATHE), "納刀の動きをせずに納めた: " + seen);
            k.discard();
        });
    }

    /** 落ち着いて立つ護衛が自分で手入れを始め、布と打刀を戻す。手入れを攻撃としては扱わない。 */
    @GameTest(template = HUGE, batch = BATCH + "_maintenance", timeoutTicks = 700)
    public static void katanaMaintainsItsBladeWhileIdle(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 7));
        k.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 7)), RuinAndroid.MODE_FOLLOW, 0);
        k.setNoAi(false);
        int charge = k.charge();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_MAINTENANCE && k.stateTicks < KatanaAndroid.MAINTENANCE_DRAW,
                        "その場で手入れ箱を開くのを待つ"))
                .thenExecute(() -> helper.assertFalse(k.drawn() || k.bladeInHand(0), "布を取る前から刀が抜けている"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_MAINTENANCE && k.stateTicks == KatanaAndroid.MAINTENANCE_WIPE,
                        "刀を拭くところまで進むのを待つ"))
                .thenExecute(() -> helper.assertTrue(k.bladeInHand(0) && !k.drawn() && !k.greatBladeInHand(0), "手入れ中は腰の打刀だけを手にする"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_MAINTENANCE && k.stateTicks == KatanaAndroid.MAINTENANCE_STOW,
                        "打刀を納めて布をしまうのを待つ"))
                .thenExecute(() -> helper.assertFalse(k.bladeInHand(0) || k.drawn(), "手入れを終えても刀が抜けている"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == RuinAndroid.STATE_IDLE, "箱を閉じて待機に戻るのを待つ"))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(k.charge() == charge && k.state() == RuinAndroid.STATE_IDLE && !k.drawn(), "手入れで電力を消費したか、続けて手入れを始めた");
                    k.discard();
                })
                .thenSucceed();
    }

    /** 打刀を拭いている間に接敵したら、手にある一本をそのまま使う。移動で中断したときは戦闘用に抜かない。 */
    @GameTest(template = HUGE, batch = BATCH + "_maintenance_interrupt", timeoutTicks = 140)
    public static void katanaInterruptsMaintenanceForMovementAndCombat(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 7));
        k.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 7)), RuinAndroid.MODE_FOLLOW, 0);
        k.setNoAi(false);
        Zombie[] foe = new Zombie[1];
        helper.startSequence().thenIdle(6)
                .thenExecute(() -> {
                    k.setState(KatanaAndroid.STATE_MAINTENANCE);
                    k.stateTicks = KatanaAndroid.MAINTENANCE_WIPE;
                    k.setDeltaMovement(0.25, 0, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(k.state() == RuinAndroid.STATE_IDLE && !k.drawn(), "動き出して手入れをやめるのを待つ"))
                .thenIdle(12)
                .thenExecute(() -> {
                    k.setState(KatanaAndroid.STATE_MAINTENANCE);
                    k.stateTicks = KatanaAndroid.MAINTENANCE_WIPE;
                    foe[0] = dummy(helper, new BlockPos(7, 1, 8));
                    foe[0].setPos(k.getX() + 1, k.getY(), k.getZ());
                    k.setTarget(foe[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(k.drawn() && k.state() != KatanaAndroid.STATE_DRAW && k.state() != KatanaAndroid.STATE_MAINTENANCE,
                        "接敵時、手にある打刀を抜き直した"))
                .thenWaitUntil(() -> helper.assertTrue(foe[0].getHealth() < 200, "手入れから実際の打刀の命中につながるのを待つ"))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 抜いている途中で敵が消え、納めている途中で別の敵が来ても、刀の受け渡しを最後まで行う。 */
    @GameTest(template = HUGE, batch = BATCH + "_handoff", timeoutTicks = 180)
    public static void katanaFinishesDrawAndSheatheWhenTargetChanges(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 7));
        BlockPos adjacent = new BlockPos(7, 1, 8);
        Zombie first = dummy(helper, adjacent);
        k.setTarget(first);
        k.setNoAi(false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_DRAW && !k.drawn(), "最初の抜刀を待つ"))
                .thenExecute(first::discard)
                .thenWaitUntil(() -> helper.assertTrue(k.state() == RuinAndroid.STATE_IDLE && k.drawn(), "敵が消えたあとも刀を抜ききる"))
                .thenExecute(() -> k.setNoAi(true))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_SHEATHE, "納刀の開始を待つ"))
                .thenExecute(() -> {
                    k.setTarget(dummy(helper, adjacent));
                    k.setNoAi(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_SHEATHE && k.stateTicks == KatanaAndroid.SHEATHE_SEAT,
                        "接敵しても鍔が鞘に着くまで納刀を続ける"))
                .thenExecute(() -> helper.assertFalse(k.drawn(), "鍔が着いたときに刀が鞘へ戻っていない"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == RuinAndroid.STATE_IDLE && !k.drawn(), "納刀後に手を離す"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_DRAW && !k.drawn(), "次の敵に対して改めて抜刀する"))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 生きた敵が近くに残る間は礼をせず、最後の斬撃のあとに礼を開始。礼の途中の再接敵にも応じる。 */
    @GameTest(template = HUGE, batch = BATCH + "_victory_melee", timeoutTicks = 330)
    public static void katanaSalutesAfterLastKillAndReengages(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 5));
        k.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 5)), 0, 0);
        Zombie first = dummy(helper, new BlockPos(7, 1, 6));
        Zombie second = dummy(helper, new BlockPos(7, 1, 12));
        first.setHealth(1);
        k.setTarget(first);
        k.setNoAi(false);
        boolean[] salutedEarly = {false};
        helper.onEachTick(() -> {
            if (k.state() == KatanaAndroid.STATE_VICTORY && second.isAlive()) salutedEarly[0] = true;
        });
        Zombie[] newcomer = new Zombie[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(first.isAlive(), "最初の敵を倒すのを待つ"))
                .thenIdle(16)
                .thenExecute(() -> {
                    helper.assertFalse(salutedEarly[0], "次の敵が残っている間に刀礼を始めた");
                    second.setHealth(1);
                    k.setTarget(second);
                })
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_VICTORY
                        && k.stateTicks >= KatanaAndroid.VICTORY_RAISE, "最後の敵を倒して紅刀を掲げるのを待つ"))
                .thenExecute(() -> {
                    helper.assertFalse(second.isAlive() || salutedEarly[0], "最後の敵を倒す前に刀礼を始めた");
                    newcomer[0] = dummy(helper, new BlockPos(7, 1, 10));
                    newcomer[0].setPos(k.getX() + 1, k.getY(), k.getZ());
                    k.setTarget(newcomer[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(k.state() != KatanaAndroid.STATE_VICTORY && k.drawn(), "新たな敵を前に刀礼を続けた"))
                .thenWaitUntil(() -> helper.assertTrue(newcomer[0].getHealth() < 200,
                        "再接敵後の打刀の命中を待つ。状態=" + k.state() + " 敵との距離=" + k.distanceTo(newcomer[0])
                                + " 狙っているか=" + (k.getTarget() == newcomer[0])))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 遅延斬撃で最後の二体を倒す。残心から紅刀を抜いたまま勝利の礼へ進み、一度だけ納刀する。 */
    @GameTest(template = HUGE, batch = BATCH + "_victory_iai", timeoutTicks = 220)
    public static void katanaSalutesWithDrawnIaiBlade(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 7));
        Zombie second = dummy(helper, new BlockPos(7, 1, 8));
        first.setHealth(10);
        second.setHealth(10);
        k.drawForTest();
        k.setTarget(first);
        k.setState(KatanaAndroid.STATE_IAI_STANCE);
        k.setNoAi(false);
        int[] previous = {k.state()}, previousTicks = {0}, salutes = {0};
        helper.onEachTick(() -> {
            int s = k.state();
            helper.assertFalse(s == KatanaAndroid.STATE_VICTORY, "紅一閃のあとに紅刀を半分抜き直した");
            if (s == KatanaAndroid.STATE_IAI_ZANSHIN) {
                helper.assertTrue(k.greatBladeInHand(0) && first.isAlive() && second.isAlive(), "勝敗が決まる前に紅刀を納めた");
            }
            if (s == KatanaAndroid.STATE_IAI_VICTORY && previous[0] != s) {
                salutes[0]++;
                helper.assertTrue(previous[0] == KatanaAndroid.STATE_IAI_ZANSHIN && previousTicks[0] == KatanaAndroid.IAI_RELEASE
                        && !first.isAlive() && !second.isAlive() && k.markedCount() == 0 && k.greatBladeInHand(0),
                        "遅延斬撃を解放した瞬間に、抜いたままの紅刀で残心から礼へつながらなかった");
            }
            previous[0] = s;
            previousTicks[0] = k.stateTicks;
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                        && k.markedCount() == 2 && first.isAlive() && second.isAlive(), "二体を斬り抜け残心に入るのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_VICTORY
                        && k.stateTicks == KatanaAndroid.IAI_VICTORY_RETURN && k.greatBladeInHand(0), "紅刀を最後まで掲げるのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_VICTORY
                        && k.stateTicks == KatanaAndroid.IAI_VICTORY_SEAT && !k.greatBladeInHand(0) && k.drawn(),
                        "腰の打刀を持ったまま、背中の紅刀だけが鞘に収まるのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IDLE && !k.drawn(), "通常の腰への納刀を待つ"))
                .thenExecute(() -> helper.assertTrue(salutes[0] == 1, "刀礼の回数が違う: " + salutes[0]))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 紅一閃の駆け抜けで相手が倒れた場合も専用の礼。礼の途中に現れた敵には右手の打刀で応じる。 */
    @GameTest(template = HUGE, batch = BATCH + "_victory_iai_interrupt", timeoutTicks = 150)
    public static void katanaReengagesAfterIaiPassKill(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 2));
        Zombie first = dummy(helper, new BlockPos(7, 1, 7));
        first.setHealth(1);
        k.drawForTest();
        k.setTarget(first);
        k.setState(KatanaAndroid.STATE_IAI_STANCE);
        k.setNoAi(false);
        Zombie[] newcomer = new Zombie[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_FLASH && !first.isAlive(),
                        "駆け抜けの浅い斬撃で敵を倒すのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_VICTORY && k.stateTicks == 8
                        && k.greatBladeInHand(0), "抜いたままの紅刀を掲げるのを待つ"))
                .thenExecute(() -> {
                    newcomer[0] = dummy(helper, new BlockPos(7, 1, 8));
                    newcomer[0].setPos(k.getX() + 1, k.getY(), k.getZ());
                    k.setTarget(newcomer[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(k.state() != KatanaAndroid.STATE_IAI_VICTORY && k.drawn(), "再接敵しても刀礼を中断しない"))
                .thenWaitUntil(() -> helper.assertTrue(newcomer[0].getHealth() < 200 && k.drawn(), "再接敵後の打刀の命中を待つ"))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 遅延斬撃で敵を倒しても、別の相手が生き残れば通常の残心で納刀して次の戦闘へ戻る。 */
    @GameTest(template = HUGE, batch = BATCH + "_victory_iai_survivor", timeoutTicks = 150)
    public static void katanaSheathesNormallyWhenIaiLeavesOpponent(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = katana(helper, new BlockPos(7, 1, 2));
        k.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 2)), 0, 0);
        Zombie victim = dummy(helper, new BlockPos(7, 1, 7));
        Zombie survivor = dummy(helper, new BlockPos(7, 1, 8));
        victim.setHealth(10);
        k.drawForTest();
        k.setTarget(victim);
        k.setState(KatanaAndroid.STATE_IAI_STANCE);
        k.setNoAi(false);
        helper.onEachTick(() -> helper.assertFalse(k.state() == KatanaAndroid.STATE_VICTORY || k.state() == KatanaAndroid.STATE_IAI_VICTORY,
                "斬り残した相手がいるのに礼を始めた"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                        && k.stateTicks == KatanaAndroid.IAI_RELEASE && victim.isAlive() && k.markedCount() == 2 && k.greatBladeInHand(0),
                        "紅刀を手に残したまま遅延斬撃を解放するのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                        && !victim.isAlive() && survivor.getHealth() < 190 && k.greatBladeInHand(0), "一体だけを倒すのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IAI_ZANSHIN
                        && k.stateTicks == KatanaAndroid.IAI_SHEATHE_SEAT && !k.greatBladeInHand(0), "通常の残心で鍔が着くのを待つ"))
                .thenWaitUntil(() -> helper.assertTrue(k.state() == KatanaAndroid.STATE_IDLE && k.drawn(), "腰の打刀を持った構えに戻るのを待つ"))
                .thenExecute(k::discard)
                .thenSucceed();
    }

    /** 戦いの動き（AI つき）: 護衛の刀型が敵を見つけると、抜いて斬り、紅一閃（構え・駆け抜け・残心）まで進む。 */
    @GameTest(template = HUGE, batch = BATCH + "_fight", timeoutTicks = 900)
    public static void katanaFightsAndUsesIai(GameTestHelper helper) {
        floor(helper);
        KatanaAndroid k = SinguloEntities.KATANA_ANDROID.get().create(helper.getLevel());
        k.makeGuard(java.util.UUID.randomUUID(), helper.absolutePos(new BlockPos(7, 0, 1)), 0, 0);
        { var pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 2))); k.moveTo(pos.x, pos.y, pos.z, 0, 0); }
        helper.getLevel().addFreshEntity(k);
        Zombie target = dummy(helper, new BlockPos(7, 1, 9));
        Set<Integer> seen = new java.util.HashSet<>();
        boolean[] twin = {false, false};         // 駆け抜けで二刀を持っていたか・残心後に腰の刀だけが手にあるか
        int[] last = {RuinAndroid.STATE_IDLE};
        helper.onEachTick(() -> {
            int s = k.state();
            seen.add(s);
            if (s == KatanaAndroid.STATE_IAI_FLASH && k.bladeInHand(0) && k.greatBladeInHand(0)) {
                twin[0] = true;
            }
            if (last[0] == KatanaAndroid.STATE_IAI_ZANSHIN && s != KatanaAndroid.STATE_IAI_ZANSHIN) {
                twin[1] = k.bladeInHand(0) && !k.greatBladeInHand(0);
            }
            last[0] = s;
            target.setHealth(Math.max(target.getHealth(), 60));         // 倒しきらないように
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen.contains(KatanaAndroid.STATE_DRAW), "刀を抜かない: " + seen);
            for (int s : new int[]{KatanaAndroid.STATE_IAI_STANCE, KatanaAndroid.STATE_IAI_FLASH, KatanaAndroid.STATE_IAI_ZANSHIN}) {
                helper.assertTrue(seen.contains(s), "紅一閃が進まない（" + s + " がない）: " + seen);
            }
            helper.assertTrue(twin[0], "駆け抜けで、腰の刀と背中の紅い刀の二刀を持っていない");
            helper.assertTrue(twin[1], "残心のあと、手に腰の刀だけが残るのを待つ");
            helper.assertTrue(target.getHealth() < 200, "敵に当たらない");
            k.discard();
        });
    }
}
