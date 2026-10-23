## Singulo-0.11.1-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional. A bug-fix release for 0.11.0.

### Panels and gravity
- **Rayleigh Sky Panels** no longer flicker black when they sit at the edge of the view, and the lit panel's underside now shows the sky color.
- **Zero-Gravity Control Boxes** joined through the same zero-gravity blocks now share one height. Changing one updates all of them, and a newly placed box adopts the group's height.
- A broken control box keeps its height on the item. Breaking and re-placing a box no longer gives a tall zone for free.
- The power cost of zero-gravity height now grows by **×1.4** per block (30 FE/t per block at the default height of 5).
- Androids no longer drift or move on their own in zero-gravity zones or on low-gravity panels; they walk normally.

### Blueprints
- Facility blueprint recipes are now **shaped**: the blueprint and materials must be placed in the pattern shown in JEI.
- New **Room Light** (Lighting Blueprint, makes 4): a ceiling light that glows while it receives a redstone signal. It needs no facility power.

### Machines
- The **Precision Assembler**, without a chosen recipe, now also starts mixes that could be part of a larger recipe. It starts them once items have stopped going in for 3 seconds, so automation no longer stalls on them.
- Androids select the recipe they recorded before inserting materials, so they always craft the item they intend. The Singularity Encapsulator works the same way.

### Cables
- At extreme power, the **Horizon Bus** core streaks stay visible inside the glowing sheath and frames.

### Underground Android Laboratory
- The tree at the **Outdoor Test Ground** now stands on the shore instead of floating over the pond.
- The test ground's observation hut has a chest with observation supplies. It usually holds the **Rayleigh Sky Panel Blueprint**.
- All eight **private rooms** have a Room Light in the ceiling with a lever beside it.
- In the **Solarium**, the swing seat is a top slab. Each deck chair is one stair with a bottom slab as the seat.
- **Reactor Control Panels** now face the reactor core in every room rotation.
- Generated only in newly explored chunks; labs already generated are unchanged.

### Bosses and enemies
- Arena fixtures (Echo Projectors and Production Lines) can no longer be broken while a boss is emerging nearby.
- Androids printed by **Ruin Android Fabricators** and drones launched by **Guard Docks** drop a few useful parts when killed by a player, such as circuits, steel plates, copper, redstone and sometimes a Self-Repair Kit. Ones from spawn eggs or commands drop nothing.

### Textures
- The **Pressure Crystal** is redrawn as an octahedral gem, so it no longer looks like the Muon Catalyst.

### Also in 0.11.0
These shipped in 0.11.0 but were missing from its notes:
- **Glasses** decoration for androids, worn in a new face slot and dyeable. Sneak-use **Survey Glasses** or Glasses to adjust how high they sit (up to 4 pixels up or down).
- The **Relocation Wrench** has a cable mode. Sneak-use it in the air to switch modes. In cable mode:
  - use a cable to take it back
  - sneak-use to take up to 64 connected cables of the same kind
  - left-click to break cables instantly
- Spare cables can be reused:
  - 1 Superconducting Wire + 4 Copper Wire make 4 Superconducting Cable
  - 1 Entangled Element + 16 Superconducting Cable make 8 Topological Wire (Cryogenic Cooling Tower)
  - 8 Copper Wire melt back into 2 copper ingots in the Kiln
- The **Horizon Bus** gains a glowing sheath above 100 MFE/t and travelling frames above 1 GFE/t.
- Held two-block-tall devices show the whole device shrunk to one block.
- Vending machines wear the same red casing on every side.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
