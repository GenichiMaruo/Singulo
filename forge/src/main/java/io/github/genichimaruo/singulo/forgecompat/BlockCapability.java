package io.github.genichimaruo.singulo.forgecompat;

import net.minecraftforge.common.capabilities.Capability;

/** Forge 版: NeoForge の BlockCapability と同じ形で使える、ブロックの機能の鍵（Forge の Capability を包む）。 */
public final class BlockCapability<T, C> {
    private final Capability<T> forge;

    BlockCapability(Capability<T> forge) {
        this.forge = forge;
    }

    public Capability<T> forge() {
        return forge;
    }
}
