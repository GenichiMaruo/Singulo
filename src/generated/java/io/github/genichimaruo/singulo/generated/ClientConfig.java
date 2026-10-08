package io.github.genichimaruo.singulo.generated;

import net.neoforged.neoforge.common.ModConfigSpec;

/** tools/gen_data.py が config_spec.py から生成。手で編集しない。 */
public final class ClientConfig {
    private ClientConfig() {}

    // ---- ClientConfig ----
    public static final ModConfigSpec.BooleanValue GRAVITATIONAL_LENSING;
    public static final ModConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS;
    public static final ModConfigSpec.BooleanValue DOPPLER_BEAMING;
    public static final ModConfigSpec.ConfigValue<String> LENSING_QUALITY;
    public static final ModConfigSpec.BooleanValue FORMATION_EFFECTS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.BooleanValue GRAVITATIONAL_LENSING_;
        ModConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS_;
        ModConfigSpec.BooleanValue DOPPLER_BEAMING_;
        ModConfigSpec.ConfigValue<String> LENSING_QUALITY_;
        ModConfigSpec.BooleanValue FORMATION_EFFECTS_;
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("render");
        b.comment("重力レンズの歪み表示", "範囲: true / false");
        GRAVITATIONAL_LENSING_ = b.define("gravitationalLensing", true);
        b.comment("Iris系シェーダーパック使用時に専用の歪み処理を使うか（falseならフォールバック表示）", "範囲: true / false");
        LENSING_WITH_SHADER_PACKS_ = b.define("lensingWithShaderPacks", true);
        b.comment("降着円盤のドップラー・ビーミング", "範囲: true / false");
        DOPPLER_BEAMING_ = b.define("dopplerBeaming", true);
        b.comment("歪み処理の解像度", "範囲: \"low\" / \"medium\" / \"high\"");
        LENSING_QUALITY_ = b.defineInList("lensingQuality", "high", java.util.Arrays.asList("low", "medium", "high"));
        b.comment("マルチブロック形成時の光の演出", "範囲: true / false");
        FORMATION_EFFECTS_ = b.define("formationEffects", true);
        b.pop();
        GRAVITATIONAL_LENSING = GRAVITATIONAL_LENSING_;
        LENSING_WITH_SHADER_PACKS = LENSING_WITH_SHADER_PACKS_;
        DOPPLER_BEAMING = DOPPLER_BEAMING_;
        LENSING_QUALITY = LENSING_QUALITY_;
        FORMATION_EFFECTS = FORMATION_EFFECTS_;
        SPEC = b.build();
    }
}
