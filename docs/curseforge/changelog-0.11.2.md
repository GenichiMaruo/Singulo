## Singulo 0.11.2 — Minecraft 1.20.1 (Forge / NeoForge)

For **Minecraft 1.20.1 / Forge or NeoForge**, Java 17. A compatibility fix for 0.11.1.

- Fix a crash while Embeddium builds chunk meshes, including ordinary Minecraft blocks. Singulo no longer asks renderer snapshots for an unsupported live light engine.
- Fix a lighting deadlock when leaving or re-entering a world near Rayleigh Sky Panels, including without Embeddium. Sunlight rebuilds use already available chunks, and light-worker notifications no longer wait for the rebuild lock.
- Rayleigh Sky Panels now preserve clear-noon lighting with **Embeddium 0.3.31**, with shader packs disabled. Natural skylight, block light and artificial sunlight remain separate through Embeddium's lighting interpolation and terrain shader, so rain or midnight outside does not darken the panel-lit area or turn the whole world into daytime.
- Embeddium remains optional. Third-party shader packs replace the lighting shader and are outside this compatibility check.
- Keep the vanilla light include for ordinary rendering, while Embeddium terrain uses the dedicated lighting integration.
