package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * クリエイティブ電源（クリエイティブ専用）。電力が尽きない。毎tick、隣の6面へ受け取れるだけ送り（mod のケーブルや装置へは
 * int の上限を超えて、ほかの mod の装置へは1面あたり int まで）、
 * ケーブルや装置から取り出すこともできる。検証用。
 */
public class CreativeEnergyBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    private final IEnergyStorage infinite = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return amount;            // 何でも飲み込む（捨てる）
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return amount;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public CreativeEnergyBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CREATIVE_ENERGY.get(), pos, state);
    }

    public IEnergyStorage energy() {
        return infinite;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeEnergyBlockEntity be) {
        for (Direction dir : Direction.values()) {
            IEnergyStorage target = Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive() && !(level.getBlockEntity(pos.relative(dir)) instanceof CreativeEnergyBlockEntity)) {
                io.github.genichimaruo.singulo.energy.LongEnergyStorage.receive(target, Long.MAX_VALUE / 4, false);
            }
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("tooltip.singulo.creative_energy.hint"), true);
    }
}
