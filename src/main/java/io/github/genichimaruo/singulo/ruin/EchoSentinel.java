package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 残響の番人（研究棟のボス）。旧文明の記録を守り続ける警備ホログラム。
 * <ul>
 *   <li>宙に浮いて相手の周りを回り、追いかける光弾を撃つ（体力半分からは5発）</li>
 *   <li>しばらく見られていないと、相手の背後へ跳んで斬りつける（跳ぶ前に光が集まる）。体力半分からは続けて跳ぶ</li>
 *   <li>投影槍: 部屋の残響投影器が一斉に相手を狙い、狙いの線（前触れ）のあとで光の槍を撃つ</li>
 *   <li>投影陣: 相手の足元に光の陣を映し、少しあとで光の柱を立ち上げる（陣の外へ出れば当たらない）</li>
 *   <li>残っている投影器が多いほど実体が薄く、受けるダメージが小さい</li>
 *   <li>体力が半分を切ると、1撃で消える分身を2体呼び、自分も跳んで入れ替わる</li>
 *   <li>倒されると、投影が足元からほどけ、最後に胸の記録結晶が砕ける</li>
 * </ul>
 */
public class EchoSentinel extends RuinBoss {
    private static final EntityDataAccessor<Integer> PROJECTORS = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRIKE = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MINION = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.BOOLEAN);
    /** 投影槍: 狙いの残り tick、撃った光が残る tick、狙った場所。 */
    private static final EntityDataAccessor<Integer> LANCE = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LANCE_FLASH = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> LANCE_AT = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.VECTOR3);
    /** 投影陣: 立ち上がるまでの残り tick、光の柱が残る tick、陣の中心。 */
    private static final EntityDataAccessor<Integer> CIRCLE = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CIRCLE_FLASH = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> CIRCLE_AT = SynchedEntityData.defineId(EchoSentinel.class, EntityDataSerializers.VECTOR3);

    public static final int EMERGE_TICKS = 100;
    public static final int DEATH_TICKS = 60;
    public static final float HEALTH = 320;
    /** 残っている投影器の数（0〜4）ごとの、受けるダメージの倍率。 */
    public static final float[] PROJECTION_FACTOR = {1.0F, 0.65F, 0.4F, 0.22F, 0.1F};
    public static final float BOLT_DAMAGE = 6;
    static final int BOLT_INTERVAL = 36;
    public static final int BLINK_UNSEEN_TICKS = 24;
    static final int BLINK_COOLDOWN = 90;
    /** 体力半分からの、続けて跳ぶ回数（最初の1回のほかに）。 */
    static final int BLINK_CHAIN = 2;
    /** 背後へ跳んでから斬るまでの tick（光が集まる間に避けられる）。 */
    public static final int STRIKE_WINDUP = 12;
    public static final float STRIKE_DAMAGE = 10;
    static final double STRIKE_RANGE = 3.5;
    /** 投影槍・投影陣のどちらかを使う間隔（体力半分からは短い）。 */
    static final int SKILL_INTERVAL = 70;
    static final int SKILL_INTERVAL_ENRAGED = 46;
    public static final int LANCE_WINDUP = 24;
    public static final float LANCE_DAMAGE = 7;
    public static final double LANCE_RANGE = 28;
    public static final int CIRCLE_WINDUP = 30;
    public static final float CIRCLE_DAMAGE = 9;
    public static final double CIRCLE_RADIUS = 3.5;
    public static final double CIRCLE_RADIUS_ENRAGED = 4.5;
    static final int FLASH_TICKS = 8;
    public static final int MINIONS = 2;
    static final int MINION_INTERVAL = 400;
    public static final int MINION_LIFETIME = 600;
    /** 相手の周りを回る距離と高さ。 */
    static final double ORBIT = 6;
    static final double HOVER = 2.0;

    private final List<BlockPos> projectors = new ArrayList<>();
    private int boltCooldown = BOLT_INTERVAL;
    private int blinkCooldown = BLINK_COOLDOWN;
    private int skillCooldown = SKILL_INTERVAL / 2;
    private boolean lanceNext = true;
    private int chain;
    private int minionCooldown;
    private int unseenTicks;
    private int life;
    private double orbitAngle;

    public EchoSentinel(EntityType<? extends EchoSentinel> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.xpReward = 80;
        setNoGravity(true);
        orbitAngle = random.nextDouble() * Math.PI * 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, STRIKE_DAMAGE)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROJECTORS, 0);
        builder.define(STRIKE, 0);
        builder.define(MINION, false);
        builder.define(LANCE, 0);
        builder.define(LANCE_FLASH, 0);
        builder.define(LANCE_AT, new Vector3f());
        builder.define(CIRCLE, 0);
        builder.define(CIRCLE_FLASH, 0);
        builder.define(CIRCLE_AT, new Vector3f());
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
        return 1.0;
    }

    @Override
    public boolean isMinion() {
        return entityData.get(MINION);
    }

    /** 分身にする（体力1、ボスバーなし、しばらくで消える）。 */
    public void makeMinion() {
        entityData.set(MINION, true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);
        setHealth(1);
        xpReward = 0;
        bossBar.setVisible(false);
    }

    /** 体力が半分を切ったか（技が増え、速くなる）。 */
    public boolean enraged() {
        return getHealth() <= getMaxHealth() / 2;
    }

    /** 姿を映している投影器の位置（封印核が渡す）。 */
    public void setProjectors(List<BlockPos> positions) {
        projectors.clear();
        projectors.addAll(positions);
        countProjectors();
    }

    /** 今も残っている投影器の数（描画にも使う）。 */
    public int projectorCount() {
        return entityData.get(PROJECTORS);
    }

    /** 背後へ跳んでから斬るまでの残り tick（0 なら構えていない）。 */
    public int strikeTicks() {
        return entityData.get(STRIKE);
    }

    /** 投影槍の狙いの残り tick（0 なら狙っていない）。 */
    public int lanceTicks() {
        return entityData.get(LANCE);
    }

    /** 投影槍を撃った光が残る tick。 */
    public int lanceFlash() {
        return entityData.get(LANCE_FLASH);
    }

    public Vec3 lanceTarget() {
        Vector3f v = entityData.get(LANCE_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    /** 投影陣が立ち上がるまでの残り tick（0 なら映していない）。 */
    public int circleTicks() {
        return entityData.get(CIRCLE);
    }

    /** 投影陣の光の柱が残る tick。 */
    public int circleFlash() {
        return entityData.get(CIRCLE_FLASH);
    }

    public Vec3 circleCenter() {
        Vector3f v = entityData.get(CIRCLE_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    public double circleRadius() {
        return enraged() ? CIRCLE_RADIUS_ENRAGED : CIRCLE_RADIUS;
    }

    private void countProjectors() {
        int n = 0;
        for (BlockPos p : projectors) {
            if (level().getBlockState(p).is(SinguloBlocks.ECHO_PROJECTOR.get())) {
                n++;
            }
        }
        entityData.set(PROJECTORS, Math.min(n, PROJECTION_FACTOR.length - 1));
    }

    /** 残っている投影器の位置。 */
    public List<BlockPos> intactProjectors() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : projectors) {
            if (level().getBlockState(p).is(SinguloBlocks.ECHO_PROJECTOR.get())) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ 出現

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        Vec3 c = position().add(0, 1.2, 0);
        // 部屋のあちこちから光の粒が集まり、姿を結ぶ
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 3 + random.nextDouble() * 3;
            level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + (random.nextDouble() - 0.3) * 3,
                    c.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 0.25);
        }
        if (t == 1) {
            playSound(SinguloSounds.get("echo_sentinel.emerge"), 2.0F, 1.0F);
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.2, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 40, 0.4, 0.8, 0.4, 0.15);
    }

    // ------------------------------------------------------------------ 戦闘

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDeadOrDying()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0 && !isMinion()) {
            countProjectors();
        }
        if (isMinion() && ++life > MINION_LIFETIME) {
            vanish(level);
            return;
        }
        tickFlashes();
        LivingEntity target = getTarget();
        drift(target);
        tickLance(level);
        tickCircle(level);
        if (target == null || !target.isAlive()) {
            return;
        }
        int strike = strikeTicks();
        if (strike > 0) {
            entityData.set(STRIKE, strike - 1);
            getLookControl().setLookAt(target);
            if (strike - 1 == 0) {
                strike(level, target);
            }
            return;
        }
        if (--boltCooldown <= 0) {
            shootBolts(target);
        }
        if (!isMinion() && --skillCooldown <= 0 && lanceTicks() == 0 && circleTicks() == 0) {
            skillCooldown = enraged() ? SKILL_INTERVAL_ENRAGED : SKILL_INTERVAL;
            if (lanceNext && projectorCount() > 0) {
                startLance(target);
            } else {
                startCircle(target);
            }
            lanceNext = !lanceNext;
        }
        unseenTicks = watchedBy(target) ? 0 : unseenTicks + 1;
        if (--blinkCooldown <= 0 && unseenTicks >= BLINK_UNSEEN_TICKS) {
            chain = 0;
            blinkBehind(level, target);
        }
        if (!isMinion() && enraged() && --minionCooldown <= 0) {
            summonEchoes(level, target);
        }
    }

    private void tickFlashes() {
        if (lanceFlash() > 0) {
            entityData.set(LANCE_FLASH, lanceFlash() - 1);
        }
        if (circleFlash() > 0) {
            entityData.set(CIRCLE_FLASH, circleFlash() - 1);
        }
    }

    /** 相手の周りを、少し高いところで回る（相手がいなければ核の上に戻る）。 */
    private void drift(@Nullable LivingEntity target) {
        Vec3 goal;
        if (target != null && target.isAlive()) {
            orbitAngle += enraged() ? 0.04 : 0.025;
            goal = target.position().add(Math.cos(orbitAngle) * ORBIT, HOVER, Math.sin(orbitAngle) * ORBIT);
        } else if (home != null) {
            goal = Vec3.atBottomCenterOf(home).add(0, 1.0 + HOVER, 0);
        } else {
            return;
        }
        if (home != null) {
            Vec3 h = Vec3.atBottomCenterOf(home);
            Vec3 off = goal.subtract(h);
            double max = ARENA_RADIUS - 4;
            if (off.horizontalDistance() > max) {
                goal = h.add(off.x * max / off.horizontalDistance(), off.y, off.z * max / off.horizontalDistance());
            }
        }
        Vec3 v = goal.subtract(position()).scale(0.06);
        if (v.length() > 0.32) {
            v = v.normalize().scale(0.32);
        }
        setDeltaMovement(getDeltaMovement().scale(0.6).add(v.scale(0.4)));
    }

    /** 相手がこちらを見ているか（視線の先、約40度以内で、間に壁がない）。 */
    public boolean watchedBy(LivingEntity target) {
        Vec3 to = getEyePosition().subtract(target.getEyePosition()).normalize();
        return target.getViewVector(1).dot(to) > 0.75 && target.hasLineOfSight(this);
    }

    public void shootBolts(LivingEntity target) {
        boltCooldown = BOLT_INTERVAL + random.nextInt(16);
        if (!hasLineOfSight(target)) {
            return;
        }
        Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
        float damage = isMinion() ? BOLT_DAMAGE / 2 : BOLT_DAMAGE;
        int n = !isMinion() && enraged() ? 5 : 3;
        for (int i = 0; i < n; i++) {
            double a = (i - (n - 1) / 2.0) * 0.5;
            Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90).scale(Math.sin(a) * 0.8);
            HorizonBolt bolt = new HorizonBolt(level(), this).homing(target).style(0x7FE8FF, 0.7F, damage);
            bolt.setPos(from.add(side));
            Vec3 d = target.getEyePosition().subtract(from).normalize().add(0, 0.25, 0).add(side.scale(0.5));
            bolt.shoot(d.x, d.y, d.z, 0.85F, 0);
            level().addFreshEntity(bolt);
        }
        playSound(SinguloSounds.get("echo_sentinel.bolt"), 1.5F, 1.0F);
    }

    /** 見られていない間に、相手の背後へ跳んで構える。 */
    public boolean blinkBehind(ServerLevel level, LivingEntity target) {
        blinkCooldown = enraged() ? BLINK_COOLDOWN * 2 / 3 : BLINK_COOLDOWN;
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) {
            look = new Vec3(0, 0, 1);
        }
        // 続けて跳ぶときは、背後ではなく左右に回り込む
        double turn = chain == 0 ? 0 : (chain % 2 == 1 ? 1 : -1) * Math.PI / 2;
        Vec3 dir = look.normalize().yRot((float) turn);
        Vec3 behind = target.position().subtract(dir.scale(2.5));
        Vec3 before = position();
        if (!randomTeleport(behind.x, target.getY() + 0.5, behind.z, false)) {
            return false;
        }
        unseenTicks = 0;
        entityData.set(STRIKE, chain == 0 ? STRIKE_WINDUP : STRIKE_WINDUP * 2 / 3);
        setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.END_ROD, before.x, before.y + 1.2, before.z, 30, 0.3, 0.8, 0.3, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 20, 0.3, 0.8, 0.3, 0.2);
        playSound(SinguloSounds.get("echo_sentinel.blink"), 1.5F, 1.0F);
        // 構えの音は斬るまでの長さに合わせる（続けて跳ぶときは短いので高く速く）
        playSound(SinguloSounds.get("echo_sentinel.strike_windup"), 1.5F, chain == 0 ? 1.0F : 1.5F);
        return true;
    }

    private void strike(ServerLevel level, LivingEntity target) {
        Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + dir.x * 0.5, getY() + 1.2, getZ() + dir.z * 0.5, 1, 0, 0, 0, 0);
        playSound(SinguloSounds.get("echo_sentinel.strike"), 1.5F, 1.0F);
        if (distanceTo(target) <= STRIKE_RANGE) {
            float damage = isMinion() ? STRIKE_DAMAGE / 2 : STRIKE_DAMAGE;
            if (target.hurt(damageSources().mobAttack(this), damage) && dir.lengthSqr() > 1e-4) {
                Vec3 k = dir.normalize();
                target.knockback(0.6, -k.x, -k.z);
            }
        }
        // 体力半分からは、続けて回り込んで斬る
        if (!isMinion() && enraged() && chain < BLINK_CHAIN && random.nextFloat() < 0.7F) {
            chain++;
            blinkBehind(level, target);
        }
    }

    // ------------------------------------------------------------------ 投影槍

    /** 残っている投影器が一斉に相手を狙う。狙う場所は始めたときの相手の胸（動けばよけられる）。 */
    public boolean startLance(LivingEntity target) {
        if (intactProjectors().isEmpty()) {
            return false;
        }
        entityData.set(LANCE, LANCE_WINDUP);
        Vec3 at = target.getBoundingBox().getCenter();
        entityData.set(LANCE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
        for (BlockPos p : intactProjectors()) {
            level().playSound(null, p, SinguloSounds.get("echo_sentinel.lance_aim"), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        return true;
    }

    private void tickLance(ServerLevel level) {
        int l = lanceTicks();
        if (l <= 0) {
            return;
        }
        entityData.set(LANCE, l - 1);
        if (l - 1 > 0) {
            return;
        }
        Vec3 at = lanceTarget();
        for (BlockPos p : intactProjectors()) {
            Vec3 lens = Vec3.atBottomCenterOf(p).add(0, 0.95, 0);
            Vec3 dir = at.subtract(lens).normalize();
            Vec3 from = lens.add(dir.scale(0.6));
            Vec3 to = lens.add(dir.scale(LANCE_RANGE));
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.8),
                    e -> e.isAlive() && !RuinGuards.isGuard(e))) {
                if (!e.getBoundingBox().inflate(0.3).clip(from, end).isEmpty()) {
                    e.hurt(damageSources().indirectMagic(this, this), LANCE_DAMAGE);
                }
            }
            for (int i = 0; i <= 16; i++) {
                Vec3 q = from.lerp(end, i / 16.0);
                level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.01);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 8, 0.1, 0.1, 0.1, 0.2);
            level.playSound(null, p, SinguloSounds.get("echo_sentinel.lance_fire"), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        entityData.set(LANCE_FLASH, FLASH_TICKS);
    }

    // ------------------------------------------------------------------ 投影陣

    /** 相手の足元に光の陣を映す。少しあとで光の柱が立ち上がる。 */
    public void startCircle(LivingEntity target) {
        entityData.set(CIRCLE, CIRCLE_WINDUP);
        Vec3 at = target.position();
        entityData.set(CIRCLE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
        level().playSound(null, at.x, at.y, at.z, SinguloSounds.get("echo_sentinel.circle_cast"), SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    private void tickCircle(ServerLevel level) {
        int c = circleTicks();
        if (c <= 0) {
            return;
        }
        entityData.set(CIRCLE, c - 1);
        Vec3 at = circleCenter();
        double r = circleRadius();
        if (c % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
        if (c - 1 > 0) {
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 1, r).expandTowards(0, 3, 0),
                e -> e.isAlive() && !RuinGuards.isGuard(e))) {
            double dx = e.getX() - at.x;
            double dz = e.getZ() - at.z;
            if (dx * dx + dz * dz <= r * r && e.hurt(damageSources().indirectMagic(this, this), CIRCLE_DAMAGE)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                e.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 60, r * 0.5, 1.5, r * 0.5, 0.05);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        entityData.set(CIRCLE_FLASH, FLASH_TICKS);
        level.playSound(null, at.x, at.y, at.z, SinguloSounds.get("echo_sentinel.circle_erupt"), SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    // ------------------------------------------------------------------ 分身

    /** 1撃で消える分身を呼び、自分も跳んで入れ替わる。 */
    public void summonEchoes(ServerLevel level, LivingEntity target) {
        minionCooldown = MINION_INTERVAL;
        long alive = level.getEntitiesOfClass(EchoSentinel.class, getBoundingBox().inflate(32), EchoSentinel::isMinion).size();
        for (int i = (int) alive; i < MINIONS; i++) {
            EchoSentinel echo = SinguloEntities.ECHO_SENTINEL.get().create(level);
            if (echo == null) {
                continue;
            }
            echo.makeMinion();
            double a = orbitAngle + Math.PI * 2 * (i + 1) / (MINIONS + 1);
            echo.moveTo(target.getX() + Math.cos(a) * ORBIT, target.getY() + HOVER, target.getZ() + Math.sin(a) * ORBIT, getYRot(), 0);
            echo.orbitAngle = a;
            echo.setTarget(target);
            if (home != null) {
                echo.restrictTo(home, ARENA_RADIUS);
            }
            level.addFreshEntity(echo);
            level.sendParticles(ParticleTypes.FLASH, echo.getX(), echo.getY() + 1.2, echo.getZ(), 1, 0, 0, 0, 0);
        }
        orbitAngle += Math.PI * 2 / (MINIONS + 1) * (1 + random.nextInt(MINIONS));
        Vec3 swap = target.position().add(Math.cos(orbitAngle) * ORBIT, HOVER, Math.sin(orbitAngle) * ORBIT);
        randomTeleport(swap.x, swap.y, swap.z, false);
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.2, getZ(), 1, 0, 0, 0, 0);
        playSound(SinguloSounds.get("echo_sentinel.summon"), 2.0F, 1.0F);
    }

    /** 分身が消える。 */
    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 20, 0.3, 0.6, 0.3, 0.08);
        playSound(SinguloSounds.get("echo_sentinel.echo_vanish"), 1.0F, 1.0F);
        discard();
    }

    /** 投影器が姿を映しているあいだは、受けるダメージが小さい。分身は何を受けても消える。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (emergeTicks() > 0 || RuinGuards.friendlyFire(source)) {
            return super.hurt(source, amount);
        }
        if (isMinion()) {
            if (amount > 0 && !level().isClientSide && !isInvulnerableTo(source)) {
                vanish((ServerLevel) level());
                return true;
            }
            return false;
        }
        boolean bypass = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        int n = projectorCount();
        float scaled = bypass ? amount : amount * PROJECTION_FACTOR[Mth.clamp(n, 0, PROJECTION_FACTOR.length - 1)];
        if (n > 0 && !bypass && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 6 + n * 2, 0.4, 0.6, 0.4, 0.3);
        }
        return super.hurt(source, scaled);
    }

    // ------------------------------------------------------------------ 倒されたとき

    @Override
    public void die(DamageSource source) {
        super.die(source);
        entityData.set(LANCE, 0);
        entityData.set(CIRCLE, 0);
        entityData.set(STRIKE, 0);
        if (level() instanceof ServerLevel level && !isMinion()) {
            for (EchoSentinel echo : level.getEntitiesOfClass(EchoSentinel.class, getBoundingBox().inflate(40), EchoSentinel::isMinion)) {
                echo.vanish(level);
            }
            playSound(SinguloSounds.get("echo_sentinel.death_unravel"), 2.0F, 1.0F);
        }
    }

    /**
     * 倒されたときの演出: 投影がちらつきながら足元からほどけて光の粒になって昇り、
     * 投影器が火花を散らして消える。最後に、宙に残った胸の記録結晶にひびが走って砕け散る。
     */
    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        Vec3 c = position();
        float k = (float) t / DEATH_TICKS;
        double front = 2.4 * Math.min(1, k / 0.75);
        if (t < DEATH_TICKS * 3 / 4) {
            // ほどけていく境目から、光の粒が昇る
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.END_ROD, c.x + (random.nextDouble() - 0.5) * 0.8, c.y + front,
                        c.z + (random.nextDouble() - 0.5) * 0.8, 0, (random.nextDouble() - 0.5) * 0.1, 0.12, (random.nextDouble() - 0.5) * 0.1, 1);
            }
            if (t % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + front, c.z, 4, 0.4, 0.1, 0.4, 0.15);
            }
        }
        if (t == 2 || t == 20) {
            // 投影器が力を失い、火花を散らす
            for (BlockPos p : projectors) {
                if (level.getBlockState(p).is(SinguloBlocks.ECHO_PROJECTOR.get())) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.2);
                    if (t == 2) {
                        level.playSound(null, p, SinguloSounds.get("echo_projector.power_down"), SoundSource.HOSTILE, 1.0F, 1.0F);
                    }
                }
            }
        }
        if (t == DEATH_TICKS * 9 / 10) {
            // 記録結晶が砕け散る
            Vec3 core = c.add(0, 1.57, 0);
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 2, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 90, 0.1, 0.1, 0.1, 0.35);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 40, 0.2, 0.2, 0.2, 0.5);
            level.playSound(null, blockPosition(), SinguloSounds.get("echo_sentinel.death_shatter"), SoundSource.HOSTILE, 2.5F, 1.0F);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;                                  // 体から鳴り続ける音は、クライアントの BossSounds が鳴らす
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SinguloSounds.get("echo_sentinel.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("minion", isMinion());
        tag.putInt("life", life);
        ListTag list = new ListTag();
        for (BlockPos p : projectors) {
            list.add(NbtUtils.writeBlockPos(p));
        }
        tag.put("projectors", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("minion")) {
            makeMinion();
        }
        life = tag.getInt("life");
        projectors.clear();
        for (Tag t : tag.getList("projectors", Tag.TAG_INT_ARRAY)) {
            CompoundTag c = new CompoundTag();
            c.put("p", t);
            NbtUtils.readBlockPos(c, "p").ifPresent(projectors::add);
        }
    }
}
