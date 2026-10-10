package io.github.genichimaruo.singulo.generated;

import net.minecraftforge.common.ForgeConfigSpec;

/** tools/gen_data.py が config_spec.py から生成。手で編集しない。 */
public final class ClientConfig {
    private ClientConfig() {}

    // ---- ClientConfig ----
    public static final ForgeConfigSpec.BooleanValue GRAVITATIONAL_LENSING;
    public static final ForgeConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS;
    public static final ForgeConfigSpec.BooleanValue DOPPLER_BEAMING;
    public static final ForgeConfigSpec.ConfigValue<String> LENSING_QUALITY;
    public static final ForgeConfigSpec.BooleanValue FORMATION_EFFECTS;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.BooleanValue GRAVITATIONAL_LENSING_;
        ForgeConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS_;
        ForgeConfigSpec.BooleanValue DOPPLER_BEAMING_;
        ForgeConfigSpec.ConfigValue<String> LENSING_QUALITY_;
        ForgeConfigSpec.BooleanValue FORMATION_EFFECTS_;
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
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
