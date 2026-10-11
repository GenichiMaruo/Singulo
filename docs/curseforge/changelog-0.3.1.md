## Singulo-0.3.1-1.21.1N (draft)

### Fixes
- Equipment that needed several Time Crystal Catalysts (Core Controller, Singularity Encapsulator, Shield Tower Core, Wormhole Generator Core) could not be crafted because catalysts do not stack. These recipes now use the new stackable **Time Crystal Module**, cut from a crystal with at least 30 minutes of life left (the remaining 15 minutes stay usable). The T-Core and Graviton Manipulator use it too.
- Server settings that had no effect now apply at runtime to machine recipes and JEI: `preset` (casual / standard / expert), nether star counts, exotic matter batch size, artificial star cores, spent catalyst recycling, time crystal growth time, shell materials, reactor shell output and sculk per echo shard. Default values keep the data-pack recipes as they are.
- `maxAnchorsPerPlayer` now limits how many Worldline Anchors each player can run at once (0 = no limit).
- Generators (Cryogenic Turbine, Quantum Heat Engine, Degenerate Furnace) now wait while their storage is full instead of burning nitrogen, fuel and catalysts.

### Balance
- New P-Reactor cores start in **harvest mode**: core mass stays at 480 or below so H-Condensate keeps forming, and an overgrown core sheds 2 mass per second until it is back. Turn it off for full power.
- One degenerate shell now makes 24 reactor shell blocks (was 12) and needs one metal core (was two): rock for ignition drops from about 63,000 to 34,000 mass and metal from about 6,300 to 1,700 ingots' worth.
- Degenerate Furnace output 20 → 5 MFE/t. Generators running a catalyst one tier below give 25% of their rated output (new setting `generatorLowerTierOutput`): Quantum Heat Engine on muon 50 kFE/t, Degenerate Furnace on BE 1.25 MFE/t.
- Overworld alternatives: unfired ceramic with flint instead of quartz; heat exchange cores and coolant bands with packed ice instead of blue ice.

### New
- **Awakened Horizon Warden rematch**: craft an Awakening Record Core and use it on the Final Lab's seal console. 2.5× health, 1.5× damage, faster moves and earlier phases; rewards two Overclock Chips, eight exotic matter and two H-Condensate. The core is returned if you give up.
- Ignition diagnostics: the reactor screen shows the required intake, the current intake and what will arrive; failure messages say what was missing.
- New advancements (Hawking Drop, Forging a Star, Horizon Reawakened), handbook pages on expedition planning, and corrected numbers in the handbook and manuals.

This file is published with the **Beta** release type. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
