package io.github.genichimaruo.singulo.forgecompat;

import net.minecraftforge.common.capabilities.Capability;

/** Forge 版: NeoForge の ItemCapability と同じ形で使える、アイテムの機能の鍵（Forge の Capability を包む）。 */
public final class ItemCapability<T, C> {
    private final Capability<T> forge;

    ItemCapability(Capability<T> forge) {
        this.forge = forge;
    }

    public Capability<T> forge() {
        return forge;
    }
}
