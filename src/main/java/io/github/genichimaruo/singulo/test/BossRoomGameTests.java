package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan;
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
import net.minecraft.world.item.ItemStack;
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
        // 前の組のテストが残した投影器が近くにあると数がずれるので、このテストの場所の中のものだけにする
        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 8, 16)));
        be.projectors().removeIf(p -> !bounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)));
        return be;
    }

    private static int dropped(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8))))) {
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
        helper.assertTrue(max == EchoSentinel.health(), "体力が設計値でない: " + max);
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

    /** 覚醒した番人の経験値は、データを当て直しても（/data merge など）増えない。読み込み直した個体とも同じ。 */
    @GameTest(template = EMPTY, batch = BATCH + "_29")
    public static void awakenedXpSurvivesDataMerge(GameTestHelper helper) {
        EchoSentinel s = io.github.genichimaruo.singulo.registry.SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        s.makeAwakened();
        int before = s.getExperienceReward();
        net.minecraft.nbt.CompoundTag tag = s.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        tag.putBoolean("Glowing", true);
        try {
            new net.minecraft.server.commands.data.EntityDataAccessor(s).setData(tag);
            new net.minecraft.server.commands.data.EntityDataAccessor(s).setData(tag);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
        int after = s.getExperienceReward();
        EchoSentinel cold = io.github.genichimaruo.singulo.registry.SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        cold.load(tag);
        int reloaded = cold.getExperienceReward();
        helper.assertTrue(before == after && after == reloaded, "当て直すと経験値が変わる: " + before + " → " + after + "（読み込み直し " + reloaded + "）");
        helper.assertTrue(before == 500 * 3, "覚醒した残響の番人の経験値が設計と違う: " + before);
        s.discard();
        cold.discard();
        helper.succeed();
    }

    /** 記録保管室から出ない:押し出されると核の上へ戻り、部屋の外の相手の背後へは跳ばない。 */
    // 押し出した先はテストの区画の外のチャンクなので、強制読み込みして動けるようにする
    // （読み込みは実時間で進むので、tick を速めたテストでは戻るまで待つ）
    @GameTest(template = EMPTY, batch = BATCH + "_28", timeoutTicks = 200)
    public static void sentinelStaysInItsRoom(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        BlockPos home = helper.absolutePos(CORE);
        net.minecraft.world.level.ChunkPos pushed = new net.minecraft.world.level.ChunkPos(home.offset(14, 0, 0));
        boolean wasForced = helper.getLevel().getForcedChunks().contains(pushed.toLong());
        helper.getLevel().setChunkForced(pushed.x, pushed.z, true);
        s.teleportTo(home.getX() + 14.5, home.getY() + 2, home.getZ() + 0.5);
        helper.startSequence().thenWaitUntil(() -> {
            double dx = Math.abs(s.getX() - (home.getX() + 0.5));
            double dz = Math.abs(s.getZ() - (home.getZ() + 0.5));
            helper.assertTrue(dx <= EchoSentinel.ROOM_HALF + EchoSentinel.ROOM_SLACK && dz <= EchoSentinel.ROOM_HALF + EchoSentinel.ROOM_SLACK,
                    "部屋の外に出たまま: dx=" + dx + " dz=" + dz);
        }).thenExecute(() -> {
            var outside = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 4).offset(12, 0, 0));
            if (s.blinkBehind(helper.getLevel(), outside)) {
                double bx = Math.abs(s.getX() - (home.getX() + 0.5));
                helper.assertTrue(bx <= EchoSentinel.ROOM_HALF + 0.01, "部屋の外の相手の背後へ跳んだ: dx=" + bx);
            }
            outside.discard();
            s.discard();
            helper.getLevel().setChunkForced(pushed.x, pushed.z, wasForced);
        }).thenSucceed();
    }

    /** 体力が半分を切ると、1撃で消える分身を2体呼ぶ。分身はボスバーもドロップも持たない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_5", timeoutTicks = 40)
    public static void sentinelSummonsOneHitEchoes(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        EchoSentinel s = (EchoSentinel) core.awaken(helper.getLevel());
        s.skipEmerging();
        s.setHealth(EchoSentinel.health() / 2 - 1);
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
            helper.assertTrue(dropped(helper, item("sentinel_core")) == 2, "番人の投影核を2つ落とさない: " + dropped(helper, item("sentinel_core")) + ", participants=" + s.rewardParticipants());
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
        helper.assertTrue(r.getMaxHealth() == GravityRemnant.health() && r.shell() == GravityRemnant.SHELL_PIECES, "体力か殻が設計値でない");
        r.hurt(helper.getLevel().damageSources().magic(), 20);
        float lost = GravityRemnant.health() - r.getHealth();
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

    // ------------------------------------------------------------------ 覚醒した番人（攻略後の再戦）

    /** 倒したあと（保管庫が開いている）の部屋を作る。 */
    private static GuardianCoreBlockEntity clearedRoom(GameTestHelper helper, String ruin) {
        GuardianCoreBlockEntity core = room(helper, ruin, VAULT, CORE);
        RuinCacheBlockEntity vault = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT);
        vault.refillIfDue(helper.getLevel(), helper.getLevel().getGameTime());
        vault.setSealed(false);
        return core;
    }

    private static ItemStack stack(String id) {
        return new ItemStack(item(id));
    }

    /** 覚醒の残響核で、保管庫が開いていても覚醒した残響の番人（体力×2.5）が現れる。ほかの遺構の覚醒核では現れない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_15", timeoutTicks = 40)
    public static void echoCoreSummonsAwakenedSentinel(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "research_building");
        helper.assertTrue(core.awaken(helper.getLevel()) == null, "保管庫が開いているのに普通の番人が現れた");
        helper.assertTrue(core.awakenAwakened(helper.getLevel(), null, stack("awakening_gravity_core")) == null,
                "研究棟で覚醒の重力核が使えた");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, stack("awakening_echo_core"));
        helper.assertTrue(boss instanceof EchoSentinel && boss.awakened(), "覚醒の残響核で覚醒した残響の番人が現れない: " + boss);
        boss.skipEmerging();
        double expected = EchoSentinel.health() * RuinBoss.awakenedHealthMultiplier();
        helper.assertTrue(Math.abs(boss.effectiveMaxHealth() - expected) < 1.0, "体力が " + expected + " 相当でない: " + boss.effectiveMaxHealth());
        helper.assertTrue(((EchoSentinel) boss).minionCount() == EchoSentinel.MINIONS + 1, "覚醒した番人の分身が増えない");
        helper.assertTrue(core.awakenAwakened(helper.getLevel(), null, stack("awakening_echo_core")) == null, "覚醒戦の最中に2体目が現れた");
        boss.discard();
        helper.succeed();
    }

    /** 覚醒した重力の澱は体力が上限（1024）を超えるので、受けるダメージを減らして同じ硬さにする。 */
    @GameTest(template = EMPTY, batch = BATCH + "_16", timeoutTicks = 40)
    public static void awakenedRemnantKeepsHealthBeyondCap(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "culture_facility");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, stack("awakening_gravity_core"));
        helper.assertTrue(boss instanceof GravityRemnant && boss.awakened(), "覚醒の重力核で覚醒した重力の澱が現れない: " + boss);
        boss.skipEmerging();
        double expected = GravityRemnant.health() * RuinBoss.awakenedHealthMultiplier();
        helper.assertTrue(boss.getMaxHealth() <= 1024 && Math.abs(boss.effectiveMaxHealth() - expected) < 1.0,
                "体力が " + expected + " 相当でない: " + boss.getMaxHealth() + " / " + boss.effectiveMaxHealth());
        GravityRemnant r = (GravityRemnant) boss;
        r.setHealth(r.getMaxHealth() * 0.6F);
        helper.assertTrue(r.wellOpen() && r.wellRadius() == GravityRemnant.AWAKENED_WELL_RADIUS, "体力60%で広い重力井戸が開かない");
        boss.discard();
        helper.succeed();
    }

    /** 覚醒した残響の番人は、本気になると壊された投影器を2つまで映し直す（1戦に1回）。 */
    @GameTest(template = EMPTY, batch = BATCH + "_17", timeoutTicks = 60)
    public static void awakenedSentinelReprojects(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "research_building");
        EchoSentinel s = (EchoSentinel) core.awakenAwakened(helper.getLevel(), null, stack("awakening_echo_core"));
        s.skipEmerging();
        for (BlockPos p : PROJECTORS) {
            helper.setBlock(p, Blocks.AIR);
        }
        s.setHealth(s.getMaxHealth() * 0.6F);
        helper.runAtTickTime(5, () -> {
            int intact = s.intactProjectors().size();
            helper.assertTrue(intact == EchoSentinel.AWAKENED_REPROJECT, "投影器を2つ映し直さない: " + intact);
            for (BlockPos p : PROJECTORS) {
                helper.setBlock(p, Blocks.AIR);
            }
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(s.intactProjectors().isEmpty(), "2回目も映し直した");
            s.discard();
            helper.succeed();
        });
    }

    /** 覚醒した番人を倒すと、保管庫が開き、通常のドロップに加えて追加の報酬が出る。 */
    @GameTest(template = EMPTY, batch = BATCH + "_18", timeoutTicks = 40)
    public static void awakenedSentinelDropsRewards(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "research_building");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, stack("awakening_echo_core"));
        boss.skipEmerging();
        boss.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒しても保管庫が開かない");
            helper.assertTrue(dropped(helper, item("overclock_chip")) == 1, "オーバークロック・チップが出ない");
            helper.assertTrue(dropped(helper, item("sentinel_core")) == 4, "番人の投影核が通常の2＋追加2にならない: "
                    + dropped(helper, item("sentinel_core")));
            helper.succeed();
        });
    }

    /** 覚醒戦をやめて離れると、番人は戻り、覚醒核が封印核の上に返る。部屋の仕掛けは直さない（核に手が届くように）。 */
    @GameTest(template = EMPTY, batch = BATCH + "_19", timeoutTicks = 300)
    public static void abandonedAwakenedFightReturnsCore(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "culture_facility");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, stack("awakening_gravity_core"));
        boss.skipEmerging();
        // 挑戦者がいなくなっても、しばらく（3分）は残る。猶予を飛ばすと戻る
        helper.runAtTickTime(100, () -> {
            helper.assertFalse(boss.isRemoved(), "猶予の間に消えた");
            boss.skipResetGrace();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 100, "猶予を確かめる前");
            helper.assertTrue(boss.isRemoved(), "挑戦者がいないのに覚醒した番人が残っている");
            helper.assertTrue(core.activeBoss(helper.getLevel()) == null, "封印核が戦闘中のまま");
            helper.assertTrue(dropped(helper, item("awakening_gravity_core")) == 1, "覚醒核が返ってこない");
        });
    }

    // ------------------------------------------------------------------ エルゴ球の大蛇（沈んだ炉心区画）

    /** 沈んだ炉心区画の封印核は、エルゴ球の大蛇を出現の演出つきで起こす。体は12節で、出現のあいだは傷つかない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_20", timeoutTicks = 40)
    public static void coreWakesLeviathanForSunkenReactor(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "sunken_reactor", VAULT, CORE);
        RuinBoss boss = core.awaken(helper.getLevel());
        helper.assertTrue(boss instanceof ErgosphereLeviathan, "沈んだ炉心区画でエルゴ球の大蛇が現れない: " + boss);
        ErgosphereLeviathan l = (ErgosphereLeviathan) boss;
        helper.assertTrue(l.emergeTicks() == ErgosphereLeviathan.EMERGE_TICKS, "出現の演出が始まらない");
        helper.assertTrue(l.getParts().length == ErgosphereLeviathan.SEGMENTS, "体の節の数がおかしい: " + l.getParts().length);
        helper.assertFalse(l.segments()[3].hurt(helper.getLevel().damageSources().generic(), 50), "出現の途中で体が傷ついた");
        double health = ErgosphereLeviathan.HEALTH * ErgosphereLeviathan.healthFactor();
        helper.assertTrue(Math.abs(l.effectiveMaxHealth() - health) < 1.0, "体力が" + health + "相当でない: " + l.effectiveMaxHealth());
        l.discard();
        helper.succeed();
    }

    /** 体の節は、頭の通った跡に沿って決まった間隔で並ぶ。 */
    @GameTest(template = EMPTY, batch = BATCH + "_21", timeoutTicks = 80)
    public static void leviathanBodyFollowsHead(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "sunken_reactor", VAULT, CORE);
        ErgosphereLeviathan l = (ErgosphereLeviathan) core.awaken(helper.getLevel());
        l.skipEmerging();
        helper.runAtTickTime(60, () -> {
            net.minecraft.world.phys.Vec3 prev = l.headCenter();
            for (int i = 0; i < ErgosphereLeviathan.SEGMENTS; i++) {
                net.minecraft.world.phys.Vec3 c = l.segments()[i].position().add(0, ErgosphereLeviathan.radius(i), 0);
                double gap = c.distanceTo(prev);
                double want = i == 0 ? ErgosphereLeviathan.HEAD_GAP : ErgosphereLeviathan.SPACING;
                helper.assertTrue(gap <= want + 0.3 && gap >= want * 0.5, "節 " + i + " の間隔がおかしい: " + gap);
                prev = c;
            }
            l.discard();
            helper.succeed();
        });
    }

    /** 尾の光る芯は弱く（×1.6）、胴は硬い（×0.6）。 */
    @GameTest(template = EMPTY, batch = BATCH + "_22", timeoutTicks = 40)
    public static void leviathanTailIsWeakPoint(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "sunken_reactor", VAULT, CORE);
        ErgosphereLeviathan l = (ErgosphereLeviathan) core.awaken(helper.getLevel());
        l.skipEmerging();
        float before = l.getHealth();
        l.segments()[2].hurt(helper.getLevel().damageSources().magic(), 10);
        float body = before - l.getHealth();
        l.invulnerableTime = 0;
        before = l.getHealth();
        l.segments()[ErgosphereLeviathan.SEGMENTS - 1].hurt(helper.getLevel().damageSources().magic(), 10);
        float tail = before - l.getHealth();
        helper.assertTrue(body > 0 && Math.abs(tail / body - ErgosphereLeviathan.TAIL_FACTOR / ErgosphereLeviathan.BODY_FACTOR) < 0.2,
                "尾と胴のダメージの比がおかしい: 胴 " + body + " / 尾 " + tail);
        l.discard();
        helper.succeed();
    }

    /** 倒すと保管庫が開き、渦の断章を落とす。演出のあいだ体は残り、終わると消える。 */
    @GameTest(template = EMPTY, batch = BATCH + "_23", timeoutTicks = ErgosphereLeviathan.DEATH_TICKS + 30)
    public static void leviathanDefeatUnsealsVaultAndSinks(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "sunken_reactor", VAULT, CORE);
        ErgosphereLeviathan l = (ErgosphereLeviathan) core.awaken(helper.getLevel());
        l.skipEmerging();
        l.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(((RuinCacheBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, VAULT)).isSealed(), "倒しても保管庫が開かない");
            helper.assertTrue(dropped(helper, item("ergosphere_chapter")) == 1, "渦の断章を落とさない");
        });
        helper.runAtTickTime(ErgosphereLeviathan.DEATH_TICKS / 2, () -> helper.assertFalse(l.isRemoved(), "演出の途中で消えた"));
        helper.runAtTickTime(ErgosphereLeviathan.DEATH_TICKS + 10, () -> {
            helper.assertTrue(l.isRemoved(), "演出が終わっても消えない");
            helper.succeed();
        });
    }

    /** 覚醒の渦核で、覚醒したエルゴ球の大蛇（体力×2.5）が現れる。 */
    @GameTest(template = EMPTY, batch = BATCH + "_24", timeoutTicks = 40)
    public static void ergosphereCoreSummonsAwakenedLeviathan(GameTestHelper helper) {
        GuardianCoreBlockEntity core = clearedRoom(helper, "sunken_reactor");
        RuinBoss boss = core.awakenAwakened(helper.getLevel(), null, stack("awakening_ergosphere_core"));
        helper.assertTrue(boss instanceof ErgosphereLeviathan && boss.awakened(), "覚醒の渦核で覚醒した大蛇が現れない: " + boss);
        double expected = ErgosphereLeviathan.HEALTH * ErgosphereLeviathan.healthFactor() * RuinBoss.awakenedHealthMultiplier();
        helper.assertTrue(Math.abs(boss.effectiveMaxHealth() - expected) < 1.0, "体力が " + expected + " 相当でない: " + boss.effectiveMaxHealth());
        boss.discard();
        helper.succeed();
    }

    /** 体力 2/3 で脱皮して骨格になる。一撃で境目を飛び越えず、脱皮のあいだは傷つかない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_25", timeoutTicks = 100)
    public static void leviathanMoltsAtTwoThirds(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "sunken_reactor", VAULT, CORE);
        ErgosphereLeviathan l = (ErgosphereLeviathan) core.awaken(helper.getLevel());
        l.skipEmerging();
        l.hurt(helper.getLevel().damageSources().magic(), 100000);
        helper.assertTrue(l.isAlive() && l.getHealth() >= l.getMaxHealth() * 2F / 3 - 0.1F, "一撃で装甲の姿を飛び越えた: " + l.getHealth());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(l.state() == ErgosphereLeviathan.MOLT && l.transforming(), "脱皮が始まらない: " + l.state());
            l.invulnerableTime = 0;
            helper.assertFalse(l.hurt(helper.getLevel().damageSources().magic(), 10), "脱皮の最中に傷ついた");
        });
        helper.runAtTickTime(ErgosphereLeviathan.MOLT_TICKS + 6, () -> {
            helper.assertTrue(l.form() == 2 && !l.transforming(), "脱皮のあと骨格にならない: " + l.form());
            l.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 挑戦人数

    /** 挑戦者が増えると、体力の割合を保ったまま体力が増える。減っても弱くならない。 */
    @GameTest(template = EMPTY, batch = BATCH + "_26", timeoutTicks = 40)
    public static void guardianScalesWithChallengers(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "culture_facility", VAULT, CORE);
        RuinBoss boss = core.awaken(helper.getLevel());
        boss.skipEmerging();
        float base = boss.effectiveMaxHealth();
        boss.setHealth(boss.getMaxHealth() * 0.8F);
        boss.setParticipants(3);
        double expected = base * io.github.genichimaruo.singulo.ruin.BossScaling.healthFactor(3);
        helper.assertTrue(Math.abs(boss.effectiveMaxHealth() - expected) < 1.0, "3人で体力が " + expected + " 相当にならない: " + boss.effectiveMaxHealth());
        helper.assertTrue(Math.abs(boss.getHealth() / boss.getMaxHealth() - 0.8F) < 0.01F, "体力の割合が保たれない: " + boss.getHealth() / boss.getMaxHealth());
        helper.assertTrue(boss.participants() == 3, "人数を覚えていない");
        boss.setParticipants(1);
        helper.assertTrue(boss.participants() == 3 && Math.abs(boss.effectiveMaxHealth() - expected) < 1.0, "人が減って弱くなった");
        boss.discard();
        helper.succeed();
    }

    /** 大勢で倒すと、落とす物が人数ぶん出る。 */
    @GameTest(template = EMPTY, batch = BATCH + "_27", timeoutTicks = 40)
    public static void guardianDropsPerChallenger(GameTestHelper helper) {
        GuardianCoreBlockEntity core = room(helper, "research_building", VAULT, CORE);
        RuinBoss boss = core.awaken(helper.getLevel());
        boss.skipEmerging();
        boss.setParticipants(2);
        boss.kill();
        helper.runAtTickTime(5, () -> {
            int n = dropped(helper, item("sentinel_core"));
            helper.assertTrue(n == 2 * io.github.genichimaruo.singulo.ruin.BossScaling.lootRolls(2), "2人ぶんの番人の投影核が出ない: " + n);
            helper.succeed();
        });
    }
}
