package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Absolute minimum buffer sizes; tiers replace each other in a dedicated slot. */
public final class CapacityUpgrades {
    private static final int[] CAPACITIES = {0, 1_000_000, 4_000_000, 40_000_000};
    private CapacityUpgrades() {}

    public static int tier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (int tier = 1; tier <= 3; tier++)
            if (stack.is(BuiltInRegistries.ITEM.get(Singulo.id("capacity_upgrade_" + tier)))) return tier;
        return 0;
    }

    public static ItemStack item(int tier) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("capacity_upgrade_" + tier)));
    }

    public static int capacity(int tier) { return CAPACITIES[tier]; }

    public static int requiredTier(int demand) {
        for (int tier = 1; tier <= 3; tier++) if (CAPACITIES[tier] >= demand) return tier;
        return 3;
    }
}
