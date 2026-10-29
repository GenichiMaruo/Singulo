# Singulo — English Manual

[README](../../README.md) · [日本語](ja.md) · [English](en.md) · [简体中文](zh-cn.md) · [繁體中文](zh-tw.md) · [한국어](ko-kr.md) · [Recipes](../recipes/README.md)

Defaults for the 0.13.0 development branch. Existing `timeCrystalGrowthTicks=14400` settings are preserved; `9600` gives eight minutes.

## What is Singulo?

Singulo is a **technology mod that takes your factory from a small generator to a rotating black hole reactor**. Process materials, supply power and catalysts, build multiblock machines, and unlock increasingly advanced technology.

Exploration is part of that progression. Guarded ruins contain records and damaged components you cannot simply manufacture. Recovering and restoring them opens the next stage of your factory. Late-game devices add gravity manipulation, shields, time acceleration and remote logistics. The final goal is the Genesis Engine, which uses a black hole's power to create a universe.

Superconductivity, particle accelerators, the Casimir effect and Hawking radiation provide the themes. Mechanics and numbers are adapted for gameplay; this is not a complete physical simulation.

## Requirements and installation

- **Minecraft 1.21.1**, with a compatible **NeoForge 21.1.256 or later** build for that Minecraft version. Minecraft runs on Java 21.
- Put the Singulo JAR in `mods/`. Multiplayer needs the mod on both the client and server.
- JEI is optional. Its integration shows machine recipes and multiblock construction information.
- Singulo supplies energy cables. Compatible transport mods can provide pipes for item and fluid automation.

For building from source, see the [development instructions in the README](../../README.md#開発するには).

## Your first session

1. **Open the handbook.** You receive the Singulo Handbook on your first login. Right-click it and read Stage 1 and Progress for your first goals.
2. **Make steel and ceramic.** Craft Steel Blend from iron and coal, then smelt it in a blast furnace. Craft Unfired Ceramic from clay, Nether quartz, bone meal and sand, then fire it in a furnace. Before visiting the Nether, two flint can replace the quartz.
3. **Make circuits and frames.** Steel, ceramic, copper and redstone form the early material base. Check the [recipe gallery](../recipes/README.md) for exact quantities.
4. **Generate and distribute power.** Place a Thermoelectric Generator next to a supported heat source, such as a lit campfire, magma block or lava, and a cold source, such as water, a snow block or ice. Connect it to machines with Copper Wire.
5. **Build and explore together.** Establish a Kiln, Compressor, Electrolyzer and Archive Terminal. Use an Explorer Compass to find an Observation Post, bring back its records and damaged components, restore them, and work toward your first cooling tower.

Larger temperature differences produce more power, but exceeding the cold source's tolerance consumes or degrades water and ice. Check the generator screen rather than assuming a cold block lasts forever. Magma block + blue ice (about 76 FE/t) and campfire + packed ice (about 55 FE/t) never melt; lava + blue ice gives about 106 FE/t but the ice starts melting within about 40 seconds. Two to four generators running one machine at a time are enough to start.

Cooling tower heat exchange cores and coolant bands can use three packed ice instead of blue ice.

## The five stages

| Stage | Your focus | What it enables |
| --- | --- | --- |
| 1: Startup | Steel, ceramic, circuits and thermoelectric power | Firing, compression, electrolysis, copying records and repairing components |
| 2: Cryogenics | Cooling towers, accelerator rings and Muon Catalysts | Muon-Catalyzed Fusion Reactors, Superconductors, cryogenic turbines, SMES storage, precision assembly and chunk loading |
| 3: Quantum | Research Building salvage and liquid helium | Quantum components, laser cooling, BE Condensate Catalysts, exploration tools and inertial control |
| 4: Temporal | Nihonium frames, Culture Facility salvage and time crystal growth | Degenerate Compactors, exotic matter from C-Cavities, Degenerate Furnaces and Probe Stations |
| 5: Singularity | Defeat the Final Lab guardian and awaken a singularity seed | Penrose reactors, Singularity Cores, shields, time acceleration, gravity tools and wormhole logistics |

After stage 5, awakened rematches, a hidden boss and the final goal, the Genesis Engine, are waiting.

These stages describe the material and technology progression. Unlocking an advancement does not replace the recipes, power supply or components you need.

## Power, logistics and catalysts

Energy is measured in FE. Upgrade from Copper Wire to Superconducting Cable, Topological Wire and the Horizon Bus as your factory grows. Copper loses energy, and the lowest-capacity cable on each route limits its transfer rate. Unrelated branches do not lower that limit; transfers using the same cable share its per-tick capacity. The Horizon Bus has no limit: between Singulo devices it can move more than about 2.1 billion FE per tick (other mods' devices still accept up to about 2.1 billion FE/t each). Only cables that are actually carrying power glow, with a gentle wave running in the direction of flow; the brightness follows the amount of power. Above 100 MFE/t the Horizon Bus is wrapped in a larger glowing sheath, and above 1 GFE/t square frames of light travel along it with the flow.

Spare lower-tier cables feed higher tiers: a crafting table turns 1 Superconducting Wire and 4 Copper Wire into 4 Superconducting Cable, and the Cryogenic Cooling Tower turns 1 Entangled Element and 16 Superconducting Cable into 8 Topological Wire. The Kiln melts 8 Copper Wire back into 2 copper ingots.

Sources nearer to the receiver along the cables supply power first; more distant sources cover the deficit. Equal distances use a stable coordinate order. An empty nearby source or a bottleneck on its route does not block the backup sources. Breaking a cable immediately clears the glow of that wiring.

Ordinary processing machines have a Sides screen for face-specific input, output and auto-ejection. Machines that output two gases, such as the Electrolyzer, let you choose the output faces for each gas. The power button on the right of the screen stops a machine, and machines that can make something from power alone, such as liquid nitrogen in the cooling tower, have a switch to skip those recipes. Multiblocks use shared Multiblock I/O Ports; the Penrose reactor uses its own Extraction Ports. Singulo's cables carry energy, not items or fluids.

Catalysts are consumables placed in dedicated slots. The progression is Muon Catalyst, BE Condensate Catalyst, Time Crystal Catalyst, then Singularity Core. Matching tiers give normal operation. One tier below gives half speed and twice the wear; two or more below cannot run the machine. Higher tiers provide speed and wear bonuses. Depleted catalysts increase power requirements, and insufficient power can affect catalyst wear or product quality. The Quantum Heat Engine and Degenerate Furnace give 25% of their rated output on a catalyst one tier below. These rules apply to devices running from a catalyst slot; a catalyst listed as a recipe ingredient just uses a fixed count or amount of life and does not change processing speed.

Fusion reactors, Quantum Heat Engines, Degenerate Furnaces and nitrogen-storage Cryogenic Turbines stand by when their energy buffer is nearly full, consume no resources while waiting, and resume below 75%.

Time crystal growth defaults to **9,600 running ticks**, or 8 minutes at 20 TPS. It does not progress in unloaded chunks. Time fields and server configuration can change the effective duration. Purity is kept at 80% of the requested power or more; time below that shortens the crystal's life. Spent catalysts have recycling recipes.

Equipment such as the Core Controller, Singularity Encapsulator, Shield Tower Core and Wormhole Generator Core uses stackable **Time Crystal Modules** from the Precision Assembler. A batch of four cuts two thirds of a fresh crystal's life (30 minutes by default) from the crystal in the catalyst slot; the rest (15 minutes by default) stays usable. Crystals with less than that left cannot be cut. Changing catalyst lifetime settings scales the amount.

### Power and materials

- The Muon-Catalyzed Fusion Reactor is the stage 2 power source: at default settings, 4 kFE/t, 3000 mB of water and one fresh muon catalyst for 10 minutes. It wears catalysts twice as fast, so a used catalyst runs for half its remaining lifetime.
- The Cryogenic Turbine recovers stored power from liquid nitrogen. It does not make net power. Nitrogen costs 250 FE/mB to produce; lava allows up to 90% recovery, capped at 4.5 kFE/t. A height-7 tower draws about 1786 FE/t for nitrogen and keeps progress when power runs short. Other cooling tower processes still use less total power on taller towers.
- Fluid Canisters carry 1000 mB of liquids or gases. Coolant Cartridges carry 100 mB of nitrogen and return empty when poured into a machine. Precision Assembler recipes consume the container too.
- Crush tuff for zinc and collect bismuth as a by-product of raw copper refining. Fuse them into nihonium in a ring of side 16 or more. Plates and frames are used in stage 4 and 5 machines and parts. Meteoric iron also goes into magnetic shields, Magnetic Bottles and Gyro Drives.
- Limestone powder replaces bone meal, and carbon dust replaces coal. Two flint can replace quartz in Unfired Ceramic; zinc phosphor provides glowstone dust. In the Kiln, 250 mB oxygen, 2 iron and 1 coal or carbon dust make 2 steel. The Peltier Cooler freezes 1000 mB water into 1 ice.
- The Hydrogen Fuel Cell stores and returns energy, rather than producing a surplus: it uses hydrogen and oxygen at 2:1 and returns 4.8 FE/mB of hydrogen, up to 240 FE/t, at default settings.
- Only catalyst growth in the Time Crystal Incubator tolerates partial power, with reduced purity and lifetime. Coal, mossy cobblestone and oxidized copper need full power to progress.


## Building multiblocks

Place the controller and **right-click it with a Holo Projector**. Follow the projected shape and missing-material list: fill cyan positions and clear red obstructions. Sneak-use the projector to cycle the size of adjustable structures. Once assembled, right-clicking a component can open the controller screen too.

| Machine | Footprint and key conditions |
| --- | --- |
| Cryogenic Cooling Tower | 5×5, 7–15 blocks high, with a narrow waist. Liquid helium requires height 10 or greater |
| Particle Accelerator | Horizontal square ring, side length 8–32. Magnets at all corners and at least one quarter of the perimeter; controller beside the ring |
| Degenerate Compactor | Closed 5×5×5 press |
| C-Cavity | 5×5×5 vessel with facing mirror plates inside the top and bottom |
| Degenerate Furnace | 5×5×7 furnace, 5 MFE/t from Lv2 compressed blocks and a Time Crystal Catalyst (1.25 MFE/t on BE catalysts) |
| Penrose Reactor | Three orthogonal rings within 13×13×13; radius 6, controller five blocks below the center |
| Event Horizon Shield Tower | 5×5, nine blocks high |
| T-Cylinder | 5×5×9; its field accelerates supported machine processing |
| Wormhole Generator | Spherical structure within 5×5×5 |

The outer dimensions are only a starting point. Frames, windows, internal components and required air spaces must also match. Shared I/O Ports replace designated panels, not arbitrary components. Accelerator ports sit on either side of its controller. Reactor Extraction Ports replace ring shell blocks: the six ring intersections use Gyro Drives, while the twelve diagonal positions use **Reactor Stabilizer Coils**.

## Exploring ruins

| Ruin | Important salvage and purpose |
| --- | --- |
| Observation Post | Observation Logs and degraded Control Units; the entry to superconducting and muon technology |
| Research Building | Quantum Data Fragments and degraded Cold Atom Traps for quantum technology |
| Culture Facility | Culture Data and degraded Time Crystal Seeds for temporal technology and exotic matter |
| Final Lab | Anomaly Samples and the initial Dormant Singularity Seed, guarded by the Horizon Warden |
| Sunken Reactor Sector | A dead second reactor: exotic matter, Jet Condensate and H-Condensate, guarded by the Ergosphere Leviathan |

Only the Observation Post stands on the surface; the Research Building is buried shallow underground, the Culture Facility deep underground, the Final Lab deeper still, and the Sunken Reactor Sector below it (around Y −60 to −54). The Explorer Compass starts out finding only Observation Posts. Sneak-use it on the cache of a ruin you have cleared (opened yourself) to tune it for the next ruin; sneak-use it in the air to switch between the ruins it can find. Operators can place a ruin with `/singulo ruin <ruin> [pos]`. Gravitational Wave Detectors and Neutrino Scanners provide further exploration support. After a cache is emptied, contents replenish by default after seven in-game days for Observation Posts and Research Buildings, or fourteen for Culture Facilities, Final Labs and Sunken Reactor Sectors.

Every ruin hides a **sealed container** that opens only with the key of the next stage (magnetic, quantum, temporal, then singularity). Keys are made in the Precision Assembler and used up each time. Inside are materials, coloured lamps and sometimes a rare item found nowhere else: the Overclock Chip, Gravity Boots, Catalyst Stabilizer or Dimensional Pocket. Breaking a sealed container destroys its contents. Once you reach a stage you can craft its container yourself, and sneak-use a key on it to seal it.

A loaded Singularity Core also unlocks Research Building collection. At the matching tier, a probe visits one site in five minutes. Cooldown and full-output waiting consume no resources, and overflow cargo is saved. A Probe Station only visits ruins a player has discovered by opening their caches. With default settings, the stage 4 station automates Observation Posts. Culture Facilities, Final Labs and Sunken Reactor Sectors always require manual exploration. Decode Record Fragments in the Archive Terminal and read the decoded items to add ancient stories to your handbook.

## Androids

Every ruin has **Ruin Android Fabricators** against its walls. When a survival player comes near, a fabricator prints two or three androids of its kind onto its bed, built up from the feet, and sends them out. Defeat them all and a **compact crystal memory** of that design appears on the fabricator (which then rests for a day).

| Kind | Main moves |
| --- | --- |
| Blade Android "Lumina" | Dash slash with afterimages, pillars of light from the floor (leave the warning rings), spin slash |
| Whip Android "Verna" | A lash reaching about 6 blocks, a bind that pulls you in, a shockwave along the floor (jump it) |
| Saber Android "Soleil" | Lunging thrust, a sweep whose blade stretches, a flying crescent slash. Raises a light shield when hit and counters if you keep striking |

Androids run on charge and run out after about ten big moves. They return to a nearby fabricator, unfold a solar panel under the open daytime sky, or sit down at night or indoors to recharge (fabricator 5 s, solar 10 s, sitting 14 s). They do not attack while charging.

**Your own androids:** make an **Android Assembly Station** and android kits in the stage 2 Precision Assembler. Put a compact crystal memory (never used up) and kits (one per print) in the station and supply power, and it prints one guard android on top (40,000 FE each). If it falls, the station prints it again while kits last.

- Guards attack hostile mobs only and never fight players or bosses. Player and boss attacks do not hurt them.
- Modes: Guard (by the station), Wander (within 16 blocks) and Follow (goes with its owner). Below its return health it goes back to the station and repairs and recharges in 5 s.
- While following, if you carry an **Android Wireless Charger** (30 refills when full), it recharges as it walks beside you.
- While following, choose "Just follow" (never fights) or "Escort" (fights nearby enemies), in the station or by clicking the mode tag in the android screen.
- Put a **Shield Permit** in the station's permit slot and only the owner and registered players can open the station and android screens. You can also choose whether it follows the owner or one of the registered players.
- Right-click an android to open its inventory: **Self-Repair Kits** (one restores half its health when it gets hurt) and an **Android Armor Plate** that takes armor enchantments.
- **Upgrades:** a **Production Upgrade** in the station lets it print up to two androids (only one charges on top at a time; the other waits beside it). On the android's "Gear and inventory" page (the button left of its name) you can fit **Battery Upgrades** (+50% charge each, up to two), a **Backpack Upgrade** (three tiers: +12, +18 or +24 inventory slots; each tier is made from the one below) and a **Mining Upgrade** (for mining work; takes pickaxe enchantments). Androids carry 5 slots of items, more with a backpack.
- **Work automation:** an **Android Work Console** uses the chests next to it as the stockpile (add **Supply Ports** for more). Link **Recipe Racks** to it with a **Work Location Card**. Put **Recipe Cards** recorded in the console's Record tab in a rack row, with a Work Location Card holding a crafting table, and you can order them at the console. Finished items go to the console's outbox or a named **Delivery Port**. Orders need every material in stock, and intermediate materials are crafted automatically.
- **Processing, procedures and mining:** the **Processing** mode of the Record tab records what goes into a furnace or machine (with counts) and what comes out (byproducts too); androids put it in from above and take it out from below (JEI's + button works here too). The **Procedure** mode writes insert, extract and mine steps onto a **Procedure Card**, which keeps the places on the card. Androids with a **Mining Upgrade** dig the blocks in an area until they have enough (like a diamond pickaxe, with the upgrade's enchantments). An **Android Workbench** crafts four times as fast as a vanilla crafting table.
- **Saved orders and progress:** switch the button when confirming an order to **save** it instead (up to 16): **Keep in stock** (orders automatically whenever the stockpile drops below the amount), **Once per signal** (orders each time the console receives a redstone pulse) or **Keep stock while powered**. If materials are short, it waits until they are in stock. A comparator reads 15 from the console while an order is running. Click a running order to see each step's progress and what every android is doing (an icon also floats over their heads; it can be turned off in the config).
- **Fighting back and greetings:** an android attacked while working fights back against that attacker only, then resumes where it stopped. It waves when you come back after a while, nods to fellow androids it passes, and gives a little nod when it delivers.
- **Farming and using blocks:** procedure cards can also harvest (pick crops and replant), fell (cut whole trees and replant; needs a **Felling Upgrade**), breed, shear, milk, cull, and use blocks (levers on/off, buttons, doors).
- **When an android stops:** the reason (station out of power, destination unreachable, missing materials, protected block, nothing to work on in the area, storage full) appears in red in the android screen's speech bubble and in the Work Console's order details. With **Survey Glasses** on, a barrier icon floats over its head.
- **Liquids and gases:** androids with a **Tank Upgrade** (two tanks of 32,000 mB) take liquids and gases from tanks next to supply ports and carry them to machines. Click a pattern slot with a bucket or other container, or use JEI's + button, to place a fluid in processing or procedure mode.
- **Decorations:** the third page of the android screen holds a head slot (ribbon, hair flower, beret, headdress), a back/waist slot (cape, scarf, waist ribbon) and a hand slot (paperback, bouquet, teacup, umbrella) and a face slot (glasses). They are crafted on a crafting table and can be dyed. Sneak-use **Survey Glasses** or Glasses to set how high they sit, up to 4 pixels up or down (others see the same height).
- The station has a refill slot (tops up repair kits automatically) and an upgrade slot (a **Transfer Upgrade** passes a fallen android's inventory to the next one), and hoppers can insert into it.
- Name your android in the station or android screen, and read its health with a comparator. If the station is broken, Link the android to a new one.

**Android glass:** Barrier Glass (only androids cannot pass) and Gate Glass (only androids can pass), each in a plain kind that joins with its neighbours and a patterned kind.

## Black holes and late-game technology

Insert your first Singularity Seed into the Core Controller, start ignition, and supply **50 GFE within ten seconds** under default settings. The formation sequence then creates the core. Prepare a high-capacity power network before attempting ignition: 50 GFE in ten seconds averages 250 MFE/t, and a power route is capped by its lowest-capacity cable, so a single topological-wire route delivers at most 20 GFE. While igniting the screen shows the intake and what will arrive, and a failure reports what was missing.

A new core starts in **harvest mode**: it keeps mass at 480 or below, so H-Condensate forms every five minutes with a Hawking Collector. The first Singularity Core needs six (four directly, two to restore the Anomaly Sample). Turn harvest mode off afterwards for full power. Harvest mode also sheds 2 mass per second from an overgrown core.

During operation, feed Mass Pellets and manage core mass and spin. Spin determines accretion efficiency; mass affects the fuel injection limit. Collectors produce Jet Condensate, H-Condensate (Hawking Condensate) and exotic matter under their respective operating conditions. Pellet-driven growth is capped, but feeding other matter beyond safe limits can collapse the reactor and leave a rogue black hole.

**Bosses and rematches:** the Echo Sentinel guards the Research Building, the Gravity Remnant the Culture Facility, the Horizon Warden the Final Lab and the Ergosphere Leviathan the Sunken Reactor Sector; each cache stays sealed until its guardian falls. After the endgame, awakening cores made in the Singularity Encapsulator (Awakening Record Core, Awakened Echo, Gravity and Ergosphere Cores) can be used on the seal console or a guardian core to fight an awakened version (2.5× health, 1.5× damage, faster moves) as often as you like. Victory adds Overclock Chips and more to the usual drops. If you give up and leave, the core is returned.

**Chapters of records:** the three awakened guardians (Echo Sentinel, Gravity Remnant, Horizon Warden) drop chapters of records, and the Ergosphere Leviathan drops the Chapter of the Vortex. Bind all four into a Terminus Record in the Singularity Encapsulator and use it on the Final Lab's seal console, and something that slept in the time field wakes up. The chamber is sealed until the fight ends, and some attacks change gravity, so prepare for falls. The Genesis Engine also needs one chapter of the Vortex, so collect extras if you want both.

**Playing together:** bosses grow stronger with the number of challengers in the room (each extra player adds 50% health and slightly shortens their attack gaps, up to six players). Everyone's attacks land independently, so a larger party defeats a boss faster. Drops come once per challenger present when the fight begins; players who join later do not add more. Server settings `bossHealthPerPlayer`, `bossMaxScaledPlayers` and `bossLootPerPlayer` adjust this. Each boss is tuned so that the gear you would normally have at that point can just about win; the server setting `bossHardMode` makes them stronger. Boss drops appear on the guardian core in the floor (on the seal console for the Horizon Warden), and boss experience is split evenly among the challengers in the room and goes straight to them (the total, like the drops, counts the challengers present when the fight begins; Mending still works).

Late-game devices protect a base, accelerate supported machines, keep chunks loaded and manipulate gravity. The Metric Drive works from your inventory and does not require a Curios equipment slot. Wormholes are paired mouths that you generate, stabilize and place; nearby ports link distant energy, items and fluids. Exotic matter maintains the mouths, and the destination chunks must remain loaded.

## The final goal: the Genesis Engine

The old civilization tried to turn all mass into energy and perished. The Genesis Engine does the reverse: it uses a black hole's power to make a universe.

1. **Build:** place eight Genesis Frames level with the Genesis Core, three blocks out on each axis and two blocks out on each diagonal. Stack three Genesis Pylons on each of the four diagonal frames (twelve in all). One recipe makes two frames. The Holo Projector and the Builder Wand support it.
2. **Primordial Seed:** craft it in the Singularity Encapsulator from a Chapter of the Vortex, one use of a restored anomaly sample and the other required materials, then use it on the core.
3. **Kindling:** pour in 1.5 TFE, at most 1 GFE per tick (a little over a minute at full P-Reactor output).
4. **Stabilizing:** hold 100 MFE/t for about four minutes of active progress. Now and then a surge alarm sounds, and four seconds later the surge begins. Stop the feed while it surges, with the switch on the screen (use the core empty-handed) or a redstone signal on the core. If you keep feeding, instability rises, and at 100% the proto-universe collapses and progress is lost. While stopped, upkeep still drains the buffer and events (gravity leaks, echoes, gravity waves) still happen. A shield softens surges and events; a T-Cylinder field speeds progress up.
5. **Birth:** after a 30-second cinematic, the newborn galaxy rises into the overworld night sky, visible to every player.

A born universe performs **mass synthesis**. Put the item you want in the frame on the screen, and the engine makes it from power. One item costs `mass × 1 GFE × 10^rarity`, with rarity 0 (stone, wood) to 4 (nether star, elytra, dragon egg). It makes the plain item: enchantments and names on the sample are not copied. Processing synthesized items and feeding them to the P-Reactor never produces a power surplus.

A broken core keeps the memory of its universe (stage, galaxy, chosen item) when placed again, but stored power and partial progress are lost. The list of items is a datapack (`data/<namespace>/mass_synthesis/`).

## Troubleshooting and hazards

- **A machine will not run:** check power, ingredients, catalyst tier and remaining life, output space and required fluids. For multiblocks, check the structure and port positions too.
- **Tool controls differ:** hold right-click for the Inertial Control Gauntlet. Hold left-click for the Graviton Manipulator; while levitating a target, charge right-click and release to throw it. Check your configured key bindings in-game.
- **A generator is idle:** it is on standby because its storage is full; it resumes when power is used.
- **The factory stops when you leave:** unloaded chunks do not process. Worldline Anchors also need their operating requirements maintained. Each player can run up to 8 anchors at once by default (server setting `maxAnchorsPerPlayer`, 0 for no limit).
- **Dangerous equipment:** black holes exert pull, deal tidal damage and consume entities at the event horizon. Hydrogen leaks near fire can explode. Large accelerators with low stored energy can leak strangelets; prepare Magnetic Bottles and protective equipment.

Standard textures are 16×16. Enable the bundled **Singulo HD** resource pack for 32×32 textures. Accelerator sides and corners connect automatically, with a circulating light effect while operating.

The server setting `preset` changes difficulty: `casual` removes nether stars, doubles catalyst life and halves ruin regrowth, Warden health and shell compression; `expert` doubles stars, uses 0.75× catalyst life, allows two repairs, uses 1.5× shell compression and doubles ignition energy. Individual settings such as star counts, shell materials and growth time also apply to recipes and JEI when changed from their defaults.

This manual describes the current implementation and default settings. See the [recipe gallery](../recipes/README.md) and [documentation audit](../history.md) for references. Server settings and data packs may change recipes or behavior; use the actual in-game information for your world.


## Relocation and automatic ejection

Craft the Relocation Wrench from 3 steel ingots, 2 copper ingots and a basic circuit. Sneak-use it to pack a supported device with its items, fluids, energy, progress and side settings. Only the controller of a multiblock is packed. See “Power and Logistics → Relocation Wrench” in the handbook for supported devices.

The wrench also has a **cable mode**; sneak-use it in the air to switch modes. In cable mode, use a cable to take back that one cable, sneak-use to take up to 64 connected cables of the same kind, and left-click to break cables instantly.

Automatic ejection on ordinary processing machines defaults off. Enable the master switch in Sides to eject through allowed output faces. Hover a face to see the adjacent block. Newly generated underground labs always include both shafts, with the supply shaft connected to a dedicated cargo lift room.

Unused Observation Logs, Quantum Data Fragments, Culture Data and restored parts stack to 64 when their state matches. Each machine input slot accepts only one; shift-click inserts one as well. Partly used items do not stack.

Restored parts and original records exhausted during processing become item outputs with their usage and repair data intact. If the output slot is occupied, they wait in their input slot and can still be extracted by hoppers or item pipes. Enable automatic ejection to send them to adjacent inventories. Parts inserted for repair are not automatically ejected.

## Precision Assembler recipe selection

The Precision Assembler normally stores 100,000 FE and accepts up to 4,000,000 FE per reception. Install one Capacity Upgrade Mk1, Mk2 or Mk3 in the dedicated slot on the right rail for 1,000,000, 4,000,000 or 40,000,000 FE. Craft Mk1 on a stage-2 crafting table; craft Mk2 and Mk3 in the assembler using the previous tier as an ingredient. The separate slot supports speed upgrades at the same time, and intake limits stay unchanged. Removing or replacing an upgrade preserves surplus charge, including charge from older versions, and blocks further reception until the buffer falls below capacity. Recipes whose per-tick cost exceeds the normal buffer, such as the Mk3 sensitivity module, require a capacity upgrade; JEI shows this requirement.

Without a chosen recipe, the Precision Assembler starts crafting when the kinds of items inside exactly match a recipe. A mix that could also be part of another recipe starts only after items have stopped going in for 3 seconds. Choose a recipe with the button below the output slot to craft only that one; the choice is saved, and other items may be present. Androids select their recorded recipe before inserting materials. The Singularity Encapsulator works the same way.

## Facility blueprints

Ruin and laboratory chests hold **blueprints**: Ruin Panel, Pristine Ruin Material, Ruin Glass, Lighting, Laboratory Material, Laboratory Furnishing and Rayleigh Sky Panel. Arrange a blueprint and materials in its shape on a crafting table to make building blocks; the blueprint stays in the grid. Tooltips and JEI show where each is found and its recipes. Lab doors and the Lab Rayleigh Sky Panels in lab ceilings cannot be collected or crafted.

## Sky and gravity panels

A **Panel Receiver** powers connected low-gravity, high-gravity and zero-gravity panels and crafted Rayleigh Sky Panels. The nearest receivers pay first, and other receivers cover what they cannot. One receiver can handle thousands of panels.

- **Rayleigh Sky Panel:** placed in a ceiling, it lights the space below with clear noon sunlight (sky light, daylight detectors and sunlight-burning mobs all respond). World time and weather are unchanged. The Lab Rayleigh Sky Panel runs on facility power.
- **Zero-Gravity Block:** makes a weightless space above it. Jump to drift up and sneak to drift down. The height is 5 blocks by default and changes with a **Zero-Gravity Control Box**. Each block uses 30 FE/t at height 5, and each extra block of height multiplies that by 1.4. Control boxes joined through zero-gravity blocks share one height, and a broken box remembers it. Androids are not affected by gravity changes.

## Laboratory rooms

Every newly generated underground lab has at least one of a **Solarium**, planted under an artificial sky, or an **Outdoor Test Ground** for training. The observation hut chest at the test ground usually holds the Rayleigh Sky Panel Blueprint. Each of the eight **private rooms** has a **Room Light** in its ceiling, switched by the lever beside it (no facility power needed; craftable from the Lighting Blueprint).

Androids printed by Ruin Android Fabricators and drones launched by Guard Docks drop a few parts when killed by a player. Ones from spawn eggs and similar sources drop nothing.


### Expansion and storage

Thirty minutes of catalyst life, one optical lattice substrate and two steel plates make four Time Crystal Modules. Large SMES units use shaped crafting with a temporal component. A shaped upgrade takes an old cell at the centre and preserves its stored energy. After the initial BE and time catalysts are unlocked, projection cores and degenerate nuclei can be reconstructed in the Precision Assembler, so expansion need not repeat the same boss fights. Older generators can remain as auxiliary power.
