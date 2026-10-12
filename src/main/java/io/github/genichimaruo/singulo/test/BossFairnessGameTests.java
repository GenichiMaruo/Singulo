package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.BossScaling;
import io.github.genichimaruo.singulo.ruin.LastArchivist;
import io.github.genichimaruo.singulo.ruin.RuinBoss;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ボス戦の後始末と、人数・報酬の数え方（PR #8 のレビューで見つかった問題の回帰テスト）。
 * サバイバルの挑戦者が要るので、本物の ServerPlayer をつないで使い、終わったら必ず外す
 * （makeMockServerPlayerInLevel は創造モード扱いで、挑戦者に数えられない）。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BossFairnessGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation TIDE_GRAVITY = Singulo.id("archivist_tide_gravity");
    private static final ResourceLocation TIDE_FALL = Singulo.id("archivist_tide_fall");

    private BossFairnessGameTests() {}

    /** 非公開の段階処理を呼ぶ（ServerLevel だけを受け取るもの）。 */
    private static void call(Object target, Class<?> owner, String name, GameTestHelper helper) {
        try {
            Method m = owner.getDeclaredMethod(name, ServerLevel.class);
            m.setAccessible(true);
            m.invoke(target, helper.getLevel());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** サバイバルの挑戦者を feet に立たせる（サーバーにつながった本物のプレイヤー）。 */
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
        helper.assertTrue(!p.isCreative() && p.isAlive(), "サバイバルの挑戦者を用意できない");
        return p;
    }

    private static void leave(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static LastArchivist archivist(GameTestHelper helper) {
        LastArchivist b = SinguloEntities.LAST_ARCHIVIST.get().create(helper.getLevel());
        b.awaken(helper.absolutePos(new BlockPos(3, 4, 3)));
        b.skipEmerging();
        Vec3 at = LastArchivist.spawnAt(b.home());
        b.setPos(at.x, at.y, at.z);
        return b;
    }

    private static double bodyDistance(ServerPlayer p, Vec3 c) {
        return p.position().add(0, p.getBbHeight() / 2, 0).distanceTo(c);
    }

    /** 重力潮汐を受けたあと部屋の外へ出れば、低重力・落下無効はすぐ外れる。ボスが消えたときも、離れた所にいる人から外れる。 */
    @GameTest(template = EMPTY, batch = "boss_fair_tide")
    public static void tideEffectsEndWhenLeavingOrWhenBossGoes(GameTestHelper helper) {
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(5, 0, 0));
        try {
            b.startTide(helper.getLevel());
            for (int i = 0; i < 42; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertTrue(p.getAttribute(Attributes.GRAVITY).hasModifier(TIDE_GRAVITY), "潮汐で低重力にならない");
            p.teleportTo(c.x + 50, c.y, c.z);
            call(b, LastArchivist.class, "tickTide", helper);
            helper.assertFalse(p.getAttribute(Attributes.GRAVITY).hasModifier(TIDE_GRAVITY), "部屋を出ても低重力が残る");
            helper.assertFalse(p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).hasModifier(TIDE_FALL), "部屋を出ても落下無効が残る");
            // もう一度受けてから遠くへ行き、そのままボスが消える
            p.teleportTo(c.x + 5, c.y, c.z);
            call(b, LastArchivist.class, "tickTide", helper);
            helper.assertTrue(p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).hasModifier(TIDE_FALL), "戻ると潮汐を受ける");
            p.teleportTo(c.x + 60, c.y, c.z);
            b.discard();
            helper.assertFalse(p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).hasModifier(TIDE_FALL), "ボスが消えても落下無効が残る");
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1e-6, "重力が戻らない: " + p.getAttributeValue(Attributes.GRAVITY));
        } finally {
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 力場が張られている間は、外にいる人に潮汐はかからない。 */
    @GameTest(template = EMPTY, batch = "boss_fair_tide_out")
    public static void tideSparesPlayersOutsideTheShield(GameTestHelper helper) {
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(16, -0.9, 0));
        try {
            call(b, LastArchivist.class, "raiseShield", helper);
            b.startTide(helper.getLevel());
            for (int i = 0; i < 42; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertFalse(p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).hasModifier(TIDE_FALL), "力場の外の人に潮汐がかかる");
            b.discard();
        } finally {
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 読み込み直しても、力場の内側（壁の近く）にいた参加者は外へ追い出されない。 */
    @GameTest(template = EMPTY, batch = "boss_fair_reload")
    public static void reloadKeepsParticipantsInside(GameTestHelper helper) {
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(14, -0.9, 0));
        try {
            call(b, LastArchivist.class, "raiseShield", helper);
            call(b, LastArchivist.class, "holdShield", helper);
            double before = bodyDistance(p, c);
            helper.assertTrue(before < 15, "用意した位置がすでに力場の外: " + before);
            CompoundTag tag = b.saveWithoutId(new CompoundTag());
            b.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            LastArchivist reloaded = archivist(helper);
            reloaded.load(tag);
            call(reloaded, LastArchivist.class, "holdShield", helper);
            double after = bodyDistance(p, c);
            helper.assertTrue(after < 15, "読み込み直したら外へ追い出された: " + before + " → " + after);
            reloaded.discard();
        } finally {
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 力場の外で待っている人は、強さにも数えず、戦いを続ける理由にもならない（中に誰もいなければ戦いは終わる）。 */
    @GameTest(template = EMPTY, batch = "boss_fair_outside")
    public static void outsidersNeitherScaleNorHoldTheFight(GameTestHelper helper) {
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer one = survivor(helper, c.add(18, 0, 0));
        ServerPlayer two = survivor(helper, c.add(-18, 0, 0));
        try {
            call(b, LastArchivist.class, "raiseShield", helper);
            for (int i = 0; i < 6; i++) {
                call(b, RuinBoss.class, "checkChallengers", helper);
            }
            helper.assertTrue(b.participants() == 1, "外の人が強さに数えられた: " + b.participants());
            helper.assertTrue(b.isRemoved(), "中に誰もいないのに戦いが終わらない");
        } finally {
            leave(helper, one, two);
        }
        helper.succeed();
    }

    /** 報酬の人数は戦いが始まったときの人数で決まる。終盤に合流しても、体力は増えるが報酬は増えない。 */
    @GameTest(template = EMPTY, batch = "boss_fair_rewards")
    public static void lateJoinersAddHealthButNotRewards(GameTestHelper helper) {
        var solo = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        solo.makeAwakened();
        solo.lockRewardParticipants();                       // ひとりで戦いを始めた
        solo.setHealth(solo.getMaxHealth() * 0.01F);
        solo.setParticipants(6);
        helper.assertTrue(solo.participants() == 6, "合流しても強さに数えない");
        helper.assertTrue(solo.rewardParticipants() == 1, "終盤の合流で報酬の人数が増えた: " + solo.rewardParticipants());
        solo.discard();
        var party = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        party.setParticipants(3);
        party.lockRewardParticipants();                      // 3人で戦いを始めた
        party.setParticipants(5);
        helper.assertTrue(party.rewardParticipants() == 3 && BossScaling.lootRolls(party.rewardParticipants()) == BossScaling.lootRolls(3),
                "始めた人数ぶんの報酬にならない: " + party.rewardParticipants());
        CompoundTag tag = party.saveWithoutId(new CompoundTag());
        var reloaded = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.assertTrue(reloaded.rewardParticipants() == 3, "読み込み直すと報酬の人数が変わる: " + reloaded.rewardParticipants());
        party.discard();
        reloaded.discard();
        helper.succeed();
    }

    /** 設定で許される高い体力と大人数でも、保存・読み込みのあとで硬さが変わらない（最後の記録者とホライズン・ウォーデン）。 */
    @GameTest(template = EMPTY, batch = "boss_fair_health")
    public static void highHealthSettingsSurviveReload(GameTestHelper helper) {
        int oldHealth = ServerConfig.ARCHIVIST_HEALTH.get();
        int oldMax = ServerConfig.BOSS_MAX_SCALED_PLAYERS.get();
        double oldPer = ServerConfig.BOSS_HEALTH_PER_PLAYER.get();
        try {
            ServerConfig.ARCHIVIST_HEALTH.set(100_000);
            ServerConfig.BOSS_MAX_SCALED_PLAYERS.set(32);
            ServerConfig.BOSS_HEALTH_PER_PLAYER.set(4.0);
            LastArchivist b = archivist(helper);
            b.setParticipants(32);
            double before = b.effectiveMaxHealth();
            LastArchivist reloaded = archivist(helper);
            reloaded.load(b.saveWithoutId(new CompoundTag()));
            double after = reloaded.effectiveMaxHealth();
            helper.assertTrue(before > 12_000_000 && Math.abs(after - before) / before < 1e-3, "読み込み直すと硬さが変わる: " + before + " → " + after);
            b.discard();
            reloaded.discard();
            var warden = SinguloEntities.HORIZON_WARDEN.get().create(helper.getLevel());
            warden.setParticipants(32);
            double wBefore = warden.effectiveMaxHealth();
            var wReloaded = SinguloEntities.HORIZON_WARDEN.get().create(helper.getLevel());
            wReloaded.load(warden.saveWithoutId(new CompoundTag()));
            helper.assertTrue(Math.abs(wReloaded.effectiveMaxHealth() - wBefore) / wBefore < 1e-3,
                    "ホライズン・ウォーデンの硬さが変わる: " + wBefore + " → " + wReloaded.effectiveMaxHealth());
            warden.discard();
            wReloaded.discard();
        } finally {
            ServerConfig.ARCHIVIST_HEALTH.set(oldHealth);
            ServerConfig.BOSS_MAX_SCALED_PLAYERS.set(oldMax);
            ServerConfig.BOSS_HEALTH_PER_PLAYER.set(oldPer);
        }
        helper.succeed();
    }

    /** 床の欠けた所をホログラムで埋めても、戦いが終われば元の水に戻る。読み込み直したあとでも戻る。 */
    @GameTest(template = EMPTY, batch = "boss_fair_floor")
    public static void temporaryFloorRestoresWater(GameTestHelper helper) {
        LastArchivist b = archivist(helper);
        BlockPos pos = b.home().offset(0, -3, 0);
        helper.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        call(b, LastArchivist.class, "fillFloor", helper);
        helper.assertFalse(helper.getLevel().getBlockState(pos).is(Blocks.WATER), "床で埋まらない");
        LastArchivist reloaded = archivist(helper);
        reloaded.load(b.saveWithoutId(new CompoundTag()));
        call(reloaded, LastArchivist.class, "clearFloor", helper);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.WATER), "元の水に戻らない: " + helper.getLevel().getBlockState(pos));
        b.discard();
        reloaded.discard();
        helper.succeed();
    }
}
