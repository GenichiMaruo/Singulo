package io.github.genichimaruo.singulo.ruin;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

/**
 * ボス（遺構の番人・ホライズン・ウォーデン、その分身も）に共通のポーションの決まり。
 * <ul>
 *   <li>悪い効果（毒・衰弱・鈍化・ウィザーなど）は受けない（{@link #accepts}）</li>
 *   <li>負傷のポーションも即時回復のポーションも、どちらも回復になる（{@code HealOrHarmMobEffectMixin}）。
 *       負傷は効果としては付けられるようにしておき（矢の効果がここを通る）、効き目で回復に変える</li>
 * </ul>
 */
public final class BossEffects {
    private BossEffects() {}

    public static boolean isBoss(Entity entity) {
        return entity instanceof RuinBoss || entity instanceof HorizonWarden;
    }

    /** ボスの canBeAffected: 悪い効果は断る。負傷だけは通して、効き目で回復にする。 */
    public static boolean accepts(MobEffectInstance effect) {
        return effect.getEffect().getCategory() != MobEffectCategory.HARMFUL || effect.getEffect() == MobEffects.HARM;
    }

    /** 即時回復と同じ回復量（強さ amplifier、近さ proximity 0〜1）。 */
    public static float healing(int amplifier, double proximity) {
        return (int) (proximity * (double) Math.max(4 << amplifier, 0) + 0.5);
    }
}
