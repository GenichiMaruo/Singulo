# Minecraft 1.20.1 port review (2026-11-08)

This report covers the isolated 1.20.1 Forge / NeoForge port. Other files in this directory record 1.21.1 evidence and must not be treated as 1.20.1 release evidence.

## Source

- Legacy baseline: `2456947c534050891d6f0cc17967ca5db0fd971a` (`origin/1.20.1`, freshly fetched).
- Feature source: `ec97c0bea131a9690b532cc4c60af298a75ea2a2`.
- Feature delta starts at common modern ancestor `dd06f2152981c8704de59419547a83fb5f7dad9b`.
- Preserve the legacy Java 17 build, Forge 47.4.26 / NeoForge 47.1.106, plural data directories, release verification and duplicate-publication guards.
- All validation runs on GenPC-Main; GitHub Actions is not used as validation evidence.

## Adaptations and review

- Port cosmic chambers, fluid pipes, wormhole passage and cassette features using legacy NBT, menu buffers, registries, capabilities and event lifecycles.
- Packed cosmic controllers set the existing legacy packed-device marker, preserving the single-item stack limit and contents across placement.
- Regenerate legacy source/data from generators. Retain unrelated checked-in PNGs instead of committing Windows/Pillow encoder and font differences.
- Refresh cached Forge capability optionals when machine side settings change, including settings-card paste. Retain the dispatcher so subsequent lookups recover correctly.
- Provide vanilla cauldron fluid endpoints missing from 1.20.1 Forge. Only complete buckets change the discrete vanilla levels; simulation and stale block handles must not mutate the world.
- Port client rendering and review harnesses; delayed jet rendering must use captured model-view rather than the legacy AFTER_LEVEL projection stack.

## Validation status

Local pre-occlusion checkpoint: `c492ed3b46f6138b4d4f9bbc75127fe9d3b3e76e`.

- Forge 47.4.26: **719/719** regular GameTests pass (280.333 seconds).
- NeoForge 47.1.106: **719/719** regular GameTests pass (345.127 seconds).
- Both distributions pass exact named membership, Java 17, loader metadata, resource layout, mixin/refmap and development-code exclusion checks.
- Focused regressions: **69/69**, including cosmic storage/packing, cassette, wormhole passage, pipe transport, cauldron parity and retained capability invalidation.
- Python safeguards: **63/63** after the final occlusion source adaptation.
- Legacy source layout, three translation catalogs (3,458 entries each), five-language gameplay text and model checks pass.
- Initial pre-adapter Forge run: 715/716 passed; the lava-cauldron failure was fixed rather than excluded. Later suites include three additional legacy regressions.

Later changes are the private-only cleanup (`69ec99f2`, parity with modern `7de9831`) and Fabulous occlusion correction plus legacy nine-state regression (`043c3f96`, parity with modern `9202b1fc`). The old full-suite evidence is not represented as exact-head evidence for those later commits. Native visual review is in progress; final-tree CI must produce the artifacts promoted for publication.

Release remains **not ready** until final-source CI, remaining GUI/integration checks and independent review pass. No merge, tag or upload is authorized by this report itself.
