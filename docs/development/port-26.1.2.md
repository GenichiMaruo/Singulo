# Minecraft 26.1.2 NeoForge port checkpoint

**Status: INCOMPLETE. Main Java compilation and development JAR creation pass.
Full tests and runtime compatibility are not yet verified. Not a release.**

This isolated `feature/26.1.2` branch starts at 1.21.1 commit
`0f6fcff7645a2a5066a072acce55d4a03884080e` (Singulo 0.19.0).
Existing 1.21.1 / 1.20.1 branches and their releases are unchanged.
No merge, upload, tag or release dispatch has been performed for this port.

## Selected and resolved toolchain

- Minecraft 26.1.2; NeoForge 26.1.2.126.
- JDK 25, Gradle 9.8.0, ModDevGradle 2.0.148.
- JEI 29.53.0.115 APIs resolved and compiling; runtime integration remains unverified.
- Mekanism dependencies and its source package are excluded for this target,
  as requested. No Mekanism API is required or bundled.
- Sodium 0.9.2 for Minecraft 26.1.2 (official Modrinth version zg4YQ9EL):
  its nested implementation JAR is extracted for compile-only API access.
  It is never bundled into Singulo. Runtime integration remains unverified.

Official NeoForge/Minecraft resolved sources were used to inspect actual APIs.
`createMinecraftArtifacts`, `compileJava`, and `jar` now pass. The generated
`Singulo-0.19.0-26.1.2N.jar` is for dependent-addon development only.
The first full GameTest compile exposed 566 remaining test-source API errors;
these are being ported without weakening assertions. A successful JAR build is
not proof of client/server startup, visual correctness, or old-world safety.

### Independently executed checks

- Energy native transaction checks: 21 passed (including long amounts/rollback).
- Legacy attachment-shape migration: 16 passed (opaque data preserved, source
  unchanged, idempotence, and round trips for pocket/records/discoveries).
- Client clipping primitive: 9 assertions passed in a headless harness.
- Generated assets: 2523 JSON files parsed; 524 item definitions generated
  idempotently; custom model references resolved and 13 spawn-egg colors retained.

These are focused checks, not substitutes for the full GameTest/runtime gates.

## Work retained in this checkpoint

- Identifier and relocated class APIs, interaction results, explicit NBT defaults,
  UUID codecs and item stack registry codecs.
- Block entity ValueInput/ValueOutput bridge retaining existing save keys.
  Entity additional-save methods similarly use ValueInput/ValueOutput without
  nesting their data under a new key. These paths need round-trip tests.
- ItemStackHandler ValueIO serialization helpers; old persistence keys are kept.
- Survey glasses equippable component and swap semantics.
- GUI extraction/input event first pass, immutable GUI/custom-geometry snapshots,
  small entity renderer extraction, jet-beam RenderPipeline and GLSL uniform work.

These are migration candidates, not verified functionality. In particular,
ValueIO codecs, defaults and error reporting must be reviewed against malformed
and legacy saves before use in a real world.

## Required next work (do not bypass)

1. Replace removed ItemHandler/EnergyStorage/FluidHandler capabilities with
   NeoForge transactional ResourceHandler/EnergyHandler APIs. Keep external
   Android shared-storage API compatibility where possible; no unsafe fake
   legacy capability with the same resource identifier.
2. Complete entity changes, recipes/ingredients, saved-data codecs, registrations,
   equipment components, networking and remaining ValueIO serialization calls.
3. Complete large entity render states/models/layers, GPU post-processing,
   CoverModel/ModelData, BE item models, JEI, GUI and mixin targets.
4. The custom-geometry snapshots do not retain world/entity references, but
   existing renderers writing directly to GlowDepth global buffers still need
   conversion. Validate ordering, state isolation, clipping and performance.
5. Port GameTests to TEST_FUNCTION plus test_instance data while retaining the
   existing tests, assertions and deployment exclusion. Run the entire suite.
6. Audit all resources/data pack formats, models, equipment, spawn eggs, shaders,
   translations and metadata; Java compilation alone is insufficient.
7. Build and test client/dedicated server with and without optional integrations.
   Validate old-world inventory/energy/fluid/owner data; compare gameplay and
   rendering with 1.21.1 before offering this as compatible.
8. Provide a real built base-mod JAR to Arsenal and Applied Singulo, then test
   both add-ons together. Do not use the old 1.21.1 JAR as runtime proof.

## Reproduction

Use JDK 25 and run `gradlew compileJava --no-daemon --console=plain`.
Full diagnostic logs remain local in `build/port-diagnostics/` (not shipped).
The final diagnostic count is recorded in the commit/task report. No passing
build or test result should be inferred from this checkpoint.
