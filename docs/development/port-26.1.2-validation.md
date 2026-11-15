# 26.1.2 validation and publication boundary

This branch targets Minecraft 26.1.2, NeoForge 26.1.2.126 and Java 25. Mekanism integration is intentionally excluded, including its four optional tests. The standard suite remains mandatory: 815 Singulo tests with exact registered/result membership, plus the vanilla `minecraft:always_pass` test. `tools/check_release_2612.py` requires the 816-test completion and the explicit vanilla pass marker, then inspects the distribution JAR. A packaging-only check does not certify release readiness.

The PR workflow targets `feature/26.1.2` and `26.1.2`. Existing GUI, cosmic, occlusion, lensing, Sodium, Distant Horizons and Ponder evidence gates remain mandatory. Their screenshot counts and regression assertions are not relaxed. Development review sources and the common screenshot helper are never part of ordinary builds or distributable JARs. Test-world preparations use the 26.1.2 GameTest save directory `build/gametest/gametestserver/gametestworld`.

The publication workflows and their trusted provenance helpers have NOT been enabled for 26.1.2. They remain bound to their previous supported versions and must not be bypassed. A future authorized publication requires a separately reviewed 26.1.2 protocol, exact successful CI provenance, immutable tag checks, current platform game/loader metadata, receipt verification and duplicate-upload protection. No publication is performed by this port.

The user explicitly reauthorized local visual validation after an earlier stop. GUI captures (272, five languages), cosmic device captures (18, both standard and Sodium/Distant Horizons), cosmic GUI captures (35), 15 simultaneous lenses and nine foreground-occlusion conditions have passed. Sodium/Distant Horizons lighting passed all GPU ramps and the exact 84,000-pixel floor comparison; five reconnect cycles also passed. The standard-renderer floor comparison also passes after isolating light from the new vanilla rain fog: review-only environmental fog is fixed at 32..1024 for the four lighting comparisons, while a fifth capture retains real rain fog. Production weather/fog is unchanged. Static compilation and partial visual checks are not a substitute for the complete evidence matrix.

Create/Ponder has no verified 26.1.2 release at the time of this port. The Ponder gate must reject missing compatible dependencies, not reuse a 1.21.1 JAR or silently claim coverage. A verified compatible distribution is required before the full visual CI matrix can pass.

## Local visual evidence (2026-11-15)

- GUI: 272 captures across five languages; GUI pixel/layout checks passed (`review-gui-run1.log`, `gui-pixel-check.log`).
- Cosmic devices: 18 visible device captures, geometry and shader-lifetime checks passed in standard and Sodium/Distant Horizons clients (`review-cosmic-run3.log`, `review-cosmic-optional-run3.log`). Actual device-pixel readiness now prevents empty early captures.
- Cosmic GUI: 35 captures and region comparisons passed (`review-cosmic-gui-run1.log`).
- Lensing: 15 simultaneous holes plus a remnant; foreground occlusion in nine Fancy/Fabulous conditions passed (`review-lensing-run2.log`, `review-occlusion-run2.log`). The zero-height final mip bug is fixed with a minimum-dimension bound and independent scratch mip textures; 65 layout assertions cover aspect ratios and invalid inputs.
- Lighting: standard and optional clients passed four 256-texel lightmap comparisons, native AO/entity checks, exact 84,000-pixel floor comparison and power-off/recovery checks (`review-rayleigh-standard8.log`, `review-rayleigh-optional5.log`). Optional checks also cover 241 Sodium artificial-light encodings, emissive preservation and four DH natural-bank comparisons. A fifth image preserves actual rainy fog; only the four lighting comparisons use review-only fixed fog.
- Reconnect: five Sodium world exits/rejoins passed (`review-rejoin1.log`).

All current review options set both master and music volume to zero. Review-only startup code sets clear weather and `advance_weather=false` only for the disposable `Review` save. Dedicated weather tests may explicitly set rain afterwards. Neither ordinary player worlds nor operating-system audio settings are changed. These helpers are rejected from distributable JARs.

Full visual CI is still not claimed: official Create/Ponder dependencies have not been verified for 26.1.2. Existing worlds also require separate backup-based migration testing.
