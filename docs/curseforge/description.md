# Singulo

**From a thermocouple to a rotating black hole.**

Singulo is a technology mod for Minecraft 1.21.1 (NeoForge) and 1.20.1 (Forge or NeoForge).
Build a factory, recover lost technology from guarded ruins and work your way up through cryogenics, quantum physics and time crystals.
You ignite a black hole and draw power from its spin, and in the end you use that power to give birth to a universe.

The themes come from real physics: superconductivity, particle accelerators, the Casimir effect, Hawking radiation and the Penrose process.
The mechanics and numbers are tuned for gameplay, not for simulation.

---

## Features

### A five-stage tech tree

| Stage | What you do | What it unlocks |
| --- | --- | --- |
| **1. Startup** | Steel, ceramic, circuits and thermoelectric power | Kilns, compressors, electrolyzers and the Archive Terminal |
| **2. Cryogenics** | Cooling towers, particle accelerators and muon catalysts | Superconductors, cryogenic turbines, SMES storage and chunk loading |
| **3. Quantum** | Liquid helium and salvage from research buildings | Quantum components, laser cooling and exploration tools |
| **4. Temporal** | Time crystals and salvage from culture facilities | Degenerate compactors, exotic matter and degenerate furnaces |
| **5. Singularity** | Defeat the final guardian and awaken a singularity seed | The Penrose reactor, shields, time fields, gravity tools and wormholes |

You start with only a temperature difference.
A thermoelectric generator runs on a campfire, magma or lava next to water, snow or ice.

### Multiblock machines

Cooling towers, accelerator rings, presses, furnaces and the reactor are built from their own parts.
Each has a matching look and plays its own animation while running.

- A **Holo Projector** shows the shape and lists the missing parts.
- **Shared I/O ports** can replace marked panels and push out products automatically.
- Once a machine is built, any of its parts opens the controller screen.

### The Penrose reactor

Three gyroscopic rings hold a black hole in place.

1. Load a singularity seed and supply **50 GFE within ten seconds** to ignite it.
2. Feed it mass pellets and manage its mass and spin.
3. Spin it up to extract up to **2.1 GFE/t**.

Collectors around the core gather jet condensate, Hawking condensate and exotic matter.
A mass alarm sends a redstone signal when the core gets too heavy.

**Don't overfeed it.**
Throw enough extra matter into a full reactor and the frame is torn apart and swallowed.
A rogue black hole is left behind and slowly evaporates over several hours.

### Gravity and spacetime

- **Gravitational lensing:** the scenery behind a black hole bends in real time.
- **Graviton Manipulator:** a staff with a black hole at its tip. Lift creatures and blocks, then throw them.
- **Black hole bombs:** a short-lived black hole that pulls in everything nearby and eats blocks and fluids.
- **Gravity slingshot:** dive past a black hole with an elytra to gain speed.
- **Event horizon shields:** protect your base. A permit restricts building, breaking and taking items to the players you register.
- **T-Cylinders:** speed up time for the machines inside their field.
- **Wormholes:** generate, stabilize and place paired mouths that carry power, items and fluids across distances and dimensions.

### Exploration

Ruins of a lost civilization hide records and damaged components that you cannot simply craft.
Bring them home, decode and restore them to reach the next stage.

| Ruin | What you find |
| --- | --- |
| Observation Post | Observation logs and control units for superconductor and muon technology |
| Research Building | Quantum data and cold atom traps |
| Culture Facility | Culture data and time crystal seeds |
| Final Lab | Anomaly samples and the dormant singularity seed |
| Sunken Reactor Sector | Exotic matter, jet condensate and H-Condensate from a dead second reactor |

The **Horizon Warden** guards the Final Lab.
It sweeps the ground with crescents, fires a chest laser, grabs you with gravity and slams you down.
In its later phases it sends your projectiles back at you.
Deepest of all, the **Ergosphere Leviathan** swims around the dead core of the Sunken Reactor Sector:
a long serpent of reactor plates that dives through the chamber, drags you along its wake and breathes a vortex beam.
When it coils around the core to reignite it, only the shadow of a containment pillar will save you.
After the endgame, every boss can be refought in an awakened form with more health, harder hits and faster moves:
use an Awakening Record Core on the Final Lab's seal console, or an awakened core on the guardian core of the other ruins.
Awakened guardians drop chapters of records... and someone is still waiting to be read.

Bosses grow stronger with the number of challengers, and everyone's attacks land, so bigger parties win faster. Rewards are counted for the players present when the fight begins.
Each boss is tuned so that the gear you would normally have at that point can just about win. For a tougher fight, turn on the server setting `bossHardMode`.

Decoded records add the story of the lost civilization to your handbook.

### Androids

Ruin fabricators print androids that fight with an arm blade, a light whip or a saber, each with its own moves, warnings and openings.
They run on charge: when it runs out they head back to their fabricator, unfold a solar panel or sit down to recharge.
Defeat a fabricator's androids to recover a compact crystal memory of their design, then print your own guard android in an Android Assembly Station.
Choose how it guards (stay, wander or follow you), give it a name, self-repair kits and an enchantable armor plate, recharge it on the go with a wireless charger,
and keep it out of (or let it through) places with android barrier and gate glass.

On the 1.21.1 branch, androids can also take crafting, processing and mining orders from an **Android Work Console**.
Record recipes and procedures, keep items in stock or trigger orders with redstone, and let them farm, tend animals and operate blocks.
Upgrades add battery capacity, inventory space and fluid tanks; head, back/waist and hand decorations can be dyed.

### The final goal: the Genesis Engine

The old civilization tried to turn all mass into energy and perished. The Genesis Engine does the reverse.

1. Build a **Genesis Core** ringed by eight frames and twelve pylons, and plant a **Primordial Seed** made from a chapter of the Vortex.
2. Pour in **1.5 TFE** to kindle it, then hold **100 MFE/t** while a proto-universe stabilizes. Stop the feed when the surge alarm sounds, or the universe collapses and you lose progress.
3. Watch it be born: light falls into a point, a flash, and a galaxy unfurls and **rises into the overworld night sky** for every player.

Afterwards the engine performs **mass synthesis**: it turns power into items, from stone to nether stars. Rarer items cost orders of magnitude more power.

---

## Getting started

When you first join a world, you receive the **Singulo Handbook**.
It lists the goals for each stage.

Hover over any item to see what it does.
Hold **Shift** to see how it is made and what it is used for.
With **JEI** installed, you can also browse every machine recipe and multiblock layout.

- [English manual](https://github.com/GenichiMaruo/Singulo/blob/1.21.1/docs/player/en.md)
- [Japanese manual (日本語の説明書)](https://github.com/GenichiMaruo/Singulo/blob/1.21.1/docs/player/ja.md)
- [Recipe gallery with pictures](https://github.com/GenichiMaruo/Singulo/blob/main/docs/recipes/README.md)

## Requirements and downloads

Choose the file for your Minecraft version. The suffix **F** means Forge and **N** means NeoForge.

| Minecraft | Mod loader | Java | Files and source |
| --- | --- | --- | --- |
| **1.20.1** | **Forge 47.4.26** or later for 1.20.1 | **17** | [1.20.1F files](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.20.1&gameVersionTypeId=1) · [1.20.1 source and manuals](https://github.com/GenichiMaruo/Singulo/tree/1.20.1) |
| **1.20.1** | **NeoForge 47.1.106** or later for 1.20.1 | **17** | [1.20.1N files](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.20.1&gameVersionTypeId=6) · [1.20.1 NeoForge source and manuals](https://github.com/GenichiMaruo/Singulo/tree/1.20.1) |
| **1.21.1** | **NeoForge 21.1.256** or later for 1.21.1 | **21** | [1.21.1N files](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.21.1&gameVersionTypeId=6) · [1.21.1 source and manuals](https://github.com/GenichiMaruo/Singulo/tree/main) |

You can also filter the CurseForge Files tab by Minecraft version and mod loader.
JEI is optional and supported on both versions.

Multiplayer servers and clients both need the mod.
Recipes and many values can be changed in the server config.

A **Singulo HD** resource pack with 32×32 textures is included.

## Languages

English, Japanese (日本語), Simplified Chinese (简体中文), Traditional Chinese (繁體中文) and Korean (한국어).

## Support

If you enjoy Singulo, you can support its development on [Ko-fi](https://ko-fi.com/graycat9).
In game, the **Support** button at the top right of the handbook opens the same page.

## Feedback

Please report bugs and suggestions on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
The source code is on [GitHub](https://github.com/GenichiMaruo/Singulo).
