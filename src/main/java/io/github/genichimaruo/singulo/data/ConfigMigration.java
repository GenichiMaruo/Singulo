package io.github.genichimaruo.singulo.data;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * サーバー設定ファイルの移行。0.3.0 の設定ファイルには、当時の既定値（縮退熱炉 20 MFE/t・殻の金属の核2個・炉殻12枚）が
 * 書き込まれているので、新しい既定値に変えても既存のワールドには効かない。
 * <p>
 * settingsVersion がこの版より古いファイルだけを一度だけ見て、値が<b>0.3.0 の既定値とまったく同じ</b>項目を新しい既定値へ
 * 書き換える。それ以外の値（自分で変えた値）は残す。書き換えたら settingsVersion を上げて保存するので、そのあとに
 * 改めて旧既定と同じ値を書いても、もう書き換えない。
 */
public final class ConfigMigration {
    private ConfigMigration() {}

    /** 今の設定ファイルの版。 */
    public static final int CURRENT = 1;

    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC || ServerConfig.SETTINGS_VERSION.get() >= CURRENT) {
            return;
        }
        migrate(ServerConfig.DEGENERATE_FURNACE_OUTPUT, 20_000_000, "degenerateFurnaceOutput");
        migrate(ServerConfig.SHELL_METAL_CORES, 2, "shellMetalCores");
        migrate(ServerConfig.REACTOR_PLATES_PER_SHELL, 12, "reactorPlatesPerShell");
        ServerConfig.SETTINGS_VERSION.set(CURRENT);
        try {
            ServerConfig.SPEC.save();
        } catch (RuntimeException e) {
            // サーバーから受け取った設定（マルチプレイのクライアント側）は保存できない。値の書き換えだけで十分
            Singulo.LOGGER.debug("Singulo: server config not saved after migration", e);
        }
    }

    /** 値が 0.3.0 の既定値と同じなら、今の既定値に書き換える。 */
    private static void migrate(ModConfigSpec.IntValue value, int legacyDefault, String key) {
        int now = value.getDefault();
        if (value.get() == legacyDefault && now != legacyDefault) {
            value.set(now);
            Singulo.LOGGER.info("Singulo: migrated {} from the 0.3.0 default {} to {}", key, legacyDefault, now);
        }
    }
}
