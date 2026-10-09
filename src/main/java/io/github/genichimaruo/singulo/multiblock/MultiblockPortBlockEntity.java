package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * マルチブロック搬入出ポート。つないだパイプやケーブルはコントローラへ届き（搬入）、コントローラの出力
 * （できたアイテム・液体と、発電機の電力）は隣のブロックへ自動で押し出す（搬出）。
 * 隣が同じ構造物の部品やほかのポートなら押し出さない（行ったり来たりしないように）。
 */
public class MultiblockPortBlockEntity extends PortBlockEntity {
    /** アイテムと液体を押し出す間隔（tick）。電力は毎 tick。 */
    static final int ITEM_INTERVAL = 8;
    static final int FLUID_PER_PUSH = 4_000;

    public MultiblockPortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.MULTIBLOCK_PORT.get(), pos, state);
    }

    /** コントローラが押し出させてよい出力（できたアイテム・液体）。入力や触媒は含めない。 */
    public interface Outputs {
        @Nullable
        default IItemHandler ejectItems() {
            return null;
        }

        @Nullable
        default IFluidHandler ejectFluids() {
            return null;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MultiblockPortBlockEntity port) {
        BlockPos controller = port.controllerPos();
        if (controller == null || !level.isLoaded(controller)) {
            return;
        }
        IEnergyStorage energy = port.delegate(Capabilities.EnergyStorage.BLOCK);
        boolean slow = (level.getGameTime() + pos.asLong()) % ITEM_INTERVAL == 0;
        Outputs outputs = slow && level.getBlockEntity(controller) instanceof Outputs o ? o : null;
        IItemHandler items = outputs == null ? null : outputs.ejectItems();
        IFluidHandler fluids = outputs == null ? null : outputs.ejectFluids();
        if ((energy == null || !energy.canExtract()) && items == null && fluids == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos target = pos.relative(dir);
            if (target.equals(controller) || !level.isLoaded(target)
                    || level.getBlockState(target).getBlock() instanceof MultiblockPart) {
                continue;
            }
            Direction from = dir.getOpposite();
            if (energy != null && energy.canExtract()) {
                pushEnergy(level, target, from, energy);
            }
            if (items != null) {
                pushItems(level, target, from, items);
            }
            if (fluids != null) {
                pushFluids(level, target, from, fluids);
            }
        }
    }

    private static void pushEnergy(Level level, BlockPos target, Direction from, IEnergyStorage source) {
        IEnergyStorage dest = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, from);
        if (dest == null || !dest.canReceive()) {
            return;
        }
        int offer = source.extractEnergy(Integer.MAX_VALUE, true);
        if (offer > 0) {
            int taken = dest.receiveEnergy(offer, false);
            if (taken > 0) {
                source.extractEnergy(taken, false);
            }
        }
    }

    private static void pushItems(Level level, BlockPos target, Direction from, IItemHandler source) {
        IItemHandler dest = level.getCapability(Capabilities.ItemHandler.BLOCK, target, from);
        if (dest == null) {
            return;
        }
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack offer = source.extractItem(slot, 64, true);
            if (offer.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItem(dest, offer, false);
            int moved = offer.getCount() - rest.getCount();
            if (moved > 0) {
                source.extractItem(slot, moved, false);
            }
        }
    }

    private static void pushFluids(Level level, BlockPos target, Direction from, IFluidHandler source) {
        IFluidHandler dest = level.getCapability(Capabilities.FluidHandler.BLOCK, target, from);
        if (dest == null) {
            return;
        }
        FluidStack offer = source.drain(FLUID_PER_PUSH, IFluidHandler.FluidAction.SIMULATE);
        if (offer.isEmpty()) {
            return;
        }
        int moved = dest.fill(offer, IFluidHandler.FluidAction.EXECUTE);
        if (moved > 0) {
            source.drain(offer.copyWithAmount(moved), IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
