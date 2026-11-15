package io.github.genichimaruo.singulo.api.android;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Android-only, synchronous shared storage integration, introduced by the 26.1.2 port.
 * This is deliberately NOT NeoForge's transactional item capability.
 *
 * <p>Providers must enforce ownership/access for the queried face, implement truthful simulation,
 * and return an {@link AndroidSharedStorage} marker when several blocks expose the same inventory.
 * Call level.invalidateCapabilities(pos) when a provider is replaced or its exposed identity changes.
 * Stable handlers may reflect live contents/access, so consumers must not cache access decisions.
 * Do not expose a legacy handler under NeoForge's item capability ID or pretend it supports rollback.
 */
@SuppressWarnings("removal")
public final class AndroidStorageCapabilities {
    private AndroidStorageCapabilities() { }
    public static final BlockCapability<IItemHandler, Direction> SHARED_ITEMS = BlockCapability.createSided(
        Identifier.fromNamespaceAndPath("singulo", "android_shared_storage"), IItemHandler.class);
}
