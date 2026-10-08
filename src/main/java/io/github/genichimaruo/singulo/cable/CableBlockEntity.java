package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** 導線1本。電力は溜めず、受け取った分をネットワーク経由でその場で配る。 */
public class CableBlockEntity extends BlockEntity {
    @Nullable
    EnergyNetwork network;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CABLE.get(), pos, state);
    }

    /** 10 tick ごとに、ネットワークに電力が流れているかをブロックの状態（powered）に反映する。 */
    static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CableBlockEntity be) {
        if ((level.getGameTime() + pos.hashCode()) % 10 != 0) {
            return;
        }
        boolean powered = be.network != null && be.network.isValid() && be.network.activeSince(level.getGameTime() - 20);
        if (state.getValue(CableBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(CableBlock.POWERED, powered), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public void invalidateNetwork() {
        if (network != null) {
            network.invalidate();
        }
    }

    EnergyNetwork network() {
        if (network == null || !network.isValid()) {
            EnergyNetwork.build(level, worldPosition);
        }
        return network;
    }

    @Override
    public void setRemoved() {
        invalidateNetwork();
        super.setRemoved();
    }

    @Nullable
    public IEnergyStorage energyFor(@Nullable Direction side) {
        if (level == null || level.isClientSide) {
            return null;
        }
        return new Port(side);
    }

    /** 導線の口。受け取った電力を、入ってきた口以外の受け手へ流す。 */
    private final class Port implements IEnergyStorage {
        @Nullable
        private final Direction side;

        Port(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            BlockPos source = side == null ? null : worldPosition.relative(side);
            return network().distribute(worldPosition, source, amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return network().capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
