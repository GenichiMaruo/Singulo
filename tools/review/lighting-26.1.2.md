# Lighting review harness migration: 26.1.2

- Rayleigh, rejoin and Ponder sources compile against the 26.1.2 / NeoForge 26.1.2.126 classes; independent javac passed on 2026-11-15.
- No client window was started. GPU colors, screenshots, renderer mixin application and external-mod compatibility are **not runtime verified**.
- Rayleigh reads the actual GPU lightmap through development-only OpenGL readback, preserving the 256-pixel natural/noon comparisons, weather/night separation, native NeoForge AO checks, entity light, power-off/on and terrain screenshots. NativeImage comparisons use ARGB consistently.
- Ponder validates actual natural texture selection, 256 GPU texels, nested scopes and exception cleanup. Legacy global texture-unit/filter assertions have been replaced by texture-view identity checks because 26.1 uses explicit views and per-draw samplers, not mutable lightmap texture-unit binding. Actual deferred Ponder drawing still needs a compatible Ponder build and visual verification.
- Official Modrinth 26.1.2 NeoForge artifacts verified: Sodium `zg4YQ9EL` (0.9.2), Distant Horizons `QHxoccsN` (3.3.3). DH reflective adapter behavior has not been exercised against this version.
- Official Create query returned no 26.1.2 NeoForge release. Ponder review therefore no longer loads the old 1.21.1 Create jar by default: `runClient` fails explicitly unless compatible jars are supplied via `-PponderModsDir=...`. Static compilation remains possible.
- Sodium 0.9.2 moved `ColorHelper` to `net.caffeinemc.mods.sodium.client.render.helper`; the production mixin target and review import now use that actual class. `maxBrightness(int,int)` signature was checked with javap. Actual transformed invocation remains a client-runtime test.
