# 0.15.0 legacy PC validation — 2026-11-05

## Restoration and isolation

The handoff ZIP matched SHA-256 `bfbaa596abbdc4dabe86478f9b5cf1f09fce6aa43eb81a8f68f1da0069851f09`. The port mbox was applied once on `c0e091cf`; all 4,383 tracked files initially matched the bundled source archive byte for byte. Original user checkouts were not edited. Forge and NeoForge validation use independent review worktrees because switching loaders in one checkout can remove the previous task's class output.

## Corrections found on this PC

- Removed 39 duplicated block registrations that prevented compilation.
- Converted the remaining renderer vertex calls and GameTest block-entity casts to the 1.20.1 APIs.
- Initialized a nonzero power demand in the stopped-beam regression, so it actually exercises the unpowered hazard path.
- Corrected the inventory PNG checker to require the exact cosmic slot fill `(21,27,49)`.
- Made the Gradle GameTest verifier accept `-PpythonExecutable=<path>` and run Python in UTF-8 mode on Windows.
- Replaced Singulo's legacy config watch with a dedicated JDK watch service. NightConfig 3.6 ignores coalesced modify notifications; Windows external edits were not being applied. Reload still runs on the Minecraft client thread. The GUI regression now performs a distinct external edit and checks both the notification and resulting value.
- Corrected the camera basis in gravitational lensing. Legacy `Camera.rotation()` faces local +Z; the projection faces -Z. The raw inverse camera rotation culled all four accepted traced holes as behind the camera, leaving only fallback geometry visible. The view now matches `GameRenderer`'s `Rx(pitch) * Ry(yaw + PI)`.
- Explicitly bound the legacy lensing and beam shader attributes. The GPU regression checks beam attribute locations and actual pixels for five distinct black-hole shadows, not just accepted-source counts.

The failed one-hole image, corrected five-hole image, first failing test reports, and later passing reports are retained in the task workspace. No failing checks were disabled.

## Validation

| Check | Result |
| --- | --- |
| Python verification tests | 35 / 35 |
| Translations, gameplay text, models, advancements, supporters | Pass |
| Forge full suite before final renderer correction | 677 / 677 |
| NeoForge full suite before final renderer correction | 677 / 677 |
| Final production Forge/NeoForge suites and JAR gates | 677 / 677 on each loader; Java 17, metadata and no development classes validated |
| Forge GUI | 262 PNGs; 5 languages; exact inventory/font/tooltip checks pass |
| NeoForge GUI | 262 PNGs; same checks pass |
| Distinct external client-config edit | Pass on both loaders, on client thread |
| Real Mekanism integration on Forge | 2 / 2 |
| Forge JEI layouts, actual material transfer, popup input, capacity GUIs | Pass |
| Embeddium + Distant Horizons on Forge | Vertex/emissive/lightmap assertions and four captures pass |
| Lighting PNG comparison | 84,000 pixels exactly match noon and restore after a power cycle |
| Create/Ponder + Embeddium | Mechanical press and chute; light-state cleanup and PNG checks pass |
| CI Ponder profile: Embeddium + Oculus + Alex's Caves | Both scenes and lighting/PNG checks pass |
| Five holes plus competing remnant distortion | Pass on Forge and NeoForge: four traced + one fallback visible; beam attribute locations correct |
| Same-process re-entry with Embeddium | 5 / 5 |

The GUI fixtures were rerun in a fresh disposable world after retained player inventory from an earlier failed run contaminated the PNG sampling. Only the clean 262-image runs are accepted.

## Preserved local candidates

- `Singulo-0.15.0-1.20.1F.jar`: SHA-256 `72c169dfe6064efcd561249d94a7472761d3796c1318a1f188bff7ca68db70d3`.
- `Singulo-0.15.0-1.20.1N.jar`: SHA-256 `f4ddbcfc88bdabded315322bac5a633cd86200eeabb26508f7cd8ed8807ab2d5`.

Both were copied from their normal production builds after the final source passed its full 677-test suite and distribution gate. Development clients started later do not replace these preserved files.

## Real chunk lifecycle and two-client regression

The isolated dedicated server binds only to loopback. The controller at (4095,80,4095) spans six actual chunks across a boundary. Removing the tail's FULL-only region ticket makes it inaccessible (ticket level 34); 100 explicit production ticks preserve progress and energy without loading that chunk. Restoring the ticket resumes work. The neighbor dependency retains its generation cache, so this partial eviction does not emit ChunkEvent.Unload and is not claimed as a physical disk unload.

Removing all footprint tickets does emit the controller unload event. Reload creates a new block entity and preserves progress, consumed energy and reserved inventory. Two real Minecraft clients then connect over TCP, open synchronized menus, move away to cause a second natural player-driven unload, return, and verify persisted state. Client A disconnects and reconnects while B stays online and observes both events. Both reopen the actual server menu; the reserved batch remains intact. All three loader/version targets complete this regression.

The first legacy active-beam run exposed an actual client crash: POSITION_TEX_COLOR vertices were supplied as position/color/UV. The legacy renderer now writes position/UV/color, matching BufferBuilder's element order. Both legacy loaders pass the active-beam multiplayer regression after this correction. Their production JARs and full 677-test suites were rebuilt after the fix; older legacy candidate hashes are obsolete.

The clients run at 10–15 FPS and 30 FPS with render distance 4 on an RTX 4070 SUPER. This exercises slower presentation and asynchronous client/server progress, but does not establish low-VRAM, weak-GPU shader performance, old-driver compatibility, WAN latency, or large-player-count behavior.

Initial legacy client-A login attempts timed out during concurrent startup, before entering the world. Clean client-A restarts completed the two-client regressions; initial attempt logs are retained. The cause of those startup handshake timeouts is not established, and this result does not claim every simultaneous cold-start connection succeeds.

The final NeoForge JAR gate detected an old LensingReviewHarness.class left in incremental compilation output. That candidate was rejected. The completed test results were retained; only the isolated production main-class output was cleared, and the unchanged production source was rebuilt with the build cache disabled. The final distribution gate must pass before packaging, including absence of all review classes.

## Merge and publication judgment

The local functional merge gate is satisfied, including real chunk lifecycle, persistence, active beam rendering, and two-client synchronization/reconnection on all three targets. Keep final merge/publication on hold pending required remote PR CI and acceptance of the documented GPU/hardware coverage limits. PR creation remains unauthorized. These locally tested candidates are not promoted CI release artifacts.

No target branch was merged, no PR was created or approved, and no tag or release was published. The final ZIP is saved separately to Library when that operation succeeds. The original user repository remains unchanged. Logs, results and captures are included in the review package.
