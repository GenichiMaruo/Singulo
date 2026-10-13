## Singulo-0.3.1-1.21.1N

### Fixes
- Equipment that needed several Time Crystal Catalysts (Core Controller, Singularity Encapsulator, Shield Tower Core, Wormhole Generator Core) could not be crafted because catalysts do not stack. These recipes now use the new stackable **Time Crystal Module**, cut from two thirds of a fresh crystal's life (30 minutes with default settings; the rest stays usable for exotic matter or a core). Lifetime settings and presets scale the amount, and used or low-purity crystals with less life left cannot be cut. The T-Core and Graviton Manipulator use it too.
- Server settings that had no effect now apply at runtime to machine recipes and JEI: `preset` (casual / standard / expert), nether star counts, exotic matter batch size, artificial star cores, spent catalyst recycling, time crystal growth time, shell materials, reactor shell output and sculk per echo shard. Default values keep the data-pack recipes as they are.
- `maxAnchorsPerPlayer` now limits how many Worldline Anchors each player can run at once (0 = no limit).
- Generators (Cryogenic Turbine, Quantum Heat Engine, Degenerate Furnace) now wait while their storage is full instead of burning nitrogen, fuel and catalysts.

### Fixes after the merge review (176ee06)
- `preset=expert` no longer asks for more liquid nitrogen than a tank holds for degenerate shells (shell coolant is capped at 8,000 mB; `shellCoolantMb` now ranges 0–8,000).
- Time Crystal Modules and other recipes that spend catalyst lifetime scale with the catalyst lifetime settings, so shorter lifetimes no longer block equipment.
- The strange-matter shell now follows `shellCoolantMb` (half of it) and the preset multiplier.
- Winning the awakened rematch now opens a vault whose regrowth was due, instead of resealing it.
- Lowering `maxAnchorsPerPlayer` now also stops running anchors, latest-started first, and the order survives restarts.

### Updating from 0.3.0
- Server config files saved by 0.3.0 keep their values. On first load, only entries still equal to the 0.3.0 defaults are moved to the new defaults: `degenerateFurnaceOutput` 20,000,000 → 5,000,000, `shellMetalCores` 2 → 1, `reactorPlatesPerShell` 12 → 24. Values you changed yourself are kept. The new `settingsVersion` entry records that this was done; set it back to 0 to run the check again.

### Balance
- New P-Reactor cores start in **harvest mode**: core mass stays at 480 or below so H-Condensate keeps forming, and an overgrown core sheds 2 mass per second until it is back. Turn it off for full power.
- One degenerate shell now makes 24 reactor shell blocks (was 12) and needs one metal core (was two): rock for ignition drops from about 63,000 to 34,000 mass and metal from about 6,300 to 1,700 ingots' worth.
- Degenerate Furnace output 20 → 5 MFE/t (item description and JEI page updated too). Generators running a catalyst one tier below give 25% of their rated output (new setting `generatorLowerTierOutput`): Quantum Heat Engine on muon 50 kFE/t, Degenerate Furnace on BE 1.25 MFE/t.
- Overworld alternatives: unfired ceramic with flint instead of quartz; heat exchange cores and coolant bands with packed ice instead of blue ice.

### New
- **Awakened Horizon Warden rematch**: craft an Awakening Record Core and use it on the Final Lab's seal console. 2.5× health, 1.5× damage, faster moves and earlier phases; rewards two Overclock Chips, eight exotic matter and two H-Condensate. The core is returned if you give up.
- Ignition diagnostics: the reactor screen shows the required intake, the current intake and what will arrive; failure messages say what was missing.
- New advancements (Hawking Drop, Forging a Star, Horizon Reawakened), handbook pages on expedition planning, and corrected numbers in the handbook and manuals.

This file is published with the **Beta** release type. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
