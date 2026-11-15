package io.github.genichimaruo.singulo.data;

import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

public final class SinguloReloadListeners {
    private SinguloReloadListeners() {}

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(io.github.genichimaruo.singulo.Singulo.id("mass_values"), MassValues.INSTANCE);
        event.addListener(io.github.genichimaruo.singulo.Singulo.id("thermal"), ThermalData.INSTANCE);
        event.addListener(io.github.genichimaruo.singulo.Singulo.id("mass_synthesis"), io.github.genichimaruo.singulo.genesis.MassSynthesis.INSTANCE);
    }
}
