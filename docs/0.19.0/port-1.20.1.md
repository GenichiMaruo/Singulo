# 0.19.0 / Minecraft 1.20.1

The Forge 47.4.26 and NeoForge 47.1.106 builds share the Android endpoint,
recipe snapshot, request escrow and shared-storage APIs from the 1.21.1 branch.
Both artifacts target Java 17 and use the existing legacy resource conversion.

- Item identity uses item and NBT equality; copied recipe and custody snapshots
  retain the original NBT without exposing mutable internal stacks.
- Item stacks and block positions retain the Minecraft 1.20.1 NBT representation.
  Work-console links continue to be loaded as compound tags.
- Escrow persistence uses the legacy `SavedData` factory and `save(CompoundTag)`
  override. The registry-aware overload remains available to round-trip tests.
- Fluid copies and equality use the existing `Legacy` helpers, preserving fluid
  tags while adapting the NeoForge 1.21 API calls.
- Full release verification requires both loader suites and GUIs, plus Forge
  Mekanism and renderer integration jobs. The release workflow promotes those
  exact CI artifacts without rebuilding.
- Disposable review clients mute only music. The audio regression covers ten
  review configurations for each loader, including the legacy JEI review client.

The public contracts and recovery limits are documented in
[Android integration](android-integration.md) and [Android escrow](android-escrow.md).
