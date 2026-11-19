package io.github.genichimaruo.singulo.api.item;

import io.github.genichimaruo.singulo.item.UsesItem;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/** A finite addon catalyst for tier-based Singulo recipes and machines. Register it and its shell
 * as ordinary items. The shell supplier is resolved only when a catalyst is exhausted. */
public class CatalystItem extends UsesItem {
    private final Supplier<? extends Spent> spent;

    public CatalystItem(Properties properties, int tier, int baseLifetimeTicks, Supplier<? extends Spent> spent) {
        super(properties, tier, baseLifetimeTicks, true);
        if (tier < 2 || tier > 5) throw new IllegalArgumentException("Catalyst tier must be between 2 and 5");
        if (baseLifetimeTicks <= 0) throw new IllegalArgumentException("Catalyst lifetime must be positive");
        this.spent = Objects.requireNonNull(spent);
    }

    public Spent spentItem() { return spent.get(); }

    /** Recognized by spent-catalyst outputs and by android collection, including after a reload. */
    public static class Spent extends Item {
        public Spent(Properties properties) { super(properties); }
    }
}
