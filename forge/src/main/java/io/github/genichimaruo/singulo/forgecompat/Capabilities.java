package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

/** Forge 版: NeoForge の Capabilities と同じ名前で使える、電力・アイテム・液体の機能の鍵。 */
public final class Capabilities {
    private Capabilities() {}

    public static final class EnergyStorage {
        public static final BlockCapability<IEnergyStorage, Direction> BLOCK = new BlockCapability<>(ForgeCapabilities.ENERGY);
        public static final ItemCapability<IEnergyStorage, Void> ITEM = new ItemCapability<>(ForgeCapabilities.ENERGY);
    }

    public static final class ItemHandler {
        public static final BlockCapability<IItemHandler, Direction> BLOCK = new BlockCapability<>(ForgeCapabilities.ITEM_HANDLER);
        public static final ItemCapability<IItemHandler, Void> ITEM = new ItemCapability<>(ForgeCapabilities.ITEM_HANDLER);
    }

    public static final class FluidHandler {
        public static final BlockCapability<IFluidHandler, Direction> BLOCK = new BlockCapability<>(ForgeCapabilities.FLUID_HANDLER);
        public static final ItemCapability<IFluidHandlerItem, Void> ITEM = new ItemCapability<>(ForgeCapabilities.FLUID_HANDLER_ITEM);
    }
}
