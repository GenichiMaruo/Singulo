package io.github.genichimaruo.singulo.data;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PackBalance {
    private PackBalance() {}

    /** Menus and item tooltips can be constructed before joining a world. */
    public static <T> T value(ModConfigSpec.ConfigValue<T> setting) {
        return ServerConfig.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    public static int scaled(int base, double multiplier) {
        return Math.clamp(Math.round(base * multiplier), 1, Integer.MAX_VALUE);
    }

    public static int dedicatedTank(int base) {
        return scaled(base, value(ServerConfig.DEDICATED_TANK_MULTIPLIER));
    }
}
