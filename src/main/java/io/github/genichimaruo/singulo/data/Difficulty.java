package io.github.genichimaruo.singulo.data;

import io.github.genichimaruo.singulo.generated.ServerConfig;

/**
 * 難易度プリセット（設定 general.preset）。個別の設定値の上に、プリセットの倍率を掛けて実際の値にする。
 * <ul>
 *   <li>standard / custom: 個別の設定値をそのまま使う（custom は「自分で個別の値を決めた」ことを示す名札で、standard と同じ扱い）</li>
 *   <li>casual: ネザースター不要、触媒寿命×2、遺構の再生日数×0.5、ウォーデンHP×0.5、殻の圧縮量×0.5</li>
 *   <li>expert: ネザースター×2、触媒寿命×0.75、修復は2回まで、殻の圧縮量×1.5、点火エネルギー×2</li>
 * </ul>
 * サーバー設定を読んでいないとき（クライアントのメニュー画面など）は standard として扱う。
 */
public final class Difficulty {
    private Difficulty() {}

    public enum Preset { CASUAL, STANDARD, EXPERT, CUSTOM }

    public static Preset preset() {
        if (!ServerConfig.SPEC.isLoaded()) {
            return Preset.STANDARD;
        }
        return switch (ServerConfig.PRESET.get()) {
            case "casual" -> Preset.CASUAL;
            case "expert" -> Preset.EXPERT;
            case "custom" -> Preset.CUSTOM;
            default -> Preset.STANDARD;
        };
    }

    /** 触媒の寿命に掛ける倍率。 */
    public static double catalystLifetime() {
        return switch (preset()) {
            case CASUAL -> 2.0;
            case EXPERT -> 0.75;
            default -> 1.0;
        };
    }

    /** 遺構の再生日数に掛ける倍率。 */
    public static double regenDays() {
        return preset() == Preset.CASUAL ? 0.5 : 1.0;
    }

    /** ホライズン・ウォーデンの体力に掛ける倍率。 */
    public static double wardenHealth() {
        return preset() == Preset.CASUAL ? 0.5 : 1.0;
    }

    /** 修復・再復元できる回数。 */
    public static int maxRepairs(int configured) {
        return preset() == Preset.EXPERT ? Math.min(configured, 2) : configured;
    }

    /** 点火エネルギーに掛ける倍率。 */
    public static double ignitionEnergy() {
        return preset() == Preset.EXPERT ? 2.0 : 1.0;
    }

    /** ネザースターの必要数。 */
    public static int stars(int base) {
        return switch (preset()) {
            case CASUAL -> 0;
            case EXPERT -> base * 2;
            default -> base;
        };
    }

    /** 縮退物質殻の圧縮量（岩石Lv3・金属の核・冷却材）。0 だったものは 0 のまま、それ以外は1以上に丸める。 */
    public static int compression(int base) {
        double f = switch (preset()) {
            case CASUAL -> 0.5;
            case EXPERT -> 1.5;
            default -> 1.0;
        };
        return base <= 0 ? 0 : Math.max(1, (int) Math.round(base * f));
    }
}
