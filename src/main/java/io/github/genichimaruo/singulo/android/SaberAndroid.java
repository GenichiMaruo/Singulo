package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 光剣型アンドロイド「ソレイユ」。銀髪に金のひと房。右手の柄から出る光の剣で、重い一撃と守りで戦う。
 *
 * <ul>
 * <li>溜め突き: 剣を腰だめに引いて光を強め、踏み込んで約4ブロック先まで突く。</li>
 * <li>刀身延長の横薙ぎ: 振る瞬間だけ刃が約3ブロックまで伸び、広く薙ぎ払う。</li>
 * <li>構え: 剣を正面に立てて光の盾を張り、正面からのダメージを大きく減らす。構えの中で打たれると、すぐに斬り返す。</li>
 * <li>斬撃波: 三日月の光の斬撃を前へ飛ばす。</li>
 * <li>近くでは短い斬り払い。</li>
 * </ul>
 */
public class SaberAndroid extends RuinAndroid {
    public static final int ACCENT = 0xFFC45A;

    public static final int STATE_THRUST_WINDUP = 30;
    public static final int STATE_THRUST = 31;
    public static final int STATE_SWEEP_WINDUP = 32;
    public static final int STATE_SWEEP = 33;
    public static final int STATE_GUARD = 34;
    public static final int STATE_RIPOSTE = 35;
    public static final int STATE_WAVE = 36;
    public static final int STATE_CUT = 37;

    public static final int THRUST_WINDUP = 14;
    public static final int THRUST = 8;
    public static final int SWEEP_WINDUP = 9;
    public static final int SWEEP = 8;
    public static final int GUARD = 40;
    public static final int RIPOSTE = 8;
    public static final int WAVE = 14;
    public static final int CUT = 7;

    /** 横薙ぎのときに伸びる刃の長さ（ブロック）。 */
    public static final double SWEEP_REACH = 3.4;
    public static final double THRUST_REACH = 4.0;
    /** 打たれた瞬間の盾は、この tick に1回まで（5秒）。 */
    static final int REACTIVE_GUARD_COOLDOWN = 100;
    private long nextReactiveGuard;

    /** 構えで正面から受けるダメージの倍率。 */
    static final float GUARD_FACTOR = 0.3F;

    static final float THRUST_DAMAGE = 7;
    static final float SWEEP_DAMAGE = 6;
    static final float RIPOSTE_DAMAGE = 6;
    static final float CUT_DAMAGE = 4;

    static final int COST_THRUST = 250;
    static final int COST_SWEEP = 250;
    static final int COST_GUARD = 170;
    static final int COST_WAVE = 295;
    static final int COST_CUT = 60;

    public SaberAndroid(EntityType<? extends SaberAndroid> type, Level level) {
        super(type, level);
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.SABER;
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    /** 構えのあいだ、正面からの攻撃を光の盾で受ける。受けたら斬り返す。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || source.getEntity() == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                || isInvulnerableTo(source)) {
            return super.hurt(source, amount);
        }
        boolean reactive = false;
        if (state() != STATE_GUARD && active() && level().getGameTime() >= nextReactiveGuard && charge() >= COST_GUARD
                && !(isGuard() && source.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            // 打たれた瞬間に盾を張る（この一撃から受ける。続けて打たれたら斬り返す）
            spend(COST_GUARD);
            setState(STATE_GUARD);
            nextReactiveGuard = level().getGameTime() + REACTIVE_GUARD_COOLDOWN;
            Vec3 to = source.getEntity().position().subtract(position());
            float yaw = (float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            reactive = true;
        }
        if (state() == STATE_GUARD) {
            Vec3 from = source.getEntity().position().subtract(position()).multiply(1, 0, 1);
            Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
            if (from.lengthSqr() > 1e-4 && from.normalize().dot(look) > 0.35) {
                amount *= GUARD_FACTOR;
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.3F);
                if (level() instanceof ServerLevel level) {
                    sparks(level, position().add(look.scale(0.7)).add(0, 1.0, 0), 10, 0.35);
                }
                if (!reactive) {
                    setState(STATE_RIPOSTE);
                }
            }
        }
        return super.hurt(source, amount);
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 30;
        private int cutCooldown;
        private Vec3 lunge = Vec3.ZERO;

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
            return canUse() || state() >= 30 && active();
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
                if (s >= 30 && stateTicks > 20) {
                    setState(STATE_IDLE);
                }
                return;
            }
            double dist = distanceTo(target);
            switch (s) {
                case STATE_THRUST_WINDUP -> {
                    hold(target);
                    if (stateTicks % 3 == 0) {
                        sparks(level, position().add(0, 0.9, 0), 2, 0.25);
                    }
                    if (stateTicks >= THRUST_WINDUP) {
                        lunge = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                        setState(STATE_THRUST);
                        playSound(SoundEvents.TRIDENT_RIPTIDE_2.value(), 1.0F, 1.4F);
                    }
                }
                case STATE_THRUST -> {
                    getNavigation().stop();
                    if (stateTicks <= 3) {
                        setDeltaMovement(lunge.x * 0.85, Math.min(0, getDeltaMovement().y), lunge.z * 0.85);
                    }
                    if (stateTicks == 3) {
                        sweep(THRUST_REACH, 40, THRUST_DAMAGE, 0.9);
                        sparks(level, position().add(lunge.scale(2.5)).add(0, 1.0, 0), 10, 0.3);
                    }
                    if (stateTicks >= THRUST) {
                        finish(40 + random.nextInt(25));
                    }
                }
                case STATE_SWEEP_WINDUP -> {
                    hold(target);
                    if (stateTicks >= SWEEP_WINDUP) {
                        setState(STATE_SWEEP);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
                    }
                }
                case STATE_SWEEP -> {
                    getNavigation().stop();
                    if (stateTicks == 2) {
                        sweep(SWEEP_REACH, 170, SWEEP_DAMAGE, 0.8);
                    }
                    if (stateTicks >= SWEEP) {
                        finish(40 + random.nextInt(25));
                    }
                }
                case STATE_GUARD -> {
                    hold(target);
                    if (stateTicks >= GUARD) {
                        finish(15);
                    }
                }
                case STATE_RIPOSTE -> {
                    getNavigation().stop();
                    if (stateTicks == 2) {
                        sweep(3.0, 160, RIPOSTE_DAMAGE, 0.9);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
                    }
                    if (stateTicks >= RIPOSTE) {
                        finish(30 + random.nextInt(20));
                    }
                }
                case STATE_WAVE -> {
                    hold(target);
                    if (stateTicks == 7) {
                        Vec3 dir = target.getEyePosition().subtract(position().add(0, 1.0, 0)).normalize();
                        AndroidCrescent.shoot(level, SaberAndroid.this, dir);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.6F);
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + dir.x, getY() + 1.0, getZ() + dir.z, 1, 0, 0, 0, 0);
                    }
                    if (stateTicks >= WAVE) {
                        finish(40 + random.nextInt(25));
                    }
                }
                case STATE_CUT -> {
                    hold(target);
                    if (stateTicks == 3) {
                        sweep(2.8, 120, CUT_DAMAGE, 0.4);
                    }
                    if (stateTicks >= CUT) {
                        setState(STATE_IDLE);
                        cutCooldown = 16;
                    }
                }
                default -> approach(level, target, dist);
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
            cutCooldown--;
            boolean seen = hasLineOfSight(target);
            if (dist > 3.0 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 0.95);
                }
            } else {
                getNavigation().stop();
            }
            if (nextMove <= 0 && seen) {
                float r = random.nextFloat();
                boolean playerAimsAtMe = target instanceof Player p && p.getAttackStrengthScale(0) > 0.9F && dist < 4;
                if (playerAimsAtMe && r < 0.35 && spend(COST_GUARD)) {
                    start(STATE_GUARD, SoundEvents.BEACON_POWER_SELECT, 1.4F);
                    return;
                }
                if (dist < SWEEP_REACH && r < 0.55 && spend(COST_SWEEP)) {
                    start(STATE_SWEEP_WINDUP, SoundEvents.BEACON_POWER_SELECT, 1.1F);
                    return;
                }
                if (dist >= 3 && dist < 7 && r < 0.75 && spend(COST_THRUST)) {
                    start(STATE_THRUST_WINDUP, SoundEvents.BEACON_ACTIVATE, 1.6F);
                    return;
                }
                if (dist >= 4 && dist < 14 && spend(COST_WAVE)) {
                    start(STATE_WAVE, SoundEvents.BEACON_POWER_SELECT, 1.8F);
                    return;
                }
                nextMove = 20;
            }
            if (active() && dist < 2.7 && cutCooldown <= 0 && seen && spend(COST_CUT)) {
                start(STATE_CUT, SoundEvents.PLAYER_ATTACK_WEAK, 1.3F);
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
