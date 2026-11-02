# 0.14.0 review fixes

- Detached delivery flights keep their materialized drone alive after the home station is destroyed. Removal still virtualizes an unfinished delivery; completed orphan flights disappear after handoff.
- Side configuration packets check port ownership for both individual faces and the auto-eject switch, after validating proximity.
- Destination lookup is gated on chunk availability. Unloaded destinations get a local exit/cruise route without loading the endpoint; existing entity-unload handling virtualizes the flight and retains its cargo.
- Non-phase movement validates the next segment against collision shapes. Obstructions stop the drone and invalidate its route. Nearby unobstructed docking approaches use a direct path instead of an unnecessary grid detour toward the floor.
- Virtual arrivals prefer the destination column and use the docking wait position when it is unobstructed, retaining the port's handoff animation. An entity already at its goal does not start a new route away from it.

`DroneRegressionGameTests` retains the four reproductions from review. The orphan-flight regression also waits for all five ingots to arrive and for the flight to finish, instead of merely checking the entity survives one tick. Existing drone delivery and phase-routing tests remain in the suite.

The pre-existing description and translation edits are not included in this fix commit. Release metadata is unchanged.

Validation: all 623 required GameTests passed (`build/fix014-full.log`), including the four review regressions and existing drone delivery/phase tests. Exact membership and Java 21 production JAR validation passed. Intermediate runs detected docking regressions; these were resolved before the final full-suite run.
