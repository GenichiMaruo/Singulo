package io.github.genichimaruo.singulo.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Vanilla cauldron endpoint parity: only whole buckets can change discrete water/lava levels. */
public final class LegacyCauldron implements IFluidHandler {
    private final Level level;
    private final BlockPos pos;
    public LegacyCauldron(Level level, BlockPos pos) { this.level = level; this.pos = pos.immutable(); }
    public static boolean supports(BlockState state) {
        return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON);
    }
    public int getTanks() { return 1; }
    public int getTankCapacity(int tank) { return tank == 0 ? 1000 : 0; }
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0 || !level.isLoaded(pos)) return FluidStack.EMPTY;
        var state = level.getBlockState(pos);
        if (state.is(Blocks.LAVA_CAULDRON)) return new FluidStack(Fluids.LAVA, 1000);
        if (state.is(Blocks.WATER_CAULDRON)) return new FluidStack(Fluids.WATER, 1000 * state.getValue(LayeredCauldronBlock.LEVEL) / 3);
        return FluidStack.EMPTY;
    }
    public boolean isFluidValid(int tank, FluidStack fluid) {
        return tank == 0 && (fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.LAVA);
    }
    public int fill(FluidStack fluid, FluidAction action) {
        if (!level.isLoaded(pos) || !isFluidValid(0, fluid) || fluid.getAmount() < 1000
                || !level.getBlockState(pos).is(Blocks.CAULDRON)) return 0;
        if (action.execute()) level.setBlockAndUpdate(pos, fluid.getFluid() == Fluids.LAVA
                ? Blocks.LAVA_CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        return 1000;
    }
    public FluidStack drain(FluidStack fluid, FluidAction action) {
        return !fluid.isEmpty() && !fluid.hasTag() && fluid.getFluid() == getFluidInTank(0).getFluid()
                ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
    }
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack fluid = getFluidInTank(0);
        if (maxDrain < 1000 || fluid.getAmount() != 1000) return FluidStack.EMPTY;
        if (action.execute()) level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        return fluid;
    }
}
