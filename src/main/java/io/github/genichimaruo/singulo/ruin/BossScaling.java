package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 挑戦人数に応じたボスの強さ。部屋にいる挑戦者（生きていて、創造・観戦モードでないプレイヤー）を数え、
 * <ul>
 *   <li>体力: 2人目から1人ごとに、もとの体力の bossHealthPerPlayer 倍（既定 0.5）ずつ増える</li>
 *   <li>技の間: 1人ごとに bossPacePerPlayer（既定 0.08）ずつ縮む（0.6 倍まで）</li>
 *   <li>報酬: bossLootPerPlayer なら、落とす物を挑戦者の人数ぶん出す（隠しボスの断章をそれぞれが集められるように）</li>
 * </ul>
 * 数えるのは bossMaxScaledPlayers 人まで。戦いの途中で増えたら、その時の体力の割合を保ったまま強くなる。
 * 減っても弱くはならない（抜けて弱らせる手を防ぐ）。
 */
public final class BossScaling {
    private BossScaling() {}

    public static double healthPerPlayer() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.BOSS_HEALTH_PER_PLAYER.get() : 0.5;
    }

    public static double pacePerPlayer() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.BOSS_PACE_PER_PLAYER.get() : 0.08;
    }

    public static int maxPlayers() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.BOSS_MAX_SCALED_PLAYERS.get() : 6;
    }

    public static boolean lootPerPlayer() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.BOSS_LOOT_PER_PLAYER.get();
    }

    /** ボスのハードモード（設定 bossHardMode）。0.4.1 までの強さ（残響の番人の魔法が防具を貫く）。 */
    public static boolean hardMode() {
        return ServerConfig.SPEC.isLoaded() && ServerConfig.BOSS_HARD_MODE.get();
    }

    /** ボスの経験値の倍率（設定 bossXpMultiplier）。 */
    public static double xpMultiplier() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.BOSS_XP_MULTIPLIER.get() : 1.0;
    }

    /** 倒したときに出る経験値の合計: 1人分 × 報酬に数える人数 × 倍率（部屋の挑戦者で等しく分ける）。 */
    public static int xp(int perPlayer, int players) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round((double) perPlayer * lootRolls(players) * xpMultiplier()));
    }

    /** 強さに数える人数（1〜bossMaxScaledPlayers）。 */
    public static int clamp(int players) {
        return Mth.clamp(players, 1, Math.max(1, maxPlayers()));
    }

    /** 体力の倍率。 */
    public static double healthFactor(int players) {
        return 1 + healthPerPlayer() * (clamp(players) - 1);
    }

    /** 技の間の倍率（人数が多いほど短い。0.6 倍まで）。 */
    public static float paceFactor(int players) {
        return (float) Math.max(0.6, 1 / (1 + pacePerPlayer() * (clamp(players) - 1)));
    }

    /** 落とす物を何回出すか。 */
    public static int lootRolls(int players) {
        return lootPerPlayer() ? clamp(players) : 1;
    }

    /**
     * 保存してあった「受けるダメージの倍率」を確かめる。正の有限値ならそのまま使う（高い体力の設定と大人数では、
     * とても小さい値になる）。壊れた値（0以下・NaN・無限大）だけ 1 に戻す。
     */
    public static float validDamageScale(float saved) {
        return Float.isFinite(saved) && saved > 0 ? Math.min(saved, 1F) : 1F;
    }

    /** center のまわり radius の中の挑戦者の数。 */
    public static int count(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(radius),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).size();
    }

    /** ボスバーの名前（2人以上なら人数を添える）。 */
    public static Component barName(Component base, int players) {
        return players > 1 ? Component.translatable("gui.singulo.boss.party", base, players) : base;
    }
}
