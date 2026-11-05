# 0.15.0 PC validation — 2026-11-05

The handoff ZIP matched SHA-256 `bfbaa596abbdc4dabe86478f9b5cf1f09fce6aa43eb81a8f68f1da0069851f09`.
The mbox was applied once to `6aea2a6c` in an independent local repository. All 4,343 tracked files initially matched the bundled 1.21.1 source archive byte for byte. The original `D:/projects/Singulo` checkout was clean and was not modified.

## Additional corrections

- The stopped-beam fixture now explicitly initializes a nonzero energy demand. Previously it forced the irradiation phase with the default zero demand, so its assertion never exercised an unpowered beam.
- The PNG checker now requires the exact cosmic inventory fill, RGB `(21,27,49)`, instead of expecting standard grey slots. All 700 sampled forge inventory slots pass.
- Added a development-only real-GPU five-hole regression in `tools/review/lensing.gradle`. Thirty rendered frames contain four shader-traced holes, one fallback hole, and a competing remnant distortion. The exported image was inspected: all five holes remain visible.

## Passed locally

| Check | Result |
| --- | --- |
| Python verification tests | 47 |
| Translation catalogs, gameplay text, models, advancements, supporters | Pass |
| Final normal production build and full GameTests | 662 / 662 |
| Exact test membership, Java 21, JAR metadata, no development classes | Pass |
| Real GUI capture | 262 PNGs; 5 languages; PNG/font/inventory checks pass |
| JEI layouts, actual transfer, popup input, capacity GUI | Pass |
| Real Mekanism integration | 1 / 1 |
| Lighting fixture GameTests | 9 / 9 |
| Sodium + Distant Horizons | Vertex/emissive/lightmap assertions and four captures pass |
| Lighting PNG comparison | 84,000 pixels: artificial noon equals natural noon and restores after power cycle |
| Same-process re-entry with Sodium | 5 / 5 |
| Create/Ponder | With and without Sodium; light-state cleanup and actual PNG checks pass |
| Five-hole and remnant coexistence | Pass on NVIDIA RTX 4070 SUPER |

The preserved local candidate is `Singulo-0.15.0-1.21.1N.jar`, SHA-256 `e71e518396ec1a5c16e2b9ff7f3720426e5114590d0b7ba715498c6a72852b7d`. It was copied from the normal build that passed the full suite; later development-client runs do not replace it.

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
