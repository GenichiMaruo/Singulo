# Android integration API (0.19.0 development)

Not a published release. Existing `AndroidOrders.submit` callers remain source-compatible.
AE2 is not a Singulo dependency. Applied Singulo must adopt these APIs separately before its
layout restrictions disappear.

## Remote item endpoints

Implement `api.android.AndroidWorkEndpoint` on the actual block entity. Persist a random
`endpointId()` for its placed lifetime; replacements MUST get a new identity. `endpointOwner()`
must match the console owner. Optional `supplyItems()` / `deliveryItems()` handlers use null to
disable a role. Handlers must obey IItemHandler simulation/remainder contracts, persist mutations,
not mutate arguments, throw after mutation or re-enter the scheduler. An arbitrary addon callback
that accepts items then throws or lies cannot be made transactional by this API.

Use `AndroidWorkEndpoints.link(level, console, actor, endpointPos)`, or right-click with a location
card containing the console. Card actions authenticate the actual player; trusted Java callers
must authenticate actor themselves. Both blocks must be loaded in one dimension, strictly less
than 64 blocks apart. No chunk loading. At most 64 addon links per console. Repeated link is
idempotent, not a toggle; unlink is explicit. Link changes return BUSY while any order is active.

Links persist owner and identity, not just position. Replacement/ownership changes invalidate
access. Supply is **shared stock**, not escrow. Do not expose one backing inventory through
multiple aliases. Cross-console reservations are not global locks. Item endpoints do not imply
fluid integration.

## Per-order delivery

Pass the linked endpoint position to `AndroidOrders.submit`'s existing destination parameter.
The order pins the placed endpoint UUID. Completion counts only accepted items. Partial/full
handlers are supported. Unloaded, removed or replaced endpoints keep delivery pending rather
than redirecting it to the console. Vanilla delivery-port fallback behavior is unchanged.

## Recipe snapshots

`AndroidOrders.recipes(level, console, actor)` returns an owner-only detached `RecipeSnapshot`
from loaded racks: mode/location, outputs, quantities, alternative samples and predicate JSON,
catalyst/fuel flags and liquid-sample flags. All exposed ItemStacks are defensively copied.
Alternative samples are for display; preserve the predicate for exact matching. Fuel is replenished
as needed, not necessarily once per craft. A snapshot does not guarantee current executability.

## Source-restricted reservations, NOT escrow

`AndroidOrders.submitFromEndpoint(..., destination, sourceEndpoint)` restricts **new material
reservations and retries** to one linked endpoint. Source identity persists; disappearance never
authorizes borrowing from shared stock. Request UUID fingerprints include source position;
changing source or switching to ordinary submit conflicts with an accepted request.

Supported scope: **one ordinary crafting step** only. Multi-step crafting, processing, procedures,
fuel, catalysts and liquid inputs are explicitly rejected. This avoids shared fuel/suborder paths.
Only same-source existing reservations are subtracted during planning. Produced materials and
worker recovery retain normal workbench/return behavior.

This is **not exclusive isolation**. Hoppers, other mods or consoles may alter the stock. Cancellation
may return carried materials to shared storage; already produced items are not rolled back. Do not
interpret acceptance as AE2 all-or-nothing pushPattern ingredient ownership transfer.

## Full escrow: intentionally not exposed

Filtering supplyPoints alone was rejected: fuel, auto-suborders and cancellation bypass it.
The implemented source restriction is deliberately narrower. A future console-owned escrow needs:

1. Staged deposit, validation and seal before any execution.
2. Persisted per-request ownership on worker carry and intermediate products.
3. CANCEL_REQUESTED -> RETURNING -> REFUND_READY, instead of immediate order deletion.
4. Refund tombstones retained until all workers return; acknowledgement blocked meanwhile.
5. Original endpoint identity-only refunds, retaining materials when full/unavailable.
6. Owner recovery containers on console destruction and duplicate-refund prevention tests.

Minecraft saves entities/block entities separately. Neither current APIs nor this design promise
crash-atomic exactly-once transfers after arbitrary process termination. Normal-save recovery is
the existing 0.18.0 contract, not an external durable transaction log.

## Tests

Run the full suite, not only focused `AndroidRecipeApiGameTests,AndroidEndpointApiGameTests,
AndroidOrderApiGameTests`. Added tests cover owner/range/thread checks, replacement, saved links,
real-worker remote supply and partial delivery, replay, source restriction, malformed saved
bindings and full carry with an extraction-only source.
