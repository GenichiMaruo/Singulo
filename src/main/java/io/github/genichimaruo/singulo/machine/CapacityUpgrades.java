package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Absolute minimum buffer sizes; tiers replace each other in a dedicated slot. */
public final class CapacityUpgrades {
    private static final int[] CAPACITIES = {0, 1_000_000, 4_000_000, 40_000_000};
    /** Looked up once: tier() runs on every hopper and android insert attempt. */
    private static Item[] items;
    private CapacityUpgrades() {}

    private static Item[] items() {
        Item[] cached = items;
        if (cached == null) {
            cached = new Item[4];
            for (int tier = 1; tier <= 3; tier++) cached[tier] = BuiltInRegistries.ITEM.get(Singulo.id("capacity_upgrade_" + tier));
            items = cached;
        }
        return cached;
    }

    public static int tier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item[] upgrades = items();
        for (int tier = 1; tier <= 3; tier++) if (stack.is(upgrades[tier])) return tier;
        return 0;
    }

    public static ItemStack item(int tier) {
        return new ItemStack(items()[tier]);
    }

    public static int capacity(int tier) { return CAPACITIES[tier]; }

    /** The smallest tier whose buffer holds {@code demand}, or 0 when even Mk3 is too small. */
    public static int requiredTier(int demand) {
        for (int tier = 1; tier <= 3; tier++) if (CAPACITIES[tier] >= demand) return tier;
        return 0;
    }
}
