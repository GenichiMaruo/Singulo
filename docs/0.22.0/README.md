# Singulo 0.22.0

New androids have been added and can now be found in-game.

The underground ruin antechambers, ruin placement, Explorer Compass guidance, and gravity-remnant effects have been updated. World-generation changes apply only in newly generated chunks.

See the [release notes](../curseforge/changelog-0.22.0.md) for the Minecraft 1.20.1 Forge and NeoForge builds.

## Minecraft 1.20.1 backport

The backport targets Java 17, Forge 47.4.26 and NeoForge 47.1.106. It tracks the gameplay and resource changes from [PR #91](https://github.com/GenichiMaruo/Singulo/pull/91) as merged at [`1ed1723`](https://github.com/GenichiMaruo/Singulo/commit/1ed1723c727cc1a7dc4cfa4d261fdc732ad67d03), using 1.20.1 APIs and data formats.

The old ruin templates are retained byte-for-byte at the IDs saved by 0.21.1. Newly generated ruins use different template IDs. Test both loaders with the full 1.20.1 suite (1,011 required GameTests), then validate the distribution JAR and each loader's release metadata.
