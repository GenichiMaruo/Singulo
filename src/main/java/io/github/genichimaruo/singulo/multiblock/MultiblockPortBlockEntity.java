package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import org.jspecify.annotations.Nullable;
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
    /** アイテムを押し出す間隔（tick）。液体と電力は毎 tick。 */
    static final int ITEM_INTERVAL = 8;

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

        /** ポートごとに入出力を決められる装置は、このポートから押し出してよい物を返す。 */
        @Nullable
        default IItemHandler ejectItems(BlockPos port) {
            return ejectItems();
        }

        @Nullable
        default IFluidHandler ejectFluids(BlockPos port) {
            return ejectFluids();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MultiblockPortBlockEntity port) {
        BlockPos controller = port.controllerPos();
        if (controller == null || !level.isLoaded(controller)) {
            return;
        }
        net.neoforged.neoforge.transfer.energy.EnergyHandler energy = port.delegate(Capabilities.Energy.BLOCK);
        boolean slow = (level.getGameTime() + pos.asLong()) % ITEM_INTERVAL == 0;
        Outputs outputs = level.getBlockEntity(controller) instanceof Outputs o ? o : null;
        IItemHandler items = slow && outputs != null ? outputs.ejectItems(pos) : null;
        IFluidHandler fluids = outputs == null ? null : outputs.ejectFluids(pos);
        if (fluids != null && !hasStoredFluid(fluids)) fluids = null;
        if (energy == null && items == null && fluids == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos target = pos.relative(dir);
            if (target.equals(controller) || !level.isLoaded(target)) {
                continue;
            }
            // A port of another formed structure is a valid destination. Only our
            // own ports/structure parts must be excluded to avoid feeding ourselves.
            if (level.getBlockState(target).getBlock() instanceof MultiblockPart
                    && !(level.getBlockEntity(target) instanceof PortBlockEntity other
                    && other.controllerPos() != null && !other.controllerPos().equals(controller))) {
                continue;
            }
            Direction from = dir.getOpposite();
            if (energy != null) {
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

    private static void pushEnergy(Level level, BlockPos target, Direction from, net.neoforged.neoforge.transfer.energy.EnergyHandler source) {
        var destination=level.getCapability(Capabilities.Energy.BLOCK,target,from);
        if(destination!=null)io.github.genichimaruo.singulo.energy.EnergyTransfers.move(source,destination,Integer.MAX_VALUE);
    }

    private static void pushItems(Level level, BlockPos target, Direction from, IItemHandler source) {
        IItemHandler dest = io.github.genichimaruo.singulo.compat.ItemResourceView.find(level, target, from);
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

    private static boolean hasStoredFluid(IFluidHandler source) {
        for (int tank = 0; tank < source.getTanks(); tank++) {
            if (!source.getFluidInTank(tank).isEmpty()) return true;
        }
        return false;
    }

    public static void pushFluids(Level level, BlockPos target, Direction from, IFluidHandler source) {
        IFluidHandler dest = io.github.genichimaruo.singulo.compat.FluidResourceView.find(level, target, from);
        if (dest == null) {
            return;
        }
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack fluid = source.getFluidInTank(tank);
            if (fluid.isEmpty() || !io.github.genichimaruo.singulo.pipe.PipeFluidFilter.accepts(dest, fluid)) continue;
            FluidStack offer = source.drain(fluid.copyWithAmount(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            if (offer.isEmpty()) continue;
            int moved = dest.fill(offer, IFluidHandler.FluidAction.EXECUTE);
            if (moved > 0) {
                source.drain(offer.copyWithAmount(moved), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }
}
