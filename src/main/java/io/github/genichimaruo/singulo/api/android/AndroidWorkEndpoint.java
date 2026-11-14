package io.github.genichimaruo.singulo.api.android;

import java.util.UUID;
import javax.annotation.Nullable;
import net.neoforged.neoforge.items.IItemHandler;

/** Optional addon block-entity contract, server thread only (0.19.0).
 * Persist a random identity for the placed lifetime; replacements MUST get new identities.
 * Handlers must obey IItemHandler simulation/remainder semantics, never mutate arguments,
 * never throw after accepting items and never re-enter the scheduler. Persist inventory changes.
 * This trusted Java API is not a transaction protocol. Supply is shared, not crafting escrow.
 */
public interface AndroidWorkEndpoint {
    UUID endpointId();
    UUID endpointOwner();
    @Nullable IItemHandler supplyItems();
    @Nullable IItemHandler deliveryItems();
}
