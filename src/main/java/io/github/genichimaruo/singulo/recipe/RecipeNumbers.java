package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
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

    // Recipe quantities are independent of the item inventory's stack limit.
    static final Codec<ItemStack> STACK = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStack::getItem),
            POSITIVE.optionalFieldOf("count", 1).forGetter(ItemStack::getCount),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(stack -> Optional.ofNullable(stack.getTag()))
    ).apply(i, (item, count, tag) -> {
        ItemStack stack = new ItemStack(item, count);
        tag.ifPresent(t -> stack.setTag(t.copy()));
        return stack;
    }));

    static final Codec<FluidStack> FLUID = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidStack::getFluid),
            POSITIVE.fieldOf("amount").forGetter(FluidStack::getAmount),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(stack -> Optional.ofNullable(stack.getTag()))
    ).apply(i, (fluid, amount, tag) -> new FluidStack(fluid, amount, tag.map(CompoundTag::copy).orElse(null))));

    private RecipeNumbers() {}

    public static int energy(int base) {
        return (int) Math.ceil(base * (ServerConfig.SPEC.isLoaded() ? ServerConfig.MACHINE_ENERGY_MULTIPLIER.get() : 1.0));
    }
}
