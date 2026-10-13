package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.reactor.EventHorizonTargets;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.BossCombatLog;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ボスの報酬（落とし物・経験値）、光弾の保存、重力の裂け目の当たり、叩きつけの死亡記録（PR #12 のレビューの回帰テスト）。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BossRewardGameTests {
    private static final String EMPTY = "empty";

    private BossRewardGameTests() {}

    /** サバイバルの挑戦者を feet に立たせる（サーバーにつながった本物のプレイヤー。終わったら leave で外す）。 */
    private static ServerPlayer survivor(GameTestHelper helper, Vec3 feet) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "challenger"), false);
        ServerPlayer p = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
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
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void bossLootNeverDespawnsOrMerges(GameTestHelper helper) {
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        boss.setHealth(0);
        ItemEntity loot = boss.spawnAtLocation(new ItemStack(Items.DIAMOND), 0);
        helper.assertTrue(loot != null, "落とし物が出ない");
        helper.assertTrue(loot.saveWithoutId(new CompoundTag()).getShort("Age") == -32768, "落とし物が時間で消える（無期限でない）");
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
    @GameTest(template = EMPTY, batch = "boss_reward_xp", timeoutTicks = 40)
    public static void bossExperienceGoesStraightToChallengers(GameTestHelper helper) {
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

    /** 重力の裂け目は、予告の線の上（3本、長さ ±RIFT_REACH、床から4ブロックまで）にしか当たらない。向きを変えても同じ。 */
    @GameTest(template = EMPTY)
    public static void riftOnlyHitsTheWarnedLines(GameTestHelper helper) {
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
    @GameTest(template = EMPTY)
    public static void boltKeepsItsDamageAcrossSaves(GameTestHelper helper) {
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
        HorizonBolt bolt = new HorizonBolt(helper.getLevel(), boss).style(0x7FE8FF, 0.7F, 7).armored(true);
        CompoundTag tag = bolt.saveWithoutId(new CompoundTag());
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
    @GameTest(template = EMPTY, batch = "boss_reward_log", timeoutTicks = 40)
    public static void fatalSlamIsLoggedAsDeath(GameTestHelper helper) {
        EchoSentinel boss = sentinel(helper, new BlockPos(3, 1, 3));
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
                BossCombatLog.slammed(p, boss, "test_slam");
                p.hurt(helper.getLevel().damageSources().fall(), 4);
                helper.assertTrue(p.isDeadOrDying(), "落下で倒れない");
                helper.assertTrue(count(log, "hit_player", "test_slam") - hitsBefore == 1, "叩きつけの落下ダメージが記録されない");
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
