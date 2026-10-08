package io.github.genichimaruo.singulo.ruin;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 白い警備ドローン。遺構の警備機ドックからだけ出撃する。強さは3段階（地表観測拠点=1、研究棟=2、封鎖培養施設と最終実験施設=3）。
 * 相手の周りを回りながら光弾を撃ち（段階2は3連射、段階3は追尾の3連射）、ときどき止まってレンズを光らせてから突進する。
 * 段階3は近づかれると、ためてから電磁パルスの輪を放つ（ダメージ・吹き飛ばし・鈍足）。何も落とさないので、トラップで稼げない。
 */
public class SecurityDrone extends Monster {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SecurityDrone.class, EntityDataSerializers.INT);
    /** 今の動き（見た目に使う）。 */
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SecurityDrone.class, EntityDataSerializers.INT);
    public static final int STATE_ORBIT = 0;
    public static final int STATE_RAM_WINDUP = 1;
    public static final int STATE_RAM = 2;
    public static final int STATE_PULSE = 3;

    static final double[] HEALTH = {10, 24, 40};
    /** 突進のダメージ。 */
    static final double[] DAMAGE = {4, 7, 10};
    /** 光弾のダメージと、1回に撃つ数。 */
    static final float[] BOLT_DAMAGE = {3, 5, 7};
    static final int[] BURST = {1, 3, 3};
    /** 段階ごとの光の色（レンズ・光弾）。 */
    public static final int[] COLORS = {0x78D2F0, 0xF0B450, 0xE86060};
    static final float PULSE_DAMAGE = 8;
    static final double PULSE_RADIUS = 4.5;
    static final int RAM_WINDUP = 12;
    static final int RAM_TICKS = 16;
    static final int PULSE_CHARGE = 20;

    public SecurityDrone(EntityType<? extends SecurityDrone> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH[0])
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE[0])
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(STATE, STATE_ORBIT);
    }

    public int tier() {
        return entityData.get(TIER);
    }

    public int state() {
        return entityData.get(STATE);
    }

    void setState(int state) {
        entityData.set(STATE, state);
    }

    /** 強さを決め、体力と攻撃力をその段階の値にする。 */
    public void setTier(int tier) {
        int t = Mth.clamp(tier, 1, 3);
        entityData.set(TIER, t);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[t - 1]);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE[t - 1]);
        setHealth(getMaxHealth());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CombatGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 光弾を1発撃つ（段階3は追尾）。 */
    void shoot(LivingEntity target) {
        int t = tier() - 1;
        Vec3 from = position().add(0, getBbHeight() * 0.35, 0).add(getLookAngle().scale(0.5));
        HorizonBolt bolt = new HorizonBolt(level(), this).style(COLORS[t], 0.55F, BOLT_DAMAGE[t]);
        if (tier() == 3) {
            bolt.homing(target);
        }
        bolt.setPos(from);
        Vec3 d = target.getEyePosition().subtract(from).normalize();
        bolt.shoot(d.x, d.y, d.z, 1.1F, 2.0F);
        level().addFreshEntity(bolt);
        playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 2.0F);
    }

    /** 段階3: 電磁パルスの輪を放つ。 */
    void pulse(ServerLevel level) {
        playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.8F, 1.6F);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * PULSE_RADIUS * 0.7, getY() + 0.3,
                    getZ() + Math.sin(a) * PULSE_RADIUS * 0.7, 2, 0.2, 0.2, 0.2, 0.3);
        }
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PULSE_RADIUS),
                e -> e != this && e.isAlive() && !(e instanceof SecurityDrone) && !(e instanceof HorizonWarden))) {
            if (e.distanceTo(this) > PULSE_RADIUS) {
                continue;
            }
            e.hurt(damageSources().mobAttack(this), PULSE_DAMAGE);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            Vec3 out = e.position().subtract(position()).normalize().scale(0.9).add(0, 0.35, 0);
            e.setDeltaMovement(out);
            e.hurtMarked = true;
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {}

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("tier", tier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("tier")) {
            entityData.set(TIER, Mth.clamp(tag.getInt("tier"), 1, 3));
        }
    }

    /**
     * 戦い方: ふだんは相手の周りを回りながら光弾を撃つ。ときどき止まってレンズを光らせてから突進する（よけられる）。
     * 段階3は近づかれると電磁パルスをためて放つ。
     */
    private final class CombatGoal extends Goal {
        private int timer;
        private int shotCooldown = 30;
        private int burstLeft;
        private int nextMove = 60;
        private double orbitAngle;
        @Nullable
        private Vec3 ramDir;

        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            orbitAngle = random.nextDouble() * Math.PI * 2;
            setState(STATE_ORBIT);
        }

        @Override
        public void stop() {
            setState(STATE_ORBIT);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel level)) {
                return;
            }
            switch (state()) {
                case STATE_RAM_WINDUP -> {
                    // 止まって少し下がり、レンズを光らせる（前触れ）
                    getLookControl().setLookAt(target, 60, 60);
                    Vec3 back = position().subtract(target.getEyePosition()).normalize().scale(0.04);
                    setDeltaMovement(getDeltaMovement().scale(0.6).add(back));
                    if (--timer <= 0) {
                        ramDir = target.getEyePosition().subtract(position()).normalize();
                        timer = RAM_TICKS;
                        setState(STATE_RAM);
                        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.7F);
                    }
                }
                case STATE_RAM -> {
                    // 突進: ほぼまっすぐ（少しだけ追う）
                    Vec3 want = target.getEyePosition().subtract(position()).normalize();
                    ramDir = ramDir == null ? want : ramDir.lerp(want, 0.08).normalize();
                    setDeltaMovement(ramDir.scale(0.85));
                    getLookControl().setLookAt(target, 60, 60);
                    level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 1, 0.05, 0.05, 0.05, 0);
                    if (distanceToSqr(target) < 1.8 * 1.8) {
                        doHurtTarget(target);
                        target.setDeltaMovement(target.getDeltaMovement().add(ramDir.scale(0.8)).add(0, 0.25, 0));
                        target.hurtMarked = true;
                        timer = 0;
                    }
                    if (--timer <= 0 || horizontalCollision || verticalCollision) {
                        setDeltaMovement(getDeltaMovement().scale(-0.3));
                        setState(STATE_ORBIT);
                        nextMove = 50 + random.nextInt(40);
                    }
                }
                case STATE_PULSE -> {
                    setDeltaMovement(getDeltaMovement().scale(0.5));
                    if (timer % 4 == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 4, 0.4, 0.3, 0.4, 0.05);
                    }
                    if (--timer <= 0) {
                        pulse(level);
                        setState(STATE_ORBIT);
                        nextMove = 60 + random.nextInt(40);
                    }
                }
                default -> orbit(level, target);
            }
        }

        /** 相手の周りを回りながら撃つ。ときどき突進か電磁パルスに移る。 */
        private void orbit(ServerLevel level, LivingEntity target) {
            orbitAngle += 0.035;
            double r = 6;
            Vec3 want = target.position().add(Math.cos(orbitAngle) * r, target.getBbHeight() + 1.8, Math.sin(orbitAngle) * r);
            getMoveControl().setWantedPosition(want.x, want.y, want.z, 1.1);
            getLookControl().setLookAt(target, 30, 30);
            boolean seen = hasLineOfSight(target);
            if (burstLeft > 0) {
                if (--shotCooldown <= 0 && seen) {
                    shoot(target);
                    burstLeft--;
                    shotCooldown = burstLeft > 0 ? 4 : 40 + random.nextInt(20);
                }
            } else if (--shotCooldown <= 0 && seen && distanceToSqr(target) < 16 * 16) {
                burstLeft = BURST[tier() - 1];
                shotCooldown = 0;
            }
            if (--nextMove <= 0) {
                if (tier() == 3 && distanceToSqr(target) < 5 * 5) {
                    timer = PULSE_CHARGE;
                    setState(STATE_PULSE);
                    playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 2.0F);
                } else if (seen) {
                    timer = RAM_WINDUP;
                    setState(STATE_RAM_WINDUP);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
                } else {
                    nextMove = 20;
                }
            }
        }
    }
}
