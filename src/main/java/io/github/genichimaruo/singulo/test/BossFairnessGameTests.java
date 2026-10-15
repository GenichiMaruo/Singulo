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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "challenger");
        ServerPlayer p = new ServerPlayer(server, helper.getLevel(), profile);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p);
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
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_tide")
    public static void tideEffectsEndWhenLeavingOrWhenBossGoes(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(5, 0, 0));
        try {
            b.startTide(helper.getLevel());
            for (int i = 0; i < 42; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertTrue(p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_GRAVITY)) != null, "潮汐で低重力にならない");
            p.teleportTo(c.x + 50, c.y, c.z);
            call(b, LastArchivist.class, "tickTide", helper);
            helper.assertFalse(p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_GRAVITY)) != null, "部屋を出ても低重力が残る");
            helper.assertFalse(p.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_FALL)) != null, "部屋を出ても落下無効が残る");
            // もう一度受けてから遠くへ行き、そのままボスが消える
            p.teleportTo(c.x + 5, c.y, c.z);
            call(b, LastArchivist.class, "tickTide", helper);
            helper.assertTrue(p.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_FALL)) != null, "戻ると潮汐を受ける");
            p.teleportTo(c.x + 60, c.y, c.z);
            b.discard();
            helper.assertFalse(p.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_FALL)) != null, "ボスが消えても落下無効が残る");
            helper.assertTrue(Math.abs(p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()) - 0.08) < 1e-6, "重力が戻らない: " + p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()));
        } finally {
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 力場が張られている間は、外にいる人に潮汐はかからない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_tide_out")
    public static void tideSparesPlayersOutsideTheShield(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(16, -0.9, 0));
        try {
            call(b, LastArchivist.class, "raiseShield", helper);
            b.startTide(helper.getLevel());
            for (int i = 0; i < 42; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertFalse(p.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_FALL)) != null, "力場の外の人に潮汐がかかる");
            b.discard();
        } finally {
            leave(helper, p);
        }
        helper.succeed();
    }

    /** 読み込み直しても、力場の内側（壁の近く）にいた参加者は外へ追い出されない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_reload")
    public static void reloadKeepsParticipantsInside(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        ServerPlayer p = survivor(helper, c.add(14, -0.9, 0));
        try {
            call(b, LastArchivist.class, "raiseShield", helper);
            call(b, LastArchivist.class, "holdShield", helper);
            double before = bodyDistance(p, c);
            helper.assertTrue(before < 15, "用意した位置がすでに力場の外: " + before);
            CompoundTag tag = b.saveWithoutId(new net.minecraft.nbt.CompoundTag());
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
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_outside")
    public static void outsidersNeitherScaleNorHoldTheFight(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_rewards")
    public static void lateJoinersAddHealthButNotRewards(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
        CompoundTag tag = party.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var reloaded = SinguloEntities.ECHO_SENTINEL.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.assertTrue(reloaded.rewardParticipants() == 3, "読み込み直すと報酬の人数が変わる: " + reloaded.rewardParticipants());
        party.discard();
        reloaded.discard();
        helper.succeed();
    }

    /** 設定で許される高い体力と大人数でも、保存・読み込みのあとで硬さが変わらない（最後の記録者とホライズン・ウォーデン）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_health")
    public static void highHealthSettingsSurviveReload(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
            reloaded.load(b.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            double after = reloaded.effectiveMaxHealth();
            // 体力 100,000 × 通常の強さの倍率 × 32人ぶん（1 + 4 × 31）
            helper.assertTrue(before > 100_000 * LastArchivist.NORMAL_HEALTH_FACTOR * 120 && Math.abs(after - before) / before < 1e-3,
                    "読み込み直すと硬さが変わる: " + before + " → " + after);
            b.discard();
            reloaded.discard();
            var warden = SinguloEntities.HORIZON_WARDEN.get().create(helper.getLevel());
            warden.setParticipants(32);
            double wBefore = warden.effectiveMaxHealth();
            var wReloaded = SinguloEntities.HORIZON_WARDEN.get().create(helper.getLevel());
            wReloaded.load(warden.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
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
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_floor")
    public static void temporaryFloorRestoresWater(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        BlockPos pos = b.home().offset(0, -3, 0);
        helper.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        call(b, LastArchivist.class, "fillFloor", helper);
        helper.assertFalse(helper.getLevel().getBlockState(pos).is(Blocks.WATER), "床で埋まらない");
        LastArchivist reloaded = archivist(helper);
        reloaded.load(b.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        call(reloaded, LastArchivist.class, "clearFloor", helper);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.WATER), "元の水に戻らない: " + helper.getLevel().getBlockState(pos));
        b.discard();
        reloaded.discard();
        helper.succeed();
    }

    private static int wings(GameTestHelper helper, Vec3 at) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(12))
                .stream().filter(e -> e.getItem().is(io.github.genichimaruo.singulo.registry.SinguloItems.AFTERGLOW_WINGS.get()))
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** 最後の記録者の報酬（光輪・翼など）も、戦いが始まったときの人数で決まる。中へ戻ってきた人で強さは増えても、報酬は増えない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_hidden_rewards")
    public static void archivistRewardsUseTheLockedCount(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        try {
            players.add(survivor(helper, c.add(1, 0, 0)));
            call(b, LastArchivist.class, "raiseShield", helper);
            b.lockRewardParticipants();
            for (int i = 2; i <= 6; i++) {
                players.add(survivor(helper, c.add(i * 0.5, 0, 0)));     // 中でログインし直した人たち
            }
            call(b, LastArchivist.class, "holdShield", helper);
            call(b, RuinBoss.class, "checkChallengers", helper);
            helper.assertTrue(b.participants() == 6 && b.rewardParticipants() == 1, "人数の数え方が想定と違う: " + b.participants() + "/" + b.rewardParticipants());
            int before = wings(helper, b.position());
            call(b, LastArchivist.class, "dropRewards", helper);
            int n = wings(helper, b.position()) - before;
            helper.assertTrue(n == BossScaling.lootRolls(1), "始めた人数より多く翼が落ちた: " + n);
        } finally {
            b.discard();
            leave(helper, players.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    /** 潮汐が終わったあとの猶予の間に中へ来た人も、猶予が終われば落下無効が外れる。外へ出てボスが消えても残らない。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_tide_grace")
    public static void graceEntrantsAreReleasedToo(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist b = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(b.home());
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        try {
            players.add(survivor(helper, c.add(1, 0, 0)));
            call(b, LastArchivist.class, "raiseShield", helper);
            b.startTide(helper.getLevel());
            for (int i = 0; i < LastArchivist.TIDE_LIFT + LastArchivist.TIDE_LOW; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertTrue(b.tideTicks() == 0, "猶予の間に入っていない");
            ServerPlayer late = survivor(helper, c.add(2, 0, 0));
            players.add(late);
            call(b, LastArchivist.class, "holdShield", helper);
            call(b, LastArchivist.class, "tickTide", helper);
            helper.assertTrue(late.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()).getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(TIDE_FALL)) != null, "猶予の間に来た人に落下無効が付かない");
            for (int i = 0; i < LastArchivist.TIDE_GRACE + 2; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            helper.assertTrue(late.getAttributeValue(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()) == 1, "猶予が終わっても落下無効が残る");
            // もう一度、猶予の途中で付いた人が外へ出て、ボスが消える
            b.startTide(helper.getLevel());
            for (int i = 0; i < LastArchivist.TIDE_LIFT + LastArchivist.TIDE_LOW + 3; i++) {
                call(b, LastArchivist.class, "tickTide", helper);
            }
            late.teleportTo(helper.getLevel(), c.x + 50, c.y, c.z, 0, 0);
            b.discard();
            helper.assertTrue(late.getAttributeValue(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()) == 1, "外へ出てボスが消えても落下無効が残る");
        } finally {
            b.discard();
            leave(helper, players.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    /**
     * 中にいた人を保存していない以前の版のデータでは、誰もいないうちに力場の処理が動いても、
     * あとから中でログインしてきた人は中の人のまま（別々に戻ってきても）。歩いて外から来た人は入れない。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_legacy")
    public static void legacySaveAdoptsPlayersWhoLogInInside(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        LastArchivist original = archivist(helper);
        Vec3 c = LastArchivist.shieldCenter(original.home());
        call(original, LastArchivist.class, "raiseShield", helper);
        CompoundTag old = original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        old.remove("insiders");
        old.remove("legacy_insiders");
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LastArchivist loaded = archivist(helper);
        loaded.load(old);
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        try {
            call(loaded, LastArchivist.class, "holdShield", helper);     // まだ誰もいない
            ServerPlayer first = survivor(helper, c.add(14, -0.9, 0));
            players.add(first);
            call(loaded, LastArchivist.class, "holdShield", helper);
            helper.assertTrue(bodyDistance(first, c) < 15, "ログインして戻った人が外へ追い出された: " + bodyDistance(first, c));
            // 読み込み直しても移行の途中のまま（別の人があとから戻ってくる）
            LastArchivist again = archivist(helper);
            again.load(loaded.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            loaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            loaded = again;
            call(loaded, LastArchivist.class, "holdShield", helper);
            ServerPlayer second = survivor(helper, c.add(-14, -0.9, 0));
            players.add(second);
            call(loaded, LastArchivist.class, "holdShield", helper);
            helper.assertTrue(bodyDistance(second, c) < 15 && bodyDistance(first, c) < 15, "あとから戻った人が追い出された");
            // ずっと外にいた人（ログインしたばかりではない）は、入ろうとしても中に入れない
            ServerPlayer walker = survivor(helper, c.add(20, -0.9, 0));
            players.add(walker);
            walker.tickCount = LastArchivist.LOGIN_GRACE + 1;
            walker.teleportTo(helper.getLevel(), c.x + 14.5, c.y - 0.9, c.z + 1, 0, 0);
            call(loaded, LastArchivist.class, "holdShield", helper);
            helper.assertTrue(bodyDistance(walker, c) > 15, "外から来た人が中へ入れた: " + bodyDistance(walker, c));
        } finally {
            loaded.discard();
            leave(helper, players.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    /**
     * 無敵時間はプレイヤーごと。6人がそれぞれ毎秒1回殴ると36回すべて通り、人数で増えた体力（3.5倍）より
     * 攻撃の数（6倍）が多いので、ソロより速く削れる。共有の無敵時間だと12回しか通らず、ソロより遅かった。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_hurt_party")
    public static void partyHitsAreAcceptedPerPlayer(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Vec3 c = LastArchivist.shieldCenter(helper.absolutePos(new BlockPos(3, 4, 3)));
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        LastArchivist solo = archivist(helper);
        LastArchivist party = archivist(helper);
        try {
            for (int i = 0; i < 6; i++) {
                players.add(survivor(helper, c.add(i, 0, 0)));
            }
            solo.setNoAi(true);
            party.setNoAi(true);
            party.setParticipants(6);
            float startSolo = solo.getHealth();
            float startParty = party.getHealth();
            int soloAccepted = 0;
            int partyAccepted = 0;
            int partyAttempted = 0;
            for (int tick = 0; tick < 120; tick++) {
                solo.tick();
                party.tick();
                if (tick % 20 == 0 && solo.hurt(helper.getLevel().damageSources().playerAttack(players.get(0)), 20)) {
                    soloAccepted++;
                }
                for (int i = 0; i < 6; i++) {
                    if (tick % 20 == i * 20 / 6) {                     // 1秒の中に散らして殴る（0・3・6・10・13・16 tick）
                        partyAttempted++;
                        if (party.hurt(helper.getLevel().damageSources().playerAttack(players.get(i)), 20)) {
                            partyAccepted++;
                        }
                    }
                }
            }
            double soloLost = (startSolo - solo.getHealth()) / solo.getMaxHealth();
            double partyLost = (startParty - party.getHealth()) / party.getMaxHealth();
            helper.assertTrue(soloAccepted == 6, "ソロの攻撃が通らない: " + soloAccepted);
            helper.assertTrue(partyAccepted == partyAttempted, "6人の攻撃が全部は通らない: " + partyAccepted + "/" + partyAttempted);
            helper.assertTrue(partyLost > soloLost, "6人のほうがソロより遅く削れる: " + partyLost + " <= " + soloLost);
        } finally {
            solo.discard();
            party.discard();
            leave(helper, players.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    /** ひとりひとりにはバニラと同じ無敵時間がかかる（同じ人が0.5秒以内に同じ強さで殴っても通らない。強い攻撃は差の分だけ通る）。 */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_hurt_single")
    public static void eachPlayerKeepsTheirOwnHurtWindow(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Vec3 c = LastArchivist.shieldCenter(helper.absolutePos(new BlockPos(3, 4, 3)));
        LastArchivist b = archivist(helper);
        ServerPlayer one = survivor(helper, c.add(1, 0, 0));
        ServerPlayer two = survivor(helper, c.add(-1, 0, 0));
        try {
            b.setNoAi(true);
            var sources = helper.getLevel().damageSources();
            helper.assertTrue(b.hurt(sources.playerAttack(one), 10), "最初の一撃が通らない");
            for (int i = 0; i < 5; i++) {
                b.tick();
            }
            helper.assertFalse(b.hurt(sources.playerAttack(one), 10), "同じ人の同じ強さの連打が0.5秒以内に通った");
            helper.assertTrue(b.hurt(sources.playerAttack(two), 10), "別の人の攻撃が、他の人の無敵時間に止められた");
            float before = b.getHealth();
            helper.assertTrue(b.hurt(sources.playerAttack(one), 30), "強い攻撃が通らない");
            helper.assertTrue(b.getHealth() < before, "強い攻撃で体力が減らない");
            for (int i = 0; i < 10; i++) {
                b.tick();
            }
            helper.assertTrue(b.hurt(sources.playerAttack(one), 10), "無敵時間が終わっても通らない");
            // プレイヤー以外の攻撃は、プレイヤー以外どうしの共有の無敵時間で決まる（プレイヤーの攻撃には止められない）
            helper.assertTrue(b.hurt(sources.generic(), 5), "プレイヤーの攻撃のせいで、プレイヤー以外の攻撃が止められた");
            helper.assertFalse(b.hurt(sources.generic(), 5), "プレイヤー以外の攻撃が、その共有の無敵時間を無視した");
        } finally {
            b.discard();
            leave(helper, one, two);
        }
        helper.succeed();
    }

    /** 重力の澱（殻・防具なし、AIなし）。 */
    private static io.github.genichimaruo.singulo.ruin.GravityRemnant remnant(GameTestHelper helper, int shell) {
        var r = SinguloEntities.GRAVITY_REMNANT.get().create(helper.getLevel());
        BlockPos home = helper.absolutePos(new BlockPos(3, 4, 3));
        r.awaken(home);
        r.skipEmerging();
        r.setShell(shell);
        r.setNoAi(true);
        r.getAttribute(Attributes.ARMOR).setBaseValue(0);
        r.setPos(Vec3.atCenterOf(home));
        return r;
    }

    private static void ticks(net.minecraft.world.entity.Mob mob, int n) {
        for (int i = 0; i < n; i++) {
            mob.tick();
        }
    }

    /**
     * 殻の軽減は、無敵時間の中でも外でも同じ。同じ人の同じ強さの連打は通らず、殻も減らない。
     * 強い連撃は軽減したあとの差だけ通り、殻は剥がれない。別の人の一撃は、それぞれ殻を1枚剥がす。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_shell")
    public static void shellMitigationCannotBeSkippedByRepeating(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Vec3 c = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 4, 3)));
        ServerPlayer a = survivor(helper, c.add(2, 0, 0));
        ServerPlayer b = survivor(helper, c.add(-2, 0, 0));
        var r = remnant(helper, 8);
        var sources = helper.getLevel().damageSources();
        try {
            float start = r.getHealth();
            helper.assertTrue(r.hurt(sources.playerAttack(a), 20), "最初の一撃が通らない");
            helper.assertTrue(Math.abs(start - r.getHealth() - 6) < 1e-4 && r.shell() == 7, "殻の軽減か剥離がおかしい: "
                    + (start - r.getHealth()) + " / " + r.shell());
            float after = r.getHealth();
            helper.assertFalse(r.hurt(sources.playerAttack(a), 20), "同じ人の同じ強さの連打が通った");
            helper.assertTrue(r.getHealth() == after && r.shell() == 7, "通らなかった連打で体力か殻が減った");
            helper.assertTrue(r.hurt(sources.playerAttack(a), 40), "強い連撃が通らない");
            helper.assertTrue(Math.abs(after - r.getHealth() - 6) < 1e-4, "強い連撃が軽減後の差（6）にならない: " + (after - r.getHealth()));
            helper.assertTrue(r.shell() == 7, "無敵時間の中の連撃で殻が剥がれた");
            helper.assertTrue(r.hurt(sources.playerAttack(b), 20) && r.shell() == 6, "別の人の一撃で殻が剥がれない");
            ticks(r, 10);
            helper.assertTrue(r.hurt(sources.playerAttack(a), 20) && r.shell() == 5, "無敵時間のあとの一撃で殻が剥がれない");
        } finally {
            r.discard();
            leave(helper, a, b);
        }
        helper.succeed();
    }

    /**
     * プレイヤーの攻撃は、プレイヤー以外の攻撃の無敵時間（強さ・残り時間）を書き換えない。
     * A: 環境の30のあとにプレイヤーの20が通っても、同じ環境の30はまだ通らない。
     * B: プレイヤーたちの攻撃が続いても、環境の無敵時間は環境の一撃からの時間だけで減っていく。
     */
    @GameTest(setupTicks = 20, template = EMPTY, batch = "boss_fair_shared_window")
    public static void playerHitsLeaveTheSharedWindowAlone(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Vec3 c = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 4, 3)));
        ServerPlayer a = survivor(helper, c.add(2, 0, 0));
        ServerPlayer b = survivor(helper, c.add(-2, 0, 0));
        var sources = helper.getLevel().damageSources();
        var first = remnant(helper, 0);
        var second = remnant(helper, 0);
        try {
            helper.assertTrue(first.hurt(sources.generic(), 30), "環境の一撃が通らない");
            ticks(first, 2);
            helper.assertTrue(first.hurt(sources.playerAttack(a), 20), "プレイヤーの一撃が通らない");
            float before = first.getHealth();
            helper.assertFalse(first.hurt(sources.generic(), 30), "プレイヤーの一撃のあと、同じ強さの環境の攻撃が通った");
            helper.assertTrue(first.getHealth() == before, "環境の攻撃で体力が減った");
            // B
            helper.assertTrue(second.hurt(sources.generic(), 30), "環境の一撃が通らない");
            ticks(second, 2);
            helper.assertTrue(second.hurt(sources.playerAttack(a), 10), "Aの一撃が通らない");
            ticks(second, 3);
            helper.assertTrue(second.hurt(sources.playerAttack(b), 10), "Bの一撃が通らない");
            ticks(second, 4);
            helper.assertTrue(second.hurt(sources.playerAttack(a), 30), "Aの強い一撃が通らない");
            helper.assertTrue(second.invulnerableTime == 11, "環境の無敵時間が書き換わった: " + second.invulnerableTime);
            helper.assertFalse(second.hurt(sources.generic(), 30), "環境の無敵時間が短くなった");
        } finally {
            first.discard();
            second.discard();
            leave(helper, a, b);
        }
        helper.succeed();
    }
}
