# 0.17.0 release review — 2026-11-12

Reviewed PR #66 against 1.21.1 base aac80aa8f379668d536374f98b82990c31abfff9. Original head: f6230540049e6a329c70bd460207345e5ac13eb4. Work took place in a separate worktree; the maintainer checkout and 0.16.0 release worktrees were not changed.

## Independent review and corrections

Three Astra reviewers examined fluid/storage behavior, drone/wormhole lifecycles, and packaging/test infrastructure. A second independent pass reviewed the resulting gameplay changes.

- Vault empty-hand extraction now reaches the real player interaction path; rapid second-click inventory deposit is preserved. Stack-aware rules reject packed containers and prevent filled vault nesting.
- Capsules pour before a machine menu consumes the click. No-space interactions pass through; partial transfer retains the remainder and empty capsules recover their normal stacking components.
- Source pumping no longer adds free space reported by aliases of a shared tank. One endpoint and route must accept a whole bucket. Tank-to-tank transfers are unchanged.
- Visiting drones cannot replace local flights at the same station coordinates. Persisted source positions allow intact cargo to return after a remote endpoint disappears, including stationless visitors and missing return mouths.
- Projectile transit respects the projectile's normal entity collision filter before the portal plane, including owner exclusion.
- The sentinel containment fixture now includes its displaced entity and target. Assertions and timeout are unchanged.
- Review init scripts skip projects without the mod plugin, including the new buildSrc. The release count guard now matches the reviewed test inventory.

## Local verification

- Full build and **732/732** regular GameTests passed; exact named source/report membership validated. The server shut down normally. Ten regression tests were added; the original 722 tests remain present.
- Python safeguards: **119/119** passed.
- Translation catalogs (3,539 entries each), five-language gameplay text, models, advancements and supporter checks passed. Generated resources were not edited.
- Java 21 distribution metadata, filename, loader and exclusion checks passed.
- Packaging inspection compared all main classes/resources with the JAR: JSON values and PNG pixels are preserved; no test classes or duplicate entries ship.
- The corrected rayleigh init script configured successfully with both Sodium and Distant Horizons flags. Actual GUI and optional-mod execution must still pass on final-source CI.

Local logs and exact result JSON are retained under build/review-017 in the review worktree. These local results do not replace successful final-source GitHub CI or authorize promoting a locally rebuilt JAR.

## Remaining scope

Formal PR CI must complete all three jobs before merge/release. Publish only its validated artifact and verify the final remote head and source tree. Large-network performance and prolonged multiplayer operation were not measured. No Modrinth task or publication setting was changed. The 1.20.1 Forge/NeoForge port requires its own validation and release evidence.
