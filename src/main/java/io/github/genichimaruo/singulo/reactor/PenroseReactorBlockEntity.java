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
 *   <li>副産物: ジェット・コレクター（発電中、1基につき1分に1個のジェット凝縮体）、ホーキング・コレクター（質量150〜400で
 *       5分に1個のホーキング凝縮体）、エルゴスフィア・リング（スピン0.7以上・質量400〜2000で1分に1個のエキゾチック物質。スピンが減る）</li>
 *   <li>危険: 引力帯（半径 blackHolePullRadius）と潮汐帯（半径 tidalDamageRadius、防具を無視）</li>
 * </ul>
 */
public class PenroseReactorBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener {
    public enum State { UNFORMED, DORMANT, IGNITING, RUNNING }

    public enum Mode { POWER, CATALYST, ERGO, STANDBY, DANGER }

    public static final double START_MASS = 500;
    public static final double MAX_MASS = 5000;
    public static final double BURST_MASS = 100;
    public static final double PELLET_MASS = 16;
    public static final double GROWTH_FRACTION = 0.1;
    public static final double EVAPORATION_PER_SECOND = 0.006;
    public static final double MIN_INJECTION = 0.03;
    public static final long BUFFER_CAPACITY = 40_000_000_000L;
    public static final long SPIN_POWER = 100_000_000L;
    /** スピンを 1 上げるのに要る電力 = SPIN_ENERGY × 炉心質量（FE）。質量5000・100 MFE/t で約5分。 */
    public static final double SPIN_ENERGY = 1.2e8;
    public static final double[] SPIN_TARGETS = {0.0, 0.5, 0.9, 0.99, 1.0};
    static final int CHECK_INTERVAL = 40;
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
    private double pelletCarry;
    private int jetTimer;
    private int hawkingTimer;
    private int ergoTimer;
    private long lastOutput;
    private long nextCheck;
    @Nullable
    private BlockPos center;
    private List<BlockPos> ports = List.of();
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
        if (mass <= 400) {
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
        if (state != before || level.getGameTime() % 20 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void checkStructure(ServerLevel level) {
        Structures.Reactor r = Structures.findReactor(level, worldPosition);
        List<BlockPos> newPorts = r == null ? List.of() : r.ports();
        if (!newPorts.equals(ports)) {
            link(level, ports, null);
            link(level, newPorts, worldPosition);
            ports = newPorts;
        }
        io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, worldPosition, formed, r != null, firstCheck, 7, 1, 12);
        firstCheck = false;
        formed = r != null;
        center = r == null ? worldPosition.above(Structures.CONTROLLER_BELOW_CENTER) : r.center();
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
        int window = ServerConfig.SPEC.isLoaded() ? ServerConfig.IGNITION_WINDOW_SECONDS.get() : 10;
        ignitionTicks = window * 20;
        if (level != null) {
            // 点火の音（電力を注ぐ10秒のあいだの高まり）
            Vec3 core = coreCenter();
            level.playSound(null, core.x, core.y, core.z, io.github.genichimaruo.singulo.registry.SinguloSounds.get("penrose_reactor_ignition"),
                    net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
        }
        setChanged();
        return true;
    }

    static long ignitionEnergy() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.IGNITION_ENERGY.get() : 50_000_000_000L;
    }

    private void tickIgnition(ServerLevel level) {
        if (ignitionStored >= ignitionEnergy()) {
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
        if (--ignitionTicks <= 0) {
            // 時間切れ。注いだ電力は失われるが、種は残る（重いが詰まない）
            state = State.DORMANT;
            ignitionStored = 0;
            broadcast(level, "gui.singulo.reactor.ignition_failed");
            setChanged();
        }
    }

    private void run(ServerLevel level) {
        double eta = efficiency(spin);
        long perPellet = Math.round(energyPerPellet() * eta);
        double max = eddingtonPerTick();
        double wanted = perPellet <= 0 ? 0 : (double) (BUFFER_CAPACITY - buffer) / perPellet;
        double rate = Math.max(MIN_INJECTION * max, Math.min(max, wanted));
        pelletCarry += rate;
        int whole = (int) pelletCarry;
        int taken = whole <= 0 ? 0 : items.extractItem(SLOT_FUEL, whole, false).getCount();
        pelletCarry = taken < whole ? 0 : pelletCarry - whole;
        if (taken > 0) {
            buffer = Math.min(BUFFER_CAPACITY, buffer + taken * perPellet);
            mass = Math.min(MAX_MASS, mass + taken * PELLET_MASS * GROWTH_FRACTION);
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
        if (mass >= 150 && mass <= 400 && !items.getStackInSlot(SLOT_HAWKING).isEmpty() && ++hawkingTimer >= HAWKING_TICKS) {
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
        return 0.6 + 1.0 * Math.min(1.0, mass / MAX_MASS);
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
        Vec3 to = c.subtract(body);
        double d = to.length();
        if (d < 1e-3) {
            return null;
        }
        if (d <= INNER_RADIUS) {
            double innerStrength = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_INNER_PULL_STRENGTH.get() : 0.25;
            double accel = innerStrength * (1 + 3.5 * (1 - d / INNER_RADIUS));
            Vec3 v = velocity.scale(0.85).add(to.scale(accel / d));
            double max = 1.6;
            return v.lengthSqr() > max * max ? v.normalize().scale(max) : v;
        }
        int radius = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_RADIUS.get() : 24;
        if (d > radius) {
            return null;
        }
        double strength = ServerConfig.SPEC.isLoaded() ? ServerConfig.BLACK_HOLE_PULL_STRENGTH.get() : 0.04;
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
                    int sent = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, budget), false);
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
    private final class EnergyPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (state != State.IGNITING) {
                return 0;
            }
            long take = Math.min(amount, Math.max(0, ignitionEnergy() - ignitionStored));
            if (!simulate) {
                ignitionStored += take;
            }
            return (int) take;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int take = (int) Math.min(amount, buffer);
            if (!simulate) {
                buffer -= take;
            }
            return take;
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
            D_BUFFER_MFE = 6, D_IGNITION_PCT = 7, D_IGNITION_SECS = 8, D_TARGET = 9, D_MODE = 10, COUNT = 11;

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
            case D_IGNITION_SECS -> ignitionTicks / 20;
            case D_TARGET -> spinTarget;
            case D_MODE -> mode().ordinal();
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
    }

    private void writeCore(CompoundTag tag) {
        tag.putString("state", state.name());
        tag.putDouble("mass", mass);
        tag.putDouble("spin", spin);
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
        if (state == State.IGNITING) {
            state = State.DORMANT;
        }
        mass = tag.getDouble("mass");
        spin = tag.getDouble("spin");
        buffer = tag.getLong("buffer");
        spinTarget = tag.getInt("spin_target");
        jetTimer = tag.getInt("jet");
        hawkingTimer = tag.getInt("hawking");
        ergoTimer = tag.getInt("ergo");
        ignitedBefore = tag.getBoolean("ignited_before");
    }

    /** 描画用に状態・質量・スピンをクライアントへ送る。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeCore(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
