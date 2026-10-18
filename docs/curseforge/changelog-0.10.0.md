## Singulo-0.10.0-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional.

### Stage 2 power rework
- New generator: the **Muon-Catalyzed Fusion Reactor** (stage 2, made in the Precision Assembler).
  - Runs on muon catalysts and water (deuterium) and produces 4 kFE/t.
  - Each catalyst lasts 10 minutes, then comes out as a spent muon catalyst, which the Catalytic Reactor can turn back into muon bundles.
  - It uses 1 mB of water every 4 ticks. Water can be poured in with a bucket.
  - It stands by while its storage is full.
- The **Cryogenic Turbine** is now liquid-nitrogen energy storage instead of a generator.
  - Running it from a cooling tower used to create power out of nothing.
  - Liquid nitrogen now holds the power used to make it: 250 FE per mB. The turbine recovers up to 90% of that next to lava, and less with cooler heat sources.
  - Output is up to 4.5 kFE/t. The tank holds 32,000 mB.
- **Liquid nitrogen** now costs 250 FE per mB in the cooling tower. A taller tower still works faster, but it draws proportionally more power per tick, so the cost per mB is the same at any height.
- Recipes that use liquid nitrogen now need about a fifth as much, so crafting costs roughly the same power as before.
- The `shellCoolantMb` setting is renamed to `shellNitrogenMb` (default 1200 mB) so old configs do not keep the old amount.

### No pipe mod needed for liquid nitrogen
- New item: **Empty Coolant Cartridge**, crafted from steel plates and glass.
- Fill an empty cartridge by right-clicking the cooling tower controller with it, or by processing it in the tower.
- A filled **Coolant Cartridge** works like a bucket: right-click a machine to pour its 100 mB of liquid nitrogen in and get the empty cartridge back.
- Cryogenic Turbines also take coolant cartridges in an input slot and put the empty ones in an output slot. Hoppers can feed and empty both slots.
- Machines without a crafting GUI that hold fluids (the Cryogenic Turbine and the fusion reactor) now accept buckets and cartridges by right-click.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
