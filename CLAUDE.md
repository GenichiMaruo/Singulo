# Singulo development context

Start with [docs/README.md](docs/README.md) and [docs/development/README.md](docs/development/README.md).

- The current record covers `feature/0.13.0`, Minecraft 1.21.1 / NeoForge / Java 21. Confirm the actual branch and `gradle.properties` before changing code.
- Current changes and evidence: [docs/0.13.0/README.md](docs/0.13.0/README.md). Read the latest balance adjustment before the preserved baseline reports.
- Open questions and completed fixes: [issue.md](issue.md).
- Build and test commands: [docs/development/testing.md](docs/development/testing.md).
- Edit generation sources in `tools/`; regenerate `src/generated/`. Keep all five language catalogs synchronized.
- New or changed machine recipes must be shaped and use meaningful component placement.
- Preserve inventories and settings; do not drop overflow products or duplicate stored energy.
- Do not change version numbers, merge, tag, or publish without a task authorizing it. Distribution JARs go to CurseForge, not GitHub Releases.
