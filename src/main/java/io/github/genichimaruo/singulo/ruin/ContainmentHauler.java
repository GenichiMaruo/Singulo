package io.github.genichimaruo.singulo.ruin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 封じ込め搬送機。最終実験施設の前室（搬入格納庫）の中ボスで、格納庫の中継器が起こす（自然には湧かない）。
 * 特異点の封じ込め容器を背に載せて点火室へ運んでいた、4本脚の重機。止まれなくなったまま、格納庫に入った者を荷として押しのける。
 * <ul>
 *   <li>突進: 赤い線で進む向きを示してから、まっすぐ走る。壁や柱にぶつかると背の容器の固定が外れて止まり（{@value #STUN_TICKS} tick）、
 *       そのあいだ受けるダメージが {@value #STUNNED_DAMAGE} 倍になる。体力が半分を切ると、ぶつからなかったとき続けてもう一度走る</li>
 *   <li>踏み鳴らし: 前脚を上げて床を打ち、床を伝う衝撃の輪を広げる。跳べばかわせる。体力が半分を切ると輪が2つになる</li>
 *   <li>封じ込め場: 相手の足元と両脇に、足の遅くなる場を落とす（容器を固定していた場の流用）</li>
 *   <li>挟み: すぐ近くの相手を前の挟み具で打つ</li>
 * </ul>
 * 倒すと格納庫の中継器が起動できるようになる。挑戦者がいなくなると中継器へ戻り、また近づくと起き直す。
 */
public class ContainmentHauler extends RuinBoss {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ContainmentHauler.class, EntityDataSerializers.INT);
    public static final int STATE_IDLE = 0, STATE_RAM_WINDUP = 1, STATE_RAM = 2, STATE_STUNNED = 3, STATE_STOMP_WINDUP = 4, STATE_CLAMP = 5;

    public static final float HEALTH = 340;
    public static final float HEALTH_NORMAL = 280;
    public static final int ARENA = 14;
    public static final int EMERGE_TICKS = 60, DEATH_TICKS = 40;
    public static final float RAM_DAMAGE = 11, STOMP_DAMAGE = 8, CLAMP_DAMAGE = 9;
    public static final int RAM_WINDUP = 30, RAM_CHAIN_WINDUP = 16, RAM_TICKS = 26, STUN_TICKS = 60;
    /** 突進の速さ（ブロック/tick）。向きを決めるのは溜めの何 tick 目までか。 */
    public static final double RAM_SPEED = 0.8;
    static final int RAM_AIM_TICKS = 20;
    public static final float STUNNED_DAMAGE = 1.5F;
    public static final int STOMP_WINDUP = 20;
    /** 衝撃の輪が広がる速さ（ブロック/tick）と、届く広さ。 */
    public static final double RING_SPEED = 0.55, RING_RADIUS = 11, RING_WIDTH = 0.7;
    public static final int FIELD_TICKS = 120;
    public static final double FIELD_RADIUS = 2.0;
    public static final int CLAMP_WINDUP = 10;
    public static final double CLAMP_REACH = 3.4;
    static final int GLOW_COLOR = 0x78DCFF;

    private int moveCooldown = 40;
    private int stateTicks;
    private int clientState = -1, clientStateTicks;
    private int clampCooldown;
    private int lastMove = -1;
    private boolean chained;
    private Vec3 ramDir = Vec3.ZERO;
    private final Set<UUID> ramHit = new HashSet<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();

    /** 床を伝う衝撃の輪。 */
    private static final class Ring {
        final Vec3 center;
        double radius = 1;
        final Set<UUID> hit = new HashSet<>();

        Ring(Vec3 center) {
            this.center = center;
        }
    }

    /** 足の遅くなる場。 */
    private static final class Field {
        final Vec3 center;
        int ticks = FIELD_TICKS;

        Field(Vec3 center) {
            this.center = center;
        }
    }

    public ContainmentHauler(EntityType<? extends ContainmentHauler> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.xpReward = 300;
        if (!level.isClientSide) {
            setEffectiveHealth(health());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static float health() {
        return (float) BossScaling.baseHealth(BossScaling.hardMode() ? HEALTH : HEALTH_NORMAL);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_IDLE);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
    }

    /** いまの状態になってからの時間（tick。クライアントでは届いた状態が変わってから数える）。 */
    public float stateTime(float partialTick) {
        return level().isClientSide ? clientStateTicks + partialTick : stateTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int s = state();
            if (s != clientState) {
                clientState = s;
                clientStateTicks = 0;
            } else {
                clientStateTicks++;
            }
        }
    }

    /** いま広がっている衝撃の輪の数。 */
    public int rings() {
        return rings.size();
    }

    /** いま残っている封じ込め場の数。 */
    public int fields() {
        return fields.size();
    }

    @Override
    public int emergeDuration() {
        return EMERGE_TICKS;
    }

    @Override
    public int deathDuration() {
        return DEATH_TICKS;
    }

    @Override
    public double spawnHeight() {
        return 0.5;
    }

    @Override
    public int arenaRadius() {
        return ARENA;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 技のダメージの倍率（通常の強さでは下げる）。 */
    float damageFactor() {
        return BossScaling.hardMode() ? 1F : 0.8F;
    }

    private boolean enraged() {
        return getHealth() < getMaxHealth() * enrageFraction();
    }

    // ------------------------------------------------------------------ 出現

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        if (t == 1) {
            playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.5F);
        }
        if (t % 12 == 0) {
            // 脚を一本ずつ床へ下ろす
            playSound(SoundEvents.NETHERITE_BLOCK_PLACE, 1.6F, 0.5F + t / 120F);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 6, 1.0, 0.05, 1.0, 0.02);
        }
        if (t % 4 == 0) {
            level.sendParticles(new DustParticleOptions(glowColor(), 1.0F), getX(), getY() + 1.6, getZ(), 3, 0.3, 0.3, 0.3, 0);
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 20, 1.4, 0.05, 1.4, 0.05);
    }

    private static Vector3f glowColor() {
        return new Vector3f(((GLOW_COLOR >> 16) & 0xFF) / 255F, ((GLOW_COLOR >> 8) & 0xFF) / 255F, (GLOW_COLOR & 0xFF) / 255F);
    }

    // ------------------------------------------------------------------ 戦い

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        stateTicks++;
        if (clampCooldown > 0) {
            clampCooldown--;
        }
        tickRings(level);
        tickFields(level);
        LivingEntity target = getTarget();
        switch (state()) {
            case STATE_RAM_WINDUP -> tickRamWindup(level, target);
            case STATE_RAM -> tickRam(level);
            case STATE_STUNNED -> tickStunned(level);
            case STATE_STOMP_WINDUP -> tickStompWindup(level);
            case STATE_CLAMP -> tickClamp(level, target);
            default -> {
                if (target != null && target.isAlive()) {
                    approach(level, target);
                } else {
                    getNavigation().stop();
                }
            }
        }
    }

    /** 相手へ歩み寄り、技を選ぶ。 */
    private void approach(ServerLevel level, LivingEntity target) {
        getLookControl().setLookAt(target, 20, 20);
        double dist = horizontalDistance(target);
        if (dist < CLAMP_REACH && clampCooldown <= 0) {
            startClamp();
            return;
        }
        if (dist > 3) {
            getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
        if (--moveCooldown <= 0) {
            chooseMove(level, target, dist);
        }
    }

    private double horizontalDistance(Entity e) {
        return Math.sqrt(Mth.square(e.getX() - getX()) + Mth.square(e.getZ() - getZ()));
    }

    private void chooseMove(ServerLevel level, LivingEntity target, double dist) {
        List<Integer> moves = new ArrayList<>(List.of(1));
        if (dist >= 4) {
            moves.add(0);
            moves.add(0);
        }
        if (fields.isEmpty()) {
            moves.add(2);
        }
        if (moves.stream().anyMatch(m -> m != lastMove)) {
            moves.removeIf(m -> m == lastMove);
        }
        int move = moves.get(random.nextInt(moves.size()));
        lastMove = move;
        switch (move) {
            case 0 -> startRam(target, false);
            case 1 -> startStomp();
            default -> dropFields(level, target);
        }
    }

    // --- 突進

    /** 突進の溜めを始める（chain: 続けての2回目。溜めが短い）。 */
    public void startRam(LivingEntity target, boolean chain) {
        moveCooldown = pace(50);
        chained = chain;
        getNavigation().stop();
        ramDir = flatDirection(target.position().subtract(position()));
        setState(STATE_RAM_WINDUP);
        if (chain) {
            stateTicks = RAM_WINDUP - RAM_CHAIN_WINDUP;
        }
        playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.6F);
    }

    private static Vec3 flatDirection(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private void tickRamWindup(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
        if (target != null && target.isAlive() && stateTicks < RAM_AIM_TICKS) {
            ramDir = flatDirection(target.position().subtract(position()));     // 前半は相手を追い、後半は向きを固定する
        }
        faceAlong(ramDir);
        if (stateTicks % 2 == 0) {
            DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.2F), 1.4F);
            for (int i = 2; i <= 16; i += 2) {
                Vec3 p = position().add(ramDir.scale(i));
                level.sendParticles(red, p.x, getY() + 0.15, p.z, 1, 0.15, 0, 0.15, 0);
            }
        }
        if (stateTicks >= RAM_WINDUP) {
            charge(ramDir);
        }
    }

    /** 向きを決めてすぐ走り出す（溜めのあと。テストからも呼ぶ）。 */
    public void charge(Vec3 direction) {
        ramDir = flatDirection(direction);
        ramHit.clear();
        faceAlong(ramDir);
        setState(STATE_RAM);
        playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.6F);
    }

    private void faceAlong(Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void tickRam(ServerLevel level) {
        getNavigation().stop();
        if (stateTicks > 2 && horizontalCollision) {
            crash(level);
            return;
        }
        faceAlong(ramDir);
        setDeltaMovement(ramDir.x * RAM_SPEED, getDeltaMovement().y, ramDir.z * RAM_SPEED);
        if (stateTicks % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 2, 0.6, 0.05, 0.6, 0.01);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4),
                e -> e != this && e.isAlive() && !RuinGuards.isGuard(e) && !ramHit.contains(e.getUUID()))) {
            ramHit.add(e.getUUID());
            if (hurtWith("ram", e, damageSources().mobAttack(this), RAM_DAMAGE * damageFactor())) {
                e.push(ramDir.x * 1.4, 0.5, ramDir.z * 1.4);
                e.hurtMarked = true;
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.6F);
            }
        }
        if (stateTicks >= RAM_TICKS) {
            setDeltaMovement(Vec3.ZERO);
            LivingEntity target = getTarget();
            if (enraged() && !chained && target != null && target.isAlive()) {
                startRam(target, true);
            } else {
                setState(STATE_IDLE);
                moveCooldown = pace(30);
            }
        }
    }

    /** 壁にぶつかった: 背の容器の固定が外れて止まる。 */
    private void crash(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        Vec3 front = position().add(ramDir.scale(1.2));
        level.sendParticles(ParticleTypes.EXPLOSION, front.x, getY() + 0.8, front.z, 2, 0.3, 0.3, 0.3, 0);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, getSoundSource(), 2.0F, 0.5F);
        playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.6F, 0.6F);
        setState(STATE_STUNNED);
    }

    private void tickStunned(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (stateTicks % 5 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.7, getZ(), 5, 0.4, 0.3, 0.4, 0.15);
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.5, getZ(), 3, 0.5, 0.2, 0.5, 0.01);
        }
        if (stateTicks >= STUN_TICKS) {
            playSound(SoundEvents.PISTON_CONTRACT, 1.6F, 0.6F);
            setState(STATE_IDLE);
            moveCooldown = pace(20);
        }
    }

    // --- 踏み鳴らし

    public void startStomp() {
        moveCooldown = pace(55);
        getNavigation().stop();
        setState(STATE_STOMP_WINDUP);
        playSound(SoundEvents.PISTON_EXTEND, 1.6F, 0.4F);
    }

    private void tickStompWindup(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (stateTicks >= STOMP_WINDUP) {
            stomp(level);
            setState(STATE_IDLE);
        }
    }

    private void stomp(ServerLevel level) {
        rings.add(new Ring(position()));
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.4F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.0, 0.1, 1.0, 0);
    }

    private void tickRings(ServerLevel level) {
        if (rings.size() == 1 && enraged() && rings.getFirst().radius >= 1 + RING_SPEED * 10 && rings.getFirst().radius < 1 + RING_SPEED * 11) {
            rings.add(new Ring(rings.getFirst().center));      // 体力が半分を切ると、続けてもう一つ
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.7F);
        }
        rings.removeIf(ring -> {
            ring.radius += RING_SPEED;
            int n = (int) (ring.radius * 5);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                level.sendParticles(ParticleTypes.CLOUD, ring.center.x + Math.cos(a) * ring.radius, ring.center.y + 0.15,
                        ring.center.z + Math.sin(a) * ring.radius, 1, 0, 0, 0, 0);
            }
            AABB box = new AABB(ring.center, ring.center).inflate(ring.radius + 1, 1.5, ring.radius + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != this && e.isAlive() && e.onGround() && !RuinGuards.isGuard(e) && !ring.hit.contains(e.getUUID()))) {
                double d = Math.sqrt(Mth.square(e.getX() - ring.center.x) + Mth.square(e.getZ() - ring.center.z));
                if (Math.abs(d - ring.radius) <= RING_WIDTH) {
                    ring.hit.add(e.getUUID());
                    if (hurtWith("stomp", e, damageSources().mobAttack(this), STOMP_DAMAGE * damageFactor())) {
                        e.push(0, 0.55, 0);
                        e.hurtMarked = true;
                    }
                }
            }
            return ring.radius >= RING_RADIUS;
        });
    }

    // --- 封じ込め場

    /** 相手の足元と両脇に、足の遅くなる場を落とす。 */
    public void dropFields(ServerLevel level, LivingEntity target) {
        moveCooldown = pace(40);
        Vec3 at = target.position();
        Vec3 side = flatDirection(at.subtract(position())).yRot(Mth.HALF_PI).scale(3.5);
        for (Vec3 p : List.of(at, at.add(side), at.subtract(side))) {
            fields.add(new Field(p));
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.6F);
    }

    private void tickFields(ServerLevel level) {
        fields.removeIf(f -> {
            f.ticks--;
            if (f.ticks % 4 == 0) {
                DustParticleOptions cyan = new DustParticleOptions(glowColor(), 1.0F);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + f.ticks * 0.05;
                    level.sendParticles(cyan, f.center.x + Math.cos(a) * FIELD_RADIUS, f.center.y + 0.1, f.center.z + Math.sin(a) * FIELD_RADIUS,
                            1, 0, 0, 0, 0);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, f.center.x, f.center.y + 0.3, f.center.z, 2, 0.8, 0.1, 0.8, 0);
                AABB box = new AABB(f.center, f.center).inflate(FIELD_RADIUS, 1.5, FIELD_RADIUS);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !RuinGuards.isGuard(e)
                        && e.distanceToSqr(f.center.x, e.getY(), f.center.z) <= FIELD_RADIUS * FIELD_RADIUS)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false, true), this);
                }
            }
            return f.ticks <= 0;
        });
    }

    // --- 挟み

    private void startClamp() {
        clampCooldown = 40;
        getNavigation().stop();
        setState(STATE_CLAMP);
        playSound(SoundEvents.IRON_DOOR_OPEN, 1.4F, 0.6F);
    }

    private void tickClamp(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (target != null) {
            getLookControl().setLookAt(target, 30, 30);
        }
        if (stateTicks >= CLAMP_WINDUP) {
            if (target != null && target.isAlive() && horizontalDistance(target) <= CLAMP_REACH + 0.4) {
                if (hurtWith("clamp", target, damageSources().mobAttack(this), CLAMP_DAMAGE * damageFactor())) {
                    Vec3 push = flatDirection(target.position().subtract(position()));
                    target.push(push.x * 0.6, 0.25, push.z * 0.6);
                    target.hurtMarked = true;
                }
            }
            playSound(SoundEvents.IRON_DOOR_CLOSE, 1.6F, 0.5F);
            setState(STATE_IDLE);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state() == STATE_STUNNED) {
            amount *= STUNNED_DAMAGE;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;                                 // 体当たりはしない（技だけで戦う）
    }

    // ------------------------------------------------------------------ 終わり

    @Override
    public void die(DamageSource source) {
        super.die(source);
        rings.clear();
        fields.clear();
        if (level() instanceof ServerLevel level) {
            RuinRelayBlockEntity.notifyGuard(level, home(), true);
        }
    }

    @Override
    protected void onAbandoned(ServerLevel level) {
        super.onAbandoned(level);
        RuinRelayBlockEntity.notifyGuard(level, home(), false);
    }

    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        if (t % 8 == 1) {
            level.sendParticles(ParticleTypes.EXPLOSION, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + 1.0,
                    getZ() + (random.nextDouble() - 0.5) * 1.5, 1, 0, 0, 0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.2F);
        }
        level.sendParticles(new DustParticleOptions(glowColor(), 1.2F), getX(), getY() + 1.6, getZ(), 3, 0.4, 0.4, 0.4, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("clamp_cooldown", clampCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        clampCooldown = tag.getInt("clamp_cooldown");
    }
}
