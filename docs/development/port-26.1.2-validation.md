# 26.1.2 validation and publication boundary

This branch targets Minecraft 26.1.2, NeoForge 26.1.2.126 and Java 25. Mekanism integration is intentionally excluded, including its four optional tests. The standard suite remains mandatory: 815 Singulo tests with exact registered/result membership, plus the vanilla `minecraft:always_pass` test. `tools/check_release_2612.py` requires the 816-test completion and the explicit vanilla pass marker, then inspects the distribution JAR. A packaging-only check does not certify release readiness.

The PR workflow targets `feature/26.1.2` and `26.1.2`. Existing GUI, cosmic, occlusion, lensing, Sodium, Distant Horizons and Ponder evidence gates remain mandatory. Their screenshot counts and regression assertions are not relaxed. Development review sources and the common screenshot helper are never part of ordinary builds or distributable JARs. Test-world preparations use the 26.1.2 GameTest save directory `build/gametest/gametestserver/gametestworld`.

The publication workflows and their trusted provenance helpers have NOT been enabled for 26.1.2. They remain bound to their previous supported versions and must not be bypassed. A future authorized publication requires a separately reviewed 26.1.2 protocol, exact successful CI provenance, immutable tag checks, current platform game/loader metadata, receipt verification and duplicate-upload protection. No publication is performed by this port.

Local visual validation was stopped by the user. Static compilation and headless checks are not a substitute for running the complete visual evidence gates on an authorized CI environment. Do not claim the graphics port fully validated until those gates succeed.

Create/Ponder has no verified 26.1.2 release at the time of this port. The Ponder gate must reject missing compatible dependencies, not reuse a 1.21.1 JAR or silently claim coverage. A verified compatible distribution is required before the full visual CI matrix can pass.
