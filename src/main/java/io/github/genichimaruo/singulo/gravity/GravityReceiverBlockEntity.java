package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * パネル受電器。受けた電力を、接しているパネルから面でつながったパネルすべてへ配る。配る相手は
 * 重力パネル（低重力・高重力・無重力）と、設計図から作ったレイリー散乱パネル（種類が違っても、面で接していれば伝う）。
 * 1枚が毎tick使う電力は種類で決まる: 低重力・高重力は {@link #perPanel()}、レイリー散乱パネルは {@link #RAYLEIGH_COST}、
 * 無重力ブロックは集まりの無重力の高さで決まる（{@link ZeroGravity#costPerBlock}。コントロールボックスで変える）。
 * <p>
 * 同じパネルの集まりに受電器が何台つながっていてもよい。どの受電器も、1台で集まり全体を働かせられるだけの電力をためられ、
 * 1 tick にいくらでも受け取れる（受け取るのは足りない分だけ）。パネルごとに、パネルを伝った最短経路で
 * いちばん近い受電器が払い、その受電器が払えなければ、ほかの受電器（位置の小さい順）が払う。どこからも払えないパネルは止まる。
 * <p>
 * 重くならないように: 集まりの形は、まとめ役（位置がいちばん小さい受電器）が {@link #scanInterval} tick ごとに1回の幅優先探索で調べ直す
 * （どの受電器がいちばん近いかも同じ探索で決まる）。どの受電器も自分の受け持ちを払えるふつうの tick は、受電器の数だけの計算で済み、
 * パネルには触らない。電力が足りなくなったときや形が変わったときだけパネルを1枚ずつ見直し、状態が変わるパネルだけを書き換える。
 */
public class GravityReceiverBlockEntity extends BlockEntity {
    /** 毎tick必要な電力の既定値（設定 gravityPanelPower）。 */
    public static final int DEFAULT_PER_PANEL = 40;
    /** レイリー散乱パネル1枚の電力（FE/t）。 */
    public static final int RAYLEIGH_COST = 20;
    /** 形を調べ直す間隔の最短（tick）。集まりが大きいほど延ばす（{@link #scanInterval}）。 */
    public static final int SCAN_INTERVAL = 20;
    /** 1つの集まりで数えるパネルの上限（どれだけ大きくても、ここまで）。 */
    public static final int MAX_PANELS = 1 << 20;
    /** 距離を比べる受電器の上限（それより多いときは、位置の小さい順にこの数まで）。 */
    public static final int MAX_RECEIVERS = 64;
    /** パネルの状態を変えるときの印（隣の見た目の更新はいらない）。 */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Direction[] DIRECTIONS = Direction.values();

    public static int perPanel() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.GRAVITY_PANEL_POWER.get() : DEFAULT_PER_PANEL;
    }

    /** 形の調べ直しの間隔: 2,048 枚ごとに 1 tick 延ばす（最長 10 秒）。 */
    public static int scanInterval(int panels) {
        return Math.min(200, SCAN_INTERVAL + panels / 2048);
    }

    // ---- まとめ役だけが使う
    /** 近い順のパネルの位置。 */
    private long[] order = new long[0];
    /** パネルごとの、いちばん近い受電器（receivers の番号）。 */
    private int[] nearest = new int[0];
    /** パネルごとの、毎tickの電力。 */
    private long[] cost = new long[0];
    /** 受電器ごとの、いちばん近いパネルの電力の合計（ふつうの tick に払う分）。 */
    private long[] share = new long[0];
    /** Stable panel counts per owner; the normal tick never traverses the panels. */
    private int[] shareCount = new int[0];
    /** パネルごとの、いま働かせているか。 */
    private BitSet on = new BitSet();
    /** 前の tick に、すべてのパネルを働かせられたか（ふつうの tick の近道を使えるか）。 */
    private boolean allOn;
    /** 集まりの無重力の高さ（コントロールボックスがなければ {@link ZeroGravity#DEFAULT_HEIGHT}）。 */
    private int zeroHeight = ZeroGravity.DEFAULT_HEIGHT;

    // ---- どの受電器も持つ
    /** 同じ集まりの受電器（自分を含む、位置の小さい順）。 */
    private List<BlockPos> receivers = List.of();
    private boolean leader = true;
    /** 集まりのパネルの数。 */
    private int groupPanels;
    /** 集まり全体の毎tickの電力。 */
    private long groupDemand;
    /** 集まり全体で働いているパネルの数（まとめ役が書く）。 */
    private int active;
    /** この受電器が電力を払っているパネルの数（まとめ役が書く）。 */
    private int supplying;
    private long supplyingDemand;
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
        return Math.max(perPanel(), groupDemand);
    }

    /** たまっている電力。 */
    public long stored() {
        return stored;
    }

    /** つながっているパネルの数（集まり全体）。 */
    public int panels() {
        return groupPanels;
    }

    /** 集まり全体が毎tick使う電力。 */
    public long demand() {
        return groupDemand;
    }

    /** 直前の tick に働いたパネルの数（集まり全体）。 */
    public int activePanels() {
        return active;
    }

    /** 直前の tick に、この受電器が電力を払ったパネルの数。 */
    public int supplyingPanels() {
        return supplying;
    }

    public long supplyingDemand() {
        return supplyingDemand;
    }

    public boolean leader() {
        return leader;
    }

    /** 次の tick に形を調べ直す（パネルやコントロールボックスが変わったとき）。 */
    public void requestScan() {
        nextScan = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityReceiverBlockEntity be) {
        if (level.getGameTime() >= be.nextScan) {
            be.scan(level);
            be.nextScan = level.getGameTime() + scanInterval(be.groupPanels);
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

    /** 配る相手か（重力パネルと、作ったレイリー散乱パネル。研究所の天井のものは研究所の電力で点く）。 */
    static boolean isPanel(BlockState s) {
        return s.getBlock() instanceof GravityPanelBlock || s.getBlock() instanceof RayleighPanelBlock r && !r.lab();
    }

    /**
     * パネルの集まりを調べ直す。すべての受電器から同時に広げる幅優先探索1回で、集まり・つながっている受電器・
     * パネルごとのいちばん近い受電器・近い順の並びがわかる。無重力の高さは、無重力ブロックに付いたコントロールボックスの高さ（いちばん高いもの）。
     */
    void scan(Level level) {
        // 1回目: 自分から広げて、集まりにつながっている受電器を見つける
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        List<BlockPos> found = new ArrayList<>();
        found.add(worldPosition);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        enqueue(level, worldPosition, seen, queue, n);
        while (!queue.isEmpty()) {
            p.set(queue.dequeueLong());
            for (Direction d : DIRECTIONS) {
                n.setWithOffset(p, d);
                if (level.isLoaded(n) && level.getBlockEntity(n) instanceof GravityReceiverBlockEntity && !found.contains(n)) {
                    found.add(n.immutable());
                }
            }
            if (seen.size() < MAX_PANELS) {
                enqueue(level, p, seen, queue, n);
            }
        }
        found.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        if (found.size() > MAX_RECEIVERS) {
            List<BlockPos> cut = new ArrayList<>(found.subList(0, MAX_RECEIVERS));
            if (!cut.contains(worldPosition)) {
                cut.set(MAX_RECEIVERS - 1, worldPosition);       // 自分は必ず入れる（まとめ役かどうかの判断のため）
                cut.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
            }
            found = cut;
        }
        receivers = List.copyOf(found);
        boolean wasLeader = leader;
        leader = found.get(0).equals(worldPosition);
        groupPanels = seen.size();
        if (!leader) {
            // まとめ役が配る。自分が働かせていたパネルの面倒はまとめ役が見る
            order = new long[0];
            nearest = new int[0];
            cost = new long[0];
            share = new long[0];
            shareCount = new int[0];
            on = new BitSet();
            allOn = false;
            return;
        }
        // 2回目: すべての受電器から同時に広げる（先に届いた受電器が、そのパネルのいちばん近い受電器）
        long[] oldOrder = order;
        BitSet oldOn = on;
        seen.clear();
        LongArrayFIFOQueue wave = new LongArrayFIFOQueue();
        LongArrayList sorted = new LongArrayList();
        it.unimi.dsi.fastutil.ints.IntArrayList owner = new it.unimi.dsi.fastutil.ints.IntArrayList();
        it.unimi.dsi.fastutil.ints.IntArrayList waveOwner = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (int r = 0; r < found.size(); r++) {
            int before = wave.size();
            enqueue(level, found.get(r), seen, wave, n);
            for (int k = before; k < wave.size(); k++) {
                waveOwner.add(r);
            }
        }
        int head = 0;
        int height = 0;
        boolean anyZero = false;
        while (!wave.isEmpty() && sorted.size() < MAX_PANELS) {
            long packed = wave.dequeueLong();
            int r = waveOwner.getInt(head++);
            sorted.add(packed);
            owner.add(r);
            p.set(packed);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof GravityPanelBlock g && g.kind() == GravityPanelBlock.Kind.ZERO) {
                anyZero = true;
                for (Direction d : DIRECTIONS) {
                    n.setWithOffset(p, d);
                    if (level.isLoaded(n) && level.getBlockEntity(n) instanceof ZeroGravityControllerBlockEntity c) {
                        height = Math.max(height, c.height());
                    }
                }
            }
            int before = wave.size();
            enqueue(level, p, seen, wave, n);
            for (int k = before; k < wave.size(); k++) {
                waveOwner.add(r);
            }
        }
        int newHeight = height > 0 ? height : ZeroGravity.DEFAULT_HEIGHT;
        boolean heightChanged = newHeight != zeroHeight;
        zeroHeight = newHeight;
        int count = sorted.size();
        order = sorted.toLongArray();
        nearest = owner.toIntArray();
        cost = new long[count];
        share = new long[found.size()];
        shareCount = new int[found.size()];
        long zeroCost = ZeroGravity.costPerBlock(zeroHeight);
        long demand = 0;
        for (int i = 0; i < count; i++) {
            p.set(order[i]);
            BlockState s = level.getBlockState(p);
            long c = s.getBlock() instanceof GravityPanelBlock g ? (g.kind() == GravityPanelBlock.Kind.ZERO ? zeroCost : perPanel())
                    : RAYLEIGH_COST;
            cost[i] = c;
            share[nearest[i]] = ZeroGravity.saturatedAdd(share[nearest[i]], c);
            shareCount[nearest[i]]++;
            demand = ZeroGravity.saturatedAdd(demand, c);
        }
        groupDemand = demand;
        groupPanels = count;
        // 前の並びで働いていたパネルは、新しい並びの番号へ写す（集まりから外れたものは止める）
        BitSet moved = new BitSet(count);
        if (wasLeader && oldOrder.length > 0) {
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap index = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(count);
            index.defaultReturnValue(-1);
            for (int i = 0; i < count; i++) {
                index.put(order[i], i);
            }
            for (int i = oldOn.nextSetBit(0); i >= 0; i = oldOn.nextSetBit(i + 1)) {
                int j = i < oldOrder.length ? index.get(oldOrder[i]) : -1;
                if (j >= 0) {
                    moved.set(j);
                } else if (i < oldOrder.length) {
                    setPowered(level, p.set(oldOrder[i]), false, 0);
                }
            }
        }
        // Saved block states survive reload and leadership changes; transient BitSets do not.
        // Reconcile the real state so an unfunded saved panel is explicitly switched off.
        for (int i = 0; i < count; i++) {
            p.set(order[i]);
            BlockState actual = level.getBlockState(p);
            moved.set(i, actual.getBlock() instanceof GravityPanelBlock
                    ? actual.getValue(GravityPanelBlock.POWERED) : actual.getValue(RayleighPanelBlock.LIT));
        }
        on = moved;
        allOn = false;                                            // 形が変わったので、次の tick はパネルを見直す
        if (heightChanged || anyZero) {
            // 働いている無重力ブロックの高さを、無重力の場所の一覧に入れ直す
            for (int i = on.nextSetBit(0); i >= 0; i = on.nextSetBit(i + 1)) {
                p.set(order[i]);
                if (level.getBlockState(p).getBlock() instanceof GravityPanelBlock g && g.kind() == GravityPanelBlock.Kind.ZERO) {
                    ZeroGravity.put(level, p, zeroHeight);
                }
            }
        }
    }

    private static void enqueue(Level level, BlockPos from, LongOpenHashSet seen, LongArrayFIFOQueue queue, BlockPos.MutableBlockPos n) {
        for (Direction d : DIRECTIONS) {
            n.setWithOffset(from, d);
            long key = n.asLong();
            if (!seen.contains(key) && level.isLoaded(n) && isPanel(level.getBlockState(n))) {
                seen.add(key);
                queue.enqueue(key);
            }
        }
    }

    /** パネルごとに、近い受電器から払える分だけ払って働かせる。 */
    private void distribute(Level level) {
        int rc = receivers.size();
        GravityReceiverBlockEntity[] group = new GravityReceiverBlockEntity[rc];
        long[] budget = new long[rc];
        for (int r = 0; r < rc; r++) {
            BlockPos pos = receivers.get(r);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GravityReceiverBlockEntity other && !other.isRemoved()) {
                group[r] = other;
                budget[r] = other.stored;
            }
        }
        int count = order.length;
        int[] supplied = new int[rc];
        long[] paid = new long[rc];
        // ふつうの tick: どの受電器も自分の受け持ちを払えるなら、パネルには触らない
        boolean easy = allOn && share.length == rc;
        for (int r = 0; easy && r < rc; r++) {
            easy = share[r] == 0 || group[r] != null && budget[r] >= share[r];
        }
        if (easy) {
            for (int r = 0; r < rc; r++) {
                budget[r] -= share[r];
                supplied[r] = shareCount[r];
                paid[r] = share[r];
            }
        } else {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int lit = 0;
            for (int i = 0; i < count; i++) {
                long c = cost[i];
                int payer = -1;
                int first = nearest[i];
                if (group[first] != null && budget[first] >= c) {
                    payer = first;
                } else {
                    for (int r = 0; r < rc; r++) {
                        if (r != first && group[r] != null && budget[r] >= c) {
                            payer = r;
                            break;
                        }
                    }
                }
                boolean want = payer >= 0;
                if (want) {
                    budget[payer] -= c;
                    supplied[payer]++;
                    paid[payer] = ZeroGravity.saturatedAdd(paid[payer], c);
                    lit++;
                }
                if (want != on.get(i)) {
                    p.set(order[i]);
                    if (setPowered(level, p, want, zeroHeight)) {
                        on.set(i, want);
                    } else {
                        on.clear(i);
                    }
                }
            }
            allOn = lit == count && on.cardinality() == count;
        }
        int now = easy ? count : on.cardinality();
        for (int r = 0; r < rc; r++) {
            GravityReceiverBlockEntity other = group[r];
            if (other == null) {
                continue;
            }
            if (other.stored != budget[r]) {
                other.stored = budget[r];
                other.setChanged();
            }
            other.supplying = supplied[r];
            other.supplyingDemand = paid[r];
            other.active = now;
            other.groupPanels = count;
            other.groupDemand = groupDemand;
        }
    }

    /** パネルを働かせる・止める。そこにパネルがあれば true。無重力ブロックは、無重力の場所の一覧も書き換える。 */
    private static boolean setPowered(Level level, BlockPos p, boolean on, int zeroHeight) {
        if (!level.isLoaded(p)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof GravityPanelBlock g) {
            if (s.getValue(GravityPanelBlock.POWERED) != on) {
                level.setBlock(p, s.setValue(GravityPanelBlock.POWERED, on), SET_FLAGS);
            }
            if (g.kind() == GravityPanelBlock.Kind.ZERO) {
                if (on) {
                    ZeroGravity.put(level, p, zeroHeight);
                } else {
                    ZeroGravity.remove(level, p);
                }
            }
            return true;
        }
        if (s.getBlock() instanceof RayleighPanelBlock r && !r.lab()) {
            if (s.getValue(RayleighPanelBlock.LIT) != on) {
                level.setBlock(p, s.setValue(RayleighPanelBlock.LIT, on), Block.UPDATE_ALL);
            }
            return true;
        }
        return false;
    }

    /** 壊されたら、働かせていたパネルを止める（ほかの受電器があれば、次の調べ直しでまとめ役が引き継ぐ）。 */
    public void onBroken(Level level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = on.nextSetBit(0); i >= 0; i = on.nextSetBit(i + 1)) {
            if (i < order.length) {
                setPowered(level, p.set(order[i]), false, 0);
            }
        }
        on.clear();
        allOn = false;
        // 同じ集まりのほかの受電器は、すぐに調べ直して引き継ぐ
        for (BlockPos r : receivers) {
            if (!r.equals(worldPosition) && level.isLoaded(r) && level.getBlockEntity(r) instanceof GravityReceiverBlockEntity other) {
                other.requestScan();
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) onBroken(level);
        super.onChunkUnloaded();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestScan();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("energy", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = Math.max(0, tag.getLong("energy"));
    }
}
