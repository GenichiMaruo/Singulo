package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * クリエイティブ電源（クリエイティブ専用）。電力が尽きない。毎tick、隣の6面へ受け取れるだけ送り（mod のケーブルや装置へは
 * int の上限を超えて、ほかの mod の装置へは1面あたり int まで）、
 * ケーブルや装置から取り出すこともできる。検証用。
 */
public class CreativeEnergyBlockEntity extends io.github.genichimaruo.singulo.compat.LegacyBlockEntity implements AbstractMachineBlock.MenuOpener {
    private final io.github.genichimaruo.singulo.energy.LongEnergyHandler infinite = new io.github.genichimaruo.singulo.energy.LongEnergyHandler() {
        public long insertLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){if(amount<0)throw new IllegalArgumentException("Negative energy insertion");return 0;}
        public long extractLong(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){if(amount<0)throw new IllegalArgumentException("Negative energy extraction");return amount;}
        public long getAmountAsLong(){return Long.MAX_VALUE;}
        public long getCapacityAsLong(){return Long.MAX_VALUE;}
        public boolean canExtract(){return true;}
        public boolean canReceive(){return false;}
    };

    public CreativeEnergyBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CREATIVE_ENERGY.get(), pos, state);
    }

    public io.github.genichimaruo.singulo.energy.LongEnergyHandler energy() {
        return infinite;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeEnergyBlockEntity be) {
        for (Direction dir : Direction.values()) {
            net.neoforged.neoforge.transfer.energy.EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null && !(level.getBlockEntity(pos.relative(dir)) instanceof CreativeEnergyBlockEntity)) {
                io.github.genichimaruo.singulo.energy.EnergyTransfers.moveLong(be.infinite, target, Long.MAX_VALUE / 4);
            }
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatable("tooltip.singulo.creative_energy.hint"));
    }
}
