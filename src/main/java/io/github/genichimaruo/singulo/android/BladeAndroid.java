package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 光刃型アンドロイド「ルミナ」。白い髪に水色のひと房。右腕に付けた透きとおる光の刃（アームブレード）で戦う。
 *
 * <ul>
 * <li>瞬歩斬り: 身をかがめて刃を強く光らせ（前触れ）、残像を残して一気に詰め寄り、腕ごと振り抜く。</li>
 * <li>光柱: 片膝をついて床に手を当て、相手の足元とその周りに予告の輪を出す。少しして光の柱が立つ（ボスより弱い）。</li>
 * <li>回転斬り: 近くに寄られたり囲まれたりすると、刃を広げてその場で一回転する。</li>
 * <li>近くでは軽い斬り払い。</li>
 * </ul>
 */
public class BladeAndroid extends RuinAndroid {
    public static final int ACCENT = 0x78E1FF;

    public static final int STATE_DASH_WINDUP = 10;
    public static final int STATE_DASH = 11;
    public static final int STATE_SLASH = 12;
    public static final int STATE_SPIN_WINDUP = 13;
    public static final int STATE_SPIN = 14;
    public static final int STATE_CAST = 15;
    public static final int STATE_SWING = 16;

    public static final int DASH_WINDUP = 12;
    public static final int DASH_MAX = 9;
    public static final int SLASH = 8;
    public static final int SPIN_WINDUP = 7;
    public static final int SPIN = 10;
    public static final int CAST = 18;
    public static final int SWING = 7;

    static final float DASH_DAMAGE = 6;
    static final float SPIN_DAMAGE = 5;
    static final float SWING_DAMAGE = 4;
    static final double DASH_SPEED = 1.15;

    /** 技ごとの充電の消費（満タンは {@link #CHARGE_MAX}）。 */
    static final int COST_DASH = 250;
    static final int COST_SPIN = 250;
    static final int COST_CAST = 310;
    static final int COST_SWING = 60;

    public BladeAndroid(EntityType<? extends BladeAndroid> type, Level level) {
        super(type, level);
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 30;
        private int swingCooldown;
        private Vec3 dashDir = Vec3.ZERO;

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
            return canUse() || busy();
        }

        private boolean busy() {
            int s = state();
            return s >= 10 && active();
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
            if (target == null || !target.isAlive()) {
                if (s >= 10 && stateTicks > 20) {
                    setState(STATE_IDLE);
                }
                if (target == null) {
                    return;
                }
            }
            double dist = distanceTo(target);
            switch (s) {
                case STATE_DASH_WINDUP -> {
                    getNavigation().stop();
                    getLookControl().setLookAt(target, 60, 60);
                    faceTarget(target);
                    setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
                    if (stateTicks % 3 == 0) {
                        sparks(level, position().add(0, 1.0, 0), 2, 0.3);
                    }
                    if (stateTicks >= DASH_WINDUP) {
                        Vec3 aim = target.position().add(target.getDeltaMovement().scale(4));
                        dashDir = aim.subtract(position()).multiply(1, 0, 1).normalize();
                        setState(STATE_DASH);
                        playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.5F);
                    }
                }
                case STATE_DASH -> {
                    setDeltaMovement(dashDir.x * DASH_SPEED, Math.min(0, getDeltaMovement().y), dashDir.z * DASH_SPEED);
                    setYRot((float) (Mth.atan2(dashDir.z, dashDir.x) * Mth.RAD_TO_DEG) - 90);
                    yBodyRot = getYRot();
                    yHeadRot = getYRot();
                    level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.9, getZ(), 1, 0.15, 0.3, 0.15, 0);
                    if (dist < 2.2 || stateTicks >= DASH_MAX || horizontalCollision) {
                        setDeltaMovement(dashDir.scale(0.25));
                        setState(STATE_SLASH);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
                    }
                }
                case STATE_SLASH -> {
                    getNavigation().stop();
                    if (stateTicks == 2) {
                        sweep(3.2, 150, DASH_DAMAGE, 0.7);
                        sparks(level, position().add(Vec3.directionFromRotation(0, yBodyRot).scale(1.4)).add(0, 1, 0), 8, 0.5);
                    }
                    if (stateTicks >= SLASH) {
                        finish(40 + random.nextInt(30));
                    }
                }
                case STATE_SPIN_WINDUP -> {
                    getNavigation().stop();
                    faceTarget(target);
                    if (stateTicks >= SPIN_WINDUP) {
                        setState(STATE_SPIN);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.9F);
                    }
                }
                case STATE_SPIN -> {
                    getNavigation().stop();
                    if (stateTicks == 4) {
                        sweep(3.3, 360, SPIN_DAMAGE, 1.0);
                        for (int i = 0; i < 16; i++) {
                            double a = Math.PI * 2 * i / 16;
                            sparks(level, position().add(Math.cos(a) * 2.2, 0.9, Math.sin(a) * 2.2), 1, 0.05);
                        }
                    }
                    if (stateTicks >= SPIN) {
                        finish(45 + random.nextInt(30));
                    }
                }
                case STATE_CAST -> {
                    getNavigation().stop();
                    faceTarget(target);
                    if (stateTicks == 8) {
                        castPillars(level, target);
                    }
                    if (stateTicks >= CAST) {
                        finish(50 + random.nextInt(30));
                    }
                }
                case STATE_SWING -> {
                    getNavigation().stop();
                    faceTarget(target);
                    if (stateTicks == 3) {
                        sweep(2.8, 120, SWING_DAMAGE, 0.4);
                    }
                    if (stateTicks >= SWING) {
                        setState(STATE_IDLE);
                        swingCooldown = 16;
                    }
                }
                default -> approach(level, target, dist);
            }
        }

        private void finish(int pause) {
            setState(STATE_IDLE);
            nextMove = pause;
        }

        private void approach(ServerLevel level, LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextMove--;
            swingCooldown--;
            boolean seen = hasLineOfSight(target);
            if (dist > 3.2 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, dist > 9 ? 1.15 : 1.0);
                }
            } else {
                getNavigation().stop();
                faceTarget(target);
            }
            if (nextMove <= 0 && seen) {
                int crowd = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.2),
                        e -> e != BladeAndroid.this && e.isAlive() && !isAlliedTo(e) && e instanceof net.minecraft.world.entity.player.Player).size();
                if (dist < 3.2 && (crowd >= 2 || random.nextFloat() < 0.4) && spend(COST_SPIN)) {
                    start(STATE_SPIN_WINDUP);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.8F);
                    return;
                }
                if (dist >= 3.5 && dist < 11 && random.nextFloat() < 0.6 && spend(COST_DASH)) {
                    start(STATE_DASH_WINDUP);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
                    return;
                }
                if (dist < 16 && spend(COST_CAST)) {
                    start(STATE_CAST);
                    playSound(SoundEvents.BEACON_ACTIVATE, 0.6F, 2.0F);
                    return;
                }
                nextMove = 20;
            }
            if (active() && dist < 2.6 && swingCooldown <= 0 && seen && spend(COST_SWING)) {
                start(STATE_SWING);
                playSound(SoundEvents.PLAYER_ATTACK_WEAK, 0.9F, 1.4F);
            }
        }

        private void start(int s) {
            if (active()) {
                setState(s);
            }
        }

        private void faceTarget(LivingEntity target) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        /** 相手の足元（少し先読み）と、その周りの2か所に光柱の予告を出す。 */
        private void castPillars(ServerLevel level, LivingEntity target) {
            Vec3 at = target.position().add(target.getDeltaMovement().scale(6));
            AndroidLaser.spawn(level, BladeAndroid.this, at);
            double base = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 2; i++) {
                double a = base + Math.PI * i;
                AndroidLaser.spawn(level, BladeAndroid.this, at.add(Math.cos(a) * 2.4, 0, Math.sin(a) * 2.4));
            }
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
        }
    }
}
