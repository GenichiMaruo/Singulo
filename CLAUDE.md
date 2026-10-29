# Minecraft 1.20.1 branch notes

This branch targets Java 17, Forge 47.4.26 and NeoForge 47.1.106. Run Gradle with Java 21; the Java 17 toolchain compiles and runs Minecraft. Always pass `-Ploader=forge` or `-Ploader=neoforge`.

- `python3 tools/gen_data.py` generates legacy resources through `tools/legacy_data.py`; keep this conversion.
- Full server verification: `bash gradlew -Ploader=forge build runGameTestServer` (repeat with NeoForge).
- Focused debugging: `-PtestClasses=BalanceRevisionGameTests -PtestNamespace=singulo`; final validation must run the full suite.
- Required CI: both loader full suites and client GUIs; shared Mekanism and renderer checks run on Forge.
- Exact test membership: `tools/check_gametests.py`; Java 17 artifact gate: `tools/check_port_1201.py`.
- Publishing promotes tested artifacts without rebuilding. Publish both loaders to CurseForge; never attach mod JARs to GitHub releases.
- Current backport record: `docs/0.13.0/port-1.20.1.md`. Other 0.13.0 reports document the source 1.21.1 evaluation unless explicitly stated otherwise.
