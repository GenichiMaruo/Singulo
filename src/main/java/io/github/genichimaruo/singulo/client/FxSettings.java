package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.generated.ClientConfig;

/**
 * 目への負担を減らす設定（クライアント設定 flashIntensity・screenShake）。
 * 視界の広い範囲を白く染める閃光（ボスの出現と最期・創世炉の誕生・鍛造炉・P リアクター・画面の閃光）と画面の揺れに掛ける。
 * 攻撃の予告（光の線・陣）は読みやすさのため弱めない。
 */
final class FxSettings {
    private FxSettings() {}

    /** 閃光の強さ（0〜1）。 */
    static float flash() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.FLASH_INTENSITY.get().floatValue() : 1;
    }

    /** 閃光の不透明度 alpha（0〜255）に、設定の強さを掛ける。 */
    static int flash(int alpha) {
        return Math.round(alpha * flash());
    }

    /** 画面の揺れの強さ（0〜1）。 */
    static float shake() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.SCREEN_SHAKE.get().floatValue() : 1;
    }
}
