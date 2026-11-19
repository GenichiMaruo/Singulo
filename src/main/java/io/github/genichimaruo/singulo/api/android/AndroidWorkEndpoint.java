package io.github.genichimaruo.singulo.api.android;

import java.util.UUID;
import javax.annotation.Nullable;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Optional addon block-entity contract, server thread only (0.19.0).
 * Persist a random identity for the placed lifetime; replacements MUST get new identities.
 * Handlers must obey IItemHandler simulation/remainder semantics, never mutate arguments,
 * never throw after accepting items and never re-enter the scheduler. Persist inventory changes.
 * This trusted Java API is not a transaction protocol. Supply is shared, not crafting escrow.
 */
public interface AndroidWorkEndpoint {
    /** Register on a block or multipart host to expose the endpoint on a specific face. */
    BlockCapability<AndroidWorkEndpoint, Direction> CAPABILITY =
            BlockCapability.createSided(Singulo.id("android_work_endpoint"), AndroidWorkEndpoint.class);
    UUID endpointId();
    UUID endpointOwner();
    @Nullable IItemHandler supplyItems();
    @Nullable IItemHandler deliveryItems();

    /** Shared workshop stock for pickup and returns. Fluid quantities are millibuckets. */
    @Nullable default IFluidHandler supplyFluids() { return null; }
    /** Final product receiver. A partial fill leaves the rest with the worker for retry. */
    @Nullable default IFluidHandler deliveryFluids() { return null; }
    @Nullable default AndroidChemicalStorage supplyChemicals() { return null; }
    @Nullable default AndroidChemicalStorage deliveryChemicals() { return null; }
}
