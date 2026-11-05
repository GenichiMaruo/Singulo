package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import net.minecraft.util.Mth;

/**
 * 降着ジェット鍛造炉の演出の進み（クライアント）。段階と段階の中の tick から、各部の明るさ・進み具合を 0〜1 で出す。
 * 描画（JetForgeRenderer）・画面の閃光と揺れ・音（JetForgeEffects）が同じ値を使う。
 *
 * @param phase   段階（JetForgeBlockEntity の IDLE〜COOL）
 * @param t       段階の中の経過（tick。部分 tick を含む）
 * @param prime   予告の進み（警告灯・力場の膜・素材が浮かぶ）
 * @param power   拘束コイルと降着円盤の明るさ
 * @param jet     ジェットの強さ（点火で伸び、仕上げで切れる）
 * @param focus   収束の進み（3つのリングが順に入る）
 * @param irr     照射の進み
 * @param heat    素材の熱（赤熱 → 白熱。冷却で下がる）
 * @param crack   亀裂の広がり
 * @param shatter 破片と光の輪の強さ
 * @param finish  仕上げに入ってからの tick（仕上げでなければ −1）
 * @param cool    冷却の進み
 * @param stalled 電力不足などで止まっている（ビームがちらつく）
 */
record JetForgeTimeline(int phase, float t, float prime, float power, float jet, float focus, float irr, float heat, float crack,
                        float shatter, float finish, float cool, boolean stalled) {
    static final JetForgeTimeline IDLE = new JetForgeTimeline(JetForgeBlockEntity.IDLE, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, false);

    static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 収束リング i（0〜2）の入り具合。 */
    float ring(int i) {
        return switch (phase) {
            case JetForgeBlockEntity.FOCUS -> ease((focus - i / 3F) * 3);
            case JetForgeBlockEntity.IRRADIATE -> 1;
            case JetForgeBlockEntity.FINISH -> 1 - Mth.clamp(t / 30F, 0, 1);
            default -> 0;
        };
    }

    /** 動いているか（待機中でない）。 */
    boolean active() {
        return phase != JetForgeBlockEntity.IDLE;
    }

    static JetForgeTimeline of(JetForgeBlockEntity be, float partialTick) {
        int phase = be.phase();
        if (phase == JetForgeBlockEntity.IDLE) {
            return IDLE;
        }
        boolean stalled = be.hold() != JetForgeBlockEntity.OK;
        float length = Math.max(1, be.duration(phase));
        float t = Math.min(length, be.phaseTicks() + (stalled ? 0 : partialTick));
        float k = t / length;
        float prime = phase == JetForgeBlockEntity.PRIME ? k : 1;
        float power;
        float jet = 0;
        float focus = 0;
        float irr = 0;
        float heat = 0;
        float cool = 0;
        float finish = -1;
        switch (phase) {
            case JetForgeBlockEntity.PRIME -> power = 0.1F * k;
            case JetForgeBlockEntity.IGNITE -> {
                power = 0.1F + 0.9F * ease(k);
                jet = ease((t - 15) / 65F);
                // ジェットが出始めると、素材はもう赤く焼け始める
                heat = 0.3F * ease(k);
            }
            case JetForgeBlockEntity.FOCUS -> {
                power = 1;
                jet = 1;
                focus = k;
                heat = 0.3F + 0.15F * k;
            }
            case JetForgeBlockEntity.IRRADIATE -> {
                power = 1;
                jet = 1;
                focus = 1;
                irr = k;
                heat = 0.45F + 0.55F * ease(k / 0.6F);
            }
            case JetForgeBlockEntity.FINISH -> {
                power = 1 - 0.6F * k;
                jet = Math.max(0, 1 - t / 12F);
                focus = 1;
                irr = 1;
                heat = 1.3F - 0.3F * k;
                finish = t;
            }
            default -> {
                // 冷却
                power = 0.4F * (1 - k);
                irr = 1;
                cool = k;
                heat = (float) Math.pow(1 - k, 1.4);
            }
        }
        float crack = phase >= JetForgeBlockEntity.IRRADIATE ? ease((irr - 0.25F) / 0.3F) : 0;
        float shatter = phase == JetForgeBlockEntity.IRRADIATE ? ease((irr - 0.5F) / 0.15F) * (0.6F + 0.4F * irr) : 0;
        return new JetForgeTimeline(phase, t, prime, power, jet, focus, irr, heat, crack, shatter, finish, cool, stalled);
    }
}
