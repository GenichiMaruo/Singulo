## Singulo 0.11.3 — Minecraft 1.20.1 (Forge / NeoForge)

For **Minecraft 1.20.1 / Forge or NeoForge**, Java 17.

- Fix black Distant Horizons terrain when shader packs are disabled. DH receives the standard 16×16 natural lightmap instead of Singulo's expanded artificial-sunlight texture. Verified against DH 3.3.3.
- Limit each Guard Dock wave to 2 drones at tier 1 and 3 drones at tiers 2–3 (previously 4 at tier 3).
- Drone bolts now use normal projectile damage, allowing armor to reduce their damage instead of bypassing armor as magic damage.
- Lower drone combat flight to roughly 1.25 blocks above the target's feet, with a 2.5-block flight ceiling above the ground or target. Drones descend from excessive heights and cannot fire from far overhead; damaged drones retain their lower flight ceiling.
- Remove knockback from drone bolts, rams and electromagnetic pulses while preserving the victim's existing movement. Pulse damage and slowness remain.

- Preserve machine recipe IDs across repeated world logins to prevent duplicate-recipe synchronization failures.

Also includes the 0.11.2 lighting snapshot and world re-entry fixes. On 1.20.1, optional accelerated terrain lighting is integrated with Embeddium 0.3.31 instead of Sodium 0.6.13. Third-party shader packs are outside this compatibility check.
