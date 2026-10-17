package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

/** Forge 版: 「飛べる」能力値（{@link FlightRegistry}）。登録が済んだあとに初めて読まれる所でだけ使う。 */
public final class Flight {
    private Flight() {}

    public static final Holder<Attribute> CREATIVE_FLIGHT = FlightRegistry.CREATIVE_FLIGHT.getHolder().orElseThrow();
}
