package io.github.genichimaruo.singulo.data;

import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Reads the world capacity on use. A smaller capacity only blocks additional fills. */
public class ConfiguredFluidTank extends FluidTank {
    private final IntSupplier limit;

    public ConfiguredFluidTank(IntSupplier limit, Predicate<FluidStack> validator) {
        super(1, validator);
        this.limit = limit;
    }

    @Override public int getCapacity() { return limit.getAsInt(); }
    @Override public int getTankCapacity(int tank) { return getCapacity(); }
    @Override public int getSpace() { return Math.max(0, getCapacity() - getFluidAmount()); }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        setCapacity(getCapacity());
        return getSpace() == 0 ? 0 : super.fill(resource, action);
    }
}
