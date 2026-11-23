package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 暴走した炉殻修理機。沈んだ炉心区画の前室（炉殻の破片の整備場）の中ボスで、整備場の中継器が起こす（自然には湧かない）。
 * 剥がれた炉殻を直し続けていた大型の修理機で、挑戦者を「直すべき破片」と見なして襲う。宙に浮いて相手の周りを回り、
 * <ul>
 *   <li>溶接の火花: 橙の光弾を3発撃つ</li>
 *   <li>修理ドローンを呼ぶ: 警備ドローン（段階2）を2機ずつ、同時に {@value #MAX_DRONES} 機まで</li>
 *   <li>押しつぶし: 相手の上へ昇り、赤い印の所へ落ちて周りを打つ。落ちたあとしばらく床に伏せ、受けるダメージが {@value #GROUNDED_DAMAGE} 倍になる</li>
 *   <li>自己修理（体力が半分を切ってから）: 止まって体力を戻す。プレイヤーが殴ると止まる</li>
 * </ul>
 * 倒すか挑戦者がいなくなると、呼んだドローンは止まる。倒すと整備場の中継器が起動できるようになる。
 */
public class HullRepairer extends RuinBoss {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(HullRepairer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> SLAM_AT = SynchedEntityData.defineId(HullRepairer.class, EntityDataSerializers.VECTOR3);
    public static final int STATE_IDLE = 0, STATE_WINDUP = 1, STATE_DROP = 2, STATE_GROUNDED = 3, STATE_REPAIR = 4;

    public static final float HEALTH = 360;
    public static final float HEALTH_NORMAL = 300;
    public static final int ARENA = 14;
    public static final int EMERGE_TICKS = 60, DEATH_TICKS = 40;
    /** 浮かぶ高さ（相手の足元から）と、回る距離。 */
    static final double HOVER = 3.2, ORBIT = 6;
    public static final int MAX_DRONES = 4;
    public static final float WELD_DAMAGE = 6, SLAM_DAMAGE = 12;
    public static final double SLAM_RADIUS = 3.5;
    public static final int WINDUP_TICKS = 26, DROP_TICKS = 16, GROUNDED_TICKS = 50, REPAIR_TICKS = 60;
    public static final float GROUNDED_DAMAGE = 1.5F;
    /** 自己修理で戻す体力（最大体力の割合。5 tick ごと）と、次に修理できるまでの間。 */
    static final float REPAIR_RATE = 0.01F;
    static final int REPAIR_COOLDOWN = 600;
    static final int WELD_COLOR = 0xFFA040;

    private int moveCooldown = 40;
    private int stateTicks;
    private int repairCooldown = REPAIR_COOLDOWN / 2;
    private int lastMove = -1;
    private double orbitAngle;
    private final List<UUID> drones = new ArrayList<>();

    public HullRepairer(EntityType<? extends HullRepairer> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 400;
        this.moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
        if (!level.isClientSide) {
            setEffectiveHealth(health());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static float health() {
        return (float) BossScaling.baseHealth(BossScaling.hardMode() ? HEALTH : HEALTH_NORMAL);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, STATE_IDLE);
        entityData.define(SLAM_AT, new Vector3f());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int state() {
        return entityData.get(STATE);
    }

    /** 呼んだ修理ドローンの ID（まだ生きているかは確かめない）。 */
    public List<UUID> drones() {
        return List.copyOf(drones);
    }

    public Vec3 slamAt() {
        Vector3f v = entityData.get(SLAM_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
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
        return 1.5;
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

    // ------------------------------------------------------------------ 出現

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 4, 0.7, 0.5, 0.7, 0.08);
        if (t == 1) {
            playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
        }
        if (t % 15 == 0) {
            playSound(SoundEvents.ANVIL_USE, 1.0F, 0.6F + t / 100F);
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 3, 0.5, 0.3, 0.5, 0);
    }

    // ------------------------------------------------------------------ 戦い

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        drones.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        stateTicks++;
        LivingEntity target = getTarget();
        switch (state()) {
            case STATE_WINDUP -> tickWindup(level, target);
            case STATE_DROP -> tickDrop(level);
            case STATE_GROUNDED -> {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5));
                if (tickCount % 6 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6, getZ(), 4, 0.6, 0.2, 0.6, 0.01);
                }
                if (stateTicks >= GROUNDED_TICKS) {
                    setState(STATE_IDLE);
                    setDeltaMovement(0, 0.4, 0);
                }
            }
            case STATE_REPAIR -> tickRepair(level);
            default -> {
                if (target != null && target.isAlive()) {
                    hover(target);
                    if (--moveCooldown <= 0) {
                        chooseMove(level, target);
                    }
                } else {
                    setDeltaMovement(getDeltaMovement().scale(0.8));
                }
            }
        }
        if (repairCooldown > 0) {
            repairCooldown--;
        }
    }

    /** 相手の周りを、少し上で回る（守る場所から離れすぎない）。 */
    private void hover(LivingEntity target) {
        orbitAngle += 0.025;
        double x = target.getX() + Math.cos(orbitAngle) * ORBIT;
        double z = target.getZ() + Math.sin(orbitAngle) * ORBIT;
        BlockPos home = home();
        if (home != null) {
            Vec3 h = Vec3.atCenterOf(home);
            Vec3 d = new Vec3(x - h.x, 0, z - h.z);
            double max = ARENA - 3;
            if (d.length() > max) {
                d = d.normalize().scale(max);
                x = h.x + d.x;
                z = h.z + d.z;
            }
        }
        getMoveControl().setWantedPosition(x, target.getY() + HOVER, z, 1.0);
        getLookControl().setLookAt(target, 30, 30);
    }

    private void chooseMove(ServerLevel level, LivingEntity target) {
        List<Integer> moves = new ArrayList<>(List.of(0, 0, 2));
        if (drones.size() + 2 <= MAX_DRONES) {
            moves.add(1);
        }
        if (getHealth() < getMaxHealth() * enrageFraction() && repairCooldown <= 0) {
            moves.add(3);
        }
        if (moves.stream().anyMatch(m -> m != lastMove)) {
            moves.removeIf(m -> m == lastMove);
        }
        int move = moves.get(random.nextInt(moves.size()));
        lastMove = move;
        switch (move) {
            case 0 -> weld(target);
            case 1 -> callDrones(level);
            case 2 -> startSlam(target);
            default -> startRepair(level);
        }
    }

    /** 溶接の火花: 橙の光弾を3発。 */
    public void weld(LivingEntity target) {
        moveCooldown = pace(45);
        Vec3 from = position().add(0, getBbHeight() * 0.35, 0);
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        for (int i = -1; i <= 1; i++) {
            Vec3 d = dir.yRot(i * 10 * net.minecraft.util.Mth.DEG_TO_RAD);
            HorizonBolt bolt = new HorizonBolt(level(), this).style(WELD_COLOR, 0.6F, WELD_DAMAGE * damageFactor());
            bolt.setPos(from);
            bolt.shoot(d.x, d.y, d.z, 1.2F, 1.0F);
            level().addFreshEntity(bolt);
        }
        playSound(SoundEvents.BLAZE_SHOOT, 1.2F, 1.4F);
    }

    /** 修理ドローン（警備ドローン・段階2）を2機呼ぶ。 */
    public void callDrones(ServerLevel level) {
        moveCooldown = pace(70);
        for (int i = 0; i < 2 && drones.size() < MAX_DRONES; i++) {
            SecurityDrone drone = SinguloEntities.SECURITY_DRONE.get().create(level);
            if (drone == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2;
            drone.moveTo(getX() + Math.cos(a) * 1.5, getY() + 0.5, getZ() + Math.sin(a) * 1.5, random.nextFloat() * 360, 0);
            drone.setTier(2);
            BlockPos home = home();
            drone.restrictTo(home != null ? home : blockPosition(), ARENA);
            level.addFreshEntity(drone);
            drones.add(drone.getUUID());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, drone.getX(), drone.getY(), drone.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.8F);
    }

    /** 押しつぶし: 相手の上へ昇り、印の所へ落ちる。 */
    public void startSlam(LivingEntity target) {
        moveCooldown = pace(60);
        setState(STATE_WINDUP);
        entityData.set(SLAM_AT, target.position().toVector3f());
        playSound(SoundEvents.PISTON_EXTEND, 1.6F, 0.5F);
    }

    private void tickWindup(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 spot = slamAt();
        if (target != null && target.isAlive() && stateTicks < WINDUP_TICKS / 2) {
            // 前半は相手を追って印を動かし、後半は固定する
            spot = new Vec3(target.getX(), target.getY(), target.getZ());
            entityData.set(SLAM_AT, spot.toVector3f());
        }
        getMoveControl().setWantedPosition(spot.x, spot.y + HOVER + 3, spot.z, 1.4);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.2F), 1.2F);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + stateTicks * 0.2;
            level.sendParticles(red, spot.x + Math.cos(a) * SLAM_RADIUS, spot.y + 0.1, spot.z + Math.sin(a) * SLAM_RADIUS, 1, 0, 0, 0, 0);
        }
        if (stateTicks >= WINDUP_TICKS) {
            setState(STATE_DROP);
            playSound(SoundEvents.ANVIL_PLACE, 1.4F, 0.6F);
        }
    }

    private void tickDrop(ServerLevel level) {
        Vec3 spot = slamAt();
        Vec3 to = spot.subtract(position());
        Vec3 v = to.lengthSqr() > 1 ? to.normalize().scale(1.3) : to;
        setDeltaMovement(v);
        if (onGround() || getY() <= spot.y + 0.3 || stateTicks >= DROP_TICKS || horizontalCollision && verticalCollision) {
            impact(level);
        }
    }

    /** 落ちた所の周りを打つ（遺構の守り手は打たない）。 */
    private void impact(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        Vec3 c = position();
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 6, SLAM_RADIUS / 2, 0.2, SLAM_RADIUS / 2, 0);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, getSoundSource(), 2.0F, 0.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(SLAM_RADIUS, 2, SLAM_RADIUS),
                e -> e != this && e.isAlive() && !RuinGuards.isGuard(e))) {
            if (hurtWith("slam", e, damageSources().mobAttack(this), SLAM_DAMAGE * damageFactor())) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    push = push.normalize();
                }
                e.push(push.x * 0.8, 0.45, push.z * 0.8);
                e.hurtMarked = true;
            }
        }
        setState(STATE_GROUNDED);
    }

    /** 自己修理を始める。 */
    public void startRepair(ServerLevel level) {
        moveCooldown = pace(40);
        repairCooldown = REPAIR_COOLDOWN;
        setState(STATE_REPAIR);
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.6F, 0.8F);
    }

    private void tickRepair(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().scale(0.6));
        if (stateTicks % 5 == 0) {
            heal(getMaxHealth() * REPAIR_RATE);
            level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 0.8, getZ(), 6, 0.6, 0.5, 0.6, 0.05);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 4, 0.6, 0.5, 0.6, 0.1);
        }
        if (stateTicks >= REPAIR_TICKS) {
            setState(STATE_IDLE);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state() == STATE_GROUNDED) {
            amount *= GROUNDED_DAMAGE;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && state() == STATE_REPAIR && source.getEntity() instanceof Player) {
            // 殴られると修理をやめる
            setState(STATE_IDLE);
            playSound(SoundEvents.ITEM_BREAK, 1.4F, 0.6F);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;                                 // 体当たりはしない（技だけで戦う）
    }

    // ------------------------------------------------------------------ 終わり

    /** 呼んだドローンを止める（倒されたとき・戻るとき）。 */
    private void recallDrones(ServerLevel level) {
        for (UUID id : drones) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY(), e.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                e.discard();
            }
        }
        drones.clear();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            recallDrones(level);
            RuinRelayBlockEntity.notifyGuard(level, home(), true);
        }
    }

    @Override
    protected void onAbandoned(ServerLevel level) {
        super.onAbandoned(level);
        recallDrones(level);
        RuinRelayBlockEntity.notifyGuard(level, home(), false);
    }

    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        if (t % 6 == 1) {
            level.sendParticles(ParticleTypes.EXPLOSION, getX() + (random.nextDouble() - 0.5), getY() + 0.8, getZ() + (random.nextDouble() - 0.5),
                    1, 0, 0, 0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.4F);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 3, 0.6, 0.5, 0.6, 0.2);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("repair_cooldown", repairCooldown);
        ListTag list = new ListTag();
        for (UUID id : drones) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("drones", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        repairCooldown = tag.getInt("repair_cooldown");
        drones.clear();
        for (Tag t : tag.getList("drones", Tag.TAG_INT_ARRAY)) {
            drones.add(NbtUtils.loadUUID(t));
        }
    }
}
