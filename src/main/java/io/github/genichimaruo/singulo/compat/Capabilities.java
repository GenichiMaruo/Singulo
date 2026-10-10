package io.github.genichimaruo.singulo.compat;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class Capabilities {
    public static final class EnergyStorage {
        public static final Capability<IEnergyStorage> BLOCK = ForgeCapabilities.ENERGY;
        public static final Capability<IEnergyStorage> ITEM = ForgeCapabilities.ENERGY;
    }
    public static final class ItemHandler {
        public static final Capability<IItemHandler> BLOCK = ForgeCapabilities.ITEM_HANDLER;
    }
    public static final class FluidHandler {
        public static final Capability<IFluidHandler> BLOCK = ForgeCapabilities.FLUID_HANDLER;
    }
    public static <T> T get(net.minecraft.world.level.Level level, Capability<T> cap,
                          net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side) {
        var be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(cap, side).orElse(null);
    }
    public static <T> T get(net.minecraft.world.item.ItemStack stack, Capability<T> cap) {
        return stack.getCapability(cap).orElse(null);
    }
    private Capabilities() {}
}
