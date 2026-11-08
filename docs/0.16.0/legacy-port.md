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

Production and GameTest sources compile for Forge. Legacy source-layout, localization, gameplay text and model checks pass. Python release/tool checks pass. The first full Forge run exposed a lava-to-cauldron compatibility gap; final results must be recorded after the adapter and capability fixes are tested.

Release is **not ready** until final full suites for both loaders, GUI/integration checks and the reported spindle distortion occlusion fix have been verified. No merge, tag or upload is authorized by this report itself.
