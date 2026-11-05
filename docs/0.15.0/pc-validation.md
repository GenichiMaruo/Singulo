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

- `Singulo-0.15.0-1.20.1F.jar`: SHA-256 `cd8539bec978f2719cb0fd93b1414747070cd5253ea1a37a354dea64fca8e012`.
- `Singulo-0.15.0-1.20.1N.jar`: SHA-256 `6cbe9efebfab6798242fa67ba58f4bec606a80c1ea914eef1fb835350a1ef0c6`.

Both were copied from their normal production builds after the final source passed its full 677-test suite and distribution gate. Development clients started later do not replace these preserved files.

## Merge and publication judgment

Keep merge and publication on hold until required PR CI and the remaining manual coverage are accepted: actual forge chunk unload/reload at a real boundary, multiplayer behavior for the new forge, and a low-performance GPU. The synthetic availability override does not prove a real chunk lifecycle. GPU runs here used an NVIDIA RTX 4070 SUPER.

No target branch was merged, no PR was created or approved, and no tag, publication, or upload was performed. Local review-worktree synchronization is not a merge into the user's target branches. Local JAR candidates are not promoted CI release artifacts. Follow `docs/development/releasing.md` before publication.
