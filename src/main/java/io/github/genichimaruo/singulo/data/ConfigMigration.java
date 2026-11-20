package io.github.genichimaruo.singulo.data;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/** 既存のサーバー設定を一度だけ移行する。前の既定値と異なる設定は維持する。 */
public final class ConfigMigration {
    private ConfigMigration() {}

    /** 今の設定ファイルの版。 */
    public static final int CURRENT = 4;

    private static volatile long revision;

    public static long revision() { return revision; }

    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC) return;
        revision++;
        int version = ServerConfig.SETTINGS_VERSION.get();
        if (version >= CURRENT) {
            return;
        }
        if (version < 1) {
            migrate(ServerConfig.DEGENERATE_FURNACE_OUTPUT, 20_000_000, "degenerateFurnaceOutput");
            migrate(ServerConfig.SHELL_METAL_CORES, 2, "shellMetalCores");
            migrate(ServerConfig.REACTOR_PLATES_PER_SHELL, 12, "reactorPlatesPerShell");
        }
        double pumpCost = ServerConfig.PUMP_FE_PER_MB.get();
        if ((version < 2 && pumpCost == 1.0) || (version == 2 && pumpCost == 0.75)
                || (version == 3 && pumpCost == 0.1)) {
            ServerConfig.PUMP_FE_PER_MB.set(ServerConfig.PUMP_FE_PER_MB.getDefault());
        }
        ServerConfig.SETTINGS_VERSION.set(CURRENT);
        try {
            ServerConfig.SPEC.save();
        } catch (RuntimeException e) {
            // サーバーから受け取った設定（マルチプレイのクライアント側）は保存できない。値の書き換えだけで十分
            Singulo.LOGGER.debug("Singulo: server config not saved after migration", e);
        }
    }

    /** 値が 0.3.0 の既定値と同じなら、今の既定値に書き換える。 */
    private static void migrate(ForgeConfigSpec.IntValue value, int legacyDefault, String key) {
        int now = value.getDefault();
        if (value.get() == legacyDefault && now != legacyDefault) {
            value.set(now);
            Singulo.LOGGER.info("Singulo: migrated {} from the 0.3.0 default {} to {}", key, legacyDefault, now);
        }
    }
}
