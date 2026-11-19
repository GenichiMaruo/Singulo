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
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** One generator export pass, including all of its ports. Pumps receive the remaining power last. */
public final class NeighborEnergyOutput {
    private static final Direction[] FACES = Direction.values();
    private final Level level;
    private final LongSupplier available;
    private final LongConsumer spend;
    private List<IEnergyStorage> pumps;

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
            var target = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, targetPos, face.getOpposite());
            if (target instanceof FluidPumpBlockEntity.PowerInput) {
                if (pumps == null) pumps = new ArrayList<>();
                pumps.add(target);
            } else if (target != null && target.canReceive()) {
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

    private void send(IEnergyStorage target, long offer) {
        long sent = Math.min(offer, LongEnergyStorage.receive(target, offer, false));
        if (sent > 0) spend.accept(sent);
    }
}
