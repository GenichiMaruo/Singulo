## Singulo-0.11.2-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. A compatibility fix for 0.11.1.

- Fix a crash while Sodium builds chunk meshes, including ordinary Minecraft blocks. Singulo no longer asks renderer snapshots for an unsupported live light engine.
- Rayleigh Sky Panels now preserve clear-noon lighting with **Sodium 0.6.13**, including Iris with shader packs disabled. Natural skylight, block light and artificial sunlight remain separate through Sodium's lighting interpolation and terrain shader, so rain or midnight outside does not darken the panel-lit area or turn the whole world into daytime.
- Sodium remains optional. Third-party shader packs replace the lighting shader and are outside this compatibility check.
