package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.ruin.RuinGuards;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 遺構のアンドロイド（光刃型・鞭型・光剣型の共通部分）。印刷台の上で、下から積み上がるように印刷されて現れる。
 *
 * <p>充電（{@link #CHARGE_MAX}）を持ち、技を使うたびに減る。0 になると攻撃をやめ（目の光が消える）、印刷台へ戻って
 * 充電する（{@link #CHARGE_MAX} tick、約15秒で満タン）。印刷台がなければ、その場で座って充電する。死ぬことはない。
 */
public abstract class RuinAndroid extends Monster {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 印刷の進み（0〜PRINT_TICKS。PRINT_TICKS で完成）。 */
    private static final EntityDataAccessor<Integer> PRINT = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);

    /** 共通の動き。型ごとの技は 10 から。 */
    public static final int STATE_IDLE = 0;
    public static final int STATE_LOW_POWER = 1;
    public static final int STATE_CHARGING = 2;

    /** 充電の満タン（0 から満タンまで、印刷台の上でこの tick）。 */
    public static final int CHARGE_MAX = 300;
    /** 印刷にかかる tick。 */
    public static final int PRINT_TICKS = 60;
    /** 印刷台から離れていい距離（遺構のもの）。 */
    public static final int HOME_RADIUS = 18;

    @Nullable
    private BlockPos home;
    /** 今の動きを始めてからの tick（サーバー）。 */
    protected int stateTicks;
    /** クライアント: 今の動きが始まった時刻（見た目のため）。 */
    private int clientStateStart;
    private int clientPrevState;

    protected RuinAndroid(EntityType<? extends RuinAndroid> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** 光の色（目・刃・印刷の光）。型ごとに違う。 */
    public abstract int accent();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_IDLE);
        builder.define(CHARGE, CHARGE_MAX);
        builder.define(PRINT, PRINT_TICKS);
    }

    // ------------------------------------------------------------------ 状態

    public int state() {
        return entityData.get(STATE);
    }

    protected void setState(int state) {
        if (state() != state) {
            entityData.set(STATE, state);
        }
        stateTicks = 0;
    }

    public int charge() {
        return entityData.get(CHARGE);
    }

    public float chargeFraction() {
        return charge() / (float) CHARGE_MAX;
    }

    protected void setCharge(int charge) {
        entityData.set(CHARGE, Mth.clamp(charge, 0, CHARGE_MAX));
    }

    /** 充電を cost 使う。足りなければ使わずに false。0 になったら充電切れ（戻る）。 */
    protected boolean spend(int cost) {
        if (charge() < cost) {
            return false;
        }
        setCharge(charge() - cost);
        if (charge() <= 0) {
            powerDown();
        }
        return true;
    }

    /** 充電切れ: 攻撃をやめて、印刷台へ戻る。 */
    protected void powerDown() {
        setCharge(0);
        setState(STATE_LOW_POWER);
        getNavigation().stop();
        playSound(SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
    }

    /** 攻撃できるか（充電があり、印刷が終わり、戻り・充電中でない）。 */
    public boolean active() {
        int s = state();
        return printed() && s != STATE_LOW_POWER && s != STATE_CHARGING;
    }

    public boolean printed() {
        return entityData.get(PRINT) >= PRINT_TICKS;
    }

    /** 印刷の進み（0〜1）。 */
    public float printProgress(float partialTick) {
        int p = entityData.get(PRINT);
        return p >= PRINT_TICKS ? 1 : Math.min(1, (p + partialTick) / PRINT_TICKS);
    }

    /** 印刷を始める（印刷台から出すとき）。 */
    public void startPrinting() {
        entityData.set(PRINT, 0);
    }

    /** スポーンエッグやコマンドで出したときも、印刷されて現れる。 */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                   net.minecraft.world.DifficultyInstance difficulty,
                                                                   net.minecraft.world.entity.MobSpawnType reason,
                                                                   @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        startPrinting();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
        if (home != null) {
            restrictTo(home, HOME_RADIUS);
        }
    }

    /** クライアント: 今の動きを始めてからの時間（tick、小数）。 */
    public float stateTime(float partialTick) {
        return tickCount - clientStateStart + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide) {
            int s = state();
            if (s != clientPrevState) {
                clientPrevState = s;
                clientStateStart = tickCount;
            }
        }
    }

    // ------------------------------------------------------------------ 行動

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PowerGoal());
        registerCombat();
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 型ごとの戦い方（優先度 2〜5）。 */
    protected abstract void registerCombat();

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        stateTicks++;
        int p = entityData.get(PRINT);
        if (p < PRINT_TICKS) {
            entityData.set(PRINT, p + 1);
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (p + 1 == PRINT_TICKS) {
                playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.8F);
            } else if (p % 6 == 0) {
                playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.4F + p / (float) PRINT_TICKS);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!printed()) {
            getNavigation().stop();
        }
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return RuinGuards.isGuard(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!printed() || RuinGuards.isGuard(source.getEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** 前方 arcDeg 度・距離 reach の扇の中の相手に当てる（技の当たり判定）。当たった数を返す。 */
    protected int sweep(double reach, double arcDeg, float damage, double knockback) {
        if (!(level() instanceof ServerLevel)) {
            return 0;
        }
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
                e -> e != this && e.isAlive() && !isAlliedTo(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            double dist = to.length();
            if (dist > reach + e.getBbWidth() / 2) {
                continue;
            }
            if (arcDeg < 360 && dist > 0.6) {
                double cos = to.normalize().dot(look);
                if (cos < Math.cos(Math.toRadians(arcDeg / 2))) {
                    continue;
                }
            }
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                hits++;
                Vec3 push = (dist > 0.01 ? to.normalize() : look).scale(knockback);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.2 + knockback * 0.2, push.z));
                e.hurtMarked = true;
            }
        }
        return hits;
    }

    /** 光の粒（型の色）。 */
    protected void sparks(ServerLevel level, Vec3 at, int count, double spread) {
        int c = accent();
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F), 0.8F),
                at.x, at.y, at.z, count, spread, spread, spread, 0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public float getVoicePitch() {
        return 1.25F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("charge", charge());
        tag.putInt("print", entityData.get(PRINT));
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("charge")) {
            setCharge(tag.getInt("charge"));
        }
        if (tag.contains("print")) {
            entityData.set(PRINT, Mth.clamp(tag.getInt("print"), 0, PRINT_TICKS));
        }
        NbtUtils.readBlockPos(tag, "home").ifPresent(this::setHome);
        if (charge() <= 0) {
            setState(STATE_LOW_POWER);
        }
    }

    /**
     * 充電切れのとき: 印刷台の上へ歩いて戻り（ゆっくり）、着いたら充電する。満タンになったら戦いに戻る。
     * 印刷台がない（または遠すぎる）ときは、その場で充電する。
     */
    private final class PowerGoal extends Goal {
        PowerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !printed() || state() == STATE_LOW_POWER || state() == STATE_CHARGING;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!printed() || !(level() instanceof ServerLevel level)) {
                return;
            }
            if (state() == STATE_LOW_POWER) {
                BlockPos h = home;
                Vec3 dock = h == null ? null : Vec3.atBottomCenterOf(h.above());
                if (dock == null || dock.distanceToSqr(position()) > 48 * 48 || stateTicks > 400) {
                    setState(STATE_CHARGING);
                    return;
                }
                if (dock.distanceToSqr(position()) < 1.6 * 1.6) {
                    getNavigation().stop();
                    setState(STATE_CHARGING);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.4F);
                    return;
                }
                if (stateTicks % 10 == 1) {
                    getNavigation().moveTo(dock.x, dock.y, dock.z, 0.75);
                }
            } else if (state() == STATE_CHARGING) {
                getNavigation().stop();
                setCharge(charge() + 1);
                if (stateTicks % 8 == 0) {
                    sparks(level, position().add(0, 0.2, 0), 3, 0.35);
                }
                if (charge() >= CHARGE_MAX) {
                    setState(STATE_IDLE);
                    playSound(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.9F);
                }
            }
        }
    }
}
