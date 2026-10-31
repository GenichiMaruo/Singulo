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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 輸送ドローン。ステーションの上で待機し、便の記録（DroneNetwork.Flight）に従って、発送元のポートの真上 → 行き先のポートの真上
 * → ステーションへと飛ぶ。荷物は記録の側にあり、ドローンは見た目のために箱の段階だけを持つ。
 * 飛ぶときは、まず巡航の高さまで上がり、水平に進み、目的地の近くで降りる。プレイヤーやモブの攻撃では傷つかない。
 */
public class CargoDrone extends Mob {
    public static final int PRINT_TICKS = 50;
    /** 巡航の高さの余裕（途中の地形と発着点より上）。 */
    static final int CLEARANCE = 4;
    static final double ACCEL = 0.05;

    private static final EntityDataAccessor<Integer> PRINT = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.INT);
    /** 運んでいる箱の段階（0 は空）。 */
    private static final EntityDataAccessor<Integer> CRATE = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.INT);
    /** ポートの真上で受け渡しをしているか（爪を下ろす）。 */
    private static final EntityDataAccessor<Boolean> DOCKED = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.BOOLEAN);
    /** 解体中か（ステーションの上に降りて、印刷を逆にたどって消える）。 */
    private static final EntityDataAccessor<Boolean> DISMANTLE = SynchedEntityData.defineId(CargoDrone.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private BlockPos station;
    private int unit;
    /** 受け持っている便（0 はなし）。ステーションが壊されても便を最後まで飛ぶため、番号で探す。 */
    private long flightId;
    /** 今の区間の目的地と巡航の高さ（目的地が変わったら計り直す。保存しない）。 */
    @Nullable
    private Vec3 legGoal;
    private double cruiseY;

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PRINT, PRINT_TICKS);
        builder.define(CRATE, 0);
        builder.define(DOCKED, false);
        builder.define(DISMANTLE, false);
    }

    /** ステーションの枠のドローンを出す。printing なら station の上で印刷から始める。 */
    @Nullable
    public static CargoDrone spawn(ServerLevel level, BlockPos station, int unit, Vec3 at, boolean printing) {
        CargoDrone d = SinguloEntities.CARGO_DRONE.get().create(level);
        if (d == null) {
            return null;
        }
        d.station = station;
        d.unit = unit;
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
        for (int back : new int[]{20, 12, 6, 0}) {
            Vec3 at = new Vec3(goal.getX() + 0.5 + dir.x * back, goal.getY() + 10, goal.getZ() + 0.5 + dir.z * back);
            if (!level.isPositionEntityTicking(BlockPos.containing(at))) {
                continue;
            }
            CargoDrone d = SinguloEntities.CARGO_DRONE.get().create(level);
            if (d == null) {
                return false;
            }
            d.station = f.station;
            d.unit = f.unit;
            d.moveTo(at.x, at.y, at.z, (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + 180, 0);
            d.entityData.set(CRATE, DroneCrateItem.tier(f.crate));
            d.flightId = f.id;
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

    /** 印刷の進み（0〜1）。 */
    public float printProgress(float partialTick) {
        int p = entityData.get(PRINT);
        return p >= PRINT_TICKS ? 1 : Math.min(1, (p + partialTick) / PRINT_TICKS);
    }

    public int crateTier() {
        return entityData.get(CRATE);
    }

    public boolean docked() {
        return entityData.get(DOCKED);
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
            playSound(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.9F);
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
    public boolean canBeLeashed() {
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
        }
    }

    private void serverStep(ServerLevel level) {
        if (station == null) {
            discard();
            return;
        }
        int print = entityData.get(PRINT);
        if (dismantling()) {
            // ステーションの上に降りてから、印刷を逆にたどって上から消える
            if (flyTo(level, Vec3.atBottomCenterOf(station.above()))) {
                entityData.set(PRINT, print - 1);
                if (print % 4 == 0) {
                    playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 2.0F - print / (float) PRINT_TICKS);
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
                playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 1.2F + print / (float) PRINT_TICKS);
            }
            if (print + 1 == PRINT_TICKS) {
                playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
            }
            return;
        }
        DroneNetwork net = DroneNetwork.get(level);
        DroneNetwork.Flight f = flight(net);
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
        DronePortBlockEntity port = level.getBlockEntity(target) instanceof DronePortBlockEntity p ? p : null;
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
        } else if (level.isLoaded(target)) {
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
     * goal へ向かって1 tick 進む。遠ければ巡航の高さまで上がってから水平に進み、近くで降りる。着いたら true（その場で止まる）。
     */
    private boolean flyTo(ServerLevel level, Vec3 goal) {
        Vec3 p = position();
        if (legGoal == null || legGoal.distanceToSqr(goal) > 0.01) {
            legGoal = goal;
            cruiseY = cruise(level, p, goal);
        }
        double hx = goal.x - p.x;
        double hz = goal.z - p.z;
        double hd = Math.sqrt(hx * hx + hz * hz);
        Vec3 aim;
        if (hd > 2.0) {
            // 巡航の高さの近くまで上がるまでは、ほぼ真上へ
            aim = p.y < cruiseY - 1.5 ? new Vec3(p.x + hx / hd * 0.2, cruiseY, p.z + hz / hd * 0.2) : new Vec3(goal.x, cruiseY, goal.z);
        } else {
            aim = goal;
        }
        Vec3 d = aim.subtract(p);
        double dist = d.length();
        if (hd <= 2.0 && p.distanceTo(goal) < 0.04) {
            setPos(goal.x, goal.y, goal.z);
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        double speed = Math.min(DroneNetwork.SPEED, Math.max(0.03, dist * 0.15));
        Vec3 want = dist < 1e-4 ? Vec3.ZERO : d.scale(Math.min(speed, dist) / dist);
        Vec3 v = getDeltaMovement();
        Vec3 dv = want.subtract(v);
        if (dv.length() > ACCEL) {
            dv = dv.normalize().scale(ACCEL);
        }
        v = v.add(dv);
        setPos(p.x + v.x, p.y + v.y, p.z + v.z);
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

    /** 巡航の高さ: 出発点と目的地、途中の（読み込まれている所の）地形より CLEARANCE 上。 */
    private static double cruise(ServerLevel level, Vec3 from, Vec3 to) {
        double top = Math.max(from.y, to.y) + CLEARANCE;
        double len = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        int steps = Math.max(1, (int) (len / 6));
        for (int i = 0; i <= steps; i++) {
            double k = i / (double) steps;
            int x = Mth.floor(Mth.lerp(k, from.x, to.x));
            int z = Mth.floor(Mth.lerp(k, from.z, to.z));
            if (level.hasChunk(x >> 4, z >> 4)) {
                top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + CLEARANCE);
            }
        }
        return Math.min(top, level.getMaxBuildHeight() - 2);
    }

    /**
     * 読み込み範囲の外へ出た・倒された・ほかの仕組みに消されたとき: 便は見えない飛行で続ける（荷物は記録の側にある）。
     * 自分で消えるのは、便が別の実体のものか、便がもうないときだけなので、ここで便を引き継いでも二重にならない。
     */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!(level() instanceof ServerLevel server) || station == null) {
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        station = NbtUtils.readBlockPos(tag, "station").orElse(null);
        unit = Mth.clamp(tag.getInt("unit"), 0, DroneStationBlockEntity.MAX_UNITS - 1);
        entityData.set(PRINT, tag.contains("print") ? Mth.clamp(tag.getInt("print"), 0, PRINT_TICKS) : PRINT_TICKS);
        flightId = tag.getLong("flight");
    }
}
