package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.TimeFields;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.multiblock.PortBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * ペンローズ・リアクター（ブラックホール発電機）の炉心制御装置。
 * <ul>
 *   <li>点火: 特異点の種（2回目以降はシンギュラリティ・コア）を入れて点火し、ignitionWindowSeconds 以内に
 *       ignitionEnergy（既定 50 GFE）を注ぐと重力崩壊して炉心ができる（質量500・無回転）</li>
 *   <li>発電: P = η × 投入量 × c²。質量ペレット1個の質量エネルギーは penroseEnergyPerPellet（既定 20 GFE）。
 *       降着効率 η はスピン a から最内安定円軌道（ISCO）で求める実在の値（a=0 で約5.7%、a=1 で約42.3%）</li>
 *   <li>エディントン限界: 1秒に飲み込めるペレットは炉心質量1000ごとに1個。超える分は投入しない</li>
 *   <li>負荷追従: 内部の蓄電が減った分だけ投入する。ただし炉心を保つため、限界の3%は常に投入する</li>
 *   <li>成長と蒸発: 投入した質量の10%が炉心質量になる（上限5000）。ホーキング放射で質量は 0.006/(M/1000)² /秒 ずつ減り、
 *       100 を割ると蒸発が暴走してバーストする</li>
 *   <li>スピン: 目標スピンまで、蓄電から最大 SPIN_POWER/t を使って回す（角運動量インジェクター）</li>
 *   <li>副産物: ジェット・コレクター（発電中、1基につき1分に1個のジェット凝縮体）、ホーキング・コレクター（質量150〜HAWKING_MAX_MASS（500）で
 *       5分に1個のホーキング凝縮体）、エルゴスフィア・リング（スピン0.7以上・質量400〜2000で1分に1個のエキゾチック物質。スピンが減る）</li>
 *   <li>危険: 引力帯（半径 blackHolePullRadius）と潮汐帯（半径 tidalDamageRadius、防具を無視）</li>
 * </ul>
 */
public class PenroseReactorBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener {
    public enum State { UNFORMED, DORMANT, IGNITING, RUNNING }

    public enum Mode { POWER, CATALYST, ERGO, STANDBY, DANGER }

    public static final double START_MASS = 500;
    /** ホーキング凝縮体ができる質量の上限。点火直後（START_MASS）から作れるように、初期質量と同じにする。 */
    public static final double HAWKING_MAX_MASS = START_MASS;
    public static final double MAX_MASS = 5000;
    public static final double BURST_MASS = 100;
    public static final double PELLET_MASS = 16;
    /** 投入したペレットの質量のうち、炉心質量になる割合（質量500→5000 は全力投入で約10分）。 */
    public static final double GROWTH_FRACTION = 0.25;
    public static final double EVAPORATION_PER_SECOND = 0.006;
    public static final double MIN_INJECTION = 0.03;
    public static final long BUFFER_CAPACITY = 40_000_000_000L;
    public static final long SPIN_POWER = 200_000_000L;
    /** スピンを 1 上げるのに要る電力 = SPIN_ENERGY × 炉心質量（FE）。質量5000・100 MFE/t で約5分。 */
    public static final double SPIN_ENERGY = 1.2e8;
    public static final double[] SPIN_TARGETS = {0.0, 0.5, 0.9, 0.99, 1.0};
    static final int CHECK_INTERVAL = 40;
    /** ペレットの投入間隔の上限（tick）。0 は自動（負荷追従）。 */
    public static final int MAX_FEED_INTERVAL = 1200;
    /** モブ・アイテムを飲み込んだときの質量（低め）。モブは最大体力あたり、アイテムは質量値あたり。 */
    public static final double MOB_MASS_PER_HEALTH = 0.05;
    public static final double ITEM_MASS_RATE = 0.02;
    /** 炉心質量が上限をこれだけ超えると、炉が耐えきれずに崩壊する（ペレットでは上限を超えないので、起きるのは物を投げ込んだときだけ）。 */
    public static final double COLLAPSE_MARGIN = 200;
    /** 崩壊の長さと、まわりを巻き込む半径（炉心の中心から）。 */
    public static final int COLLAPSE_TICKS = 160;
    public static final int COLLAPSE_SPREAD = 120;
    public static final int COLLAPSE_RADIUS = 9;
    public static final int COLLAPSE_MAX_BLOCKS = 700;
    /** ペレットを飲み込んだ合図（描画用）。 */
    static final int EVENT_PELLETS = 1;
    static final int JET_TICKS = 1200;
    static final int HAWKING_TICKS = 6000;
    static final int ERGO_TICKS = 1200;

    /** 0 種, 1 燃料, 2 ジェット・コレクター, 3 ホーキング・コレクター, 4 エルゴスフィア・リング, 5〜8 副産物。 */
    public static final int SLOT_SEED = 0, SLOT_FUEL = 1, SLOT_JET = 2, SLOT_HAWKING = 3, SLOT_ERGO = 4, SLOT_OUT = 5, SLOTS = 9;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_SEED -> stack.is(item("singularity_seed")) || stack.is(item("singularity_core"));
                case SLOT_FUEL -> stack.is(item("mass_pellet"));
                case SLOT_JET -> stack.is(item("jet_collector"));
                case SLOT_HAWKING -> stack.is(item("hawking_collector"));
                case SLOT_ERGO -> stack.is(item("ergosphere_ring"));
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_SEED ? 1 : slot >= SLOT_JET && slot <= SLOT_ERGO ? 8 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IEnergyStorage energyPort = new EnergyPort();
    private final net.neoforged.neoforge.fluids.capability.IFluidHandler darkMatterPort = new DarkMatterPort();

    /** ダークマター 1 mB で炉心に加わる質量。 */
    public static final double DARK_MATTER_MASS_PER_MB = 0.01;

    /** 炉心がある（稼働中の）リアクター（ワールドごと）。ハロー捕集器が探す。 */
    private static final java.util.Map<Level, java.util.Set<PenroseReactorBlockEntity>> RUNNING = new java.util.WeakHashMap<>();

    /** 稼働中のリアクターの位置（そのワールドの全部。テストの後片付け用）。 */
    public static java.util.List<BlockPos> runningPositions(Level level) {
        java.util.Set<PenroseReactorBlockEntity> set = RUNNING.get(level);
        return set == null ? java.util.List.of() : set.stream().map(BlockEntity::getBlockPos).toList();
    }

    /** pos から radius 以内に炉心の中心がある稼働中のリアクターのうち、いちばん近いもの。 */
    @Nullable
    public static PenroseReactorBlockEntity nearestRunning(Level level, BlockPos pos, double radius) {
        java.util.Set<PenroseReactorBlockEntity> set = RUNNING.get(level);
        PenroseReactorBlockEntity best = null;
        double bestSq = radius * radius;
        if (set != null) {
            for (PenroseReactorBlockEntity r : set) {
                if (r.isRemoved() || r.center == null || r.state != State.RUNNING) {
                    continue;
                }
                double d = r.center.distSqr(pos);
                if (d <= bestSq) {
                    bestSq = d;
                    best = r;
                }
            }
        }
        return best;
    }
    private final IItemHandler automation = new Automation();

    private State state = State.UNFORMED;
    private double mass;
    private double spin;
    private int spinTarget;
    private long buffer;
    private long ignitionStored;
    private int ignitionTicks;
    /** 点火を始めてからの tick と、点火の電力が満ちたか。 */
    private int ignitionElapsed;
    private boolean ignitionCharged;
    /** 点火の演出の長さ（16秒）。最後の IGNITION_SOUND_TICKS（点火の音の長さ、10秒）のあいだ点火の音が鳴り、鳴り終わる瞬間に炉心ができる。 */
    public static final int IGNITION_SEQUENCE_TICKS = 320;
    public static final int IGNITION_SOUND_TICKS = 200;
    private double pelletCarry;
    private int jetTimer;
    private int hawkingTimer;
    private int ergoTimer;
    private long lastOutput;
    private long nextCheck;
    @Nullable
    private BlockPos center;
    private List<BlockPos> ports = List.of();
    /** 炉心質量警報器の位置。 */
    private List<BlockPos> alarms = List.of();
    private boolean formed;
    private boolean firstCheck = true;
    private boolean ignitedBefore;

    public PenroseReactorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.PENROSE_REACTOR.get(), pos, state);
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    // ------------------------------------------------------------------ 物理

    /** 降着効率 η(a)。最内安定円軌道 r_isco から η = 1 − √(1 − 2/(3 r_isco))（順行円盤）。 */
    public static double efficiency(double spin) {
        double a = Math.max(0, Math.min(1, spin));
        double z1 = 1 + Math.cbrt(1 - a * a) * (Math.cbrt(1 + a) + Math.cbrt(1 - a));
        double z2 = Math.sqrt(3 * a * a + z1 * z1);
        double rIsco = 3 + z2 - Math.sqrt((3 - z1) * (3 + z1 + 2 * z2));
        return 1 - Math.sqrt(1 - 2 / (3 * rIsco));
    }

    /** エディントン限界（1 tick あたりのペレット数）。 */
    public double eddingtonPerTick() {
        double coef = ServerConfig.SPEC.isLoaded() ? ServerConfig.EDDINGTON_PELLETS_PER_SECOND_PER1000_MASS.get() : 1.0;
        return mass / 1000.0 * coef / 20.0;
    }

    static long energyPerPellet() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.PENROSE_ENERGY_PER_PELLET.get() : 20_000_000_000L;
    }

    static long maxOutput() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.PENROSE_MAX_OUTPUT.get() : 2_100_000_000L;
    }

    public Mode mode() {
        if (state != State.RUNNING) {
            return Mode.STANDBY;
        }
        if (mass < 150) {
            return Mode.DANGER;
        }
        if (mass <= HAWKING_MAX_MASS) {
            return Mode.CATALYST;
        }
        if (spin >= 0.7 && mass <= 2000) {
            return Mode.ERGO;
        }
        return spin >= 0.5 ? Mode.POWER : Mode.STANDBY;
    }

    // ------------------------------------------------------------------ 毎tick

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, PenroseReactorBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (collapseStart >= 0) {
            tickCollapse(level);
            return;
        }
        State before = state;
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            checkStructure(level);
        }
        lastOutput = 0;
        if (!formed) {
            if (state == State.IGNITING) {
                state = State.DORMANT;
            }
            if (state != State.RUNNING) {
                state = State.UNFORMED;
            }
        } else if (state == State.UNFORMED) {
            state = mass > 0 ? State.RUNNING : State.DORMANT;
        }
        switch (state) {
            case IGNITING -> tickIgnition(level);
            case RUNNING -> {
                if (formed) {
                    run(level);
                } else {
                    evaporate(level);
                }
                applyHazards(level);
            }
            default -> {
            }
        }
        pushEnergy(level);
        if (level.getGameTime() % 10 == 0) {
            updateAlarms(level);
        }
        java.util.Set<PenroseReactorBlockEntity> running = RUNNING.computeIfAbsent(level,
                l -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        if (state == State.RUNNING) {
            running.add(this);
        } else {
            running.remove(this);
        }
        // 炉心がある間、引力帯は重力時間膨張ゾーンになる
        if (state == State.RUNNING && center != null) {
            TimeFields.set(level, worldPosition, center,
                    ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_RADIUS.get() : 24, false);
        } else {
            TimeFields.remove(level, worldPosition);
        }
        boolean lit = state == State.RUNNING;
        BlockState bs = getBlockState();
        if (bs.hasProperty(AbstractMachineBlock.LIT) && bs.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, bs.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
        // 点火中は進み具合を描くので、こまめに送る
        if (state != before || level.getGameTime() % 20 == 0 || state == State.IGNITING && level.getGameTime() % 4 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void checkStructure(ServerLevel level) {
        Structures.Reactor r = Structures.findReactor(level, worldPosition);
        List<BlockPos> newPorts = r == null ? List.of() : r.ports();
        alarms = r == null ? List.of() : r.alarms();
        if (!newPorts.equals(ports)) {
            link(level, ports, null);
            link(level, newPorts, worldPosition);
            ports = newPorts;
        }
        io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, worldPosition, formed, r != null, firstCheck, 7, 1, 12);
        firstCheck = false;
        formed = r != null;
        center = r == null ? worldPosition.above(Structures.CONTROLLER_BELOW_CENTER) : r.center();
        if (formed && level.getBlockEntity(center) instanceof RogueBlackHoleBlockEntity rogue) {
            // 野良ブラックホールを炉心として取り込む（崩壊したリアクターを組み直したとき）
            mass = Math.min(MAX_MASS, rogue.mass());
            spin = rogue.spin();
            state = State.RUNNING;
            ignitedBefore = true;
            level.removeBlock(center, false);
            broadcast(level, "gui.singulo.reactor.recaptured");
            setChanged();
        }
    }

    private static void link(Level level, List<BlockPos> ports, @Nullable BlockPos controller) {
        for (BlockPos p : ports) {
            // 読み込まれていないチャンクには触れない（読み込み直しが起きる）
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PortBlockEntity port) {
                port.link(controller);
            }
        }
    }

    /** GUI の「点火」。種があれば点火を始め、制限時間内に点火エネルギーを受け取ると炉心ができる。 */
    public boolean ignite() {
        if (state != State.DORMANT || !formed || items.getStackInSlot(SLOT_SEED).isEmpty()) {
            return false;
        }
        state = State.IGNITING;
        ignitionStored = 0;
        ignitionElapsed = 0;
        ignitionCharged = false;
        int window = ServerConfig.SPEC.isLoaded() ? ServerConfig.IGNITION_WINDOW_SECONDS.get() : 10;
        ignitionTicks = window * 20;
        setChanged();
        return true;
    }

    private void stopIgnitionSound(ServerLevel level) {
        Vec3 core = coreCenter();
        var id = Singulo.id("penrose_reactor_ignition");
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            if (p.distanceToSqr(core) < 96 * 96) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(id, net.minecraft.sounds.SoundSource.BLOCKS));
            }
        }
    }

    /** 点火の電力がどれだけ満ちたか（0〜1。クライアントでは同期された値）。 */
    public float ignitionCharge() {
        if (level != null && level.isClientSide) {
            return clientCharge;
        }
        return ignitionCharged ? 1F : (float) Math.min(1.0, ignitionStored / (double) Math.max(1, ignitionEnergy()));
    }

    static long ignitionEnergy() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.IGNITION_ENERGY.get() : 50_000_000_000L;
    }

    private void tickIgnition(ServerLevel level) {
        ignitionElapsed++;
        if (ignitionElapsed == IGNITION_SEQUENCE_TICKS - IGNITION_SOUND_TICKS) {
            // 点火の音（鳴り終わる瞬間に炉心ができる）
            Vec3 core = coreCenter();
            level.playSound(null, core.x, core.y, core.z, io.github.genichimaruo.singulo.registry.SinguloSounds.get("penrose_reactor_ignition"),
                    net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
        }
        if (!ignitionCharged && ignitionStored >= ignitionEnergy()) {
            ignitionCharged = true;                                    // 電力は満ちた。あとは演出の終わりを待つ
            setChanged();
        }
        if (ignitionCharged && ignitionElapsed >= IGNITION_SEQUENCE_TICKS) {
            items.extractItem(SLOT_SEED, 1, false);
            state = State.RUNNING;
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, worldPosition, 64, "ignite");
            // ブラックホールができた音（炉心から、遠くまで）
            Vec3 core = coreCenter();
            level.playSound(null, core.x, core.y, core.z, io.github.genichimaruo.singulo.registry.SinguloSounds.BLACK_HOLE_FORMATION.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
            mass = START_MASS;
            spin = 0;
            ignitedBefore = true;
            ignitionStored = 0;
            if (center != null) {
                level.playSound(null, center, net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                        net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.5F);
            }
            setChanged();
            return;
        }
        if (!ignitionCharged && --ignitionTicks <= 0) {
            // 時間切れ。注いだ電力は失われるが、種は残る（重いが詰まない）。鳴り始めた点火の音も止める
            state = State.DORMANT;
            ignitionStored = 0;
            stopIgnitionSound(level);
            broadcast(level, "gui.singulo.reactor.ignition_failed");
            setChanged();
        }
    }

    private void run(ServerLevel level) {
        double eta = efficiency(spin);
        long perPellet = Math.round(energyPerPellet() * eta);
        double max = eddingtonPerTick();
        double wanted = perPellet <= 0 ? 0 : (double) (BUFFER_CAPACITY - buffer) / perPellet;
        // 投入間隔を決めていればその間隔で（エディントン限界まで）、自動なら蓄電の減った分だけ
        double rate = feedInterval > 0 ? Math.min(max, 1.0 / feedInterval) : Math.max(MIN_INJECTION * max, Math.min(max, wanted));
        pelletCarry += rate;
        int whole = (int) pelletCarry;
        int taken = whole <= 0 ? 0 : items.extractItem(SLOT_FUEL, whole, false).getCount();
        pelletCarry = taken < whole ? 0 : pelletCarry - whole;
        if (taken > 0) {
            buffer = Math.min(BUFFER_CAPACITY, buffer + taken * perPellet);
            // ペレットでは上限を超えない（すでに超えていれば減らしもしない）
            mass = Math.max(mass, Math.min(MAX_MASS, mass + taken * PELLET_MASS * GROWTH_FRACTION));
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_PELLETS, Math.min(taken, 12));
        }
        evaporate(level);
        if (state != State.RUNNING) {
            return;
        }
        // 角運動量インジェクター
        double target = SPIN_TARGETS[spinTarget];
        if (spin < target && buffer > 0) {
            long p = Math.min(buffer, SPIN_POWER);
            buffer -= p;
            spin = Math.min(target, spin + p / (mass * SPIN_ENERGY));
        }
        // 副産物
        boolean generating = taken > 0 || buffer > 0;
        int jets = items.getStackInSlot(SLOT_JET).getCount();
        if (generating && jets > 0 && (jetTimer += jets) >= JET_TICKS) {
            jetTimer -= JET_TICKS;
            output(new ItemStack(item("jet_condensate")));
        }
        if (mass >= 150 && mass <= HAWKING_MAX_MASS && !items.getStackInSlot(SLOT_HAWKING).isEmpty() && ++hawkingTimer >= HAWKING_TICKS) {
            hawkingTimer = 0;
            output(new ItemStack(item("hawking_condensate")));
        }
        if (spin >= 0.7 && mass >= 400 && mass <= 2000 && !items.getStackInSlot(SLOT_ERGO).isEmpty() && ++ergoTimer >= ERGO_TICKS) {
            ergoTimer = 0;
            spin = Math.max(0, spin - 0.02);
            output(new ItemStack(item("exotic_matter")));
        }
    }

    /** ホーキング放射で質量が減る。小さいほど速く、100 を割ると暴走してバーストする。 */
    private void evaporate(ServerLevel level) {
        double m = mass / 1000.0;
        mass -= EVAPORATION_PER_SECOND / (m * m) / 20.0;
        if (mass < BURST_MASS) {
            burst(level);
        }
    }

    private void burst(ServerLevel level) {
        state = formed ? State.DORMANT : State.UNFORMED;
        mass = 0;
        spin = 0;
        buffer = 0;
        if (center != null) {
            boolean damage = ServerConfig.SPEC.isLoaded() && ServerConfig.EVAPORATION_BURST_DAMAGES_WORLD.get();
            level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 6.0F,
                    damage ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        }
        broadcast(level, "gui.singulo.reactor.burst");
        setChanged();
    }

    /** 副産物を出力スロットへ。出力スロットは外から入れられない（isItemValid が false）ので、直接置く。 */
    private void output(ItemStack stack) {
        for (int i = SLOT_OUT; i < SLOTS; i++) {
            ItemStack here = items.getStackInSlot(i);
            if (here.isEmpty()) {
                items.setStackInSlot(i, stack.copy());
                return;
            }
            if (ItemStack.isSameItemSameComponents(here, stack) && here.getCount() < here.getMaxStackSize()) {
                items.setStackInSlot(i, here.copyWithCount(here.getCount() + 1));
                return;
            }
        }
    }

    /** 事象の地平線（黒い球）の半径（ブロック）。炉心の質量で 0.6〜1.6。描画と、触れたものを消す判定に使う。 */
    public double horizonRadius() {
        return horizonFor(mass);
    }

    /** 質量から地平線の半径（炉心と野良ブラックホールで同じ式。上限を超えた分はゆっくり大きくなる）。 */
    public static double horizonFor(double mass) {
        return 0.6 + Math.min(1.0, mass / MAX_MASS) + 0.2 * Math.max(0, mass - MAX_MASS) / MAX_MASS;
    }

    /** リングの内側（強い引力）とみなす、中心からの距離（ブロック）。 */
    public static final double INNER_RADIUS = Structures.REACTOR_RADIUS + 0.5;

    /** 炉心の中心（ワールド座標）。サーバーでもクライアントでも同じ場所。 */
    public Vec3 coreCenter() {
        return Vec3.atCenterOf(center != null ? center : worldPosition.above(Structures.CONTROLLER_BELOW_CENTER));
    }

    /**
     * 1 tick ぶんの引力をかけたあとの速さ。引力の外なら null。サーバー（モブ・アイテム）とクライアント（自分のプレイヤー）で同じ式を使う。
     * <ul>
     *   <li>リングの外（引力帯の半径まで）: 弱い引力（blackHolePullStrength、距離の二乗に反比例・上限つき）。歩けば逃げられる</li>
     *   <li>リングの内側: 強い引力（blackHoleInnerPullStrength、中心に近いほど強い）。今の速さも中心向きに寄せて逃げられない</li>
     * </ul>
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3 pulledVelocity(Vec3 velocity, Vec3 body, Vec3 c) {
        return pulledVelocity(velocity, body, c, INNER_RADIUS, 1.0);
    }

    /**
     * 引き寄せたあとの速さ。inner は強く引く内側の半径、scale は強さの倍率（野良ブラックホールは質量で大きくなる）。
     * 内側では逃げられないほど強く、外側では距離の二乗で弱まる。
     */
    public static Vec3 pulledVelocity(Vec3 velocity, Vec3 body, Vec3 c, double inner, double scale) {
        Vec3 to = c.subtract(body);
        double d = to.length();
        if (d < 1e-3) {
            return null;
        }
        if (d <= inner) {
            double innerStrength = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_INNER_PULL_STRENGTH.get() : 0.25;
            double accel = innerStrength * scale * (1 + 3.5 * (1 - d / inner));
            Vec3 v = velocity.scale(0.85).add(to.scale(accel / d));
            double max = 1.6;
            return v.lengthSqr() > max * max ? v.normalize().scale(max) : v;
        }
        int radius = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_RADIUS.get() : 24;
        if (d > radius) {
            return null;
        }
        double strength = (ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_STRENGTH.get() : 0.04) * scale;
        double accel = Math.min(strength, strength * 16 / (d * d));
        return velocity.add(to.scale(accel / d));
    }

    /**
     * 引力と潮汐と事象の地平線（サーバー、毎tick）。
     * <ul>
     *   <li>引力: {@link #pulledVelocity}。プレイヤーは自分のクライアントで同じ引力をかける（サーバーから速さを送ると歩きとぶつかってカクつくため）</li>
     *   <li>潮汐帯: 防具を無視する継続ダメージ</li>
     *   <li>事象の地平線に触れたもの: どんな体力・耐性でも必ず消える（{@link EventHorizon}）。プレイヤーもここはサーバーで判定する</li>
     * </ul>
     * クリエイティブとスペクテイターのプレイヤーは引き寄せも消滅も受けない（組み立てのため）。
     */
    private void applyHazards(ServerLevel level) {
        if (center == null) {
            return;
        }
        Vec3 c = coreCenter();
        boolean kill = !ServerConfig.SPEC.isLoaded() || ServerConfig.EVENT_HORIZON_KILL.get();
        double horizon = horizonRadius();
        int radius = Math.max((int) Math.ceil(INNER_RADIUS),
                ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_RADIUS.get() : 24);
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(radius), EventHorizonTargets::affected)) {
            if (kill && touches(e.getBoundingBox(), c, horizon)) {
                absorb(level, foreignMass(e));
                returnRecord(level, e, c, horizon);
                EventHorizon.consume(level, e);
                continue;
            }
            if (e instanceof Player) {
                continue;
            }
            Vec3 v = pulledVelocity(e.getDeltaMovement(), e.getBoundingBox().getCenter(), c);
            if (v == null) {
                continue;
            }
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            if (e.getBoundingBox().getCenter().distanceTo(c) <= INNER_RADIUS) {
                e.fallDistance = 0;
                if (e instanceof net.minecraft.world.entity.Mob mob) {
                    mob.getNavigation().stop();
                }
            }
        }
        int interval = ServerConfig.SPEC.isLoaded() ? ServerConfig.PULL_CHECK_INTERVAL_TICKS.get() : 5;
        int tidal = ServerConfig.SPEC.isLoaded() ? ServerConfig.TIDAL_DAMAGE_RADIUS.get() : 4;
        if (tidal > 0 && level.getGameTime() % 10 < interval && level.getGameTime() % interval == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(tidal),
                    e -> e.position().distanceTo(c) <= tidal && EventHorizonTargets.affected(e))) {
                e.hurt(SinguloDamageTypes.tidal(level, null), 4.0F);
            }
        }
    }

    // ------------------------------------------------------------------ 投入間隔

    /** ペレットの投入間隔（tick）。0 は自動（蓄電の減った分だけ投入する）。 */
    private int feedInterval;

    public int feedInterval() {
        return feedInterval;
    }

    public int spinTargetIndex() {
        return spinTarget;
    }

    public void setSpinTargetIndex(int index) {
        spinTarget = Math.max(0, Math.min(SPIN_TARGETS.length - 1, index));
        setChanged();
    }

    public void setFeedInterval(int ticks) {
        feedInterval = Math.max(0, Math.min(MAX_FEED_INTERVAL, ticks));
        setChanged();
    }

    // ------------------------------------------------------------------ 飲み込んだ物の質量と、炉の崩壊

    /** 地平線に飲み込まれた物の質量（低め）。モブは最大体力と身につけた物、アイテムは質量値から。 */
    public static double foreignMass(Entity e) {
        if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
            return itemMass(item.getItem());
        }
        if (e instanceof LivingEntity living) {
            double m = living.getMaxHealth() * MOB_MASS_PER_HEALTH;
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                m += itemMass(living.getItemBySlot(slot));
            }
            return m;
        }
        return 0;
    }

    /** 地平線の向こうからクリスタルメモリが戻ってくる確率（モブ1体・アイテム1つあたり）。 */
    public static final double RECORD_FROM_MOB = 0.03;
    public static final double RECORD_FROM_ITEM = 0.005;

    /**
     * 飲み込んだものから、まれに旧文明のクリスタルメモリが戻ってくる（地平線の縁から外へ弾き出される）。
     * 戻ってきたクリスタルメモリはしばらく引き寄せられない。
     */
    public static void returnRecord(ServerLevel level, Entity swallowed, Vec3 c, double horizon) {
        double chance = swallowed instanceof LivingEntity ? RECORD_FROM_MOB
                : swallowed instanceof net.minecraft.world.entity.item.ItemEntity item && !EventHorizonTargets.isReturnedRecord(item)
                ? RECORD_FROM_ITEM : 0;
        if (chance <= 0 || level.random.nextDouble() >= chance) {
            return;
        }
        Vec3 dir = new Vec3(level.random.nextGaussian(), Math.abs(level.random.nextGaussian()) + 0.5, level.random.nextGaussian()).normalize();
        Vec3 at = c.add(dir.scale(horizon + 0.6));
        var record = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, new ItemStack(item("record_fragment")));
        record.setDeltaMovement(dir.scale(0.7));
        record.getPersistentData().putLong(EventHorizonTargets.RETURNED, level.getGameTime());
        level.addFreshEntity(record);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    static double itemMass(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double per = Math.max(1, io.github.genichimaruo.singulo.data.MassValues.INSTANCE.massOf(stack));
        return per * stack.getCount() * ITEM_MASS_RATE;
    }

    /** 飲み込んだ物の質量を炉心に加える。上限を大きく超えると炉が崩壊する。 */
    private void absorb(ServerLevel level, double m) {
        if (m <= 0 || state != State.RUNNING) {
            return;
        }
        mass += m;
        setChanged();
        if (mass > MAX_MASS + COLLAPSE_MARGIN && collapseStart < 0) {
            startCollapse(level);
        }
    }

    /** 炉心質量警報器の信号の強さ: 上限未満は0、上限で1、崩壊する質量で15。 */
    public int alarmSignal() {
        if (state != State.RUNNING || mass < MAX_MASS) {
            return 0;
        }
        return (int) Math.min(15, 1 + Math.floor(14 * (mass - MAX_MASS) / COLLAPSE_MARGIN));
    }

    /** 警報器に信号を書き込み、上限を超えていれば警報音を鳴らす。 */
    private void updateAlarms(ServerLevel level) {
        int signal = alarmSignal();
        boolean blink = signal > 0 && (signal == 1 || level.getGameTime() % 20 < 10);
        for (BlockPos p : alarms) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof io.github.genichimaruo.singulo.multiblock.SignalPartBlock
                    && (s.getValue(io.github.genichimaruo.singulo.multiblock.SignalPartBlock.POWER) != signal
                    || s.getValue(io.github.genichimaruo.singulo.multiblock.SignalPartBlock.LIT) != blink)) {
                level.setBlock(p, s.setValue(io.github.genichimaruo.singulo.multiblock.SignalPartBlock.POWER, signal)
                        .setValue(io.github.genichimaruo.singulo.multiblock.SignalPartBlock.LIT, blink), Block.UPDATE_ALL);
            }
        }
        if (signal > 1 && level.getGameTime() % 20 == 0) {
            float pitch = 1.4F + 0.04F * signal;
            Vec3 at = Vec3.atCenterOf(worldPosition);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 2.5F, pitch);
            for (BlockPos p : alarms) {
                level.playSound(null, p, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), net.minecraft.sounds.SoundSource.BLOCKS,
                        1.5F, pitch);
            }
        }
    }

    /** 崩壊の始まった時刻（-1 は崩壊していない）と、吸い込まれていくブロック（近い順、最後がコントローラ）。 */
    private long collapseStart = -1;
    private List<BlockPos> collapseBlocks = List.of();
    private int[] collapseStates = new int[0];
    private int collapseDone;

    public boolean collapsing() {
        return collapseStart >= 0;
    }

    public long collapseStart() {
        return collapseStart;
    }

    public List<BlockPos> collapseBlocks() {
        return collapseBlocks;
    }

    public BlockState collapseState(int i) {
        return Block.stateById(collapseStates[i]);
    }

    /** i 番目のブロックが吸い込まれ始める、崩壊の始まりからの tick。 */
    public static int collapseDelay(int i, int n) {
        return n <= 1 ? 0 : (int) (COLLAPSE_SPREAD * Math.pow(i / (double) (n - 1), 0.85));
    }

    private void startCollapse(ServerLevel level) {
        BlockPos c = center != null ? center : worldPosition.above(Structures.CONTROLLER_BELOW_CENTER);
        java.util.Set<BlockPos> picked = new java.util.LinkedHashSet<>();
        for (BlockPos p : Structures.reactorLayout(c).keySet()) {
            if (!level.getBlockState(p).isAir()) {
                picked.add(p.immutable());
            }
        }
        // まわりの物も巻き込む（壊せない物と、入れ物などのブロックエンティティは残す）
        int r = COLLAPSE_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distSqr(c) > r * r || picked.contains(p) || p.equals(worldPosition)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null || !s.getFluidState().isEmpty()) {
                continue;
            }
            picked.add(p.immutable());
        }
        List<BlockPos> list = new ArrayList<>(picked);
        list.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(c)));
        if (list.size() > COLLAPSE_MAX_BLOCKS) {
            list = new ArrayList<>(list.subList(0, COLLAPSE_MAX_BLOCKS));
        }
        list.add(worldPosition);                                      // 最後にコントローラ自身
        collapseBlocks = List.copyOf(list);
        collapseStates = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            collapseStates[i] = Block.getId(level.getBlockState(list.get(i)));
        }
        collapseStart = level.getGameTime();
        collapseDone = 0;
        broadcast(level, "gui.singulo.reactor.collapse");
        Vec3 core = coreCenter();
        level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.5F);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void tickCollapse(ServerLevel level) {
        int t = (int) (level.getGameTime() - collapseStart);
        int n = collapseBlocks.size();
        // 引力と地平線は続く
        applyHazards(level);
        while (collapseDone < n - 1 && collapseDelay(collapseDone, n) <= t) {
            BlockPos p = collapseBlocks.get(collapseDone);
            if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            collapseDone++;
        }
        if (t % 12 == 0) {
            Vec3 core = coreCenter();
            level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 0.5F + level.random.nextFloat() * 0.3F);
        }
        if (t >= COLLAPSE_TICKS) {
            finishCollapse(level);
        }
    }

    /** 崩壊の終わり: 炉心の中心に野良ブラックホールを残し、コントローラも飲み込まれて消える。 */
    private void finishCollapse(ServerLevel level) {
        BlockPos c = center != null ? center : worldPosition.above(Structures.CONTROLLER_BELOW_CENTER);
        double m = mass;
        double a = spin;
        level.setBlock(c, io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(c) instanceof RogueBlackHoleBlockEntity rogue) {
            rogue.setCore(m, a);
        }
        Vec3 core = Vec3.atCenterOf(c);
        level.playSound(null, core.x, core.y, core.z, io.github.genichimaruo.singulo.registry.SinguloSounds.BLACK_HOLE_FORMATION.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.6F);
        // 中身も飲み込まれる
        for (int i = 0; i < SLOTS; i++) {
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        collapseStart = -1;
        state = State.UNFORMED;
        mass = 0;
        level.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void writeCollapse(CompoundTag tag) {
        tag.putLong("collapse_start", collapseStart);
        long[] ps = new long[collapseBlocks.size()];
        for (int i = 0; i < ps.length; i++) {
            ps[i] = collapseBlocks.get(i).asLong();
        }
        tag.putLongArray("collapse_pos", ps);
        tag.putIntArray("collapse_state", collapseStates);
        tag.putInt("collapse_done", collapseDone);
    }

    private void readCollapse(CompoundTag tag) {
        if (!tag.contains("collapse_start")) {
            if (level != null && level.isClientSide && tag.getBoolean("sync")) {
                collapseStart = -1;
            }
            return;
        }
        collapseStart = tag.getLong("collapse_start");
        long[] ps = tag.getLongArray("collapse_pos");
        List<BlockPos> list = new ArrayList<>(ps.length);
        for (long p : ps) {
            list.add(BlockPos.of(p));
        }
        collapseBlocks = List.copyOf(list);
        collapseStates = tag.getIntArray("collapse_state");
        collapseDone = tag.getInt("collapse_done");
    }

    // ------------------------------------------------------------------ ペレットを飲み込む様子（クライアント）

    /** 飲み込まれていくペレット（クライアント）: 出発した時刻と、どのポートから来たか。 */
    public record PelletFlight(long start, int port, float twist) {}

    private final java.util.ArrayDeque<PelletFlight> clientPellets = new java.util.ArrayDeque<>();
    public static final int PELLET_FLIGHT_TICKS = 40;

    public java.util.Collection<PelletFlight> clientPellets() {
        if (level != null) {
            long now = level.getGameTime();
            while (!clientPellets.isEmpty() && now - clientPellets.peekFirst().start() > PELLET_FLIGHT_TICKS) {
                clientPellets.pollFirst();
            }
        }
        return clientPellets;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_PELLETS) {
            if (level != null && level.isClientSide && clientPellets.size() < 64) {
                for (int i = 0; i < param; i++) {
                    clientPellets.addLast(new PelletFlight(level.getGameTime() + i * 3L, level.random.nextInt(12),
                            level.random.nextFloat() * 2 - 1));
                }
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    // ------------------------------------------------------------------ クライアント: 自分のプレイヤーへの引力

    /** クライアントで読み込まれているリアクター（自分のプレイヤーに引力をかけるため）。 */
    private static final java.util.Set<PenroseReactorBlockEntity> CLIENT_LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_LOADED.add(this);
        }
    }

    /** クライアントで、稼働中のリアクター。 */
    public static java.util.List<PenroseReactorBlockEntity> clientRunning(Level level) {
        java.util.List<PenroseReactorBlockEntity> out = new java.util.ArrayList<>();
        for (PenroseReactorBlockEntity r : CLIENT_LOADED) {
            if (!r.isRemoved() && r.level == level && r.state == State.RUNNING) {
                out.add(r);
            }
        }
        return out;
    }

    /** 当たり判定の箱が、中心 c・半径 r の球に触れているか。 */
    static boolean touches(AABB box, Vec3 c, double r) {
        double dx = Math.max(box.minX - c.x, Math.max(0, c.x - box.maxX));
        double dy = Math.max(box.minY - c.y, Math.max(0, c.y - box.maxY));
        double dz = Math.max(box.minZ - c.z, Math.max(0, c.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** 炉心制御装置と抽出ポートの隣（リアクターの部品以外）へ電力を押し出す。1 tick の合計は penroseMaxOutput まで。 */
    private void pushEnergy(ServerLevel level) {
        if (buffer <= 0) {
            return;
        }
        long budget = Math.min(buffer, maxOutput());
        List<BlockPos> sources = new ArrayList<>(ports);
        sources.add(worldPosition);
        for (BlockPos src : sources) {
            for (Direction dir : Direction.values()) {
                if (budget <= 0) {
                    break;
                }
                BlockPos n = src.relative(dir);
                if (n.equals(worldPosition) || Structures.isReactorPart(level.getBlockState(n))) {
                    continue;
                }
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
                if (target != null && target.canReceive()) {
                    // ケーブルなど mod の口へは int を超えて渡す
                    long sent = io.github.genichimaruo.singulo.energy.LongEnergyStorage.receive(target, budget, false);
                    budget -= sent;
                    buffer -= sent;
                    lastOutput += sent;
                }
            }
        }
    }

    private void broadcast(ServerLevel level, String key) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(32))) {
            p.displayClientMessage(Component.translatable(key), false);
        }
    }

    // ------------------------------------------------------------------ 外から見えるもの

    public IEnergyStorage energy() {
        return energyPort;
    }

    public IItemHandler automationItems() {
        return automation;
    }

    /** ダークマターの投入口。光を出さないのでエディントン限界を受けず、そのまま炉心の質量になる（発電はしない）。 */
    public net.neoforged.neoforge.fluids.capability.IFluidHandler darkMatterInput() {
        return darkMatterPort;
    }

    private final class DarkMatterPort implements net.neoforged.neoforge.fluids.capability.IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return stack.is(io.github.genichimaruo.singulo.registry.SinguloFluids.get("dark_matter"));
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource) || state != State.RUNNING || !formed) {
                return 0;
            }
            int room = (int) Math.floor((MAX_MASS - mass) / DARK_MATTER_MASS_PER_MB);
            int taken = Math.max(0, Math.min(resource.getAmount(), room));
            if (taken > 0 && action.execute()) {
                mass = Math.min(MAX_MASS, mass + taken * DARK_MATTER_MASS_PER_MB);
                setChanged();
            }
            return taken;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
    }

    public ItemStackHandler items() {
        return items;
    }

    public State state() {
        return state;
    }

    public double mass() {
        return mass;
    }

    public double spin() {
        return spin;
    }

    public long buffer() {
        return buffer;
    }

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public BlockPos center() {
        return center;
    }

    public void cycleSpinTarget() {
        spinTarget = (spinTarget + 1) % SPIN_TARGETS.length;
        setChanged();
    }

    /** テスト用: 炉心の状態を直接決める。 */
    public void forceCore(double mass, double spin) {
        this.mass = mass;
        this.spin = spin;
        this.state = mass > 0 ? State.RUNNING : State.DORMANT;
    }

    /** 点火中は点火エネルギーを受け取り、稼働中は蓄電から取り出せる。 */
    private final class EnergyPort implements io.github.genichimaruo.singulo.energy.LongEnergyStorage {
        @Override
        public long receiveLong(long amount, boolean simulate) {
            if (state != State.IGNITING) {
                return 0;
            }
            long take = Math.min(amount, Math.max(0, ignitionEnergy() - ignitionStored));
            if (!simulate) {
                ignitionStored += take;
            }
            return take;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public long extractLong(long amount, boolean simulate) {
            long take = Math.min(amount, buffer);
            if (!simulate) {
                buffer -= take;
            }
            return take;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return (int) extractLong(amount, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, buffer);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return state == State.RUNNING;
        }

        @Override
        public boolean canReceive() {
            return state == State.IGNITING;
        }
    }

    /** 搬入は種・燃料・抽出装置、搬出は副産物だけ。 */
    private final class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < SLOT_OUT ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= SLOT_OUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < SLOT_OUT && items.isItemValid(slot, stack);
        }
    }

    // ------------------------------------------------------------------ GUI・同期・保存

    public static final int D_STATE = 0, D_MASS_X10 = 1, D_SPIN_X1000 = 2, D_ETA_X10000 = 3, D_OUT_LO = 4, D_OUT_HI = 5,
            D_BUFFER_MFE = 6, D_IGNITION_PCT = 7, D_IGNITION_SECS = 8, D_TARGET = 9, D_MODE = 10, D_INTERVAL = 11, COUNT = 12;

    public SyncedInts syncData() {
        return SyncedInts.server(COUNT, i -> switch (i) {
            case D_STATE -> state.ordinal();
            case D_MASS_X10 -> (int) Math.round(mass * 10);
            case D_SPIN_X1000 -> (int) Math.round(spin * 1000);
            case D_ETA_X10000 -> state == State.RUNNING ? (int) Math.round(efficiency(spin) * 10000) : 0;
            case D_OUT_LO -> (int) (lastOutput & 0xFFFFFFFFL);
            case D_OUT_HI -> (int) (lastOutput >>> 32);
            case D_BUFFER_MFE -> (int) (buffer / 1_000_000L);
            case D_IGNITION_PCT -> (int) Math.min(100, ignitionStored * 100 / Math.max(1, ignitionEnergy()));
            case D_IGNITION_SECS -> ignitionCharged ? Math.max(0, IGNITION_SEQUENCE_TICKS - ignitionElapsed) / 20 : ignitionTicks / 20;
            case D_TARGET -> spinTarget;
            case D_MODE -> mode().ordinal();
            case D_INTERVAL -> feedInterval;
            default -> 0;
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PenroseReactorMenu(containerId, inventory, worldPosition, items, syncData(), this);
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, worldPosition);
    }

    public void dropContents(Level level) {
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
    }

    /** チャンクの読み込み解除のときはポートに触らない（冷却塔のコントローラと同じ理由）。 */
    private boolean chunkUnloading;

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && !chunkUnloading) {
            link(level, ports, null);
        }
        CLIENT_LOADED.remove(this);
        if (level != null) {
            TimeFields.remove(level, worldPosition);
            java.util.Set<PenroseReactorBlockEntity> running = RUNNING.get(level);
            if (running != null) {
                running.remove(this);
            }
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        writeCore(tag);
        tag.putLong("buffer", buffer);
        tag.putInt("spin_target", spinTarget);
        tag.putInt("jet", jetTimer);
        tag.putInt("hawking", hawkingTimer);
        tag.putInt("ergo", ergoTimer);
        tag.putBoolean("ignited_before", ignitedBefore);
        tag.putInt("feed_interval", feedInterval);
        if (collapseStart >= 0) {
            writeCollapse(tag);
        }
    }

    private void writeCore(CompoundTag tag) {
        tag.putString("state", state.name());
        tag.putDouble("mass", mass);
        tag.putDouble("spin", spin);
    }

    /** クライアントで受け取った点火の進み（0〜1。描画用）と、受け取った時刻・電力の満ち具合。 */
    private float clientIgnition;
    private long clientIgnitionAt;
    private float clientCharge;

    /** 点火の進み（0〜1）。サーバーでは注がれた電力の割合、クライアントでは同期された値。 */
    public float ignitionProgress() {
        if (level != null && level.isClientSide) {
            // 同期の合間は時刻で進める（なめらかに）
            if (state != State.IGNITING) {
                return clientIgnition;
            }
            return Math.min(1F, clientIgnition + (level.getGameTime() - clientIgnitionAt) / (float) IGNITION_SEQUENCE_TICKS);
        }
        return Math.min(1F, ignitionElapsed / (float) IGNITION_SEQUENCE_TICKS);
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            items.deserializeNBT(registries, tag.getCompound("items"));
        }
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.UNFORMED;
        }
        // 保存から読み込むとき、点火の途中だったものは止まった状態に戻す（描画のための同期では戻さない）
        if (state == State.IGNITING && !tag.getBoolean("sync")) {
            state = State.DORMANT;
        }
        clientIgnition = tag.getFloat("ignition");
        clientCharge = tag.getFloat("charge");
        clientIgnitionAt = level != null ? level.getGameTime() : 0;
        mass = tag.getDouble("mass");
        spin = tag.getDouble("spin");
        buffer = tag.getLong("buffer");
        spinTarget = tag.getInt("spin_target");
        jetTimer = tag.getInt("jet");
        hawkingTimer = tag.getInt("hawking");
        ergoTimer = tag.getInt("ergo");
        ignitedBefore = tag.getBoolean("ignited_before");
        if (tag.contains("feed_interval")) {
            feedInterval = tag.getInt("feed_interval");
        }
        readCollapse(tag);
    }

    /** 描画用に状態・質量・スピンをクライアントへ送る。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeCore(tag);
        tag.putBoolean("sync", true);
        tag.putFloat("ignition", ignitionProgress());
        tag.putFloat("charge", ignitionCharge());
        if (collapseStart >= 0) {
            writeCollapse(tag);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
