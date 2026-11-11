package io.github.genichimaruo.singulo.multiblock;

/**
 * コラプサー内張り: 加工をするマルチブロック（縮退圧縮炉・C空洞・極低温冷却塔）の外装板の位置に置ける、コラプサー材の板。
 * 1枚ごとに加工が {@link #PER_LINING} 速くなる（{@link #MAX} 枚まで、最大2倍）。外装板の位置ならどこでもよく、
 * マルチブロック搬入出ポートと同じく形の判定は外装板として扱う。速くなっても1回の加工の電力は同じ（毎 tick の電力が増える）。
 * <p>
 * 置ける位置はホロ投影機で金色の枠として示す（形がそろったあとも投影を続け、内張りの案内に切り替わる）。
 */
public final class CollapsarLining {
    /** 1枚ごとの速さの上乗せと、効く枚数の上限。 */
    public static final double PER_LINING = 0.05;
    public static final int MAX = 20;

    private CollapsarLining() {}

    /** 内張り n 枚の速さの倍率（1.0〜2.0）。 */
    public static double factor(int linings) {
        return 1 + PER_LINING * Math.max(0, Math.min(MAX, linings));
    }
}
