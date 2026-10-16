package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.reactor.EventHorizonTargets;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.BossCombatLog;
import io.github.genichimaruo.singulo.ruin.BossLoot;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan;
import io.github.genichimaruo.singulo.ruin.HorizonBolt;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ボスの報酬（落とし物・経験値）、光弾の保存、重力の裂け目の当たり、叩きつけの死亡記録（PR #12 のレビューの回帰テスト）。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BossRewardGameTests {
    private static final String EMPTY = "empty";

    private BossRewardGameTests() {}

    /** サバイバルの挑戦者を feet に立たせる（サーバーにつながった本物のプレイヤー。終わったら leave で外す）。 */
    private static ServerPlayer survivor(GameTestHelper helper, Vec3 feet) {
        var server = helper.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "challenger");
        ServerPlayer p = new ServerPlayer(server, helper.getLevel(), profile);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p);
        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(helper.getLevel(), feet.x, feet.y, feet.z, 0, 0);
        return p;
    }

    /** 入ってきたばかりのプレイヤーの3秒の無敵を外す（このテストのプレイヤーは tick しないので、待っても切れない）。 */
    private static void vulnerable(ServerPlayer p) {
        try {
            var f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void leave(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
    }

    private static EchoSentinel sentinel(GameTestHelper helper, BlockPos home) {
        EchoSentinel e = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        e.awaken(helper.absolutePos(home));
        e.skipEmerging();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(home)).add(0, 2, 0);
        e.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(e);
        return e;
    }

    /** ボスの落とし物は時間で消えず（無期限）、ほかのアイテムと結合しないので、ブラックホールよけの印も失わない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 80)
    public static void bossLootNeverDespawnsOrMerges(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        boss.setHealth(0);
        ItemEntity loot = boss.spawnAtLocation(new ItemStack(Items.DIAMOND), 0);
        helper.assertTrue(loot != null, "落とし物が出ない");
        helper.assertTrue(loot.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Age") == -32768, "落とし物が時間で消える（無期限でない）");
        ItemEntity ordinary = new ItemEntity(helper.getLevel(), loot.getX(), loot.getY(), loot.getZ(), new ItemStack(Items.DIAMOND, 10));
        helper.getLevel().addFreshEntity(ordinary);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!loot.isRemoved() && loot.getItem().getCount() == 1, "落とし物がほかのアイテムと結合した");
            helper.assertTrue(loot.getPersistentData().getBoolean(EventHorizonTargets.PROTECTED), "ブラックホールよけの印が消えた");
            helper.assertTrue(ordinary.getItem().getCount() == 10, "ふつうのアイテムに落とし物が混ざった: " + ordinary.getItem().getCount());
            boss.discard();
            loot.discard();
            ordinary.discard();
            helper.succeed();
        });
    }

    /** ボスの経験値は、部屋にいる挑戦者へ直接入る（オーブにならないので、時間で消えず、ブラックホールにも吸われない）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp", timeoutTicks = 40)
    public static void bossExperienceGoesStraightToChallengers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        ServerPlayer p = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 3))));
        try {
            int before = p.totalExperience;
            boss.setHealth(1);
            boss.hurt(helper.getLevel().damageSources().playerAttack(p), 1000);
            helper.assertTrue(boss.isDeadOrDying(), "倒れない");
            int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
            helper.assertTrue(p.totalExperience - before == want, "挑戦者に経験値が入らない: " + (p.totalExperience - before) + " / " + want);
            AABB around = new AABB(helper.absolutePos(new BlockPos(3, 1, 3))).inflate(12);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "経験値のオーブが出た");
        } finally {
            boss.discard();
            leave(helper, p);
        }
        helper.succeed();
    }

    /**
     * 経験値の合計は戦いの始めに決めた額のまま、部屋の挑戦者で等しく分ける（あとから増えた人がいても増えない。
     * 割り切れない分も消えない。クリエイティブは受け取らない）。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp_split", timeoutTicks = 40)
    public static void bossExperienceTotalIsSplitNotMultiplied(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        double oldMultiplier = ServerConfig.BOSS_XP_MULTIPLIER.get();
        ServerConfig.BOSS_XP_MULTIPLIER.set(1.002);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        boss.lockRewardParticipants();
        ServerPlayer a = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 3))));
        ServerPlayer b = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 3))));
        ServerPlayer creative = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 5))));
        creative.setGameMode(GameType.CREATIVE);
        try {
            int beforeA = a.totalExperience;
            int beforeB = b.totalExperience;
            int beforeC = creative.totalExperience;
            boss.setHealth(1);
            boss.hurt(helper.getLevel().damageSources().playerAttack(a), 1000);
            int gotA = a.totalExperience - beforeA;
            int gotB = b.totalExperience - beforeB;
            int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
            helper.assertTrue(want % 2 == 1, "割り切れない額になっていない: " + want);
            helper.assertTrue(gotA + gotB == want, "合計が決めた額と違う（人数で増えた・余りが消えた）: " + gotA + " + " + gotB + " / " + want);
            helper.assertTrue(Math.abs(gotA - gotB) <= 1, "等しく分けていない: " + gotA + " / " + gotB);
            helper.assertTrue(creative.totalExperience == beforeC, "クリエイティブが受け取った");
        } finally {
            ServerConfig.BOSS_XP_MULTIPLIER.set(oldMultiplier);
            boss.discard();
            leave(helper, a);
            leave(helper, b);
            leave(helper, creative);
        }
        helper.succeed();
    }

    /** 人数ぶんの報酬を切ると（bossLootPerPlayer=false）、3人で挑んでも経験値の合計は1人分（皆で分ける）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp_shared", timeoutTicks = 40)
    public static void bossExperienceIsSharedWithoutPerPlayerLoot(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        boolean oldPerPlayer = ServerConfig.BOSS_LOOT_PER_PLAYER.get();
        ServerConfig.BOSS_LOOT_PER_PLAYER.set(false);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        boss.setParticipants(3);
        boss.lockRewardParticipants();
        java.util.List<ServerPlayer> party = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                party.add(survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + 2 * i, 1, 5)))));
            }
            int before = party.stream().mapToInt(p -> p.totalExperience).sum();
            boss.setHealth(1);
            boss.hurt(helper.getLevel().damageSources().playerAttack(party.get(0)), 1000);
            int got = party.stream().mapToInt(p -> p.totalExperience).sum() - before;
            int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
            helper.assertTrue(got == want, "人数ぶんの報酬を切ったのに合計が増えた: " + got + " / " + want);
        } finally {
            ServerConfig.BOSS_LOOT_PER_PLAYER.set(oldPerPlayer);
            boss.discard();
            party.forEach(p -> leave(helper, p));
        }
        helper.succeed();
    }

    /** 直接入る経験値でも、修繕の付いた傷んだ装備が直る（オーブを拾ったときと同じ）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp_mending", timeoutTicks = 40)
    public static void bossExperienceRepairsMendingGear(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        ServerPlayer p = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 3))));
        try {
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
            chest.setDamageValue(200);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
            int before = p.totalExperience;
            boss.setHealth(1);
            boss.hurt(helper.getLevel().damageSources().playerAttack(p), 1000);
            ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            helper.assertTrue(worn.getDamageValue() < 200, "修繕で直らない: " + worn.getDamageValue());
            int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
            helper.assertTrue(p.totalExperience - before < want, "直した分の経験値が減っていない");
        } finally {
            boss.discard();
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 部屋に誰もいなければ、経験値は核の上にオーブで出る。ブラックホールよけの印が付き、ふつうより長く残る。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp_empty", timeoutTicks = 40)
    public static void bossExperienceInAnEmptyRoomIsProtected(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        ServerPlayer far = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))).add(60, 0, 0));
        try {
            boss.setHealth(1);
            boss.hurt(helper.getLevel().damageSources().playerAttack(far), 1000);
            AABB around = new AABB(helper.absolutePos(new BlockPos(3, 1, 3))).inflate(12);
            var orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around);
            helper.assertTrue(!orbs.isEmpty(), "誰もいないのにオーブが出ない");
            int total = 0;
            for (ExperienceOrb orb : orbs) {
                helper.assertTrue(orb.getPersistentData().getBoolean(EventHorizonTargets.PROTECTED), "オーブにブラックホールよけの印がない");
                helper.assertTrue(orb.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Age") < 0, "オーブがふつうの寿命のまま");
                total += orb.getValue();
                orb.discard();
            }
            int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
            helper.assertTrue(total == want, "オーブの合計が違う: " + total + " / " + want);
        } finally {
            boss.discard();
            leave(helper, far);
        }
        helper.succeed();
    }

    /** 誰もいないときのオーブの値は、ふつうのオーブの値と重ならない（同じ値どうししか結合しないので、取り込まれない）。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void protectedOrbValuesNeverMatchVanilla(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        for (int t = 1; t <= 100_000; t++) {
            helper.assertTrue(BossLoot.VANILLA_ORB_VALUES.contains(ExperienceOrb.getExperienceValue(t)),
                    "ふつうのオーブの値の一覧に抜けがある: " + ExperienceOrb.getExperienceValue(t));
        }
        for (int total = 1; total <= 20_000; total++) {
            int sum = 0;
            for (int value : BossLoot.protectedOrbValues(total)) {
                sum += value;
                helper.assertTrue(value > 0, "値が0以下: " + total);
                if (total != 1 && total != 3) {
                    helper.assertFalse(BossLoot.VANILLA_ORB_VALUES.contains(value), "ふつうのオーブと同じ値: " + total + " -> " + value);
                }
            }
            helper.assertTrue(sum == total, "合計が変わる: " + total + " -> " + sum);
        }
        helper.succeed();
    }

    /** 誰もいないときのオーブは、近くにふつうの経験値が出ても取り込まれず、保存し直しても印と長い寿命を保つ。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_xp_merge", timeoutTicks = 120)
    public static void protectedOrbsSurviveVanillaOrbsNearby(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        AABB around = new AABB(helper.absolutePos(new BlockPos(3, 1, 3))).inflate(16);
        // This fixture supplies its own XP: unrelated rewards are not part of the sum.
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around).forEach(ExperienceOrb::discard);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        ServerPlayer far = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))).add(60, 0, 0));
        boss.setHealth(1);
        boss.hurt(helper.getLevel().damageSources().playerAttack(far), 1000);
        leave(helper, far);
        boss.discard();
        int want = (int) Math.round(500 * io.github.genichimaruo.singulo.ruin.BossScaling.xpMultiplier());
        ExperienceOrb sample = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around,
                orb -> orb.getPersistentData().getBoolean(EventHorizonTargets.PROTECTED)).get(0);
        Vec3 at = sample.position();
        int vanilla = 0;
        // 保護されたオーブと近い値も含めて、ふつうの経験値を何度も出す
        for (int amount : new int[] {1, 2, 3, 5, 6, 7, 16, 17, 36, 37, 72, 73, 148, 149, 306, 307, 500}) {
            for (int i = 0; i < 3; i++) {
                ExperienceOrb.award(helper.getLevel(), at, amount);
                vanilla += amount;
            }
        }
        // Exercise vanilla proximity merging without testing random ballistic movement.
        // Values, counts, protected markers, ages and vanilla merge logic remain unchanged.
        for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around)) {
            orb.setNoGravity(true);
            orb.setDeltaMovement(Vec3.ZERO);
        }
        helper.assertTrue(helper.getLevel().players().stream().noneMatch(p -> p.position().distanceToSqr(at) < 16 * 16),
                "経験値の合流試験に回収できるプレイヤーが残っている");
        int vanillaTotal = vanilla;
        helper.runAtTickTime(80, () -> {
            int protectedXp = 0;
            int ordinaryXp = 0;
            for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around)) {
                CompoundTag tag = orb.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                int xp = orb.getValue() * Math.max(1, tag.getInt("Count"));
                if (orb.getPersistentData().getBoolean(EventHorizonTargets.PROTECTED)) {
                    protectedXp += xp;
                    helper.assertTrue(tag.getShort("Age") < 0, "保護されたオーブの寿命がふつうに戻った");
                    ExperienceOrb restored = new ExperienceOrb(net.minecraft.world.entity.EntityType.EXPERIENCE_ORB, helper.getLevel());
                    restored.load(tag);
                    helper.assertTrue(restored.getPersistentData().getBoolean(EventHorizonTargets.PROTECTED)
                            && restored.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Age") < 0, "保存し直すと印か寿命を失う");
                } else {
                    ordinaryXp += xp;
                }
                orb.discard();
            }
            helper.assertTrue(protectedXp == want, "保護されたオーブの合計が変わった（取り込まれた）: " + protectedXp + " / " + want);
            helper.assertTrue(ordinaryXp == vanillaTotal, "ふつうの経験値の合計が変わった: " + ordinaryXp + " / " + vanillaTotal
                    + " nearbyPlayers=" + helper.getLevel().players().stream().filter(p -> p.position().distanceToSqr(at) < 32 * 32).map(p -> p.getGameProfile().getName()).toList()
                    + " outside=" + helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around.inflate(32)).stream().map(orb -> orb.getValue() + "@" + orb.position()).toList());
            helper.succeed();
        });
    }

    /** 重力の裂け目は、予告の線の上（3本、長さ ±RIFT_REACH、床から4ブロックまで）にしか当たらない。向きを変えても同じ。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void riftOnlyHitsTheWarnedLines(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Vec3 c = new Vec3(0, 64, 0);
        for (double angle : new double[] {0, Math.PI / 3, 2 * Math.PI / 3}) {
            Vec3 along = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 across = new Vec3(-along.z, 0, along.x);
            for (int k = -1; k <= 1; k++) {
                Vec3 line = c.add(across.scale(k * ErgosphereLeviathan.RIFT_SPACING));
                helper.assertTrue(ErgosphereLeviathan.riftHits(c, angle, line.add(along.scale(ErgosphereLeviathan.RIFT_REACH - 0.3))),
                        "予告の線の端の手前で当たらない");
                helper.assertFalse(ErgosphereLeviathan.riftHits(c, angle, line.add(along.scale(ErgosphereLeviathan.RIFT_REACH + 1))),
                        "予告の線の先（通路など）で当たる");
                helper.assertFalse(ErgosphereLeviathan.riftHits(c, angle, line.add(along.scale(-ErgosphereLeviathan.RIFT_REACH - 1))),
                        "予告の線の反対側の先で当たる");
                helper.assertFalse(ErgosphereLeviathan.riftHits(c, angle, line.add(0, 5, 0)), "上の階で当たる");
                helper.assertFalse(ErgosphereLeviathan.riftHits(c, angle, line.add(0, -3, 0)), "下の階で当たる");
            }
            // 線と線の間は安全
            helper.assertFalse(ErgosphereLeviathan.riftHits(c, angle, c.add(across.scale(ErgosphereLeviathan.RIFT_SPACING / 2))),
                    "線と線の間で当たる");
        }
        helper.succeed();
    }

    /** 光弾は、ダメージと当たり方（防具で減るか）を保存し、読み込み直しても同じ強さで当たる。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void boltKeepsItsDamageAcrossSaves(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        HorizonBolt bolt = new HorizonBolt(helper.getLevel(), boss).style(0x7FE8FF, 0.7F, 7).armored(true);
        CompoundTag tag = bolt.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        HorizonBolt restored = SinguloEntities.HORIZON_BOLT.get().create(helper.getLevel());
        restored.load(tag);
        helper.assertTrue(restored.damage() == 7 && restored.armored(), "読み込み直した光弾の強さが変わる: " + restored.damage() + " / " + restored.armored());
        // 0.5.0 より前の保存（ダメージを持たない）は、既定の値と魔法のまま
        tag.remove("damage");
        tag.remove("armored");
        HorizonBolt old = SinguloEntities.HORIZON_BOLT.get().create(helper.getLevel());
        old.load(tag);
        helper.assertTrue(old.damage() == HorizonBolt.DAMAGE && !old.armored(), "古い保存の光弾の読み込み方が違う");
        boss.discard();
        bolt.discard();
        restored.discard();
        old.discard();
        helper.succeed();
    }

    /** 叩きつけの落下で倒れたとき、受けたダメージと死亡の両方が戦闘の記録に残る（それぞれ1行）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_reward_log", timeoutTicks = 40)
    public static void fatalSlamIsLoggedAsDeath(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        boss.setNoAi(true);
        ServerPlayer p = survivor(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 3))));
        vulnerable(p);
        helper.runAtTickTime(2, () -> {
            boolean old = ServerConfig.BOSS_COMBAT_LOG.get();
            ServerConfig.BOSS_COMBAT_LOG.set(true);
            Path log = FMLPaths.GAMEDIR.get().resolve("logs").resolve("singulo-combat.log");
            try {
                long hitsBefore = count(log, "hit_player", "test_slam");
                long deathsBefore = count(log, "player_died", "test_slam");
                p.setHealth(1);
                helper.assertTrue(ServerConfig.SPEC.isLoaded() && ServerConfig.BOSS_COMBAT_LOG.get(), "戦闘ログの設定が有効でない");
                helper.assertFalse(boss.isRemoved(), "記録する前にボスが試験領域から消えた");
                BossCombatLog.slammed(p, boss, "test_slam");
                p.hurt(helper.getLevel().damageSources().fall(), 4);
                helper.assertTrue(p.isDeadOrDying(), "落下で倒れない");
                helper.assertTrue(count(log, "hit_player", "test_slam") - hitsBefore == 1, "叩きつけの落下ダメージが記録されない");
                String lastHit = Files.readAllLines(log).stream().filter(line -> line.contains("\thit_player\t")
                        && line.contains("\ttest_slam\t")).reduce((a, b) -> b).orElseThrow();
                helper.assertTrue(lastHit.split("\t")[10].equals("0.0/20.0"), "戦闘ログの HP が被ダメージ前の値: " + lastHit);
                helper.assertTrue(count(log, "player_died", "test_slam") - deathsBefore == 1, "叩きつけの落下で倒れたのに死亡が記録されない");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            } finally {
                ServerConfig.BOSS_COMBAT_LOG.set(old);
                boss.discard();
                leave(helper, p);
            }
            helper.succeed();
        });
    }

    private static long count(Path log, String event, String attack) throws java.io.IOException {
        if (!Files.exists(log)) {
            return 0;
        }
        try (var lines = Files.lines(log)) {
            return lines.filter(l -> l.contains("\t" + event + "\t") && l.contains("\t" + attack + "\t")).count();
        }
    }
}
