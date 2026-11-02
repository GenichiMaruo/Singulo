# 0.14.0 review fixes

- Detached delivery flights keep their materialized drone alive after the home station is destroyed. Removal still virtualizes an unfinished delivery; completed orphan flights disappear after handoff.
- Side configuration packets check port ownership for both individual faces and the auto-eject switch, after validating proximity.
- Destination lookup is gated on chunk availability. Unloaded destinations get a local exit/cruise route without loading the endpoint; existing entity-unload handling virtualizes the flight and retains its cargo.
- Non-phase movement validates the next segment against collision shapes. Obstructions stop the drone and invalidate its route. Nearby unobstructed docking approaches use a direct path instead of an unnecessary grid detour toward the floor.
- Virtual arrivals prefer the destination column and use the docking wait position when it is unobstructed, retaining the port's handoff animation. An entity already at its goal does not start a new route away from it.

`DroneRegressionGameTests` retains the four reproductions from review. The orphan-flight regression also waits for all five ingots to arrive and for the flight to finish, instead of merely checking the entity survives one tick. Existing drone delivery and phase-routing tests remain in the suite.

The pre-existing description and translation edits are not included in this fix commit. Release metadata is unchanged.

Validation: all 623 required GameTests passed (`build/fix014-full.log`), including the four review regressions and existing drone delivery/phase tests. Exact membership and Java 21 production JAR validation passed. Intermediate runs detected docking regressions; these were resolved before the final full-suite run.

## PR #51 review

- Packed crates no longer fit inside container items (shulker boxes, bundles), so containers can no longer be nested inside crates without limit. Empty crates still fit.
- JEI max transfer shares one 8192-step search budget across batch counts and stops at the first failing count after a success. A smaller count can still fail where a larger one fits (moving a whole stack frees inventory space).
- JEI transfer rewrites the catalyst reserve only when the recipe needs a catalyst that is neither loaded nor already stocked, so stocked reserves stay in the machine.
- Breaking a Drone Station returns one kit per drone, including drones that are delivering or returning and idle drones in unloaded chunks.
- Cancelling a shipment refunds its full cost even above capacity (the port stops accepting power until the surplus is used); the surplus survives save and load.
- Security drones save their dock and radius, so kills after a reload still count towards the memory drop.
- Drone Port android views are rebuilt when the port is rotated.
- Drone availability is rescanned at most every 10 ticks (always on a manual send), and the station scan runs once instead of twice.
- Capacity upgrade items are looked up once. The NO_POWER tooltip and the JEI rail say when even Mk3 cannot hold a recipe's demand, instead of suggesting Mk3.

New tests: `packedCratesStayOutOfContainerItems`, `brokenStationReturnsKitsOfFlyingDrones`, `cancelRefundsEvenWhenStorageIsFull`, `guardDroneRemembersItsDockAfterReload`, `rotatedPortUpdatesAndroidFaces`, `jeiTransferKeepsReserveCatalysts`, `capacityHintNeverPointsAtAnUpgradeThatCannotHelp`. Validation: 630 GameTests, 37 Python tests, release check.

## PR #51 second review

- Drone Station automation only accepts Drone Kits and extracts nothing, so hoppers cannot pull the permit, memory or upgrades out of someone else's station (same idea as the Android Assembly Station).
- Drone, android and machine item checks compare the full `singulo:` id, so another mod's `drone_kit` or `drone_memory` is not accepted.
- JEI transfer planning reads slot limits from `MachineMenu#stackLimit` instead of `SlotItemHandler#getMaxStackSize(ItemStack)`, which emptied and refilled machine slots and reset the recipe-selection wait even when nothing moved.
- The search tracks how much each pool stack has given instead of copying the pool at every step, and the server drops transfer packets that arrive within 4 ticks of the previous one from the same player.
- The machine syncs the loaded catalyst's exact remaining uses (`D_LOADED_USES`), so the JEI preview and the server agree when a catalyst is one use short.
- A virtual delivery turned back by a broken destination restarts its progress and returns at the drone type's speed (`DroneNetwork.redirectVirtual`).
- Staged crates call for a drone once a second instead of every 5 ticks.
- Drone Port item views are cached per side and rebuilt on rotation, together with the android views.
- `AndroidStorageView` replaces the duplicated drone-port special case in the supply port and adjacent-storage lookup.
- Guard Dock Javadoc moved to the method it describes.

New tests: `stationAutomationOnlyAcceptsKits`, `redirectedVirtualFlightUsesItsKindSpeed`, `jeiTransferPlanningDoesNotTouchMachineSlots`, `loadedCatalystEstimateMatchesTheServer`. Validation: 634 GameTests, 37 Python tests, release check.
