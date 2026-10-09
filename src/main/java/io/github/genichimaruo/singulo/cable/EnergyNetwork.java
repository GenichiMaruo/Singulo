package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
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
 * 種類の違うケーブルもひとつのネットワークになり、運べる量はいちばん細いケーブルで決まる（ホライズン・バスだけなら上限なし）。
 * 損失は入口から受け手までに通るケーブルの lossPerBlock の合計（銅導線は1ブロック0.1%、超伝導はゼロ）。最大50%。
 * 電力は入口から受け手まで、ケーブルの数がいちばん少ない道を通る。どのケーブルをどちら向きにどれだけ通ったかを tick ごとに記録する
 * （ケーブルの光り方に使う）。
 */
final class EnergyNetwork {
    static final double MIN_EFFICIENCY = 0.5;

    private record Acceptor(BlockPos pos, Direction side, BlockPos cable) {}

    /** 入口から各ケーブルまでの道: 損失の合計、ひとつ手前のケーブル（入口は null）、入口からの数。 */
    private record Route(Map<BlockPos, Double> loss, Map<BlockPos, BlockPos> parent, Map<BlockPos, Integer> depth) {}

    /** 1 tick にケーブル1本を通った電力。in・out は電力が入ってきた面と出ていった面（Direction の 3D 番号のビット）。 */
    static final class Flow {
        long amount;
        int dist = Integer.MAX_VALUE;
        int in;
        int out;
    }

    private final Level level;
    private final Map<BlockPos, Double> cableLoss;
    private final List<Acceptor> acceptors;
    private final long capacity;
    private final Map<BlockPos, Route> routes = new HashMap<>();
    private boolean valid = true;
    private long tick = -1;
    private long sentThisTick;
    private Map<BlockPos, Flow> flowThisTick = new HashMap<>();
    private Map<BlockPos, Flow> flowLastTick = new HashMap<>();

    private EnergyNetwork(Level level, Map<BlockPos, Double> cableLoss, List<Acceptor> acceptors, long capacity) {
        this.level = level;
        this.cableLoss = cableLoss;
        this.acceptors = acceptors;
        this.capacity = capacity;
    }

    static void build(Level level, BlockPos start) {
        Map<BlockPos, Double> cables = new HashMap<>();
        List<Acceptor> acceptors = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        long capacity = Long.MAX_VALUE;
        boolean anyCable = false;
        queue.add(start);
        cables.put(start, lossOf(level, start));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (level.getBlockState(pos).getBlock() instanceof CableBlock cable) {
                capacity = Math.min(capacity, cable.capacity());
                anyCable = true;
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
        EnergyNetwork network = new EnergyNetwork(level, cables, acceptors, anyCable ? capacity : 0);
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

    long capacity() {
        return capacity;
    }

    /** 直前の tick に pos のケーブルを通った電力（通っていなければ null）。 */
    @Nullable
    Flow lastFlow(BlockPos pos) {
        roll();
        return flowLastTick.get(pos);
    }

    /** tick が変わったら、記録を1つずらす。 */
    private void roll() {
        long now = level.getGameTime();
        if (now != tick) {
            flowLastTick = now == tick + 1 ? flowThisTick : new HashMap<>();
            flowThisTick = new HashMap<>();
            tick = now;
            sentThisTick = 0;
        }
    }

    /** entry のケーブルから amount を配り、実際に引き取った量（送り手が払う量）を返す。source は送り手の位置（入ってきた面）。 */
    long distribute(BlockPos entry, @Nullable BlockPos source, long amount, boolean simulate) {
        roll();
        long budget = Math.min(amount, capacity - sentThisTick);
        if (budget <= 0 || acceptors.isEmpty()) {
            return 0;
        }
        Route route = route(entry);
        List<Acceptor> targets = new ArrayList<>(acceptors.size());
        for (Acceptor a : acceptors) {
            if (!a.pos().equals(source)) {
                targets.add(a);
            }
        }
        targets.sort((a, b) -> Double.compare(route.loss().getOrDefault(a.cable(), 0.0), route.loss().getOrDefault(b.cable(), 0.0)));

        long remaining = budget;
        int left = targets.size();
        for (Acceptor a : targets) {
            if (remaining <= 0) {
                break;
            }
            long share = (remaining + left - 1) / left;
            left--;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, a.pos(), a.side());
            if (target == null || !target.canReceive()) {
                continue;
            }
            double eff = Math.max(MIN_EFFICIENCY, 1.0 - route.loss().getOrDefault(a.cable(), 0.0));
            long accepted = LongEnergyStorage.receive(target, (long) (share * eff), simulate);
            long paid = Math.min(share, (long) Math.ceil(accepted / eff));
            remaining -= paid;
            if (!simulate && paid > 0) {
                record(route, entry, source, a, paid);
            }
        }
        long sent = budget - remaining;
        if (!simulate) {
            sentThisTick += sent;
        }
        return sent;
    }

    /** 受け手 a へ届いた電力の道（入口から a のとなりのケーブルまで）を記録する。 */
    private void record(Route route, BlockPos entry, @Nullable BlockPos source, Acceptor a, long paid) {
        BlockPos child = null;
        BlockPos at = a.cable();
        while (at != null) {
            Flow f = flowThisTick.computeIfAbsent(at, p -> new Flow());
            f.amount += paid;
            f.dist = Math.min(f.dist, route.depth().getOrDefault(at, 0));
            if (child == null) {
                f.out |= bit(a.side().getOpposite());
            } else {
                f.out |= bit(direction(at, child));
            }
            BlockPos parent = route.parent().get(at);
            if (parent != null) {
                f.in |= bit(direction(at, parent));
            } else if (source != null && at.equals(entry)) {
                f.in |= bit(direction(at, source));
            }
            child = at;
            at = parent;
        }
    }

    private static int bit(@Nullable Direction d) {
        return d == null ? 0 : 1 << d.get3DDataValue();
    }

    @Nullable
    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    /** entry から各ケーブルまでの道（ケーブルの数がいちばん少ない道）。入口ごとに一度だけ数える。 */
    private Route route(BlockPos entry) {
        return routes.computeIfAbsent(entry, e -> {
            Map<BlockPos, Double> loss = new HashMap<>();
            Map<BlockPos, BlockPos> parent = new HashMap<>();
            Map<BlockPos, Integer> depth = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            loss.put(e, 0.0);
            depth.put(e, 0);
            queue.add(e);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                double here = loss.get(pos);
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    Double step = cableLoss.get(next);
                    if (step != null && !loss.containsKey(next)) {
                        loss.put(next, here + step);
                        parent.put(next, pos);
                        depth.put(next, depth.get(pos) + 1);
                        queue.add(next);
                    }
                }
            }
            return new Route(loss, parent, depth);
        });
    }
}
