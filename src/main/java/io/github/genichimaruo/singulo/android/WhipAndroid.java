package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 鞭型アンドロイド「ヴェルナ」。黒髪の内側だけがマゼンタ。右手の柄から伸びる光の鞭で、中くらいの距離から戦う。
 *
 * <ul>
 * <li>鞭打ち: 頭上に振りかぶり、約6ブロック先まで鋭く打ち込む。</li>
 * <li>絡め取り: 鞭を巻きつけ、相手を自分の方へ引き寄せる（少しのあいだ鈍くなる）。</li>
 * <li>叩きつけ: 鞭を頭上で回してから床に打ち付け、前へ一直線に衝撃波を走らせる（跳べばかわせる）。</li>
 * <li>近くでは、横に払う短い一撃。</li>
 * </ul>
 */
public class WhipAndroid extends RuinAndroid {
    public static final int ACCENT = 0xFF6AC4;
    /** 絡め取った相手（見た目のため。いなければ -1）。 */
    private static final EntityDataAccessor<Integer> LINK = SynchedEntityData.defineId(WhipAndroid.class, EntityDataSerializers.INT);

    public static final int STATE_LASH_WINDUP = 20;
    public static final int STATE_LASH = 21;
    public static final int STATE_BIND = 22;
    public static final int STATE_SLAM_WINDUP = 23;
    public static final int STATE_SLAM = 24;
    public static final int STATE_CRACK = 25;

    public static final int LASH_WINDUP = 9;
    public static final int LASH = 9;
    /** 絡め取り: 投げる（〜BIND_THROW）→ 引き寄せる（〜BIND）。 */
    public static final int BIND_THROW = 5;
    public static final int BIND = 18;
    public static final int SLAM_WINDUP = 14;
    public static final int SLAM = 14;
    public static final int CRACK = 7;

    /** 鞭の届く距離。 */
    public static final double REACH = 6.2;
    /** 衝撃波の届く距離と、1 tick に進む距離。 */
    public static final double WAVE_RANGE = 8.5;
    public static final double WAVE_SPEED = 0.85;

    static final float LASH_DAMAGE = 5;
    static final float BIND_DAMAGE = 3;
    static final float WAVE_DAMAGE = 6;
    static final float CRACK_DAMAGE = 4;

    static final int COST_LASH = 235;
    static final int COST_BIND = 295;
    static final int COST_SLAM = 310;
    static final int COST_CRACK = 60;

    public WhipAndroid(EntityType<? extends WhipAndroid> type, Level level) {
        super(type, level);
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.WHIP;
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LINK, -1);
    }

    /** 絡め取っている相手（クライアントでも見える）。 */
    @Nullable
    public Entity linked() {
        int id = entityData.get(LINK);
        return id < 0 ? null : level().getEntity(id);
    }

    void setLinked(@Nullable Entity e) {
        entityData.set(LINK, e == null ? -1 : e.getId());
    }

    @Override
    protected void setState(int state) {
        super.setState(state);
        if (state != STATE_BIND) {
            setLinked(null);
        }
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    /** 前方の細い扇（半角 halfDeg）・距離 reach の中で、見えている相手に当てる。 */
    @Nullable
    LivingEntity lineTarget(double reach, double halfDeg) {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), this::canHit)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            double d = to.length();
            if (d > reach + e.getBbWidth() / 2 || !hasLineOfSight(e)) {
                continue;
            }
            if (d > 0.8 && to.normalize().dot(look) < Math.cos(Math.toRadians(halfDeg))) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 30;
        private int crackCooldown;
        private final java.util.Set<java.util.UUID> waveHit = new java.util.HashSet<>();
        private Vec3 waveDir = Vec3.ZERO;

        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return active() && t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || state() >= 20 && active();
        }

        @Override
        public void stop() {
            if (active()) {
                setState(STATE_IDLE);
            }
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level) || !active()) {
                return;
            }
            LivingEntity target = getTarget();
            int s = state();
            if (target == null) {
                if (s >= 20 && stateTicks > 20) {
                    setState(STATE_IDLE);
                }
                return;
            }
            double dist = distanceTo(target);
            switch (s) {
                case STATE_LASH_WINDUP -> {
                    hold(target);
                    if (stateTicks >= LASH_WINDUP) {
                        setState(STATE_LASH);
                        playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.6F);
                    }
                }
                case STATE_LASH -> {
                    hold(target);
                    if (stateTicks == 2) {
                        LivingEntity hit = lineTarget(REACH, 14);
                        if (hit != null && hit.hurt(damageSources().mobAttack(WhipAndroid.this), LASH_DAMAGE)) {
                            Vec3 push = hit.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.45);
                            hit.setDeltaMovement(hit.getDeltaMovement().add(push.x, 0.15, push.z));
                            hit.hurtMarked = true;
                            sparks(level, hit.position().add(0, hit.getBbHeight() * 0.6, 0), 8, 0.3);
                            playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.5F);
                        }
                    }
                    if (stateTicks >= LASH) {
                        finish(35 + random.nextInt(25));
                    }
                }
                case STATE_BIND -> {
                    hold(target);
                    if (stateTicks == BIND_THROW) {
                        LivingEntity hit = lineTarget(REACH + 0.8, 16);
                        if (hit != null) {
                            hit.hurt(damageSources().mobAttack(WhipAndroid.this), BIND_DAMAGE);
                            hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                            setLinked(hit);
                            playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
                        } else {
                            finish(30);
                            return;
                        }
                    }
                    Entity linked = linked();
                    if (stateTicks > BIND_THROW && linked instanceof LivingEntity l && l.isAlive()) {
                        Vec3 pull = position().subtract(l.position()).multiply(1, 0, 1);
                        if (pull.length() > 2.0) {
                            Vec3 v = pull.normalize().scale(0.42);
                            l.setDeltaMovement(v.x, Math.max(l.getDeltaMovement().y, 0.12), v.z);
                            l.hurtMarked = true;
                        }
                        if (stateTicks % 3 == 0) {
                            sparks(level, l.position().add(0, l.getBbHeight() * 0.5, 0), 3, 0.3);
                        }
                    }
                    if (stateTicks >= BIND) {
                        finish(30 + random.nextInt(20));
                    }
                }
                case STATE_SLAM_WINDUP -> {
                    hold(target);
                    if (stateTicks % 4 == 0) {
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.8F);
                    }
                    if (stateTicks >= SLAM_WINDUP) {
                        waveDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                        waveHit.clear();
                        setState(STATE_SLAM);
                        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.6F);
                    }
                }
                case STATE_SLAM -> {
                    getNavigation().stop();
                    wave(level);
                    if (stateTicks >= SLAM) {
                        finish(45 + random.nextInt(25));
                    }
                }
                case STATE_CRACK -> {
                    hold(target);
                    if (stateTicks == 3) {
                        sweep(3.0, 110, CRACK_DAMAGE, 0.4);
                    }
                    if (stateTicks >= CRACK) {
                        setState(STATE_IDLE);
                        crackCooldown = 18;
                    }
                }
                default -> approach(level, target, dist);
            }
        }

        /** 衝撃波: 前へ一直線に走り、波の先にいる地上の相手に当たる（跳んでいればかわせる）。 */
        private void wave(ServerLevel level) {
            double front = Math.min(WAVE_RANGE, 1.2 + stateTicks * WAVE_SPEED);
            Vec3 at = position().add(waveDir.scale(front));
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 2, 0.25, 0.02, 0.25, 0.01);
            sparks(level, at.add(0, 0.15, 0), 4, 0.25);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.1, at.z, 4, 0.3, 0.05, 0.3, 0.1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(1.0, 0.6, 1.0),
                    e -> canHit(e) && !waveHit.contains(e.getUUID()))) {
                if (!e.onGround()) {
                    continue;
                }
                waveHit.add(e.getUUID());
                if (e.hurt(damageSources().mobAttack(WhipAndroid.this), WAVE_DAMAGE)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(waveDir.x * 0.4, 0.45, waveDir.z * 0.4));
                    e.hurtMarked = true;
                }
            }
        }

        private void finish(int pause) {
            setState(STATE_IDLE);
            nextMove = pause;
        }

        private void hold(LivingEntity target) {
            getNavigation().stop();
            getLookControl().setLookAt(target, 60, 60);
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        private void approach(ServerLevel level, LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextMove--;
            crackCooldown--;
            boolean seen = hasLineOfSight(target);
            // 鞭の届く少し内側を保つ（近すぎれば下がる）
            if (dist > 5.0 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else if (dist < 2.6) {
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(3);
                if (stateTicks % 10 == 0) {
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
                }
            } else {
                getNavigation().stop();
            }
            if (nextMove <= 0 && seen) {
                float r = random.nextFloat();
                if (dist < REACH - 0.5 && r < 0.4 && spend(COST_LASH)) {
                    start(STATE_LASH_WINDUP, SoundEvents.BEACON_POWER_SELECT, 1.6F);
                    return;
                }
                if (dist >= 3.5 && dist < REACH + 0.5 && r < 0.7 && spend(COST_BIND)) {
                    start(STATE_BIND, SoundEvents.CHAIN_HIT, 1.2F);
                    return;
                }
                if (dist < WAVE_RANGE && spend(COST_SLAM)) {
                    start(STATE_SLAM_WINDUP, SoundEvents.BEACON_ACTIVATE, 1.8F);
                    return;
                }
                nextMove = 20;
            }
            if (active() && dist < 2.8 && crackCooldown <= 0 && seen && spend(COST_CRACK)) {
                start(STATE_CRACK, SoundEvents.PLAYER_ATTACK_WEAK, 1.5F);
            }
        }

        private void start(int s, net.minecraft.sounds.SoundEvent sound, float pitch) {
            if (active()) {
                setState(s);
                playSound(sound, 0.9F, pitch);
            }
        }
    }
}
