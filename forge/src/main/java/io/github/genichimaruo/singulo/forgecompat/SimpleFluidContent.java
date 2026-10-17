package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Forge 版: NeoForge の SimpleFluidContent と同じ形で使える、データ部品に入れる変わらない流体（種類と量）。 */
public final class SimpleFluidContent {
    public static final SimpleFluidContent EMPTY = new SimpleFluidContent(FluidStack.EMPTY);

    public static final Codec<SimpleFluidContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(SimpleFluidContent::getFluid),
            Codec.INT.fieldOf("amount").forGetter(SimpleFluidContent::getAmount)
    ).apply(i, (fluid, amount) -> copyOf(new FluidStack(fluid, amount))));

    public static final StreamCodec<RegistryFriendlyByteBuf, SimpleFluidContent> STREAM_CODEC =
            FluidStacks.STREAM_CODEC.map(SimpleFluidContent::copyOf, c -> c.fluid);

    private final FluidStack fluid;

    private SimpleFluidContent(FluidStack fluid) {
        this.fluid = fluid;
    }

    public static SimpleFluidContent copyOf(FluidStack fluid) {
        return fluid.isEmpty() ? EMPTY : new SimpleFluidContent(fluid.copy());
    }

    public FluidStack copy() {
        return fluid.copy();
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public Fluid getFluid() {
        return fluid.isEmpty() ? Fluids.EMPTY : fluid.getFluid();
    }

    public int getAmount() {
        return fluid.getAmount();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SimpleFluidContent other && fluid.isFluidStackIdentical(other.fluid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluid.getFluid(), fluid.getAmount(), fluid.getTag());
    }
}
