# Minecraft 26.1.2 NeoForge port

This isolated `feature/26.1.2` branch starts at 1.21.1 commit
`0f6fcff7645a2a5066a072acce55d4a03884080e` (Singulo 0.19.0).
Existing 1.21.1 / 1.20.1 branches and releases are unchanged.
No merge, upload, tag or publication dispatch has been performed for this port.

## Toolchain

- Minecraft 26.1.2; NeoForge 26.1.2.126; JDK 25; Gradle 9.8.0;
  ModDevGradle 2.0.148.
- JEI 29.53.0.115. Sodium 0.9.2 (official version zg4YQ9EL), whose nested
  implementation is extracted for compile-only access, never bundled.
- Mekanism integration is excluded as requested. Both Mekanism-specific test
  suites are explicitly excluded; the remaining suite is not silently reduced.
- Development artifact: `Singulo-0.19.0-26.1.2N.jar`.

## Implemented migration

- Modern identifiers, registry IDs, entity construction, interaction/equipment/
  consumable APIs, networking, recipe access and ingredient schemas.
- Transactional native item, fluid and energy capabilities, including rollback,
  nested transactions, components, long energy amounts and commit-only callbacks.
  Android shared storage has a separate explicit legacy-compatible capability;
  it is not falsely advertised as transactional storage.
- ValueInput/ValueOutput persistence preserving original save keys; SavedData
  migrates old flat files to namespaced files without deleting originals.
  Corrupt existing new files do not silently reset or revive an older snapshot.
- Raw attachment-list migration preserves opaque item/component data before
  ordinary decoding. Pocket, ancient records, prototype memories and ruin
  discovery are covered.
- ItemStackTemplate and FluidStackTemplate keep recipe decoding independent of
  bootstrap component initialization. Legacy ingredient-card JSON remains readable.
- Render extraction snapshots, native GPU pipelines and uniforms, entity models,
  item definitions, lighting, postprocessing, JEI and GUI API migrations.
- GameTests register through TEST_FUNCTION/test instances, retain assertions,
  clean up batch resources, and produce independent exact-membership evidence.

## Verification record

Main Java compilation, production JAR assembly, full GameTest compilation and
headless dedicated-server startup pass. The full suite contains 815 custom
required tests plus Minecraft's optional built-in always-pass test (whose success is also checked). The final full run
passes all 815 custom tests plus the built-in test, with independent exact-source
membership validation and the Java 25 distribution gate passing.
`build runGameTestServer` succeeds; custom test execution took 33.005 seconds.

Independent checks completed:

- Energy transactions: 21 checks, including long amounts and rollback.
- Legacy attachment migration: 16 checks, including opaque-data preservation,
  source immutability, idempotence and round trips.
- Clipping primitive: 9 assertions.
- Validation Python suite: 140 tests; distribution inspection passes.
- Assets: 2523 JSON files parsed, 524 item definitions generated idempotently,
  model references resolved and 13 spawn-egg colors retained.
- Addon integration on the real 26.1.2 base JAR: Arsenal 65 custom tests pass;
  Applied Singulo integration with AE2 26.1.13-beta passes (47 required total).

Regression testing caught and corrected SMES charging writeback, sealed-container
break behavior, recipe-key identity, death-event damage attribution, reactor fixture cleanup, and paired
factory geometry bookkeeping. These were not hidden by reducing test coverage.

## Remaining validation boundaries

The client reached its title screen after startup fixes. The user stopped
computer control before in-world visual QA, so no further GUI was launched.
World rendering, optional Sodium/JEI visual behavior and the complete screenshot
review gates remain unverified. Static compilation is not a visual pass.

The PR workflow is ported to Java 25 and the exact 815-test gate while
retaining visual review checks. Publication workflows are deliberately not
executed and still require a separate 26.1.2 release-path review before publishing.
The optional Create/Ponder review has no available official 26.1.2 dependency
at verification time. It stays fail-closed rather than loading an old 1.21.1 JAR
or dropping its assertions; therefore complete CI is not claimed.
No claim is made that arbitrary existing 1.21.1 worlds can safely be upgraded;
focused persistence tests are not a substitute for backup-based world migration QA.

## Reproduction

Use JDK 25. Run `gradlew build runGameTestServer --no-daemon --console=plain`,
then `python tools/check_gametests.py` and
`python tools/check_release_2612.py --loader neoforge --build-dir build`.
Exact membership, required status, no duplicate results and no retries are checked.
Local diagnostic logs are in `build/port-diagnostics/` and are not shipped.

## Final local artifact

`build/libs/Singulo-0.19.0-26.1.2N.jar`

SHA-256: `0B42F241C71ECF36C1DDEBD5D5303DCF79547522C5C2FC5166015A493A04AD25`

Passing evidence is retained locally as `port-final.log`,
`final-results.json`, and `final-server.log` under `build/port-diagnostics`.
This artifact is built and headless-tested, not published or visually certified.
