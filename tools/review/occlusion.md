# Spindle occlusion regression

Run after GameTests have produced `build/forge/gametest/world/level.dat`:

```sh
python3 tools/review/prepare_occlusion.py --loader forge
bash ./gradlew -Ploader=forge --no-configuration-cache -I tools/review/occlusion.gradle runClient
```

On a headless Linux runner, prefix the client command with `xvfb-run -a -s '-screen 0 1280x800x24' timeout 300s`. On Windows use `./gradlew.bat`. Require `SINGULO_OCCLUSION_COMPLETE 9` in `build/forge/occlusion-review/logs/latest.log`; any assertion or missing completion marker is a failed/incomplete run.

The disposable fixture builds a real tidal spindle, then compares the *same frame* immediately before and after the lens pass. It runs a complete opaque checkerboard wall, a half wall, and an open positive control in Fancy, Fabulous, then Fancy again. Complete walls must be pixel-identical (RGB tolerance 2); the occluded side of half walls must be unchanged, while the exposed side and open control must render a lens. A source-count check and pipeline check prevent false passes with a missing spindle or a graphics-mode fallback. PNG pairs and `SINGULO_OCCLUSION_RESULT` record each state.

Before the fix on 1.21.1/NeoForge 21.1.256/NVIDIA, Fancy's full wall had 0 changed pixels; Fabulous's had 43,103 changed pixels in the central 480x400 region, including the black-hole shadow painted over the wall. Fabulous's final transparency post-pass clears the main target depth. Reading the translucent target's retained world depth fixes the occlusion while preserving final composited color and the Fancy path.

The fixture is development-only and must not be included in distribution JARs. Run the ordinary `build` without the init script after client review. Shader-pack fallback is a separate path and is not exercised here.

The 1.20.1 port uses Forge tick events and loader-specific build directories. Repeat with `--loader neoforge` and `-Ploader=neoforge` for legacy NeoForge. Renderer CI requires all nine results, the completion marker, and all 18 before/after PNGs.
