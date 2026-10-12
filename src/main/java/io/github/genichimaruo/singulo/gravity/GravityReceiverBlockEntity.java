package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 重力パネル受電器。受けた電力を、接しているパネルから面でつながった重力パネル（種類は問わない）すべてへ配る。
 * パネル1枚は毎tick perPanel()（設定 gravityPanelPower）を使う。
 * <p>
 * 同じパネルの集まりに受電器が何台つながっていてもよい。どの受電器も、1台で集まり全体のパネルを働かせられるだけの
 * 電力をためられる。複数の受電器があるときは、パネルごとに「パネルを伝った最短経路のブロック数」が近い受電器が優先して払い、
 * その受電器の電力が足りなければ、次に近い受電器が払う。どこからも払えないパネルは止まる。
 * <p>
 * 計算は、集まりの中で位置がいちばん小さい受電器（まとめ役）がまとめて行う。集まりの形と距離は SCAN_INTERVAL tick ごとに調べ直す。
 */
public class GravityReceiverBlockEntity extends BlockEntity {
    /** 毎tick必要な電力の既定値（設定 gravityPanelPower）。 */
    public static final int DEFAULT_PER_PANEL = 40;
    public static final int SCAN_INTERVAL = 20;
    /** 1つの集まりで数えるパネルの上限（重くならないように）。 */
    public static final int MAX_PANELS = 4096;
    /** 距離を比べる受電器の上限（それより多いときは、位置の小さい順にこの数まで）。 */
    public static final int MAX_RECEIVERS = 32;
    /** パネルの状態を変えるときの印（隣の見た目の更新はいらない）。 */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static int perPanel() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.GRAVITY_PANEL_POWER.get() : DEFAULT_PER_PANEL;
    }

    // ---- まとめ役だけが使う
    /** 近い順のパネル（いちばん近い受電器までの距離の順）。 */
    private List<BlockPos> order = List.of();
    /** パネルごとの、払う順の受電器（receivers の番号。近い順、同じ距離なら位置の小さい順）。 */
    private int[][] preference = new int[0][];
    /** 前の tick に働かせたパネル（集まりから外れたものを止めるため）。 */
    private Set<BlockPos> powered = new HashSet<>();

    // ---- どの受電器も持つ
    /** 同じ集まりの受電器（自分を含む、位置の小さい順）。 */
    private List<BlockPos> receivers = List.of();
    private boolean leader = true;
    /** 集まりのパネルの数。 */
    private int groupPanels;
    /** 集まり全体で働いているパネルの数（まとめ役が書く）。 */
    private int active;
    /** この受電器が電力を払っているパネルの数（まとめ役が書く）。 */
    private int supplying;
    protected long stored;
    private long nextScan;

    private final LongEnergyStorage energy = new LongEnergyStorage() {
        @Override
        public long receiveLong(long amount, boolean simulate) {
            long take = Math.max(0, Math.min(amount, capacity() - stored));
            if (!simulate && take > 0) {
                stored += take;
                setChanged();
            }
            return take;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, stored);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public GravityReceiverBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.GRAVITY_RECEIVER.get(), pos, state);
    }

    protected GravityReceiverBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public LongEnergyStorage energy() {
        return energy;
    }

    /** ためておける量: 集まり全体の1 tick 分（パネルがなくても1枚分）。どの受電器も1台で全部を働かせられる。 */
    protected long capacity() {
        return (long) perPanel() * Math.max(1, groupPanels);
    }

    /** たまっている電力。 */
    public long stored() {
        return stored;
    }

    /** つながっているパネルの数（集まり全体）。 */
    public int panels() {
        return groupPanels;
    }

    /** 直前の tick に働いたパネルの数（集まり全体）。 */
    public int activePanels() {
        return active;
    }

    /** 直前の tick に、この受電器が電力を払ったパネルの数。 */
    public int supplyingPanels() {
        return supplying;
    }

    public boolean leader() {
        return leader;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityReceiverBlockEntity be) {
        if (level.getGameTime() >= be.nextScan) {
            be.nextScan = level.getGameTime() + SCAN_INTERVAL;
            be.scan(level);
        }
        if (be.leader) {
            be.distribute(level);
        }
        be.afterDistribute(level);
        boolean lit = be.supplying > 0;
        if (state.hasProperty(GravityReceiverBlock.LIT) && state.getValue(GravityReceiverBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(GravityReceiverBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** 配ったあと（衝撃発電パネルは、余った電力をケーブルへ送る）。 */
    protected void afterDistribute(Level level) {}

    /**
     * パネルの集まりを調べ直す。まとめ役は、受電器ごとにパネルを伝った距離を数え、パネルごとの払う順を決める。
     */
    void scan(Level level) {
        // 1回目: 集まりと、つながっている受電器を見つける
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> found = new LinkedHashSet<>();
        found.add(worldPosition);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        enqueueNeighbors(level, worldPosition, seen, queue);
        while (!queue.isEmpty() && seen.size() <= MAX_PANELS) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (level.isLoaded(n) && level.getBlockEntity(n) instanceof GravityReceiverBlockEntity) {
                    found.add(n.immutable());
                }
            }
            enqueueNeighbors(level, p, seen, queue);
        }
        groupPanels = Math.min(seen.size(), MAX_PANELS);
        List<BlockPos> all = new ArrayList<>(found);
        all.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        if (all.size() > MAX_RECEIVERS) {
            // 自分は必ず入れる（まとめ役かどうかの判断のため）
            List<BlockPos> cut = new ArrayList<>(all.subList(0, MAX_RECEIVERS));
            if (!cut.contains(worldPosition)) {
                cut.set(MAX_RECEIVERS - 1, worldPosition);
                cut.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
            }
            all = cut;
        }
        receivers = List.copyOf(all);
        boolean wasLeader = leader;
        leader = all.get(0).equals(worldPosition);
        if (!leader) {
            // まとめ役が配る。自分が働かせていたパネルの面倒はまとめ役が見る
            order = List.of();
            preference = new int[0][];
            powered.clear();
            return;
        }
        // 2回目: 受電器ごとに、パネルを伝った距離を数える
        List<Map<BlockPos, Integer>> distance = new ArrayList<>();
        for (BlockPos r : all) {
            distance.add(distances(level, r));
        }
        // いちばん近い受電器までの距離の順に並べる（すべての受電器から同時に広げたのと同じ順）
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> sorted = new ArrayList<>();
        ArrayDeque<BlockPos> wave = new ArrayDeque<>();
        for (BlockPos r : all) {
            enqueueNeighbors(level, r, visited, wave);
        }
        while (!wave.isEmpty() && sorted.size() < MAX_PANELS) {
            BlockPos p = wave.poll();
            sorted.add(p);
            enqueueNeighbors(level, p, visited, wave);
        }
        int[][] pref = new int[sorted.size()][];
        for (int i = 0; i < sorted.size(); i++) {
            BlockPos p = sorted.get(i);
            List<Integer> idx = new ArrayList<>();
            for (int r = 0; r < all.size(); r++) {
                if (distance.get(r).containsKey(p)) {
                    idx.add(r);
                }
            }
            final int panel = i;
            idx.sort((a, b) -> {
                int c = Integer.compare(distance.get(a).get(sorted.get(panel)), distance.get(b).get(sorted.get(panel)));
                return c != 0 ? c : Integer.compare(a, b);
            });
            pref[i] = idx.stream().mapToInt(Integer::intValue).toArray();
        }
        order = List.copyOf(sorted);
        preference = pref;
        if (!wasLeader) {
            powered.clear();
        }
    }

    /** 受電器 from から、パネルを伝った距離（受電器に接したパネルが 1）。 */
    private static Map<BlockPos, Integer> distances(Level level, BlockPos from) {
        Map<BlockPos, Integer> dist = new HashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        enqueueNeighbors(level, from, seen, queue);
        for (BlockPos p : queue) {
            dist.put(p, 1);
        }
        while (!queue.isEmpty() && dist.size() < MAX_PANELS) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            ArrayDeque<BlockPos> next = new ArrayDeque<>();
            enqueueNeighbors(level, p, seen, next);
            for (BlockPos n : next) {
                dist.put(n, d + 1);
                queue.add(n);
            }
        }
        return dist;
    }

    private static void enqueueNeighbors(Level level, BlockPos from, Set<BlockPos> seen, ArrayDeque<BlockPos> queue) {
        for (Direction d : Direction.values()) {
            BlockPos n = from.relative(d);
            if (!seen.contains(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof GravityPanelBlock) {
                BlockPos key = n.immutable();
                seen.add(key);
                queue.add(key);
            }
        }
    }

    /** パネルごとに、近い受電器から払える分だけ払って働かせる。 */
    private void distribute(Level level) {
        int per = perPanel();
        GravityReceiverBlockEntity[] group = new GravityReceiverBlockEntity[receivers.size()];
        long[] budget = new long[receivers.size()];
        for (int r = 0; r < receivers.size(); r++) {
            BlockPos pos = receivers.get(r);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GravityReceiverBlockEntity other && !other.isRemoved()) {
                group[r] = other;
                budget[r] = other.stored;
            }
        }
        int[] supplied = new int[receivers.size()];
        Set<BlockPos> now = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            BlockPos p = order.get(i);
            int payer = -1;
            for (int r : preference[i]) {
                if (group[r] != null && budget[r] >= per) {
                    payer = r;
                    break;
                }
            }
            boolean on = payer >= 0 || per <= 0 && preference[i].length > 0;
            if (payer >= 0 && per > 0) {
                budget[payer] -= per;
                supplied[payer]++;
            }
            if (setPowered(level, p, on) && on) {
                now.add(p);
            }
        }
        for (int r = 0; r < group.length; r++) {
            GravityReceiverBlockEntity other = group[r];
            if (other == null) {
                continue;
            }
            if (other.stored != budget[r]) {
                other.stored = budget[r];
                other.setChanged();
            }
            other.supplying = supplied[r];
            other.active = now.size();
            other.groupPanels = Math.max(other.groupPanels, order.size());
        }
        // 集まりから外れたパネルは止める
        for (BlockPos p : powered) {
            if (!now.contains(p)) {
                setPowered(level, p, false);
            }
        }
        powered = now;
    }

    /** パネルを働かせる・止める。そこにパネルがあれば true。 */
    private static boolean setPowered(Level level, BlockPos p, boolean on) {
        if (!level.isLoaded(p)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof GravityPanelBlock)) {
            return false;
        }
        if (s.getValue(GravityPanelBlock.POWERED) != on) {
            level.setBlock(p, s.setValue(GravityPanelBlock.POWERED, on), SET_FLAGS);
        }
        return true;
    }

    /** 壊されたら、働かせていたパネルを止める（ほかの受電器があれば、次の調べ直しでまとめ役が引き継ぐ）。 */
    public void onBroken(Level level) {
        for (BlockPos p : powered) {
            setPowered(level, p, false);
        }
        powered.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getLong("energy");
    }
}
