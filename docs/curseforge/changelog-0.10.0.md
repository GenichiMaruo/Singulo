## Singulo-0.10.0-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional.

### Stage 2 power rework
- New generator: the **Muon-Catalyzed Fusion Reactor** (stage 2, made in the Precision Assembler).
  - Runs on muon catalysts and water (deuterium) and produces 4 kFE/t.
  - It wears catalysts twice as fast as other machines: a fresh catalyst lasts 10 minutes, and a partly used one burns only for its remaining life.
  - Used-up catalysts come out as spent muon catalysts, which the Catalytic Reactor can turn back into muon bundles.
  - It uses 1 mB of water every 4 ticks. Water can be poured in with a bucket.
  - It stands by while its storage is full.
- The **Cryogenic Turbine** is now liquid-nitrogen energy storage instead of a generator.
  - Running it from a cooling tower used to create power out of nothing.
  - Liquid nitrogen now holds the power used to make it: 250 FE per mB. The turbine recovers up to 90% of that next to lava, and less with cooler heat sources.
  - Output is up to 4.5 kFE/t. The tank holds 32,000 mB.
- **Liquid nitrogen** now costs 250 FE per mB in the cooling tower. A taller tower still works faster, but it draws proportionally more power per tick, so the cost per mB is the same at any height.
- Recipes that use liquid nitrogen now need about a fifth as much, so crafting costs roughly the same power as before.
- Only liquid nitrogen has a fixed power cost per mB. Other cooling tower recipes (coils, wire, liquid helium and more) still get cheaper on a taller tower.
- The `shellCoolantMb` setting is renamed to `shellNitrogenMb` (default 1200 mB) so old configs do not keep the old amount.

### No pipe mod needed for liquid nitrogen
- New item: **Empty Coolant Cartridge**, crafted from steel plates and glass.
- Fill an empty cartridge by right-clicking the cooling tower controller with it, or by processing it in the tower.
- A filled **Coolant Cartridge** works like a bucket: right-click a machine to pour its 100 mB of liquid nitrogen in and get the empty cartridge back.
- Cryogenic Turbines also take coolant cartridges in an input slot and put the empty ones in an output slot. Hoppers can feed and empty both slots.
- Machines without a crafting GUI that hold fluids (the Cryogenic Turbine and the fusion reactor) now accept buckets and cartridges by right-click.

### Fluid Canister
- New item: **Fluid Canister** (stage 1, crafted from iron ingots and glass). It holds up to 1,000 mB of any liquid or gas.
- Right-click a machine to fill it from the output tank or pour it into the input tank, like a bucket.
- Hydrogen, helium, liquid helium and other gases can now be carried between machines without a pipe mod.
- The canister shows its contents in its name, tooltip and fill bar, and its window takes the color of the fluid.

### Why a machine is idle
- Hover over the status text in the top right of a machine screen to see why it is not running.
- When the inputs do not complete a recipe, the status now reads **Missing input**, **No recipe** or **Too small** instead of Idle.
  - The tooltip names the item the machine is trying to make and lists what is missing: items and counts, fluids in mB, or the structure size.
- Out of power, output full, structure incomplete and switched off each explain what to do.

### Ceramic without skeletons
- New item: **Limestone Powder**, crushed from calcite or dripstone blocks: 2 in the Compressor, 1 at a crafting table.
- Unfired Ceramic accepts limestone powder in place of bone meal (item tag `singulo:ceramic_flux`), so you no longer need to hunt skeletons for your first machines.

### Charcoal for steel
- New item: **Carbon Dust**, made from 3 charcoal in the Compressor (2 dust). It replaces coal in steel blend and black reinforced panels (item tag `singulo:carbon`), so coal still goes further.

### Vanilla materials from machines
- Compressor (stage 1): cobblestone to gravel, gravel to sand, sandstone to 2 sand; 4 snow blocks to ice, 4 ice to packed ice, 4 packed ice to blue ice.
- Cryogenic Cooling Tower (stage 2): water + liquid nitrogen to packed ice, lava + liquid nitrogen to obsidian.
- Catalytic Reactor (stage 2): sand + water to nether quartz.
- Entanglement Synthesizer (stage 3): entangled element + slime ball to ender pearl.
- Degenerate Compactor (stage 4): 8 coal blocks to a diamond.
- Time Crystal Incubator (stage 4): logs to coal, cobblestone to mossy cobblestone, copper block to oxidized copper. Unlike catalyst growth, these need full power to progress.
- A new handbook page lists them all.

### New element: nihonium
- The **Particle Accelerator** now has two item input slots. With a ring of side 16 or more, a zinc ingot and a bismuth ingot fuse into **Nihonium** (element 113), the same pairing RIKEN used to discover it.
- Two nihonium melt into a **Nihonium Ingot** in the Kiln, the Compressor presses it into a **Nihonium Plate**, and the Precision Assembler builds a **Nihonium Frame**.
- Stage 4 machines and controllers (Time Crystal Incubator, Degenerate Compactor, Casimir Cavity, Degenerate Furnace, Probe Station, Void Collector) now use a Nihonium Frame instead of a Basic Frame.
- Stage 5 machines and cores that still used a Basic Frame (Core Controller, Singularity Encapsulator, Halo Collector, Gravitational Containment Tank, Event Horizon Shield core, advanced Worldline Anchor, Tipler core, Wormhole Generator core, Wormhole Stabilizer) now use a Nihonium Frame too.
- Nihonium plates and meteoric iron get late-game uses: nihonium for pressure parts and shielding, meteoric iron for magnets and rotating parts.
  - Gyro Drive: 1 nihonium plate + 2 meteoric iron instead of 4 steel plates.
  - Horizon Bus: 1 nihonium plate instead of 2 steel plates (still 8 per craft).
  - Hawking Collector and Jet Collector: 2 nihonium plates instead of 4 steel plates.
  - Casimir Cavity magnetic shield: 1 steel plate + 1 meteoric iron instead of 2 steel plates.
  - Magnetic Bottle: 1 meteoric iron instead of 1 steel plate.
- Zinc and bismuth do not need new ores, so existing worlds can make them without exploring new chunks:
  - **Zinc Dust**: crush 8 tuff in the Compressor, then fire it in the Kiln into a **Zinc Ingot**.
  - **Bismuth Ingot**: refine 8 raw copper with water in the Catalytic Reactor to get 6 copper ingots plus 1 bismuth ingot as a by-product.
- Zinc and bismuth ingots from other mods (`c:ingots/zinc`, `c:ingots/bismuth`) work too.
- **Gold synthesis**: a bismuth ingot and carbon dust in the accelerator (any ring size) make a gold ingot, as carbon nuclei strip four protons from bismuth. Glenn Seaborg's team did this for real in 1980.
- The Catalytic Reactor now has a second output slot for by-products, and JEI shows by-products under the main result.
- Accelerators and Catalytic Reactors in existing worlds keep their items: outputs and the upgrade slot move to their new positions when the world loads.

### Textures
- Multiblock controller screens (Cooling Tower, Degenerate Compactor, Casimir Cavity) now line up with the recessed screen in the block model.
- Cooling tower blocks and the heat exchange core are weathered with drip streaks, grime at the bottom, rust spots and mineral stains.

### Existing worlds
- The first time you open a Cryogenic Turbine that was placed before this version, a chat message explains that it is now energy storage and points to the fusion reactor.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
