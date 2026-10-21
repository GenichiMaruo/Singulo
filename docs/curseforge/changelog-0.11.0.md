## Singulo-0.11.0-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. JEI and Mekanism are optional.

### Recipes
- The **Peltier Cooler** has its own shaped recipe: a bucket and glass on top, thermocouple modules around the frame, and copper and a circuit below. It no longer shares the Thermoelectric Generator's ingredients.
- **Raw meteoric iron** can be smelted in the Kiln as well as the blast furnace.

### Teacup
- The **Teacup** can be placed as a block: right-click to set it down as a cup on a saucer. It keeps its dyed color and comes back as the same teacup when broken.
- The teacup is now fired: shape an **Unfired Teacup** from clay at a crafting table, then fire it in a furnace or the Kiln.
- The teacup looks like a real cup: its icon shows the tea sitting below the gold rim, and the cup an android holds is hollow, with tea inside and a ring handle.
- The Unfired Teacup and the Teacup sit next to each other with the android decorations in the creative tab.

### Umbrella
- The **Umbrella** icon is redrawn with ribbed panels, a scalloped edge, a tip and a curved handle.
- Held in third person, by a player or an armor stand, the umbrella now shows as an open 3D umbrella in its dyed color, held by its handle with the canopy above the head. Players raise their arm to chest height while holding it, so others see them gripping the handle beside the head. In the inventory and in first person it stays a flat icon.
- Both the held umbrella and the one an android holds have a canopy that is hollow underneath, stepping up toward the tip.
- Androids hold the umbrella tilted back over the shoulder, with a curved handle and rib tips.
- Android decorations no longer flicker when held, because their dyed and undyed layers no longer overlap.

### Machine fixes
- Processing machines now treat exhausted control units, restored parts and original records as item outputs. If the output slot is occupied, the spent item waits safely in its input slot and can still be collected by hoppers or item pipes. Automatic ejection also handles these returns when enabled, including through multiblock I/O ports. Usage and repair data are preserved; parts inserted for repair stay as inputs.
- Machines now resume correctly when you take the finished output and shift-click more materials into a partially filled input. Inventory edits now notify the real item handler, allowing processing recipes to be checked again.
- The same inventory notification fix also covers generator, catalyst-device, reactor and wormhole-device menus.

### Inventory
- Unused **Observation Logs**, **Quantum Data Fragments**, **Culture Data** and restored parts now stack to **64** when their usage and repair data match. Partly used items remain individual.
- Machine input slots accept **one** usage-tracked item. Shift-click also inserts one, leaving the rest of the stack in your inventory.

### Device relocation and logistics
- New **Relocation Wrench**: sneak-use to pack supported processing machines, generators, SMES and catalyst devices with their contents, fluids, energy and settings. Crafted from steel, copper and a basic circuit, with a dedicated ceramic-and-copper icon.
- New machines start with **automatic ejection off**. Turning the master switch on enables output through the prepared faces. Existing saved settings are retained.
- Hover a face in the **Sides** panel to see the adjacent block's name.
- Adjacent I/O ports belonging to different multiblock structures can now transfer outputs to each other.

### Underground Android Laboratory
- The pressure hull now wraps only the rooms and corridors, two blocks thick, instead of filling the whole facility area. There are still no gaps to dig in through from outside.
- A small white building now stands over the main shaft: the old entrance with a reception desk, entry gates and an elevator hall, slightly weathered. The ladder in the main shaft now leads straight out through the elevator door.
- A new **supply shaft** (3×3) always connects a dedicated **cargo lift room** to the surface. The room has hoists, rails, conveyors, a loading platform and storage. Both the main and supply shafts are guaranteed in generated labs; the cargo lift adds one mandatory room. It is welded shut just above the room and one block below the surface with **Welded Hatches**, which can be mined with a diamond pickaxe once the overseer is defeated. The shaft itself is intact, so ladders turn it into a shortcut.
- New room: **private rooms**, eight researchers' rooms lined up on both sides of a hall, each behind its own door with a bed, a desk and a few belongings.
- The cafeteria now has a **kitchen** behind a door, with a serving hatch, smokers, a sink and food storage.
- New **Lab Sliding Door**: white twin doors that slide into the walls when right-clicked, close by themselves once no one is passing through, and stay open while powered by redstone. Lab doors cannot be crafted.
- Once facility power is restored, the lab's **vending machines** work: hold emeralds and press the button under a column to buy Canned Soda (1 emerald, Speed), Sports Drink (1, Regeneration) or Energy Drink (2, Haste II). Vending machines you place yourself work right away.
- **Coffee makers** brew three cups from a cocoa bean; fill glass bottles from the pot to get Coffee (Haste, and it resets the time until phantoms appear). Lab coffee makers start with a full pot.
- Lab chests are fuller and hold everyday items that fit the room: kitchen supplies in the cafeteria, personal belongings in private rooms and dormitories (their lockers too), stationery in the office, first aid in the medical bay, and materials and research notes elsewhere, alongside the usual lab parts.
- Sculk veins no longer creep into the rooms when a lab generates under a deep dark (for example beneath mountains).
- Generated only in newly explored chunks; labs already generated are unchanged.

### White Sliding Door
- A craftable sliding door modeled on the lab doors (4 White Panels, a Steel Plate and Redstone at a Crafting Table, makes 2). It opens and closes the same way; set into a wall, the doors slide away inside it.

### Security drones
- Weaker overall: health 6 / 14 / 24 (was 10 / 24 / 40) by tier, ram damage 2 / 3 / 4 (was 3 / 5 / 7), bolt damage 1 / 2 / 3 (was 2 / 3.5 / 5), and the tier 3 pulse deals 4 (was 6).
- Below half health a drone is crippled: it smokes and sparks, and can no longer fly more than about a block and a half above the ground.

### Fixes
- Android attack effects that reach away from the body (bits and their beams, whip lashes and slam waves, axe shockwaves, slash trails) no longer vanish when the android itself is just off screen.
- The Graviton Manipulator's held sounds no longer seem to come from odd directions while moving.

### Textures and item order
- Item outlines are now a deeper shade of each item's own color instead of a dark gray-blue, on every item drawn by the mod.
- **Zinc ingots** are now a bluish metal with a crystal spangle pattern, so they no longer look like steel ingots.
- Items now sit in the middle of their slots:
  - ingots: steel, zinc, meteoric iron, bismuth and nihonium
  - powders: zinc dust, carbon dust, limestone powder and unfired phosphor (also 1 pixel narrower)
  - Stardust (1 pixel narrower)
  - Stardust Cluster (1 pixel shorter; the placed block is unchanged)
  - Coolant Cartridges (1 pixel shorter)
  - the Degenerate Precursor, redrawn symmetrically
- Circuit boards and modules share one 14×12 outline, and cards share one 14×10 outline:
  - boards and modules: basic circuit, quantum computing module, thermocouple, time crystal and scanner modules
  - cards: data, settings, work location, area, recipe and procedure cards
- Animated vials (Jet and Hawking condensates, anomaly samples) have a solid glass rim, and their liquid no longer pokes 1 pixel outside the vial when it sloshes.
- The five **Awakened Cores** have a glowing star at the center of the crystal.
- In the creative tab, ingots are grouped together in stage order, followed by plates, substrates, circuits and modules.

Published as **Beta**. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
