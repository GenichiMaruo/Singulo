package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.GravityRemnant;
import io.github.genichimaruo.singulo.ruin.GuardianCoreBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinBoss;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlock;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 研究棟・封鎖培養施設のボス部屋: 番人の封印核と、残響の番人・重力の澱。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BossRoomGameTests {
    private static final String EMPTY = "empty";
    private static final String TALL = "tall";
    /** 封印核は近くの保管庫と投影器を探すので、ほかのテスト（ボス部屋どうしも）とは1つずつ別の組で動かす。 */
    private static final String BATCH = "boss_room";
    private static final BlockPos VAULT = new BlockPos(1, 1, 1);
    private static final BlockPos CORE = new BlockPos(4, 1, 4);
    private static final List<BlockPos> PROJECTORS = List.of(new BlockPos(6, 1, 1), new BlockPos(1, 1, 6),
            new BlockPos(6, 1, 6), new BlockPos(1, 1, 3));

    private BossRoomGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** ボス部屋を再現する: 封鎖された保管庫と封印核（研究棟なら四隅の投影器）。 */
    private static GuardianCoreBlockEntity room(GameTestHelper helper, String ruin, BlockPos vault, BlockPos core) {
        helper.setBlock(vault, SinguloBlocks.RUIN_CACHE.get().defaultBlockState().setValue(RuinCacheBlock.SEALED, true));
        RuinCacheBlockEntity cache = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, vault);
        cache.setRuin(ruin);
        if (ruin.equals("research_building")) {
            for (BlockPos p : PROJECTORS) {
                helper.setBlock(p, SinguloBlocks.ECHO_PROJECTOR.get());
            }
        }
        helper.setBlock(core, SinguloBlocks.GUARDIAN_CORE.get());
        GuardianCoreBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, core);
        be.scan(helper.getLevel());
        // Forge 1.20.1 keeps neighbouring batch structures inside the scan radius.
        // Restrict this fixture to the projectors installed for this room.
        be.projectors().removeIf(p -> !ruin.equals("research_building")
                || PROJECTORS.stream().noneMatch(relative -> helper.absolutePos(relative).equals(p)));
        return be;
    }

    private static int dropped(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 封印核

    /** 封印核は、保管庫の遺構に応じた番人を出現の演出つきで起こす。2体目は出ない。
     *  （部屋に入った挑戦者で起きる部分は、テスト用のプレイヤーが常に創造モード扱いのため、ここでは直接起こす） */
    @GameTest(template = EMPTY, batch = BATCH + "_1", timeoutTicks = 60)
    public static void coreWakesSentinelForResearchVault(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        RuinBoss boss = core.awaken(helper.getLevel());
        helper.assertTrue(boss instanceof EchoSentinel, "研究棟の部屋で残響の番人が現れない: " + boss);
        helper.assertTrue(boss.emergeTicks() > 0, "出現の演出が始まらない");
        helper.assertFalse(boss.hurt(helper.getLevel().damageSources().generic(), 50), "出現の途中で傷ついた");
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(((EchoSentinel) boss).projectorCount() == 4, "投影器を4つ数えない: " + ((EchoSentinel) boss).projectorCount());
            helper.assertTrue(core.awaken(helper.getLevel()) == null, "2体目が現れた");
            helper.assertTrue(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "戦っている間に保管庫が開いている");
            boss.discard();
            helper.succeed();
        });
    }

    /** 保管庫が開いている（倒したあと、再生する前）ときは、番人は現れない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_2")
    public static void coreSleepsWhileVaultOpen(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        RuinCacheBlockEntity vault = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT);
        vault.refillIfDue(helper.getLevel(), helper.getLevel().getGameTime());
        vault.setSealed(false);
        helper.assertTrue(core.awaken(helper.getLevel()) == null, "保管庫が開いているのに番人が現れた");
        helper.succeed();
    }

    /** 番人を起こすたびに、壊された投影器と割れた封じ込め槽が元に戻る。 */
    @GameTest(template = EMPTY, batch = BATCH + "_3")
    public static void coreRestoresRoomProps(GameTestHelper helper) {
        // 封じ込め槽（核の真上のガラスの筒）を先に置いてから覚えさせる
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(CORE.offset(2, y, 0), Blocks.PURPLE_STAINED_GLASS);
            helper.setBlock(CORE.offset(-2, y, 0), Blocks.PURPLE_STAINED_GLASS);
        }
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        helper.assertTrue(core.tank().size() == 6, "封じ込め槽のガラスを覚えない: " + core.tank().size());
        helper.setBlock(PROJECTORS.get(0), Blocks.AIR);
        helper.setBlock(CORE.offset(2, 3, 0), Blocks.AIR);
        RuinBoss boss = core.awaken(helper.getLevel());
        helper.assertTrue(boss != null, "番人が現れない");
        helper.assertBlockPresent(SinguloBlocks.ECHO_PROJECTOR.get(), PROJECTORS.get(0));
        helper.assertBlockPresent(Blocks.PURPLE_STAINED_GLASS, CORE.offset(2, 3, 0));
        boss.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ 残響の番人

    /** 投影器が残っているほど受けるダメージが小さく、全部壊すとそのまま通る。 */
    @GameTest(template = EMPTY, batch = BATCH + "_4", timeoutTicks = 80)
    public static void sentinelProjectorsShieldIt(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        float max = s.getMaxHealth();
        helper.assertTrue(max == EchoSentinel.HEALTH, "体力が設計値でない: " + max);
        s.hurt(helper.getLevel().damageSources().magic(), 20);
        float shielded = max - s.getHealth();
        helper.assertTrue(Math.abs(shielded - 20 * EchoSentinel.PROJECTION_FACTOR[4]) < 0.01F, "投影器4つで1割にならない: " + shielded);
        for (BlockPos p : PROJECTORS) {
            helper.setBlock(p, Blocks.AIR);
            helper.assertBlockPresent(Blocks.AIR, p);
        }
        helper.runAtTickTime(30, () -> {
            for (BlockPos p : PROJECTORS) helper.assertBlockPresent(Blocks.AIR, p);
            helper.assertTrue(s.projectorCount() == 0, "壊した投影器を数え直さない: " + s.projectorCount() + ", entity ticks=" + s.tickCount + ", removed=" + s.isRemoved());
            float before = s.getHealth();
            s.hurt(helper.getLevel().damageSources().magic(), 20);
            helper.assertTrue(Math.abs(before - s.getHealth() - 20) < 0.01F, "投影器がないのにダメージが減る: " + (before - s.getHealth()));
            s.discard();
            helper.succeed();
        });
    }

    /** 体力が半分を切ると、1撃で消える分身を2体呼ぶ。分身はボスバーもドロップも持たない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_5", timeoutTicks = 40)
    public static void sentinelSummonsOneHitEchoes(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        s.setHealth(EchoSentinel.HEALTH / 2 - 1);
        var target = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 1));
        s.summonEchoes(helper.getLevel(), target);
        List<EchoSentinel> echoes = helper.getLevel().getEntitiesOfClass(EchoSentinel.class, s.getBoundingBox().inflate(32), EchoSentinel::isMinion);
        helper.assertTrue(echoes.size() == EchoSentinel.MINIONS, "分身が2体にならない: " + echoes.size());
        EchoSentinel echo = echoes.get(0);
        helper.assertTrue(echo.getMaxHealth() == 1, "分身の体力が1でない");
        echo.hurt(helper.getLevel().damageSources().magic(), 1);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(echo.isRemoved(), "分身が1撃で消えない");
            helper.assertTrue(core.activeBoss(helper.getLevel()) == s, "分身が消えたら本体まで変わった");
            helper.assertTrue(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "分身を倒しただけで保管庫が開いた");
            for (EchoSentinel e : helper.getLevel().getEntitiesOfClass(EchoSentinel.class, s.getBoundingBox().inflate(32))) {
                e.discard();
            }
            target.discard();
            helper.succeed();
        });
    }

    /** 倒すと保管庫の封鎖が解け、番人の投影核を2つ落とす。 */
    @GameTest(template = EMPTY, batch = BATCH + "_6", timeoutTicks = 40)
    public static void sentinelDefeatUnsealsVault(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        s.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒しても保管庫が開かない");
            helper.assertTrue(dropped(helper, item("sentinel_core")) == 2, "番人の投影核を2つ落とさない: " + dropped(helper, item("sentinel_core")));
            helper.assertTrue(core.activeBoss(helper.getLevel()) == null, "倒したのに番人が残っている");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 重力の澱

    /** 殻がある間は1撃ごとに殻が1つ剥がれ、ダメージが小さい。殻がなくなるとそのまま通る。 */
    @GameTest(template = EMPTY, batch = BATCH + "_7", timeoutTicks = 60)
    public static void remnantShellAbsorbsHits(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        GravityRemnant r = (GravityRemnant) core.awaken(helper.getLevel());
        r.skipEmerging();
        helper.assertTrue(r.getMaxHealth() == GravityRemnant.HEALTH && r.shell() == GravityRemnant.SHELL_PIECES, "体力か殻が設計値でない");
        r.hurt(helper.getLevel().damageSources().magic(), 20);
        float lost = GravityRemnant.HEALTH - r.getHealth();
        helper.assertTrue(r.shell() == GravityRemnant.SHELL_PIECES - 1, "当てても殻が剥がれない: " + r.shell());
        helper.assertTrue(Math.abs(lost - 20 * GravityRemnant.SHELL_FACTOR) < 0.01F, "殻があるのにダメージが減らない: " + lost);
        r.setShell(0);
        helper.runAtTickTime(20, () -> {
            float before = r.getHealth();
            r.hurt(helper.getLevel().damageSources().magic(), 20);
            helper.assertTrue(Math.abs(before - r.getHealth() - 20) < 0.01F, "殻がないのにダメージが減る: " + (before - r.getHealth()));
            r.discard();
            helper.succeed();
        });
    }

    /** 周りの相手を浮かせ、少しあとで床へ叩きつけて落下ダメージを与える。 */
    @GameTest(template = TALL, batch = BATCH + "_8", timeoutTicks = 100)
    public static void remnantLiftsAndSlams(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", new BlockPos(0, 1, 0), new BlockPos(2, 0, 4));
        GravityRemnant r = (GravityRemnant) core.awaken(helper.getLevel());
        r.skipEmerging();
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        float health = pig.getHealth();
        helper.runAfterDelay(5, () -> {
            r.startFlip(helper.getLevel());
            helper.assertTrue(pig.getDeltaMovement().y > 1.0, "浮かび上がらない: " + pig.getDeltaMovement().y);
        });
        helper.runAtTickTime(5 + GravityRemnant.FLIP_HOLD + 30, () -> {
            helper.assertTrue(pig.getHealth() < health, "叩きつけられても落下ダメージがない: " + pig.getHealth());
            pig.discard();
            r.discard();
            helper.succeed();
        });
    }

    /** 倒すと保管庫の封鎖が解け、縮退核を2つ落とす。 */
    @GameTest(template = EMPTY, batch = BATCH + "_9", timeoutTicks = 40)
    public static void remnantDefeatUnsealsVault(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        GravityRemnant r = (GravityRemnant) core.awaken(helper.getLevel());
        helper.assertTrue(r != null, "封鎖培養施設の部屋で重力の澱が現れない");
        r.skipEmerging();
        r.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒しても保管庫が開かない");
            helper.assertTrue(dropped(helper, item("degenerate_nucleus")) == 2, "縮退核を2つ落とさない: " + dropped(helper, item("degenerate_nucleus")));
            helper.succeed();
        });
    }

    /** 出現の演出の途中で、封じ込め槽のガラスにひびが入り、最後に砕ける。 */
    @GameTest(template = EMPTY, batch = BATCH + "_10", timeoutTicks = GravityRemnant.EMERGE_TICKS + 40)
    public static void remnantShattersItsTank(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(CORE.offset(2, y, 0), Blocks.PURPLE_STAINED_GLASS);
        }
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        GravityRemnant r = (GravityRemnant) core.awaken(helper.getLevel());
        helper.runAtTickTime(GravityRemnant.EMERGE_TICKS / 2, () ->
                helper.assertBlockPresent(SinguloBlocks.RUIN_GLASS.get(), CORE.offset(2, 3, 0)));
        helper.runAtTickTime(GravityRemnant.EMERGE_TICKS + 10, () -> {
            helper.assertBlockPresent(Blocks.AIR, CORE.offset(2, 3, 0));
            helper.assertTrue(r.emergeTicks() == 0 && !r.isNoAi(), "演出が終わっても動き出さない");
            core.restoreProps(helper.getLevel());
            helper.assertBlockPresent(Blocks.PURPLE_STAINED_GLASS, CORE.offset(2, 3, 0));
            r.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 投影の技と、倒されたときの演出

    /** 投影槍: 残っている投影器が、狙いの線のあとで一斉に光の槍を撃ち、線上の相手を傷つける。 */
    @GameTest(template = EMPTY, batch = BATCH + "_11", timeoutTicks = 60)
    public static void sentinelProjectorsFireLances(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        s.setNoAi(true);
        var pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 2));
        pig.setNoAi(true);
        float health = pig.getHealth();
        helper.assertTrue(s.startLance(pig), "投影器があるのに投影槍を始めない");
        s.setNoAi(false);
        helper.runAtTickTime(EchoSentinel.LANCE_WINDUP / 2, () ->
                helper.assertTrue(pig.getHealth() == health, "狙いの線の途中で当たった"));
        helper.runAtTickTime(EchoSentinel.LANCE_WINDUP + 5, () -> {
            helper.assertTrue(pig.getHealth() < health, "投影槍が線上の相手に当たらない");
            pig.discard();
            s.discard();
            helper.succeed();
        });
    }

    /** 投影陣: 陣の中にいた相手だけが、光の柱で傷つく。 */
    @GameTest(template = EMPTY, batch = BATCH + "_12", timeoutTicks = 60)
    public static void sentinelCircleHitsOnlyInside(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        var inside = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        var outside = helper.spawn(EntityType.PIG, new BlockPos(6, 1, 6));
        inside.setNoAi(true);
        outside.setNoAi(true);
        float health = inside.getHealth();
        s.startCircle(inside);
        helper.runAtTickTime(EchoSentinel.CIRCLE_WINDUP + 5, () -> {
            helper.assertTrue(inside.getHealth() < health, "陣の中の相手に光の柱が当たらない");
            helper.assertTrue(outside.getHealth() == health, "陣の外の相手まで傷ついた");
            inside.discard();
            outside.discard();
            s.discard();
            helper.succeed();
        });
    }

    /** 倒されると、演出のあいだは体が残り、終わると消える（保管庫はすぐ開く）。 */
    @GameTest(template = EMPTY, batch = BATCH + "_13", timeoutTicks = EchoSentinel.DEATH_TICKS + 30)
    public static void sentinelDeathPlaysOut(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        s.kill();
        helper.runAtTickTime(EchoSentinel.DEATH_TICKS / 2, () -> {
            helper.assertFalse(s.isRemoved(), "演出の途中で消えた");
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒したのに保管庫が開かない");
        });
        helper.runAtTickTime(EchoSentinel.DEATH_TICKS + 10, () -> {
            helper.assertTrue(s.isRemoved(), "演出が終わっても消えない");
            helper.succeed();
        });
    }

    /** 重力の澱も、倒されると演出のあいだは残り、終わると消える。 */
    @GameTest(template = EMPTY, batch = BATCH + "_14", timeoutTicks = GravityRemnant.DEATH_TICKS + 30)
    public static void remnantDeathPlaysOut(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        GravityRemnant r = (GravityRemnant) core.awaken(helper.getLevel());
        r.skipEmerging();
        r.kill();
        helper.runAtTickTime(GravityRemnant.DEATH_TICKS / 2, () -> helper.assertFalse(r.isRemoved(), "演出の途中で消えた"));
        helper.runAtTickTime(GravityRemnant.DEATH_TICKS + 10, () -> {
            helper.assertTrue(r.isRemoved(), "演出が終わっても消えない");
            helper.succeed();
        });
    }
}
