## Singulo-0.4.1-1.21.1N

### New
- **Meteor Compass** (stage 2, crafting table: compass, 4 amethyst shards, 4 iron ingots). Right-click to point at the nearest meteorite core that has not been mined yet; the action bar shows the distance. When that core is mined the needle spins again, and right-clicking finds the next one.
- **Meteorite Core.** Every meteorite now has one core at its heart. Mine it (iron pickaxe or better) for 4–6 raw meteoric iron and 3–5 stardust (Fortune applies).
- **Impact Generator Panels feed cables.** Cables now connect to the panel and receive its stored power. The panel keeps five seconds of power for the gravity panels it is running and sends the rest out, up to 1,000 FE/t. It does not take power from cables.

### Changes
- **Meteor craters blend into the terrain.** Craters are now carved column by column to match the ground they land on, instead of being stamped from a template that cut a square pit into the land. The bowl eases into slopes, the raised rim uses the local ground (grass, sand, red sand, terracotta or stone), and debris is thrown out in uneven rays with the odd boulder and meteorite fragment. Craters are no longer placed on water or on steep slopes.
- **Boss experience is much higher**, and is now given once per challenger, like the drops (`bossLootPerPlayer`):
  - Echo Sentinel 80 → 500, Gravity Remnant 120 → 800, Ergosphere Leviathan 250 → 2,000.
  - Horizon Warden 200 → 1,500 (awakened 500 → 4,500), Last Archivist 1,000 → 5,000.
  - Awakened guardians still give three times their normal experience.
- **Bosses wait three minutes for you to come back.** If every challenger leaves or falls, guardians and the Horizon Warden now stay for three minutes before returning to their seal (was five seconds). The Last Archivist still ends the fight after five seconds.
- The Last Archivist floats a little lower (0.6 blocks), so melee attacks reach it without standing right under it.
- The Stardust Cluster texture is weathered: sooty roots, cloudy chips and grains of dust, with tips that stay clear.
- JEI now shows an information page with the description and how to get it for every item that no JEI recipe makes, such as Jet Condensate, Hawking Condensate and wormhole mouths.

### Fixes
- **Gravity panels with several receivers.** Panels connected to more than one Gravity Panel Receiver (or Impact Generator Panel) now work properly:
  - Any one receiver can store enough to power the whole group on its own (before, only one receiver could hold more than one panel's worth, so the others added almost nothing).
  - When several receivers have power, each panel is paid for by the receiver closest to it along the panels. If that receiver runs short, the next closest one pays.
  - The receiver's light shows whether it is powering panels right now.
- The Echo Sentinel no longer leaves its boss room. Its movement, blinks and echoes stay inside the archive hall, and if it is pushed out it flashes back above the core.

### Updating from 0.4.0
- Craters that already exist keep their old shape and have no core, so the Meteor Compass does not point to them. New craters appear in newly explored chunks.

This file is published with the **Beta** release type. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
