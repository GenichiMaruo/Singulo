package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
 * パネル1枚は毎tick perPanel()（設定 gravityPanelPower）を使い、足りなければ受電器に近い順に、払える枚数だけが働く。
 * <p>
 * 同じパネルの集まりに受電器が複数つながっているときは、位置がいちばん小さい受電器（まとめ役）が全員の電力を集めて配り、
 * 近さはいちばん近い受電器から数える。集まりの形は SCAN_INTERVAL tick ごとに調べ直す。
 */
public class GravityReceiverBlockEntity extends BlockEntity {
    /** 毎tick必要な電力の既定値（設定 gravityPanelPower）。 */
    public static final int DEFAULT_PER_PANEL = 40;
    public static final int SCAN_INTERVAL = 20;
    /** 1つの集まりで数えるパネルの上限（重くならないように）。 */
    public static final int MAX_PANELS = 4096;
    /** パネルの状態を変えるときの印（隣の見た目の更新はいらない）。 */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static int perPanel() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.GRAVITY_PANEL_POWER.get() : DEFAULT_PER_PANEL;
    }

    /** 受電器に近い順のパネル。 */
    private List<BlockPos> order = List.of();
    /** 同じ集まりの受電器（自分を含む）。 */
    private List<BlockPos> receivers = List.of();
    private boolean leader = true;
    /** 前の tick に働かせたパネル（集まりから外れたものを止めるため）。 */
    private Set<BlockPos> powered = new HashSet<>();
    protected long stored;
    private long nextScan;
    private int active;

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

    /** ためておける量: 集まり全体の1 tick 分（パネルがなくても1枚分）。 */
    protected long capacity() {
        return (long) perPanel() * Math.max(1, order.size());
    }

    /** たまっている電力。 */
    public long stored() {
        return stored;
    }

    /** つながっているパネルの数。 */
    public int panels() {
        return order.size();
    }

    /** 直前の tick に働いたパネルの数。 */
    public int activePanels() {
        return active;
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
        boolean lit = be.active > 0;
        if (state.hasProperty(GravityReceiverBlock.LIT) && state.getValue(GravityReceiverBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(GravityReceiverBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** パネルの集まりを調べ直す。受電器に接したパネルから始め、近い順に並べる（いちばん近い受電器から数える）。 */
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
        List<BlockPos> all = new ArrayList<>(found);
        all.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        receivers = List.copyOf(all);
        boolean wasLeader = leader;
        leader = all.get(0).equals(worldPosition);
        if (!leader) {
            // まとめ役が配る。自分が働かせていたパネルの面倒はまとめ役が見る
            order = List.of();
            powered.clear();
            active = 0;
            return;
        }
        // 2回目: すべての受電器から同時に広げて、近い順に並べる
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
        order = List.copyOf(sorted);
        if (!wasLeader) {
            powered.clear();
        }
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

    /** 集まり全体の電力を集め、近い順に払える枚数だけ働かせる。 */
    private void distribute(Level level) {
        int per = perPanel();
        List<GravityReceiverBlockEntity> group = new ArrayList<>();
        long available = 0;
        for (BlockPos r : receivers) {
            if (level.isLoaded(r) && level.getBlockEntity(r) instanceof GravityReceiverBlockEntity other && !other.isRemoved()) {
                group.add(other);
                available += other.stored;
            }
        }
        int n = per <= 0 ? order.size() : (int) Math.min(order.size(), available / per);
        long cost = (long) n * Math.max(0, per);
        for (GravityReceiverBlockEntity other : group) {
            long take = Math.min(cost, other.stored);
            if (take > 0) {
                other.stored -= take;
                other.setChanged();
                cost -= take;
            }
        }
        Set<BlockPos> now = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            BlockPos p = order.get(i);
            boolean on = i < n;
            if (setPowered(level, p, on) && on) {
                now.add(p);
            }
        }
        // 集まりから外れたパネルは止める
        for (BlockPos p : powered) {
            if (!now.contains(p)) {
                setPowered(level, p, false);
            }
        }
        powered = now;
        active = now.size();
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

    /** 壊されたら、働かせていたパネルを止める。 */
    public void onBroken(Level level) {
        for (BlockPos p : powered) {
            setPowered(level, p, false);
        }
        powered.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("energy", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = tag.getLong("energy");
    }
}
