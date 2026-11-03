package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 輸送ドローン。ステーションの上で待機し、便の記録（DroneNetwork.Flight）に従って、発送元のポートの真上 → 行き先のポートの真上
 * → ステーションへと飛ぶ。荷物は記録の側にあり、ドローンは見た目のために箱の段階だけを持つ。
 * 飛ぶときは、まず巡航の高さまで上がり、水平に進み、目的地の近くで降りる。プレイヤーやモブの攻撃では傷つかない。
 */
public class CargoDrone extends Mob {
    public static final int PRINT_TICKS = 50;
    /**
     * 印刷し終えてから、印刷の腕がステーションの中へしまわれるまで（PRINT はこの分まで数え続ける。解体のときは、ここから数え下ろして
     * まず腕が伸び、それから上から消えていく）。
     */
    public static final int RETRACT_TICKS = 20;
    static final double ACCEL = 0.05;

    private static final EntityDataAccessor<Integer> PRINT = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.INT);
    /** 運んでいる箱の段階（0 は空）。 */
    private static final EntityDataAccessor<Integer> CRATE = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.INT);
    /** ポートの真上で受け渡しをしているか（爪を下ろす）。 */
    private static final EntityDataAccessor<Boolean> DOCKED = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.BOOLEAN);
    /** 解体中か（ステーションの上に降りて、印刷を逆にたどって消える）。 */
    private static final EntityDataAccessor<Boolean> DISMANTLE = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.BOOLEAN);
    /** 壁を抜けている（抜ける少し前から、抜けたあと少しまで）。位相透過の光と音を出す。 */
    private static final EntityDataAccessor<Boolean> PHASING = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.BOOLEAN);
    /** 型（{@link DroneKind}）。 */
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.INT);
    /** 道がせまい所での速さ（ブロック/tick）と、壁の手前で位相透過の光を出し始める距離（ブロック）。 */
    static final double TIGHT_SPEED = 0.22, PHASE_LOOKAHEAD = 1.4;
    /** 道が見つからないとき、探し直すまで（tick）。 */
    static final int REPLAN_TICKS = 40;
    /** 壁を抜け終えてから、光を消すまで（tick）。 */
    static final int PHASE_HOLD = 8;

    @Nullable
    private BlockPos station;
    private int unit;
    /** 受け持っている便（0 はなし）。ステーションが壊されても便を最後まで飛ぶため、番号で探す。 */
    private long flightId;
    /** 今の区間の目的地と通り道（目的地が変わったら探し直す。保存しない）。道が見つからなければ route は null。 */
    @Nullable
    private Vec3 legGoal;
    @Nullable
    private DroneRoute route;
    private int replanAt;
    /** 壁を抜けて飛べるか（ステーションの位相透過アップグレード。ステーションが読み込まれている間に覚える）。 */
    private boolean phase;
    private int phaseHold;
    /** クライアント: 位相透過の光の強さ（0〜1。なめらかに変える）。 */
    private float phaseGlow, phaseGlowO;
    /** クライアント: 曲がるときの横の傾き（度。ジェット型が曲がる向きへ傾く。なめらかに変える）。 */
    private float bank, bankO;
    /** クライアント: 印刷の腕を描く場所（印刷していた上面。ドローンが飛び立っても、腕はここでしまわれる）。 */
    @Nullable
    public Vec3 armOrigin;

    public CargoDrone(EntityType<? extends CargoDrone> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PRINT, PRINT_TICKS + RETRACT_TICKS);
        entityData.define(CRATE, 0);
        entityData.define(DOCKED, false);
        entityData.define(DISMANTLE, false);
        entityData.define(PHASING, false);
        entityData.define(KIND, 0);
    }

    /** ステーションの枠のドローンを出す。printing なら station の上で印刷から始める。 */
    @Nullable
    public static CargoDrone spawn(ServerLevel level, BlockPos station, int unit, DroneKind kind, Vec3 at, boolean printing) {
        CargoDrone d = SinguloEntities.CARGO_DRONE.get().create(level);
        if (d == null) {
            return null;
        }
        d.station = station;
        d.unit = unit;
        d.entityData.set(KIND, kind.ordinal());
        d.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        if (printing) {
            d.entityData.set(PRINT, 0);
        }
        level.addFreshEntity(d);
        return d;
    }

    /**
     * 見えないまま行き先の近くまで来た便に、ドローンの実体を出す（行き先の手前の、見えている高い所から降りてくる）。出せたら true。
     */
    public static boolean materialize(ServerLevel level, DroneNetwork net, DroneNetwork.Flight f) {
        if (f.station == null && f.stage == DroneNetwork.RETURN) {
            return false;
        }
        BlockPos goal = f.target();
        if (goal == null) {
            return false;
        }
        Vec3 dir = new Vec3(f.last.x - goal.getX() - 0.5, 0, f.last.z - goal.getZ() - 0.5);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
        for (int back : new int[]{0, 6, 12, 20}) {
            double ax = goal.getX() + 0.5 + dir.x * back, az = goal.getZ() + 0.5 + dir.z * back;
            if (!level.isPositionEntityTicking(BlockPos.containing(ax, goal.getY(), az))) {
                continue;
            }
            // 空の見える所（その列のいちばん高いブロックより上）に出して、そこから道を探して降りる
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ax), Mth.floor(az));
            Vec3 at = new Vec3(ax, Math.max(goal.getY() + 3, ground + DroneRoute.CLEARANCE), az);
            Vec3 dock = new Vec3(ax, goal.getY() + 1 + DronePortBlockEntity.WAIT_LIFT, az);
            if (back == 0 && DroneRoute.clearAt(level, dock)) at = dock;
            CargoDrone d = SinguloEntities.CARGO_DRONE.get().create(level);
            if (d == null) {
                return false;
            }
            d.station = f.station;
            d.unit = f.unit;
            d.moveTo(at.x, at.y, at.z, (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + 180, 0);
            d.entityData.set(CRATE, DroneCrateItem.tier(f.crate));
            d.flightId = f.id;
            d.phase = f.phase;
            d.entityData.set(KIND, f.kind.ordinal());
            level.addFreshEntity(d);
            f.virtual = false;
            f.drone = d.getUUID();
            net.changed();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 状態

    /** 受け持つステーション（壊されていても、置かれていた場所）。 */
    @Nullable
    public BlockPos station() {
        return station;
    }

    public boolean printed() {
        return entityData.get(PRINT) >= PRINT_TICKS;
    }

    /** 印刷の数え（0〜PRINT_TICKS が印刷、そのあと RETRACT_TICKS が腕の格納）。 */
    public int printTicks() {
        return entityData.get(PRINT);
    }

    /** 印刷の進み（0〜1）。 */
    public float printProgress(float partialTick) {
        int p = entityData.get(PRINT);
        return p >= PRINT_TICKS ? 1 : Math.min(1, (p + partialTick) / PRINT_TICKS);
    }

    public int crateTier() {
        return entityData.get(CRATE);
    }

    public DroneKind kind() {
        return DroneKind.byId(entityData.get(KIND));
    }

    public boolean docked() {
        return entityData.get(DOCKED);
    }

    /** 壁を抜けているか（位相透過の光）。 */
    public boolean phasing() {
        return entityData.get(PHASING);
    }

    /** クライアント: 位相透過の光の強さ（0〜1）。 */
    public float phaseGlow(float partialTick) {
        return Mth.lerp(partialTick, phaseGlowO, phaseGlow);
    }

    /** クライアント: 曲がるときの横の傾き（度。右へ曲がると正）。 */
    public float bank(float partialTick) {
        return Mth.lerp(partialTick, bankO, bank);
    }

    /** 便を受け持たずに待機しているか（ステーションが呼び出しに使う）。 */
    public boolean idle() {
        return printed() && !dismantling() && station != null && level() instanceof ServerLevel server && flight(DroneNetwork.get(server)) == null;
    }

    public boolean dismantling() {
        return entityData.get(DISMANTLE);
    }

    /** 解体を始める（ステーションの GUI から。待機中だけ）。 */
    public void startDismantle() {
        if (printed() && !dismantling()) {
            entityData.set(DISMANTLE, true);
            entityData.set(PRINT, PRINT_TICKS + RETRACT_TICKS);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.dismantle"), 0.8F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ 傷つかない・押されない・消えない

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** 自分では動かない（tick で位置を決める）。 */
    @Override
    public void travel(Vec3 input) {}

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    // ------------------------------------------------------------------ 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverStep(server);
        } else {
            // 位相透過の光: 壁の手前から強まり、抜けたら弱まる。光っている間は、紫の粒が体から散る
            phaseGlowO = phaseGlow;
            phaseGlow = Mth.approach(phaseGlow, phasing() ? 1 : 0, 0.15F);
            if (phaseGlow > 0.25F && random.nextFloat() < 0.7F * phaseGlow) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 0.9,
                        getY() + 0.3 + random.nextDouble() * 0.7, getZ() + (random.nextDouble() - 0.5) * 0.9, 0, 0, 0);
            }
            bankO = bank;
            bank += (Mth.clamp(Mth.wrapDegrees(yBodyRot - yBodyRotO) * 5, -38, 38) - bank) * 0.2F;
            // 反重力型: 足もとから、浮かせている場の光の粒がゆっくり落ちる
            if (kind() == DroneKind.GRAVITY && printed() && !dismantling() && random.nextFloat() < 0.08F) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.5,
                        getY() + (crateTier() > 0 ? 0 : 0.6), getZ() + (random.nextDouble() - 0.5) * 0.5, 0, -0.03, 0);
            }
        }
    }

    private void serverStep(ServerLevel level) {
        if (station == null && flightId == 0) {
            discard();
            return;
        }
        int print = entityData.get(PRINT);
        if (dismantling()) {
            // ステーションの上に降りてから、印刷を逆にたどって上から消える
            if (flyTo(level, Vec3.atBottomCenterOf(station.above()))) {
                entityData.set(PRINT, print - 1);
                if (print < PRINT_TICKS && print % 4 == 0) {
                    playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.print"), 0.5F, 0.9F + print / (float) PRINT_TICKS * 0.3F);
                }
                if (print - 1 <= 0) {
                    if (level.getBlockEntity(station) instanceof DroneStationBlockEntity s) {
                        s.onDismantled(getUUID(), unit);
                    }
                    discard();
                }
            }
            return;
        }
        if (print < PRINT_TICKS) {
            entityData.set(PRINT, print + 1);
            setDeltaMovement(Vec3.ZERO);
            if (print % 4 == 0) {
                playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.print"), 0.5F, 0.9F + print / (float) PRINT_TICKS * 0.3F);
            }
            if (print + 1 == PRINT_TICKS) {
                playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.printed"), 0.8F, 1.0F);
            }
            return;
        }
        if (print < PRINT_TICKS + RETRACT_TICKS) {
            // 印刷の腕がしまわれていく（ドローンはもう飛び立てる）
            entityData.set(PRINT, print + 1);
            if (print == PRINT_TICKS) {
                playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.arm_retract"), 0.6F, 1.0F);
            }
        }
        DroneNetwork net = DroneNetwork.get(level);
        DroneNetwork.Flight f = flight(net);
        if (station != null && tickCount % 20 == 0 && level.isLoaded(station) && level.getBlockEntity(station) instanceof DroneStationBlockEntity s) {
            phase = s.hasPhaseUpgrade();
        } else if (f != null) {
            phase = f.phase;
        }
        if (f != null && f.phase != phase) {
            f.phase = phase;
            net.changed();
        }
        if (f != null) {
            f.blocked = legGoal != null && route == null;
        }
        updatePhasing(level);
        if (f != null && !getUUID().equals(f.drone)) {
            if (f.drone == null && f.virtual) {
                f.virtual = false;                        // 見えない飛行の途中で読み込み直された: そのまま引き継ぐ
                f.drone = getUUID();
                net.changed();
            } else {
                discard();                                // 別の実体が飛んでいる
                return;
            }
        }
        if (f == null) {
            if (station == null) { discard(); return; }
            entityData.set(CRATE, 0);
            entityData.set(DOCKED, false);
            if (tickCount % 20 == 0 && level.isLoaded(station)) {
                if (!(level.getBlockEntity(station) instanceof DroneStationBlockEntity s) || !s.manages(getUUID(), unit)) {
                    discard();                            // ステーションがない、または別のドローンを受け持っている
                    return;
                }
            }
            flyTo(level, DroneStationBlockEntity.hover(station, unit));
            return;
        }
        entityData.set(CRATE, DroneCrateItem.tier(f.crate));
        BlockPos target = f.target();
        if (target == null) {
            net.remove(f);
            discard();
            return;
        }
        if (f.stage == DroneNetwork.RETURN) {
            entityData.set(DOCKED, false);
            if (flyTo(level, DroneStationBlockEntity.hover(target, unit))) {
                net.remove(f);
            }
            return;
        }
        Vec3 over = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        boolean targetLoaded = level.isLoaded(target);
        DronePortBlockEntity port = targetLoaded && level.getBlockEntity(target) instanceof DronePortBlockEntity p ? p : null;
        if (port != null && port.handling(f)) {
            // 受け渡しの間は、ポートの時間に合わせて上下する（扉が動く間は高く待ち、台と同じ高さで箱を受け渡す）
            setPos(over.x, over.y + port.dockLift(), over.z);
            setDeltaMovement(Vec3.ZERO);
            entityData.set(DOCKED, true);
            f.docked = f.stage == DroneNetwork.DELIVER;
            return;
        }
        // 着いたら、扉が開き閉めする高さより上（WAIT_LIFT）で待つ
        boolean there = flyTo(level, over.add(0, DronePortBlockEntity.WAIT_LIFT, 0));
        if (f.stage == DroneNetwork.DELIVER) {
            // 届ける区間の進み（水平の距離で）
            double total = Math.sqrt(Math.pow(f.to.getX() - f.from.getX(), 2) + Math.pow(f.to.getZ() - f.from.getZ(), 2));
            double left = Math.sqrt(Math.pow(over.x - getX(), 2) + Math.pow(over.z - getZ(), 2));
            f.progress = total < 1 ? (there ? 1 : 0.5F) : (float) Mth.clamp(1 - left / total, 0, 1);
        }
        if (!there) {
            entityData.set(DOCKED, false);
            f.docked = false;
            return;
        }
        if (port != null) {
            boolean docked = port.dock(f);
            entityData.set(DOCKED, docked);
            f.docked = docked && f.stage == DroneNetwork.DELIVER;
        } else if (targetLoaded) {
            // ポートがない（置き換えられた）: 取りに行く途中なら帰り、届ける途中なら発送元へ戻す
            if (f.stage == DroneNetwork.PICKUP || net.port(f.from) == null || f.from.equals(target)) {
                if (!f.crate.isEmpty()) {
                    Block.popResource(level, target, f.crate);
                    f.crate = ItemStack.EMPTY;
                }
                net.advance(f, DroneNetwork.RETURN, level.getGameTime());
            } else {
                BlockPos back = f.from;
                f.from = f.to;
                f.to = back;
                net.changed();
            }
        }
    }

    /** 受け持っている便（番号で。なければ、ステーションに任された新しい便を探す）。 */
    @Nullable
    private DroneNetwork.Flight flight(DroneNetwork net) {
        DroneNetwork.Flight f = flightId == 0 ? null : net.flight(flightId);
        if (f == null && station != null) {
            f = net.flightOf(station, unit);
        }
        flightId = f == null ? 0 : f.id;
        return f;
    }

    /**
     * goal へ向かって1 tick 進む。目的地が変わったら通り道を探し直し（壁を抜けない道。位相透過なら抜けてまっすぐ）、
     * 道の点を順にたどる。せまい所はゆっくり正確に、巡航はなめらかに。着いたら true（その場で止まる）。
     * 道が見つからなければ、その場で待って、しばらくしてから探し直す。
     */
    private boolean flyTo(ServerLevel level, Vec3 goal) {
        Vec3 p = position();
        if (p.distanceToSqr(goal) < 0.04 * 0.04 && (phase || DroneRoute.clearAt(level, p))) {
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        if (legGoal == null || legGoal.distanceToSqr(goal) > 0.01) {
            legGoal = goal;
            route = null;
            replanAt = 0;
        }
        if (route == null) {
            if (tickCount < replanAt) {
                hold();
                return false;
            }
            route = phase ? DroneRoute.straight(level, p, goal) : DroneRoute.plan(level, p, goal);
            if (route == null) {
                replanAt = tickCount + REPLAN_TICKS;
                hold();
                return false;
            }
        }
        Vec3 aim = route.current();
        boolean tight = route.currentTight();
        boolean last = route.last();
        Vec3 d = aim.subtract(p);
        double dist = d.length();
        if (last && dist < 0.04) {
            setPos(goal.x, goal.y, goal.z);
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        if (!last && dist < (tight ? 0.12 : 0.8)) {
            route.advance();                          // 次の点へ（巡航の角は手前で曲がり始める）
            aim = route.current();
            d = aim.subtract(p);
            dist = d.length();
        }
        Vec3 v;
        if (tight) {
            // せまい所: 道の点へまっすぐ（惰性で角の壁にこすらない）
            double speed = Math.min(TIGHT_SPEED * kind().speed, Math.max(0.03, dist * 0.3));
            v = dist < 1e-4 ? Vec3.ZERO : d.scale(Math.min(speed, dist) / dist);
        } else {
            double speed = Math.min(DroneNetwork.SPEED * kind().speed, Math.max(0.05, route.looseAhead(p) * 0.15));
            Vec3 want = dist < 1e-4 ? Vec3.ZERO : d.scale(Math.min(speed, dist) / dist);
            v = getDeltaMovement();
            Vec3 dv = want.subtract(v);
            if (dv.length() > ACCEL) {
                dv = dv.normalize().scale(ACCEL);
            }
            v = v.add(dv);
        }
        Vec3 next = p.add(v);
        if (!phase && !DroneRoute.clearLine(level, p, next)) {
            route = null;
            replanAt = tickCount + REPLAN_TICKS;
            hold();
            return false;
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(v);
        double horizontal = v.horizontalDistance();
        if (horizontal > 0.02) {
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 8));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        return false;
    }

    /** 道が見つからない間は、その場で止まって待つ。 */
    private void hold() {
        setDeltaMovement(Vec3.ZERO);
    }

    /** 壁を抜けているか（今いる所か、少し先がブロックの中）。抜け終えても PHASE_HOLD tick は光を残す。 */
    private void updatePhasing(ServerLevel level) {
        boolean inside = false;
        if (phase) {
            net.minecraft.world.phys.AABB box = getBoundingBox().deflate(0.05);
            Vec3 v = getDeltaMovement();
            // 先を見るのは、今の目的地まで（降りる先のポートを壁と取り違えない）
            double reach = legGoal == null ? 0 : Math.min(PHASE_LOOKAHEAD, position().distanceTo(legGoal) - 0.05);
            Vec3 ahead = v.lengthSqr() < 1e-6 || reach <= 0 ? Vec3.ZERO : v.normalize().scale(reach);
            inside = !level.noCollision(box) || !level.noCollision(box.move(ahead)) || !level.noCollision(box.move(ahead.scale(0.5)));
        }
        phaseHold = inside ? PHASE_HOLD : Math.max(0, phaseHold - 1);
        boolean on = phaseHold > 0;
        if (on != entityData.get(PHASING)) {
            entityData.set(PHASING, on);
            if (on) {
                playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone.phase"), 0.8F, 1.0F);
            }
        }
    }

    /**
     * 読み込み範囲の外へ出た・倒された・ほかの仕組みに消されたとき: 便は見えない飛行で続ける（荷物は記録の側にある）。
     * 自分で消えるのは、便が別の実体のものか、便がもうないときだけなので、ここで便を引き継いでも二重にならない。
     */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        DroneNetwork net = DroneNetwork.get(server);
        DroneNetwork.Flight f = flight(net);
        if (f != null && getUUID().equals(f.drone)) {
            net.virtualize(f, position(), server.getGameTime());
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (station != null) {
            tag.put("station", NbtUtils.writeBlockPos(station));
        }
        tag.putInt("unit", unit);
        tag.putInt("print", entityData.get(PRINT));
        tag.putLong("flight", flightId);
        tag.putBoolean("phase", phase);
        tag.putInt("kind", entityData.get(KIND));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        station = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(tag, "station").orElse(null);
        unit = Mth.clamp(tag.getInt("unit"), 0, DroneStationBlockEntity.MAX_UNITS - 1);
        entityData.set(PRINT, tag.contains("print") ? Mth.clamp(tag.getInt("print"), 0, PRINT_TICKS + RETRACT_TICKS) : PRINT_TICKS + RETRACT_TICKS);
        flightId = tag.getLong("flight");
        phase = tag.getBoolean("phase");
        entityData.set(KIND, DroneKind.byId(tag.getInt("kind")).ordinal());
    }
}
