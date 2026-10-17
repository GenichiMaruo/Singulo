## Singulo-0.8.1-1.21.1N / Singulo-0.8.1-1.21.1F

For **Minecraft 1.21.1**, Java 21. Starting with this version, Singulo is available for both **NeoForge** (`N`) and **Forge** (`F`, 52.1.20 or later). JEI is optional on both. Mekanism integration is available only on NeoForge.

### Forge support
- Added a Forge build for Minecraft 1.21.1 with shared content, recipes and world generation.
- Fixed stale capability handles and block-entity references in the Forge compatibility layer.
- Mekanism chemical transport and its tank upgrades are not available on Forge, because Mekanism for 1.21.1 runs only on NeoForge.

### Android work
- Automatic recruitment now picks only the androids an order needs, while retaining workers with the required carrying capacity, tanks and work upgrades. Explicitly selected workers remain under your control.
- The order tab is larger and now has a search box, sorting, reverse order and optional JEI search sync. Click the box to start typing. Type `@` to search by mod.
- Intercept mode: androids now wait on their charging station, and extra androids wait beside it. They no longer start idle activities while on guard and take a short standing nap at night.
- Napping androids no longer chat with others.

### Androids
- All androids now blend smoothly between poses.
- Alma can now hold her axe in one hand or two, and her free hand no longer stays raised.
- Fixed parts of Soleil's sword and Nox's dagger sometimes not rendering. Lumina's weapon now glows faintly.

### Interface and items
- Item tooltips are now short summaries. Hold **Shift** for full details.
- Reorganized the creative tab so related items are grouped together. Existing items in your worlds are unchanged.
- Removed the center seam from machine screens and the handbook. The handbook can no longer scroll past its last page.
- Survey Glasses have a new square, rimless design with clear lenses and now sit correctly on the player's face.
- Fixed the android log overflowing its panel and shrank the Precision Actuator icon slightly.

### Blocks
- The trash bin is now hollow, and its rim texture is fixed.
- Fixed the coffee maker's cup texture, android capsules sometimes not showing their contents, and the held-item display of laboratory decorations.

Recipe costs and the five technology tiers are unchanged. Changes have been checked with automated GameTests. Complete playthroughs and all third-party machine setups have not been tested yet.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
