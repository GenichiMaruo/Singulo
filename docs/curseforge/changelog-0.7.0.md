## Singulo-0.7.0-1.20.1F

Minecraft **1.20.1**, **Forge 47.4.26**, Java **17**. This backport includes the 0.7.0 automation, decorations and review fixes from the 1.21.1 edition.

### Android upgrades
- **Production Upgrade** (station): prints and looks after up to two androids. Only one charges on top at a time; the other waits beside it.
- **Battery Upgrade** (android, up to two): each adds 50% to the full charge. A battery pack shows on the waist.
- **Backpack Upgrade** (android): adds 24 slots to the android's new 5-slot inventory. A backpack shows on its back.
- **Mining Upgrade** (android): lets it mine in work orders, like a diamond pickaxe. Takes Efficiency, Fortune and Silk Touch.
- The android screen has a "Gear and inventory" page for upgrades, and a switch for whether the android may be given work.

### Work automation
Let your androids craft, smelt and mine for you.

- **Android Work Console:** the chests next to it are the stockpile. Link **Supply Ports** (more stockpile), **Delivery Ports** (named destinations) and **Recipe Racks** to it with a **Work Location Card**.
- Order an item and amount at the console. Orders need every material in stock; intermediate materials are made too, and androids only use the materials that were reserved when the order started.
- Androids gather everything for a batch in one trip, craft step after step without going back to the stockpile, and deliver to the console's outbox (moved into adjacent chests) or a named Delivery Port.
- **Recipe Cards** are recorded in the console's Record tab, in three modes:
  - **Crafting:** a 3×3 pattern, made at a crafting table or the new **Android Workbench** (four times as fast).
  - **Processing:** furnace and machine recipes with counts and up to two byproducts. Androids put items in from above, wait, and take the results out from below.
  - **Procedure:** up to eight insert, extract and mine steps on a **Procedure Card**, which keeps the places on the card. Androids with a Mining Upgrade dig an area until they have enough (works with a cobblestone generator), skipping unbreakable, container and shield-protected blocks. While mining they swing a glowing pickaxe.
- JEI's + button fills the Record tab for any recipe. Scroll over a slot to change counts.
- New handbook pages: Upgrades, Work automation, and Processing, procedures and mining.

### Saved orders, farming and fluids
- Save up to 16 orders: keep stock, run once when a redstone signal arrives, or keep stock while powered. Orders wait for missing materials; the progress view shows each step and the android's current work.
- Procedure cards also support harvesting and replanting, tree felling and replanting (requires a **Felling Upgrade**), breeding, shearing, milking, slaughtering and block interaction (levers, buttons and doors).
- Record fuel and the item input/output faces for processing. Androids defend themselves while working and greet their owner and nearby androids.
- **Tank Upgrade:** adds two tanks of 32,000 mB each for moving liquids and gases between stockpile tanks and machines. Record fluids with a container or JEI's + button.

### Decorations and recipe variants
- The android screen's third page equips head decorations (ribbon, hair ornament, beret, headdress), back/waist decorations (cape, scarf, waist ribbon) and hand decorations (paperback, bouquet, teacup, umbrella). Craft them at a crafting table and dye them.
- Added shaped crafting recipes for the recipe rack, supply and delivery ports, workbench and work cards, alongside the work console's Precision Assembler recipe.

### Reliability fixes
- Work and reservations survive saving and reloading, including partial processing and deliveries. Transfer preserves work cargo and fluids.
- Workers stop unpaid processing when charge runs out and respect protected blocks and interactions.
- Linked menus recheck permission changes; long order details scroll independently.
- Required regression tests and real-client GUI validation run before publication.

This file is published with the **Beta** release type. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
