package io.github.genichimaruo.singulo.cable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * つながったケーブルのまとまり。ケーブルの設置・撤去や隣のブロックの変化で無効になり、次に使われたときに作り直す。
 * 種類の違うケーブルもひとつのネットワークになり、運べる量はいちばん細いケーブルで決まる。
 * 損失は入口から受け手までに通るケーブルの lossPerBlock の合計（銅導線は1ブロック0.1%、超伝導はゼロ）。最大50%。
 */
final class EnergyNetwork {
    static final double MIN_EFFICIENCY = 0.5;

    private record Acceptor(BlockPos pos, Direction side, BlockPos cable) {}

    private final Level level;
    private final Map<BlockPos, Double> cableLoss;
    private final List<Acceptor> acceptors;
    private final int capacity;
    private final Map<BlockPos, Map<BlockPos, Double>> lossCache = new HashMap<>();
    private boolean valid = true;
    private long tick = -1;
    private long sentThisTick;
    /** 最後に電力が流れたゲーム時間。 */
    private long lastActive = Long.MIN_VALUE;

    private EnergyNetwork(Level level, Map<BlockPos, Double> cableLoss, List<Acceptor> acceptors, int capacity) {
        this.level = level;
        this.cableLoss = cableLoss;
        this.acceptors = acceptors;
        this.capacity = capacity;
    }

    static void build(Level level, BlockPos start) {
        Map<BlockPos, Double> cables = new HashMap<>();
        List<Acceptor> acceptors = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        // ホライズン・バスの容量は int の上限そのものなので、「ケーブルがない」の印には long の上限を使う
        long capacity = Long.MAX_VALUE;
        queue.add(start);
        cables.put(start, lossOf(level, start));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (level.getBlockState(pos).getBlock() instanceof CableBlock cable) {
                capacity = Math.min(capacity, cable.capacity());
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof CableBlockEntity) {
                    if (!cables.containsKey(next)) {
                        cables.put(next, lossOf(level, next));
                        queue.add(next);
                    }
                } else if (level.getCapability(Capabilities.EnergyStorage.BLOCK, next, dir.getOpposite()) != null) {
                    acceptors.add(new Acceptor(next, dir.getOpposite(), pos));
                }
            }
        }
        EnergyNetwork network = new EnergyNetwork(level, cables, acceptors, capacity == Long.MAX_VALUE ? 0 : (int) capacity);
        for (BlockPos pos : cables.keySet()) {
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cable.network = network;
            }
        }
    }

    private static double lossOf(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CableBlock cable ? cable.lossPerBlock() : 0;
    }

    boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    int capacity() {
        return capacity;
    }

    /** since 以降に電力が流れたか。 */
    boolean activeSince(long since) {
        return lastActive >= since;
    }

    /** entry のケーブルから amount を配り、実際に引き取った量（送り手が払う量）を返す。 */
    int distribute(BlockPos entry, @Nullable BlockPos source, int amount, boolean simulate) {
        long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            sentThisTick = 0;
        }
        int budget = (int) Math.min(amount, (long) capacity - sentThisTick);
        if (budget <= 0 || acceptors.isEmpty()) {
            return 0;
        }
        Map<BlockPos, Double> loss = losses(entry);
        List<Acceptor> targets = new ArrayList<>(acceptors.size());
        for (Acceptor a : acceptors) {
            if (!a.pos().equals(source)) {
                targets.add(a);
            }
        }
        targets.sort((a, b) -> Double.compare(loss.getOrDefault(a.cable(), 0.0), loss.getOrDefault(b.cable(), 0.0)));

        int remaining = budget;
        int left = targets.size();
        for (Acceptor a : targets) {
            if (remaining <= 0) {
                break;
            }
            // ホライズン・バスは容量が int の上限なので、long で割らないと桁あふれして負になる
            int share = (int) (((long) remaining + left - 1) / left);
            left--;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, a.pos(), a.side());
            if (target == null || !target.canReceive()) {
                continue;
            }
            double eff = Math.max(MIN_EFFICIENCY, 1.0 - loss.getOrDefault(a.cable(), 0.0));
            int accepted = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, share * eff), simulate);
            int paid = (int) Math.min(share, Math.ceil(accepted / eff));
            remaining -= paid;
        }
        int sent = budget - remaining;
        if (!simulate) {
            sentThisTick += sent;
            if (sent > 0) {
                lastActive = now;
            }
        }
        return sent;
    }

    /** entry から各ケーブルまでの損失の合計。入口ごとに一度だけ数える。 */
    private Map<BlockPos, Double> losses(BlockPos entry) {
        return lossCache.computeIfAbsent(entry, e -> {
            Map<BlockPos, Double> out = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            out.put(e, 0.0);
            queue.add(e);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                double here = out.get(pos);
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    Double step = cableLoss.get(next);
                    if (step != null && !out.containsKey(next)) {
                        out.put(next, here + step);
                        queue.add(next);
                    }
                }
            }
            return out;
        });
    }
}
