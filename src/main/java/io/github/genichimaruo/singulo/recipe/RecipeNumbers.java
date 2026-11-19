package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
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
    static final Codec<ItemStack> STACK = RecordCodecBuilder.create(i -> i.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
            POSITIVE.optionalFieldOf("count", 1).forGetter(ItemStack::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(ItemStack::getComponentsPatch)).apply(i, ItemStack::new));

    static final Codec<FluidStack> FLUID = RecordCodecBuilder.create(i -> i.group(
            FluidStack.FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
            POSITIVE.fieldOf("amount").forGetter(FluidStack::getAmount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(FluidStack::getComponentsPatch)).apply(i, FluidStack::new));

    private RecipeNumbers() {}

    public static int energy(int base) {
        return (int) Math.ceil(base * (ServerConfig.SPEC.isLoaded() ? ServerConfig.MACHINE_ENERGY_MULTIPLIER.get() : 1.0));
    }
}
