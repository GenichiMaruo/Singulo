package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ホライズン・ウォーデン。最終実験施設の守護機で、封印コンソールに触れると起動する（自然には湧かない）。
 * <ul>
 *   <li>フェーズ1（HP 2/3 以上）: 白い外装をまとい（防御+10）、近接攻撃と光弾の射撃</li>
 *   <li>フェーズ2: 外装が剥がれ、重力攻撃（引き寄せる・浮かせて落とす）</li>
 *   <li>フェーズ3（HP 1/3 未満）: 小型の特異点を展開し、防具を無視する潮汐ダメージ域を作る</li>
 * </ul>
 * 挑戦者が死ぬか施設から離れると、全回復して封印に戻る（wardenResetOnLeave）。倒すと保管庫の力場が消える。
 */
public class HorizonWarden extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);

    public static final double ARMOR_PHASE_1 = 10;
    public static final int ARENA_RADIUS = 24;
    /** 挑戦者がいない判定（20 tick ごと）がこの回数続くと封印に戻る。 */
    static final int RESET_CHECKS = 5;
    static final int BOLT_INTERVAL = 40;
    static final int GRAVITY_INTERVAL = 60;
    static final int SINGULARITY_INTERVAL = 160;
    static final int ZONE_TICKS = 100;
    static final double ZONE_RADIUS = 3.5;
    static final float ZONE_DAMAGE = 4.0F;

    /** 潮汐ダメージ域。 */
    private record Zone(Vec3 center, int expires) {}

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<Zone> zones = new ArrayList<>();
    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos console;
    private int specialCooldown = BOLT_INTERVAL;
    private int singularityCooldown = SINGULARITY_INTERVAL;
    private boolean liftNext;
    private int emptyChecks;

    public HorizonWarden(EntityType<? extends HorizonWarden> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, ARMOR_PHASE_1)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** 起動するときに呼ぶ。守る場所と、倒されたとき知らせるコンソール。体力は設定の wardenHealth。 */
    public void awaken(BlockPos home, BlockPos console) {
        this.home = home;
        this.console = console;
        double health = ServerConfig.SPEC.isLoaded() ? ServerConfig.WARDEN_HEALTH.get() : 800;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth((float) health);
        restrictTo(home, ARENA_RADIUS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ 戦闘

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        updatePhase(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            if (--specialCooldown <= 0) {
                if (phase() == 1) {
                    shootBolt(target);
                } else {
                    gravityAttack(level, target);
                }
            }
            if (phase() == 3 && --singularityCooldown <= 0) {
                deploySingularity(target.position());
            }
        }
        tickZones(level);
        if (tickCount % 20 == 0) {
            checkChallengers(level);
        }
    }

    private void updatePhase(ServerLevel level) {
        float f = getHealth() / getMaxHealth();
        int phase = f > 2.0F / 3 ? 1 : f > 1.0F / 3 ? 2 : 3;
        if (phase == phase()) {
            return;
        }
        entityData.set(PHASE, phase);
        getAttribute(Attributes.ARMOR).setBaseValue(phase == 1 ? ARMOR_PHASE_1 : 0);
        bossBar.setColor(phase == 1 ? BossEvent.BossBarColor.WHITE : phase == 2 ? BossEvent.BossBarColor.BLUE
                : BossEvent.BossBarColor.RED);
        if (phase == 2) {
            // 外装が剥がれ落ちる
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.5, getZ(), 4, 0.6, 0.8, 0.6, 0);
            playSound(SoundEvents.IRON_GOLEM_DAMAGE, 2.0F, 0.5F);
        }
        specialCooldown = GRAVITY_INTERVAL / 2;
    }

    /** フェーズ3: その場所に小型の特異点を展開する（ZONE_TICKS のあいだ潮汐ダメージ域になる）。 */
    public void deploySingularity(Vec3 at) {
        zones.add(new Zone(at, tickCount + ZONE_TICKS));
        singularityCooldown = SINGULARITY_INTERVAL;
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
    }

    /** フェーズ1: 光弾を撃つ。近すぎるか見えないときは撃たない。撃ったら true。 */
    public boolean shootBolt(LivingEntity target) {
        specialCooldown = BOLT_INTERVAL;
        if (distanceToSqr(target) < 16 || !hasLineOfSight(target)) {
            return false;
        }
        HorizonBolt bolt = new HorizonBolt(level(), this);
        bolt.setPos(getX(), getEyeY() - 0.3, getZ());
        Vec3 dir = target.getEyePosition().subtract(bolt.position()).normalize();
        bolt.shoot(dir.x, dir.y, dir.z, 1.4F, 1.0F);
        level().addFreshEntity(bolt);
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
        return true;
    }

    /** 引き寄せと、浮かせて落とすのを交互に。リアクターの引力処理と同じ考え方で、速度を直接与える。 */
    public void gravityAttack(ServerLevel level, LivingEntity target) {
        specialCooldown = phase() == 3 ? GRAVITY_INTERVAL * 3 / 2 : GRAVITY_INTERVAL;
        if (distanceToSqr(target) > 20 * 20) {
            return;
        }
        if (liftNext) {
            target.setDeltaMovement(target.getDeltaMovement().x, 1.4, target.getDeltaMovement().z);
        } else {
            Vec3 pull = position().subtract(target.position()).normalize().scale(1.2).add(0, 0.3, 0);
            target.setDeltaMovement(pull);
        }
        target.hurtMarked = true;
        liftNext = !liftNext;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.5, 1, 0.5, 0.05);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.4F);
    }

    /** 潮汐ダメージ域: 中心へ引き寄せ、10 tick ごとに防具を無視するダメージ。 */
    private void tickZones(ServerLevel level) {
        zones.removeIf(z -> tickCount >= z.expires());
        for (Zone z : zones) {
            if (tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, z.center().x, z.center().y + 0.5, z.center().z,
                        12, ZONE_RADIUS / 2, 0.5, ZONE_RADIUS / 2, 0.02);
                level.sendParticles(ParticleTypes.SQUID_INK, z.center().x, z.center().y + 0.8, z.center().z, 2, 0.1, 0.1, 0.1, 0);
            }
            AABB box = new AABB(z.center(), z.center()).inflate(ZONE_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
                Vec3 toCenter = z.center().subtract(e.position());
                if (toCenter.lengthSqr() > ZONE_RADIUS * ZONE_RADIUS) {
                    continue;
                }
                e.setDeltaMovement(e.getDeltaMovement().add(toCenter.normalize().scale(0.08)));
                e.hurtMarked = true;
                if (tickCount % 10 == 0) {
                    e.hurt(SinguloDamageTypes.tidal(level, this), ZONE_DAMAGE);
                }
            }
        }
    }

    /** 守る場所の周りに挑戦者がいなくなったら、封印に戻る。 */
    private void checkChallengers(ServerLevel level) {
        if (home == null) {
            return;
        }
        boolean present = !level.getEntitiesOfClass(Player.class, new AABB(home).inflate(ARENA_RADIUS),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).isEmpty();
        emptyChecks = present ? 0 : emptyChecks + 1;
        boolean reset = !ServerConfig.SPEC.isLoaded() || ServerConfig.WARDEN_RESET_ON_LEAVE.get();
        if (emptyChecks >= RESET_CHECKS && reset) {
            if (console != null && level.getBlockEntity(console) instanceof SealConsoleBlockEntity c) {
                c.onWardenReset(level);
            }
            discard();
        }
    }

    // ------------------------------------------------------------------ 倒されたとき・耐性

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && console != null
                && level.getBlockEntity(console) instanceof SealConsoleBlockEntity c) {
            c.onWardenDefeated(level);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("phase", phase());
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
        if (console != null) {
            tag.put("console", NbtUtils.writeBlockPos(console));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.max(1, tag.getInt("phase")));
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
        console = NbtUtils.readBlockPos(tag, "console").orElse(null);
        if (home != null) {
            restrictTo(home, ARENA_RADIUS);
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}
