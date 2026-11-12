# 0.17.1 / Minecraft 1.20.1

Port of PR #69 and review fixes at cfd40b4d to Forge 47.4.26 and NeoForge 47.1.106, Java 17. Base: 1e8f1cbb.

- Preserve legacy BufferBuilder, PoseStack and captured model-view matrix; adapt view-depth quad routing and its 15-case client fixture.
- Use legacy tick phases, NBT update tags and typed stack data; preserve generated legacy resource conversion (forge_data).
- Preserve the existing BlockItem tooltip delegation and add four registered ItemStack-path regression tests from PR #68.
- Add three handover regression tests and update the exact inventory to 761 required tests, plus two optional Mekanism tests.
- Python tool tests: 78 passed. Static translations/models/supporters/advancements/recipe parity/gameplay text/legacy source checks passed.
- Full local loader tests and formal CI are pending at initial PR creation. Do not publish until all four required CI jobs pass on the final tree and its artifacts are promoted without rebuilding.

No release tag or upload is part of this port. Existing Modrinth review and publication sequence remain untouched.

Legacy coordinate verification: Forge 47.4.26 source GameRenderer lines 1125-1132 rotates the BER pose; LevelRenderer lines 1256-1274 adds block-camera translation. DepthSplit therefore tests emitted -z, and transforms only the world-relative core using the legacy lens view matrix. This intentionally differs from the 1.21.1 forward-dot implementation. Renderer CI now executes the cosmic-world fixtures with Embeddium/Distant Horizons and requires all 15 depth cases.
