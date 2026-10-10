package io.github.genichimaruo.singulo.data;

import net.minecraftforge.event.AddReloadListenerEvent;

public final class SinguloReloadListeners {
    private SinguloReloadListeners() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(MassValues.INSTANCE);
        event.addListener(ThermalData.INSTANCE);
    }
}
