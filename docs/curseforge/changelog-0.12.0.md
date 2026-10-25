## Singulo-0.12.0-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional.

### New GUI
- Every machine, generator, device and android screen now shares one framed layout: a header with the name, a status icon and the power button, the machine area, and your inventory centred below. Shared controls sit on tabs outside the frame, so the inventory stays in the same place on every screen.
- Long sentences are replaced by icons with short values; hover over them for the full explanation. Fluid levels are small bars, and empty dedicated slots show a greyed sample item.
- Side settings, the recipe picker and the work console's recipe registration, order confirmation and order details open as draggable popups, and the screen underneath stays usable.
- Wormhole devices, the Wormhole Stabilizer, the Genesis Core and the Penrose Reactor use a cosmic colour scheme. Android screens are tinted with the android's accent colour and have a chibi face in the header.
- FE values are shortened with K/M/G/T everywhere, including the Panel Receiver and Zero-Gravity Control Box messages. Hold Shift on a tooltip to see the exact value.
- Buttons now make a soft click when pressed.
- The recipe picker's row count and the work console's order list length can be changed in the client config.

### Android work
- New **Advanced Android Workbench** (stage 3) and **Quantum Android Workbench** (stage 4), each crafted from the previous tier. They craft 8× and 20× faster, have 54- and 81-slot holding boxes, and use less charge per craft.
- Multi-step orders gather all the materials in one trip, even when a recipe allows other variants.
- Processing recipe cards can record front, back, left and right faces relative to the machine's facing. Existing cards keep their faces.
- Recipe registration can allow other variants for each grid slot.
- Android blocks have a cute face on the front. It sleeps until it is in use: the Assembly Station wakes while it holds power, and workbenches, ports and the work console wake while an android works at them. The recipe rack wakes while one of its recipes is being crafted. Awake faces blink now and then.

### Androids
- In zero-gravity zones and in water, androids move freely in 3D: they fly to where they want to go, swim at about 3 blocks per second, never drown and are not pushed by currents. Thrusters on their backpacks and heels fire while they move.
- An android that runs out of charge in water inflates a float ring and heads for its station, its owner's charger or the nearest shore. If it can't reach one, it charges floating on the surface, or curled up on the seabed when there is no surface above.
- Androids keep fighting in water and zero gravity instead of getting stuck mid-jump.
- Lost health slowly comes back (1 every 5 seconds, using a little charge). While charging away from its station, an android uses its repair kit once its health drops below its retreat threshold.
- Gunner: the pistol has a 3-shot cartridge and reloads only when it is empty or the fight is over. Shots blocked by walls no longer count, and the gunner moves to get a clear line. The railgun shot is held back if the target hides while it charges. Damage is raised slightly (pistol 2.5 → 3, railgun 12 → 14).
- Androids have their own sounds for powering down, charging, the gunner's pistol and the railgun's assembly, charge and shot.
- The whip android's whip now shows in the inventory's full-body view.
- Tank lids on androids no longer flicker.

### Sounds
- Every sound has been remade: machines hum with layered motors, air and rhythm, and one-shots are fuller. The sound files are much smaller, so the JAR shrinks from about 12 MB to about 8 MB.
- The Foundry Overseer and the Ergosphere Leviathan's hatchlings have their own sounds instead of vanilla ones.

### Fixes
- Panel Receivers no longer store power. Panels work only while power keeps arriving, so breaking a Zero-Gravity Control Box can't leave panels running on stored energy.
- The Hydrogen Fuel Cell recipe is now shaped.
