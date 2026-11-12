# Singulo development context

Start with [docs/README.md](docs/README.md) and [docs/development/README.md](docs/development/README.md).

- This checkout targets Minecraft 1.21.1, NeoForge and Java 21. Check the active branch and `gradle.properties` for its development version.
- Historical investigations include [Create/Ponder compatibility in 0.14.1](docs/0.14.1/issue-52.md) and [balance and performance in 0.13.0](docs/0.13.0/README.md).
- Open questions and completed fixes: [issue.md](issue.md).
- Build and test commands: [docs/development/testing.md](docs/development/testing.md).
- Edit generation sources in `tools/`; regenerate `src/generated/`. Keep all five language catalogs synchronized.
- New or changed machine recipes must be shaped and use meaningful component placement.
- Preserve inventories and settings; do not drop overflow products or duplicate stored energy.
- Do not change version numbers, merge, tag, or publish without a task authorizing it. Distribution JARs go to CurseForge, not GitHub Releases.

- [0.14.0 drone logistics](docs/0.14.0/drone-logistics.md) and [Android integration](docs/0.14.0/android-drone-integration.md).
- [0.15.0 Accretion Jet Forge and black hole rework](docs/0.15.0/README.md).
- [0.16.0 cosmic machines and wormhole seams](docs/0.16.0/implementation.md).
- [0.17.0 fluid logistics and Collapsar equipment](docs/0.17.0/README.md).
