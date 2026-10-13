## Singulo-0.5.0-1.21.1N

### Boss rework
Every boss was retuned from logged playtests so that a player with the gear you would normally have at that point can just about win. The old strength is still available: turn on the new server setting `bossHardMode`.

- **Echo Sentinel** (unenchanted diamond gear): its bolts, projector lances and projection circles are now ordinary damage that armor reduces (they used to pierce armor). Damage: bolt 5, strike 8, lance 6, circle 8. Health 320 → 240. Before it enrages it fires bolts more often; after, it chains a second blink-strike 60% of the time (was 70%). It no longer casts its circle or lances at a player standing outside its boss room.
- **Gravity Remnant** (netherite chestplate, leggings and sword): its slam drops you from a lower height (fall damage ignores armor and caused most deaths). Slams come a little less often. Health 450 → 400. Its gravity well now runs in a cycle: it pulls for 4 s, charges for 1 s (glow and sound), bursts nearby players outward, then rests for 3 s while the Remnant drifts to another spot in the room.
- **Horizon Warden** (netherite with Protection III, Sharpness IV): health ×0.7 (840), attacks ×0.7, the armor-piercing beam and singularities ×0.4, slightly longer gaps between moves, a lower slam after its grip. Its singularities now warn for 1 s before they pull, pull gently enough to walk out of, and vanish when it dies.
- **Ergosphere Leviathan** (netherite with Protection IV, Feather Falling IV, Sharpness V):
  - After a dive grazes the floor it coils there for 3 s, an opening to strike back. The wyrm leaves its singularity where it rested as it takes off, not where it lands.
  - The two horn jets now aim at the distance you stand from it and can actually hit you. The breath and jets hurt more.
  - New attacks: **Crustal Quake** (it slams the center and two shockwaves race across the floor; jump them), **Gravity Rift** (three warned lines tear open, three volleys at new angles) and **Ebb Tide** (the core vortex drags everyone in; the core hurts).
  - New ultimate, **Core Reignition** (skeleton and wyrm forms): a titled warning, a 6 s charge, then a blast from the core across the whole room. Only the shadow of a containment pillar is safe. Rays of light stop at the pillars, so you can see the shadows and watch the light pass you by.
  - Health 1,600 → 1,000.
- **Last Archivist** (same gear as the Leviathan):
  - It now floats at a height you can reach (it used to hover about 6 blocks above the floor), so the nova's safe eye at its feet can be used. During a gravity tide it comes close, and you can move sideways while lifted.
  - Judgment beams track you until a lock sound, then fire; from the second phase they fire twice, and every challenger gets their own beam.
  - New attack, **Halo Sweep**: three expanding rings of light; jump the low ones and crouch under the high ones.
  - New ultimate, **Wings of Judgment** (final phase): six rotating wings of light fill the room; only their shadows are safe when the light falls.
  - Health 2,400 → 2,040.
- New effects for the new attacks are drawn as light, not only particles, so they read well from anywhere in the room.

### New
- **Reinforced Ruin Glass**: a blast-proof window with a symmetric pattern. It forms the second block of the Sunken Reactor Sector's containment pillars (in newly generated sectors).
- New server settings: `bossHardMode` (the 0.4.1 boss strength) and `bossCombatLog` (writes boss fight damage, by attack, to `logs/singulo-combat.log`; off by default).

### Fixes
- Boss drops, awakened rewards and experience now appear on the guardian core (on the seal console for the Horizon Warden) instead of where the boss died. They never despawn, and black holes do not swallow them, so loot is no longer lost through gaps in the floor.
- Warden singularities no longer keep hurting players after the Warden dies.

This file is published with the **Beta** release type. Please report issues on the [issue tracker](https://github.com/GenichiMaruo/Singulo/issues).
