package io.github.genichimaruo.singulo.energy;

import io.github.genichimaruo.singulo.pipe.FluidPumpBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** One generator export pass, including all of its ports. Pumps receive the remaining power last. */
public final class NeighborEnergyOutput {
    private static final Direction[] FACES = Direction.values();
    private final Level level;
    private final LongSupplier available;
    private final LongConsumer spend;
    private List<EnergyHandler> pumps;

    public NeighborEnergyOutput(Level level, LongSupplier available, LongConsumer spend) {
        this.level = level;
        this.available = available;
        this.spend = spend;
    }

    public void pushFrom(BlockPos pos, Predicate<BlockPos> acceptsPosition) {
        for (Direction face : FACES) {
            long offer = available.getAsLong();
            if (offer <= 0) return;
            BlockPos targetPos = pos.relative(face);
            if (!level.isLoaded(targetPos) || !acceptsPosition.test(targetPos)) continue;
            var target = level.getCapability(Capabilities.Energy.BLOCK, targetPos, face.getOpposite());
            if (target instanceof FluidPumpBlockEntity.PowerInput) {
                if (pumps == null) pumps = new ArrayList<>();
                pumps.add(target);
            } else if (target != null) {
                send(target, offer);
            }
        }
    }

    public void sendSurplusToPumps() {
        if (pumps == null) return;
        for (var pump : pumps) {
            long offer = available.getAsLong();
            if (offer <= 0) return;
            send(pump, offer);
        }
    }

    private void send(EnergyHandler target, long offer) {
        try (var tx = Transaction.openRoot()) {
            long sent = LongEnergyHandler.insert(target, offer, tx);
            if (sent <= 0 || sent > offer) return;
            spend.accept(sent);
            tx.commit();
        }
    }
}
