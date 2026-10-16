## Singulo-0.8.0-1.20.1N

For **Minecraft 1.20.1 / NeoForge**, Java 17. JEI and Mekanism are optional.

### Smarter android work
- Androids can switch between orders at the same console and work on other jobs while a machine processes its inputs.
- Improved automatic input/output face selection, fuel delivery, machine progress tracking and pickup of finished products.
- Added **Area Cards**, **Survey Glasses** and route rules for required waypoints and areas to avoid.

### Patrols and life at your base
- Added patrol routes with up to eight stops, loop or back-and-forth travel, nighttime patrols and configurable pauses.
- Patrols can call nearby guards, raise a station alarm and keep a log of enemies defeated and threats found.
- Idle androids can sit, nap, chat, tend plants and tidy nearby dropped items into storage.

### Underground android laboratory
- Explore a new underground facility, restore its central reactor and confront the manufacturing AI **Foundry**.
- Break manufacturing lines to weaken the boss, avoid its arms, presses and welding laser, and watch the conveyors.
- Earn precision actuators, prototype design memories and a **Factory Print Unit** that improves station printing and repairs.
- Added an awakened rematch for late-game factories.

### Prototype androids
- **Alma**, the axe prototype, uses wide sweeps, slams and leaping attacks.
- **Nox**, the bit prototype, combines ranged beams, short blades and lunging bits with glowing cutting edges.
- Bits visually follow independently of the body and return to the back when recalled. They do not create separate entities.
- Foundry rewards prioritize prototype memories that each player has not yet received.

### Optional Mekanism integration
- Added chemical transport, a gas tank upgrade and a combined liquid/chemical tank upgrade.
- The combined upgrade carries both kinds of resources with reduced capacity per tank.
- These recipes are enabled only when Mekanism is installed; Mekanism is not needed to complete Singulo's advancements.

### Progression and fixes
- Redesigned the advancement tree, expanding it from 54 to 73 entries while preserving existing advancement IDs and completed progress.
- Added laboratory, android printing, work delivery and prototype milestones, with offline delivery of owner-specific completion notifications.
- Updated handbook pages, translations and laboratory models.
- Fixed abandoned work reservations after order changes, lost items when an android dies while tidying, blocked seats, duplicate patrol report counts and disappearing recalled bits at screen edges.

Existing recipe costs and the five technology tiers are unchanged. New features have been checked with automated GameTests and real-client GUI tests; complete playthroughs and all third-party machine configurations are not yet covered.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).

### Minecraft 1.20.1 compatibility
- Ports the 0.8.0 features to Java 17 and the 1.20.1 Forge APIs.
- Keeps item cards, chemical-family identities, patrol routes and boss machinery across legacy NBT saves.
- Uses Mekanism 10.4 when installed; gas, infusion, pigment and slurry capabilities are handled separately.
