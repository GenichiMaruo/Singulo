## Singulo-0.11.2-1.21.1N

For **Minecraft 1.21.1 / NeoForge**, Java 21. A compatibility fix for 0.11.1.

- Fix a crash while Sodium builds chunk meshes, including ordinary Minecraft blocks. Singulo no longer asks renderer snapshots for an unsupported live light engine.
- Renderer snapshots retain their own packed lighting. Singulo's separate artificial-noon lightmap remains available through the vanilla world and chunk-region rendering paths; this fix does not add Sodium or Iris shader support for that effect.
