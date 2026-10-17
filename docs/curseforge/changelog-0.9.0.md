## Singulo-0.9.0-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional. Forge support for Minecraft 1.21.1 ended with 0.8.1; Minecraft 1.20.1 remains supported on both Forge and NeoForge.

This release focuses on polishing existing content rather than adding new areas.

### Backpack tiers
- Android backpacks now come in three tiers: **Backpack** (12 slots), **Reinforced Backpack** (18 slots, stage 3) and **Large Backpack** (24 slots, stage 4). Each tier is crafted from the one below.
- You can swap in a larger backpack without emptying the current one.
- Existing Backpack Upgrades now open 12 slots. Items already stored in later slots are kept and can still be taken out; androids don't use those slots for work.

### Android work
- Androids now show why they are stuck: station out of power, destination unreachable, missing materials, protected block, nothing to work on in the area, or storage full. The reason appears in the android's status page, in the Work Console's order details, and as an icon over its head while you wear Survey Glasses.
- Fixed a guard staying stuck in its charging state after its station disappeared.
- Androids no longer drop items on the ground when their inventory fills up. Work plans now account for byproducts, leftover containers and items already carried while still using every free slot, and miners, lumberjacks and harvesters check that the drops will fit before breaking a block, going to put items away first when they would not.

### Balance
- Felling now costs charge based on the size of the tree (12 plus 2 per log) instead of a flat 24.

### Handbook and tooltips
- Each stage chapter now starts with an overview: what to build in order, the key to the next stage and common sticking points.
- New android pages: a step-by-step first work order and a guide to fixing stuck androids.
- Long pages about bosses, upgrades and processing were split into shorter pages.
- The Progress chapter opens at your current stage and links to that stage's overview.
- More items show a short tooltip summary; hold **Shift** for full details.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
