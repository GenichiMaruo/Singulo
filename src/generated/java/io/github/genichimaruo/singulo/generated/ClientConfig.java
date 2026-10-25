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
    public static final ForgeConfigSpec.BooleanValue ANDROID_WORK_ICONS;
    public static final ForgeConfigSpec.ConfigValue<String> WORK_ORDER_SORT;
    public static final ForgeConfigSpec.BooleanValue WORK_ORDER_SORT_REVERSED;
    public static final ForgeConfigSpec.BooleanValue WORK_ORDER_JEI_SYNC;
    public static final ForgeConfigSpec.IntValue WORK_ORDER_ROWS;
    public static final ForgeConfigSpec.IntValue SELECTOR_ROWS;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.BooleanValue GRAVITATIONAL_LENSING_;
        ForgeConfigSpec.BooleanValue LENSING_WITH_SHADER_PACKS_;
        ForgeConfigSpec.BooleanValue DOPPLER_BEAMING_;
        ForgeConfigSpec.ConfigValue<String> LENSING_QUALITY_;
        ForgeConfigSpec.BooleanValue FORMATION_EFFECTS_;
        ForgeConfigSpec.BooleanValue ANDROID_WORK_ICONS_;
        ForgeConfigSpec.ConfigValue<String> WORK_ORDER_SORT_;
        ForgeConfigSpec.BooleanValue WORK_ORDER_SORT_REVERSED_;
        ForgeConfigSpec.BooleanValue WORK_ORDER_JEI_SYNC_;
        ForgeConfigSpec.IntValue WORK_ORDER_ROWS_;
        ForgeConfigSpec.IntValue SELECTOR_ROWS_;
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
        b.comment("アンドロイド作業指令台の注文タブの物の一覧の行数。6行以上にすると、注文タブではプレイヤーのインベントリを隠してその場所も使う（画面の下の段のボタンでも変えられる）", "範囲: 3〜10（整数）");
        WORK_ORDER_ROWS_ = b.defineInRange("workOrderRows", 5, 3, 10);
        b.comment("装置の「作るもの」を選ぶ板の行数。板はインベントリの見出しの上から上へ伸び、画面に収まる行数までになる（板の右上のボタンでも変えられる）", "範囲: 2〜8（整数）");
        SELECTOR_ROWS_ = b.defineInRange("selectorRows", 3, 2, 8);
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
        WORK_ORDER_ROWS = WORK_ORDER_ROWS_;
        SELECTOR_ROWS = SELECTOR_ROWS_;
        SPEC = b.build();
    }
}
