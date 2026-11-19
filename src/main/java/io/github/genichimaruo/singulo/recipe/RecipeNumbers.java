package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.FluidInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.math.BigDecimal;

/** Read the original JSON number before converting to int, so out-of-range input cannot wrap. */
public final class RecipeNumbers {
    static final Codec<Integer> NONNEGATIVE = Codec.of(Codec.INT, Codec.PASSTHROUGH.flatMap(value ->
            value.asNumber().flatMap(number -> {
                try {
                    int n = new BigDecimal(number.toString()).intValueExact();
                    if (n >= 0) return DataResult.success(n);
                } catch (ArithmeticException | NumberFormatException ignored) {}
                return DataResult.error(() -> "Expected a whole number from 0 to 2147483647, got " + number);
            })));
    static final Codec<Integer> POSITIVE = NONNEGATIVE.validate(n -> n > 0 ? DataResult.success(n)
            : DataResult.error(() -> "Expected a positive quantity"));

    // The recipe describes a machine quantity. Normal player inventory stacks keep their vanilla codec.
    static final Codec<ItemStackTemplate> STACK = RecordCodecBuilder.create(i -> i.group(
            Item.CODEC.validate(item -> item.is(Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> "Expected a non-air item") : DataResult.success(item)).fieldOf("id").forGetter(ItemStackTemplate::item),
            POSITIVE.optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(ItemStackTemplate::components)).apply(i, ItemStackTemplate::new));

    static final Codec<FluidStackTemplate> FLUID = RecordCodecBuilder.create(i -> i.group(
            FluidInstance.FLUID_HOLDER_CODEC.validate(fluid -> fluid.is(net.minecraft.world.level.material.Fluids.EMPTY.builtInRegistryHolder()) ? DataResult.error(() -> "Expected a non-empty fluid") : DataResult.success(fluid)).fieldOf("id").forGetter(FluidStackTemplate::fluid),
            POSITIVE.fieldOf("amount").forGetter(FluidStackTemplate::amount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(FluidStackTemplate::components)).apply(i, FluidStackTemplate::new));

    private RecipeNumbers() {}

    public static int energy(int base) {
        return (int) Math.ceil(base * (ServerConfig.SPEC.isLoaded() ? ServerConfig.MACHINE_ENERGY_MULTIPLIER.get() : 1.0));
    }
}
