# Singulo — English Manual

[README](../README.md) · [日本語](manual-ja.md) · [English](manual-en.md) · [简体中文](manual-zh-cn.md) · [繁體中文](manual-zh-tw.md) · [한국어](manual-ko-kr.md) · [Recipes](recipes/README.md)

## What is Singulo?

Singulo is a **technology mod that takes your factory from a small generator to a rotating black hole reactor**. Process materials, supply power and catalysts, build multiblock machines, and unlock increasingly advanced technology.

Exploration is part of that progression. Guarded ruins contain records and damaged components you cannot simply manufacture. Recovering and restoring them opens the next stage of your factory. Late-game devices add gravity manipulation, shields, time acceleration and remote logistics. The final goal is the Genesis Engine, which uses a black hole's power to create a universe.

Superconductivity, particle accelerators, the Casimir effect and Hawking radiation provide the themes. Mechanics and numbers are adapted for gameplay; this is not a complete physical simulation.

## Requirements and installation

- **Minecraft 1.21.1**, with a compatible **NeoForge 21.1.256 or later** build for that Minecraft version. Minecraft runs on Java 21.
- Put the Singulo JAR in `mods/`. Multiplayer needs the mod on both the client and server.
- JEI is optional. Its integration shows machine recipes and multiblock construction information.
- Singulo supplies energy cables. Compatible transport mods can provide pipes for item and fluid automation.

For building from source, see the [development instructions in the README](../README.md#開発するには).

## Your first session

1. **Open the handbook.** You receive the Singulo Handbook on your first login. Right-click it and read Stage 1 and Progress for your first goals.
2. **Make steel and ceramic.** Craft Steel Blend from iron and coal, then smelt it in a blast furnace. Craft Unfired Ceramic from clay, Nether quartz, bone meal and sand, then fire it in a furnace. Before visiting the Nether, two flint can replace the quartz.
3. **Make circuits and frames.** Steel, ceramic, copper and redstone form the early material base. Check the [recipe gallery](recipes/README.md) for exact quantities.
4. **Generate and distribute power.** Place a Thermoelectric Generator next to a supported heat source, such as a lit campfire, magma block or lava, and a cold source, such as water, a snow block or ice. Connect it to machines with Copper Wire.
5. **Build and explore together.** Establish a Kiln, Compressor, Electrolyzer and Archive Terminal. Use an Explorer Compass to find an Observation Post, bring back its records and damaged components, restore them, and work toward your first cooling tower.

Larger temperature differences produce more power, but exceeding the cold source's tolerance consumes or degrades water and ice. Check the generator screen rather than assuming a cold block lasts forever. Magma block + blue ice (about 76 FE/t) and campfire + packed ice (about 55 FE/t) never melt; lava + blue ice gives about 106 FE/t but the ice starts melting within about 40 seconds. Two to four generators running one machine at a time are enough to start.

Cooling tower heat exchange cores and coolant bands can use three packed ice instead of blue ice.

## The five stages

| Stage | Your focus | What it enables |
| --- | --- | --- |
| 1: Startup | Steel, ceramic, circuits and thermoelectric power | Firing, compression, electrolysis, copying records and repairing components |
| 2: Cryogenics | Cooling towers, accelerator rings and Muon Catalysts | Superconductors, cryogenic turbines, SMES storage, precision assembly and chunk loading |
| 3: Quantum | Research Building salvage and liquid helium | Quantum components, laser cooling, BE Condensate Catalysts, exploration tools and inertial control |
| 4: Temporal | Culture Facility salvage and time crystal growth | Degenerate Compactors, exotic matter from C-Cavities, Degenerate Furnaces and Probe Stations |
| 5: Singularity | Defeat the Final Lab guardian and awaken a singularity seed | Penrose reactors, Singularity Cores, shields, time acceleration, gravity tools and wormhole logistics |

After stage 5, awakened rematches, a hidden boss and the final goal, the Genesis Engine, are waiting.

These stages describe the material and technology progression. Unlocking an advancement does not replace the recipes, power supply or components you need.

## Power, logistics and catalysts

Energy is measured in FE. Upgrade from Copper Wire to Superconducting Cable, Topological Wire and the Horizon Bus as your factory grows. Copper loses energy, and the weakest cable limits a connected network's capacity. The Horizon Bus has no limit: between Singulo devices it can move more than about 2.1 billion FE per tick (other mods' devices still accept up to about 2.1 billion FE/t each). Only cables that are actually carrying power glow, with a gentle wave running in the direction of flow; the brightness follows the amount of power.

Ordinary processing machines have a Sides screen for face-specific input, output and auto-ejection. Machines that output two gases, such as the Electrolyzer, let you choose the output faces for each gas. The power button on the right of the screen stops a machine, and machines that can make something from power alone, such as liquid nitrogen in the cooling tower, have a switch to skip those recipes. Multiblocks use shared Multiblock I/O Ports; the Penrose reactor uses its own Extraction Ports. Singulo's cables carry energy, not items or fluids.

Catalysts are consumables placed in dedicated slots. The progression is Muon Catalyst, BE Condensate Catalyst, Time Crystal Catalyst, then Singularity Core. Matching tiers give normal operation. One tier below gives half speed and twice the wear; two or more below cannot run the machine. Higher tiers provide speed and wear bonuses. Depleted catalysts increase power requirements, and insufficient power can affect catalyst wear or product quality. The Quantum Heat Engine and Degenerate Furnace give 25% of their rated output on a catalyst one tier below. These rules apply to devices running from a catalyst slot; a catalyst listed as a recipe ingredient just uses a fixed count or amount of life and does not change processing speed.

Generators (Cryogenic Turbine, Quantum Heat Engine, Degenerate Furnace) wait when their storage is nearly full and use no nitrogen, fuel or catalyst until it drops below 75%.

Time crystal growth defaults to **14,400 running ticks**, or 12 minutes at 20 TPS. It does not progress in unloaded chunks. Time fields and server configuration can change the effective duration. Purity is kept at 80% of the requested power or more; time below that shortens the crystal's life. Spent catalysts have recycling recipes.

Equipment such as the Core Controller, Singularity Encapsulator, Shield Tower Core and Wormhole Generator Core uses stackable **Time Crystal Modules** from the Precision Assembler. Each cuts two thirds of a fresh crystal's life (30 minutes by default) from the crystal in the catalyst slot; the rest (15 minutes by default) stays usable. Crystals with less than that left cannot be cut. Changing catalyst lifetime settings scales the amount.

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

A Probe Station only visits ruins a player has discovered by opening their caches. With default settings, the stage 4 station automates Observation Posts. Culture Facilities, Final Labs and Sunken Reactor Sectors always require manual exploration. Decode Record Fragments in the Archive Terminal and read the decoded items to add ancient stories to your handbook.

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
- The station has a refill slot (tops up repair kits automatically) and an upgrade slot (a **Transfer Upgrade** passes a fallen android's inventory to the next one), and hoppers can insert into it.
- Name your android in the station or android screen, and read its health with a comparator. If the station is broken, Link the android to a new one.

**Android glass:** Barrier Glass (only androids cannot pass) and Gate Glass (only androids can pass), each in a plain kind that joins with its neighbours and a patterned kind.

## Black holes and late-game technology

Insert your first Singularity Seed into the Core Controller, start ignition, and supply **50 GFE within ten seconds** under default settings. The formation sequence then creates the core. Prepare a high-capacity power network before attempting ignition: 50 GFE in ten seconds averages 250 MFE/t, and a network is capped by its thinnest cable, so a topological-wire network only delivers 20 GFE. While igniting the screen shows the intake and what will arrive, and a failure reports what was missing.

A new core starts in **harvest mode**: it keeps mass at 480 or below, so H-Condensate forms every five minutes with a Hawking Collector. The first Singularity Core needs six (four directly, two to restore the Anomaly Sample). Turn harvest mode off afterwards for full power. Harvest mode also sheds 2 mass per second from an overgrown core.

During operation, feed Mass Pellets and manage core mass and spin. Spin determines accretion efficiency; mass affects the fuel injection limit. Collectors produce Jet Condensate, H-Condensate (Hawking Condensate) and exotic matter under their respective operating conditions. Pellet-driven growth is capped, but feeding other matter beyond safe limits can collapse the reactor and leave a rogue black hole.

**Bosses and rematches:** the Echo Sentinel guards the Research Building, the Gravity Remnant the Culture Facility, the Horizon Warden the Final Lab and the Ergosphere Leviathan the Sunken Reactor Sector; each cache stays sealed until its guardian falls. After the endgame, awakening cores made in the Singularity Encapsulator (Awakening Record Core, Awakened Echo, Gravity and Ergosphere Cores) can be used on the seal console or a guardian core to fight an awakened version (2.5× health, 1.5× damage, faster moves) as often as you like. Victory adds Overclock Chips and more to the usual drops. If you give up and leave, the core is returned.

**Chapters of records:** the three awakened guardians (Echo Sentinel, Gravity Remnant, Horizon Warden) drop chapters of records, and the Ergosphere Leviathan drops the Chapter of the Vortex. Bind all four into a Terminus Record in the Singularity Encapsulator and use it on the Final Lab's seal console, and something that slept in the time field wakes up. The chamber is sealed until the fight ends, and some attacks change gravity, so prepare for falls. The Genesis Engine also needs one chapter of the Vortex, so collect extras if you want both.

**Playing together:** bosses grow stronger with the number of challengers in the room (each extra player adds 50% health and slightly shortens their attack gaps, up to six players). Everyone's attacks land independently, so a larger party defeats a boss faster. Drops come once per challenger present when the fight begins; players who join later do not add more. Server settings `bossHealthPerPlayer`, `bossMaxScaledPlayers` and `bossLootPerPlayer` adjust this. Each boss is tuned so that the gear you would normally have at that point can just about win; the server setting `bossHardMode` makes them stronger. Boss drops appear on the guardian core in the floor (on the seal console for the Horizon Warden), and boss experience is split evenly among the challengers in the room and goes straight to them (the total, like the drops, counts the challengers present when the fight begins; Mending still works).

Late-game devices protect a base, accelerate supported machines, keep chunks loaded and manipulate gravity. The Metric Drive works from your inventory and does not require a Curios equipment slot. Wormholes are paired mouths that you generate, stabilize and place; nearby ports link distant energy, items and fluids. Exotic matter maintains the mouths, and the destination chunks must remain loaded.

## The final goal: the Genesis Engine

The old civilization tried to turn all mass into energy and perished. The Genesis Engine does the reverse: it uses a black hole's power to make a universe.

1. **Build:** place eight Genesis Frames level with the Genesis Core, three blocks out on each axis and two blocks out on each diagonal. Stack three Genesis Pylons on each of the four diagonal frames (twelve in all). One recipe makes two frames. The Holo Projector and the Builder Wand support it.
2. **Primordial Seed:** craft it in the Singularity Encapsulator from a Chapter of the Vortex and use it on the core.
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

This manual describes the current implementation and default settings. See the [recipe gallery](recipes/README.md) and [documentation audit](documentation-audit.md) for references. Server settings and data packs may change recipes or behavior; use the actual in-game information for your world.
