# 0.20.0 Minecraft 1.20.1 port

Source: `38c90135f55d3f03b57a3c39ff82a09cacb53b32` (reviewed PR #79).

Targets Forge 47.4.26 and NeoForge 47.1.106, Java 17.

- Preserves legacy resource conversion, 1.20.1 enchantment/NBT APIs and existing integrations.
- Registry aliases are installed through each loader's ForgeRegistry during RegisterEvent.
- Glass path evaluation uses an android-specific BlockGetter view rather than the 1.21 PathfindingContext.
- Healing/harming effects are intercepted on the legacy MobEffect implementation, only for those two effects and Singulo bosses.
- Fishing upgrades accept Lure and Luck of the Sea through the legacy enchanting hook.
- Includes audited planting protection checks and both regression tests.
- 506 recipes independently normalized from the source commit match the generated port recipes.
- Required full suite: 839 tests per loader, plus existing GUI and real Mekanism gates. Publication must promote the verified CI artifacts without rebuilding.

Validation in progress; do not publish until the full CI and review gates pass.
