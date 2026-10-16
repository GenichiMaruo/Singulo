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
    public static final ModConfigSpec.BooleanValue ANDROID_WORK_ICONS;
    public static final ModConfigSpec.ConfigValue<String> WORK_ORDER_SORT;
    public static final ModConfigSpec.BooleanValue WORK_ORDER_SORT_REVERSED;
    public static final ModConfigSpec.BooleanValue WORK_ORDER_JEI_SYNC;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.BooleanValue GRAVITATIONAL_LENSING_;
        ModConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS_;
        ModConfigSpec.BooleanValue DOPPLER_BEAMING_;
        ModConfigSpec.ConfigValue<String> LENSING_QUALITY_;
        ModConfigSpec.BooleanValue FORMATION_EFFECTS_;
        ModConfigSpec.BooleanValue ANDROID_WORK_ICONS_;
        ModConfigSpec.ConfigValue<String> WORK_ORDER_SORT_;
        ModConfigSpec.BooleanValue WORK_ORDER_SORT_REVERSED_;
        ModConfigSpec.BooleanValue WORK_ORDER_JEI_SYNC_;
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
        b.comment("作業中のアンドロイドの頭の上に、今していること（取りに行く・作る・採掘 など）のアイコンを出す（測量メガネを掛けているときだけ見える）", "範囲: true / false");
        ANDROID_WORK_ICONS_ = b.define("androidWorkIcons", true);
        b.pop();
        b.push("gui");
        b.comment("アンドロイド作業指令台の注文タブの並び（default: 指令台の並びのまま / name: 名前順 / count: 在庫の多い順 / mod: mod ごと / craftable: 作れる物を先に）。画面のボタンでも変えられる", "範囲: \"default\" / \"name\" / \"count\" / \"mod\" / \"craftable\"");
        WORK_ORDER_SORT_ = b.defineInList("workOrderSort", "default", java.util.Arrays.asList("default", "name", "count", "mod", "craftable"));
        b.comment("注文タブの並びを逆にする（画面のボタンでも変えられる）", "範囲: true / false");
        WORK_ORDER_SORT_REVERSED_ = b.define("workOrderSortReversed", false);
        b.comment("注文タブの検索欄を JEI の検索欄と連携する（JEI を入れているときだけ。画面のボタンでも変えられる）", "範囲: true / false");
        WORK_ORDER_JEI_SYNC_ = b.define("workOrderJeiSync", false);
        b.pop();
        GRAVITATIONAL_LENSING = GRAVITATIONAL_LENSING_;
        LENSING_WITH_SHADER_PACKS = LENSING_WITH_SHADER_PACKS_;
        DOPPLER_BEAMING = DOPPLER_BEAMING_;
        LENSING_QUALITY = LENSING_QUALITY_;
        FORMATION_EFFECTS = FORMATION_EFFECTS_;
        ANDROID_WORK_ICONS = ANDROID_WORK_ICONS_;
        WORK_ORDER_SORT = WORK_ORDER_SORT_;
        WORK_ORDER_SORT_REVERSED = WORK_ORDER_SORT_REVERSED_;
        WORK_ORDER_JEI_SYNC = WORK_ORDER_JEI_SYNC_;
        SPEC = b.build();
    }
}
