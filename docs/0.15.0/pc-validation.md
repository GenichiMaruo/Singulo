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

## Merge and publication judgment

Local automated checks pass. Keep merge/publication on hold until the final source receives the repository's required PR CI and the remaining manual coverage is accepted: real multiplayer/rejoin behavior for the new forge, actual chunk unload/reload at its boundary, and low-performance GPU behavior. The synthetic chunk-availability regression is not evidence of real chunk lifecycle coverage.

No PR, merge, approval, tag, publication, or upload was performed. PR creation remains outside the currently approved action scope. Local candidate files are not promoted CI release artifacts; follow `docs/development/releasing.md` for publication.

Logs and captures are retained in the task workspace's `handoff/` and `build/*-review/` directories.
