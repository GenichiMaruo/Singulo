# Singulo 0.14.0 — Minecraft 1.20.1 / Forge + NeoForge

### Drone delivery
- New **Drone Station**, **Drone Port**, three **Packing Crates**, **Drone Kits** and **Drone Crystal Memories**. Stations print cargo drones (20,000 FE each, up to three with Production Upgrades) that carry crates between your ports in the same dimension.
- Sending costs 2,000 FE plus 50 FE per block. The crate decides the range: 160, 760 or 3,160 blocks. Arriving crates unpack automatically; emptied crates become available for reuse at the receiving port.
- Ports can auto-send once cargo reaches a threshold, use per-face side settings and auto-eject, and work beside Android Supply and Delivery Ports. Empty crates can be recovered by androids and pipes.
- Drones find their way around walls, fly a few blocks above the ground and show "No route" for sealed rooms. The **Phase Shift Upgrade** (stage 4) lets a station's drones take shortcuts through walls with a violet glow.
- Three drone types with their own models, effects and sounds: **rotor**, **jet** (1.6× speed, banks into turns) and **anti-gravity** (1.25×, tractor beams and floating bits). Memories drop from ruin Guard Docks; higher types only come from higher facilities.
- Cargo is never lost: drones flying through unloaded areas keep their cargo in the flight record and deliver when the destination loads.

### Machines and JEI
- **Capacity Upgrades** Mk1–Mk3 go in a dedicated slot and raise a processing machine's buffer to 1, 4 or 40 million FE.
- JEI shows machine recipes in the same layout as the machine screen, and the **+** button moves the ingredients straight into the machine.
- Side settings open facing the machine front, and every side settings popup has the same width.

### Balance
- **Multiblock structure parts now need the controller's stage.** Accelerator tubes use Superconducting Wire. Stage 4 structure parts use 1/48 of a BE Condensate Catalyst's lifetime per craft, and stage 5 parts use a Time Crystal Catalyst the same way (a few use a Time Crystal Module). Parts already placed keep working; only new crafts change.

### Tooltips and text
- Items with long descriptions now show a one-line summary; hold Shift for the full text, recipes and uses.
- The handbook, item descriptions and player guides cover drone delivery, capacity upgrades and the new part requirements in all five languages.

JAR downloads are available on CurseForge only.

Update both client and server to 0.14.0.
