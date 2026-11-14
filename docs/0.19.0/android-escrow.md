# Private Android crafting escrow (0.19.0 development)

`api.android.AndroidEscrows` is a separate opt-in API. Ordinary `AndroidOrders` and endpoint
source restriction retain their old semantics. This feature is not yet released, and Applied
Singulo does not automatically adopt it merely by changing its dependency version.

## Why it is needed

Applied Singulo currently combines all accepted AE2 inputs in a shared 108-slot inventory, exposes
that inventory for ordinary extraction, batches by output, and matches returned products by item.
That is not request-specific ownership. A linked source alone cannot distinguish two requests for
the same item, nor safely refund materials carried by a worker after cancellation.

## Acceptance contract

```
AndroidEscrows.submit(level, console, actor, requestId, target, count, offeredStacks, workerOrNull)
```

All calls run on the server thread. Authenticate actor at the addon/network boundary. The actor
must own the console. The offered list describes **real items the caller already owns**, not a
permission to mint items. The trusted API copies values; it does not extract arbitrary external
handlers, whose partial-extraction/failed-rollback behavior cannot be made atomic.

- `acceptedNow=true`: this call transferred the entire offered list into Singulo custody. The
  caller must relinquish it exactly once. No partial material acceptance.
- `acceptedNow=false` plus an accepted receipt: this request was accepted previously. No materials
  were acquired again. Do not relinquish a second batch because a prior receipt exists.
- A start failure after custody binding returns `acceptedNow=true` with `FAILED`, rather than
  pretending the offer was rejected. Retain that request ID and reclaim its actual materials
  through the recovery API. The failure-injection regression checks this boundary.
- Rejection: the caller retains every offered item. Invalid counts, capacity, recipes or workers
  do not silently borrow shared materials.
- Same UUID with changed target/count/materials/worker selector/console binding conflicts. Keep accepted request
  IDs and never reuse an acknowledged ID.

Inputs are physically stored per request in dimension SavedData, not public item capabilities,
console stock, adjacent chests, or another request's inventory. The original offer is retained
only as an immutable replay fingerprint, not as a second claimable inventory.
Admission is bounded to 64 retained records per dimension and 128 offered stacks per request;
full custody rejects new offers without taking their materials. Recover and acknowledge old
requests rather than silently evicting them.

## Supported execution

The initial safe scope is one ordinary crafting recipe step, one initially idle worker with empty
carry, and item inputs. Multi-step plans, processing machines, procedures, fuel, catalysts and
liquid inputs are rejected. Worker identity and source dimension persist independently of the
current work goal. Private work cannot switch into an ordinary or another escrow order.

Workers physically obtain inputs, craft and deliver normally, but storage and results remain
request-specific. Shared workbench spill buffers are disabled; insufficient carry capacity pauses
without consuming a craft. Expected products enter the private product buffer; surplus inputs and
container returns remain in custody, not in a shared supply port. Stale leases cannot fetch or
deliver again.

## Completion, cancellation and recovery

`snapshot(level, actor, requestId)` works without the original console. It exposes detached slot
snapshots, receipt/outcome and `workerPending`. Slots stay stable, including empty slots.

Cancellation stops new work. The record remains RETURNING until the bound worker's actual carry
has been returned. Do not equate a cancelled execution receipt with a full material refund.
Consumed ingredients are not recreated, and already produced items are not rolled back. A loaded
worker's emergency recovery transfers its **actual** stacks into custody; an unloaded worker is
not guessed or refunded. Recovery may therefore wait for that entity to load.

Console destruction/replacement never transfers custody to the replacement: each placed console
has a persistent lifetime UUID. SavedData survives its destruction. Worker death, transfer or work
clear returns actual cargo before normal drops/station transfer, preventing duplicate drops.
Cross-dimension recovery uses the saved original dimension.

`claim(level, actor, requestId, products, slot, amount, simulate)` is standard per-stack extraction.
Simulation does not mutate custody. Products can be claimed when present; material refunds require
termination and worker return. Check the receiving inventory's capacity before real extraction.
If the receiver is full, leave the items in escrow. There is no timer-based deletion or shared-box
fallback. This API does not promise an atomic transaction with an arbitrary external receiver.

`acknowledge` succeeds only after terminal outcome, worker return, and empty input/product buffers.
The ordinary order acknowledgement API cannot bypass those checks.

## Persistence and corruption boundaries

Normal save/reload preserves custody, private order linkage and worker binding. Malformed or
conflicting saved records are retained in quarantine and not made claimable or reused. Actual
cargo from a disappearing worker with unresolvable ownership is retained separately for forensic
recovery, never attributed to a guessed owner. This is fail-closed preservation, not automatic
repair of arbitrary corrupt data.

Minecraft saves SavedData, entities and AE2 block entities independently. `setDirty` is not a
durable commit barrier. Arbitrary process crashes between their saves are **not** guaranteed
exactly-once transactions. Callers must reconcile uncertain results rather than automatically
resubmit UNKNOWN requests. Downgrading with active private orders is unsupported.

## Applied Singulo adoption work

AE2 `pushPattern` should use a unique request per accepted batch and the ownership-transfer result
above. Do not expose those inputs through the old shared escrow capability, merge requests merely
because their output matches, or delete terminal jobs before custody is recovered. Route products
by request, simulate ME capacity before claiming, and retain terminal/recovery state until
acknowledgement succeeds. Keep incompatible processing patterns disabled until they have a separate
machine-isolation protocol. These changes belong in Applied Singulo and are not implemented by
this Singulo-only commit.
