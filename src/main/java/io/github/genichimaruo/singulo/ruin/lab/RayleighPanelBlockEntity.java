package io.github.genichimaruo.singulo.ruin.lab;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * レイリー散乱パネルの電力。設計図から作ったものは FE を {@link #USAGE} FE/t 使って点き、足りなくなると消える。
 * 研究所の天井のものは電力を受けない（研究所の中央動力炉で点く）。描画は RayleighPanelRenderer。
 */
public class RayleighPanelBlockEntity extends BlockEntity {
    /** 点いている間に使う電力（FE/t）と、ためられる量。 */
    public static final int USAGE = 20, CAPACITY = 4_000;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(CAPACITY, CAPACITY / 10, 0, this::setChanged);

    public RayleighPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.RAYLEIGH_PANEL.get(), pos, state);
    }

    /** FE の受け口（研究所の天井のものにはない）。 */
    @Nullable
    public IEnergyStorage energy() {
        return getBlockState().getBlock() instanceof RayleighPanelBlock b && !b.lab() ? energy : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RayleighPanelBlockEntity be) {
        boolean on = be.energy.consume(USAGE);
        if (state.getValue(RayleighPanelBlock.LIT) != on) {
            level.setBlock(pos, state.setValue(RayleighPanelBlock.LIT, on), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
    }
}
