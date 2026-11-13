# Singulo 0.18.0: Minecraft 1.20.1 port

Targets Java 17, Forge 47.4.26 and NeoForge 47.1.106. Distribution filenames remain `Singulo-0.18.0-1.20.1F.jar` and `Singulo-0.18.0-1.20.1N.jar`.

## Compatibility decisions

- Port the reviewed 0.18.0 android order API, decorations, QIO integration, item charging, food printer, RGB dye station and conduit cover frames. Preserve the legacy renderer coordinate/blending fixes and atomic publishing guards.
- Store new item state in the existing codec-backed NBT compatibility layer. Dye colors use legacy `display.color` and the dyeable item interface.
- Send RGB and food-printer actions via a full-width integer packet. Vanilla 1.20.1 menu-button packets truncate IDs to one byte. The server checks the active container, menu type, distance and spectator state. Regression tests cover full RGB values and invalid menu/container rejection.
- QIO automation checks the official legacy Mekanism security capability and effective PUBLIC mode. Missing security fails closed. Cache values are weak references; item accounting snapshots before insertion. Fluid totals accumulate in long values.
- Recipe parity is verified against the reviewed upstream commit after legacy JSON normalization, with all 430 recipe SHA-256 values recorded in `recipe-parity-1.20.1.json`.
- Keep only actual artwork changes: regenerated PNGs with identical RGBA pixels retain their original encoding.

## Required release evidence

The official PR workflow requires both loader builds, all 787 ordinary GameTests, the five optional Mekanism tests, 272 GUI screenshots (250 machine views across five languages plus 22 standard views), pixel checks and the existing renderer integration checks. Publication must use successful CI artifacts, not these local development JARs.
