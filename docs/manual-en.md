# Singulo — English Manual

[README](../README.md) · [日本語](manual-ja.md) · [English](manual-en.md) · [简体中文](manual-zh-cn.md) · [繁體中文](manual-zh-tw.md) · [한국어](manual-ko-kr.md) · [Recipes](recipes/README.md)

## What is Singulo?

Singulo is a **technology mod that takes your factory from a small generator to a rotating black hole reactor**. Process materials, supply power and catalysts, build multiblock machines, and unlock increasingly advanced technology.

Exploration is part of that progression. Guarded ruins contain records and damaged components you cannot simply manufacture. Recovering and restoring them opens the next stage of your factory. Late-game devices add gravity manipulation, shields, time acceleration and remote logistics.

Superconductivity, particle accelerators, the Casimir effect and Hawking radiation provide the themes. Mechanics and numbers are adapted for gameplay; this is not a complete physical simulation.

## Requirements and installation

- **Minecraft 1.21.1**, with a compatible **NeoForge 21.1.256 or later** build for that Minecraft version. Minecraft runs on Java 21.
- Put the Singulo JAR in `mods/`. Multiplayer needs the mod on both the client and server.
- JEI is optional. Its integration shows machine recipes and multiblock construction information.
- Singulo supplies energy cables. Compatible transport mods can provide pipes for item and fluid automation.

For building from source, see the [development instructions in the README](../README.md#開発するには).

## Your first session

1. **Open the handbook.** You receive the Singulo Handbook on your first login. Right-click it and read Stage 1 and Progress for your first goals.
2. **Make steel and ceramic.** Craft Steel Blend from iron and coal, then smelt it in a blast furnace. Craft Unfired Ceramic from clay, Nether quartz, bone meal and sand, then fire it in a furnace.
3. **Make circuits and frames.** Steel, ceramic, copper and redstone form the early material base. Check the [recipe gallery](recipes/README.md) for exact quantities.
4. **Generate and distribute power.** Place a Thermoelectric Generator next to a supported heat source, such as a lit campfire, magma block or lava, and a cold source, such as water, a snow block or ice. Connect it to machines with Copper Wire.
5. **Build and explore together.** Establish a Kiln, Compressor, Electrolyzer and Archive Terminal. Use an Explorer Compass to find an Observation Post, bring back its records and damaged components, restore them, and work toward your first cooling tower.

Larger temperature differences produce more power, but exceeding the cold source's tolerance consumes or degrades water and ice. Check the generator screen rather than assuming a cold block lasts forever.

## The five stages

| Stage | Your focus | What it enables |
| --- | --- | --- |
| 1: Startup | Steel, ceramic, circuits and thermoelectric power | Firing, compression, electrolysis, copying records and repairing components |
| 2: Cryogenics | Cooling towers, accelerator rings and Muon Catalysts | Superconductors, cryogenic turbines, SMES storage, precision assembly and chunk loading |
| 3: Quantum | Research Building salvage and liquid helium | Quantum components, laser cooling, BE Condensate Catalysts, exploration tools and inertial control |
| 4: Temporal | Culture Facility salvage and time crystal growth | Degenerate Compactors, exotic matter from C-Cavities, Degenerate Furnaces and Probe Stations |
| 5: Singularity | Defeat the Final Lab guardian and awaken a singularity seed | Penrose reactors, Singularity Cores, shields, time acceleration, gravity tools and wormhole logistics |

These stages describe the material and technology progression. Unlocking an advancement does not replace the recipes, power supply or components you need.

## Power, logistics and catalysts

Energy is measured in FE. Upgrade from Copper Wire to Superconducting Cable, Topological Wire and the Horizon Bus as your factory grows. Copper loses energy, and the weakest cable limits a connected network's capacity.

Ordinary processing machines have a Sides screen for face-specific input, output and auto-ejection. Multiblocks use shared Multiblock I/O Ports; the Penrose reactor uses its own Extraction Ports. Singulo's cables carry energy, not items or fluids.

Catalysts are consumables placed in dedicated slots. The progression is Muon Catalyst, BE Condensate Catalyst, Time Crystal Catalyst, then Singularity Core. Matching tiers give normal operation. One tier below gives half speed and twice the wear; two or more below cannot run the machine. Higher tiers provide speed and wear bonuses. Depleted catalysts increase power requirements, and insufficient power can affect catalyst wear or product quality.

Time crystal growth defaults to **24,000 running ticks**, or 20 minutes at 20 TPS. It does not progress in unloaded chunks. Time fields and server configuration can change the effective duration. Spent catalysts have recycling recipes.

## Building multiblocks

Place the controller and **right-click it with a Holo Projector**. Follow the projected shape and missing-material list: fill cyan positions and clear red obstructions. Sneak-use the projector to cycle the size of adjustable structures. Once assembled, right-clicking a component can open the controller screen too.

| Machine | Footprint and key conditions |
| --- | --- |
| Cryogenic Cooling Tower | 5×5, 7–15 blocks high, with a narrow waist. Liquid helium requires height 10 or greater |
| Particle Accelerator | Horizontal square ring, side length 8–32. Magnets at all corners and at least one quarter of the perimeter; controller beside the ring |
| Degenerate Compactor | Closed 5×5×5 press |
| C-Cavity | 5×5×5 vessel with facing mirror plates inside the top and bottom |
| Degenerate Furnace | 5×5×7 furnace, powered by Lv2 compressed blocks and a Time Crystal Catalyst |
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

The Explorer Compass cycles between ruin types. Gravitational Wave Detectors and Neutrino Scanners provide further exploration support. After a cache is emptied, contents replenish by default after seven in-game days for Observation Posts and Research Buildings, or fourteen for Culture Facilities and Final Labs.

A Probe Station only visits ruins a player has discovered by opening their caches. With default settings, the stage 4 station automates Observation Posts. Culture Facilities and Final Labs always require manual exploration. Decode Record Fragments in the Archive Terminal and read the decoded items to add ancient stories to your handbook.

## Black holes and late-game technology

Insert your first Singularity Seed into the Core Controller, start ignition, and supply **50 GFE within ten seconds** under default settings. The formation sequence then creates the core. Prepare a high-capacity power network before attempting ignition.

During operation, feed Mass Pellets and manage core mass and spin. Spin determines accretion efficiency; mass affects the fuel injection limit. Collectors produce Jet Condensate, H-Condensate (Hawking Condensate) and exotic matter under their respective operating conditions. Pellet-driven growth is capped, but feeding other matter beyond safe limits can collapse the reactor and leave a rogue black hole.

Late-game devices protect a base, accelerate supported machines, keep chunks loaded and manipulate gravity. The Metric Drive works from your inventory and does not require a Curios equipment slot. Wormholes are paired mouths that you generate, stabilize and place; nearby ports link distant energy, items and fluids. Exotic matter maintains the mouths, and the destination chunks must remain loaded.

## Troubleshooting and hazards

- **A machine will not run:** check power, ingredients, catalyst tier and remaining life, output space and required fluids. For multiblocks, check the structure and port positions too.
- **Tool controls differ:** hold right-click for the Inertial Control Gauntlet. Hold left-click for the Graviton Manipulator; while levitating a target, charge right-click and release to throw it. Check your configured key bindings in-game.
- **The factory stops when you leave:** unloaded chunks do not process. Worldline Anchors also need their operating requirements maintained.
- **Dangerous equipment:** black holes exert pull, deal tidal damage and consume entities at the event horizon. Hydrogen leaks near fire can explode. Large accelerators with low stored energy can leak strangelets; prepare Magnetic Bottles and protective equipment.

Standard textures are 16×16. Enable the bundled **Singulo HD** resource pack for 32×32 textures. Accelerator sides and corners connect automatically, with a circulating light effect while operating.

This manual describes the current implementation and default settings. See the [recipe gallery](recipes/README.md) and [documentation audit](documentation-audit.md) for references. Server settings and data packs may change recipes or behavior; use the actual in-game information for your world.
