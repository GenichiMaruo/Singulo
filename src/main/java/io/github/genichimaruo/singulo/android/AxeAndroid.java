package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 斧型アンドロイド「アルマ」。赤銅の髪に朱色の差し色。落ち着いた力持ちで、光る刃の大きな両手斧で戦う。
 * 体力と装甲が高く、足は少し遅い。
 *
 * <ul>
 * <li>薙ぎ払い: 斧を右へ大きく引いてから、前方の広い扇を振り抜く（押し返しが強い）。</li>
 * <li>叩きつけ: 斧を頭上へ振り上げ、床へ叩きつける。前へまっすぐ衝撃波が走り、床の上の相手を打ち上げる。</li>
 * <li>踏み込み斬り: 低く構えてから相手の前へ跳びかかり、着地でまわりを叩く。</li>
 * <li>近くでは短い斬り下ろし。</li>
 * </ul>
 */
public class AxeAndroid extends RuinAndroid {
    public static final int ACCENT = 0xFF6E40;

    public static final int STATE_CLEAVE_WINDUP = 40;
    public static final int STATE_CLEAVE = 41;
    public static final int STATE_SLAM_WINDUP = 42;
    public static final int STATE_SLAM = 43;
    public static final int STATE_LEAP_WINDUP = 44;
    public static final int STATE_LEAP = 45;
    public static final int STATE_CHOP = 46;

    public static final int CLEAVE_WINDUP = 11;
    public static final int CLEAVE = 9;
    public static final int SLAM_WINDUP = 15;
    public static final int SLAM = 12;
    public static final int LEAP_WINDUP = 10;
    /** 跳んでから、着地しないまま待つ長さの上限。 */
    public static final int LEAP_MAX = 30;
    public static final int CHOP = 8;

    public static final double CLEAVE_REACH = 3.2;
    public static final double SHOCKWAVE_LENGTH = 7;
    public static final double LEAP_RADIUS = 2.6;

    static final float CLEAVE_DAMAGE = 8;
    static final float SHOCKWAVE_DAMAGE = 7;
    static final float LEAP_DAMAGE = 7;
    static final float CHOP_DAMAGE = 5;

    static final int COST_CLEAVE = 260;
    static final int COST_SLAM = 300;
    static final int COST_LEAP = 280;
    static final int COST_CHOP = 70;

    public AxeAndroid(EntityType<? extends AxeAndroid> type, Level level) {
        super(type, level);
    }

    /** ほかの型より硬く、重い。 */
    public static AttributeSupplier.Builder createAttributes() {
        return RuinAndroid.createAttributes()
                .add(Attributes.MAX_HEALTH, 42)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.AXE;
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    static boolean combat(int s) {
        return s >= STATE_CLEAVE_WINDUP && s <= STATE_CHOP;
    }

    /** 叩きつけの衝撃波: 体の前へまっすぐ length ブロック、幅 1.2 の帯の中の相手に当て、打ち上げる。当たった数を返す。 */
    public int shockwave(ServerLevel level) {
        Vec3 dir = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 from = position();
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= SHOCKWAVE_LENGTH; i++) {
            Vec3 at = from.add(dir.scale(i));
            BlockState ground = level.getBlockState(net.minecraft.core.BlockPos.containing(at).below());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 8, 0.4, 0.05, 0.4, 0.1);
            }
            sparks(level, at.add(0, 0.2, 0), 3, 0.3);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.2, 1.0, 1.2), this::canHit)) {
                if (e.onGround() && hit.add(e) && e.hurt(damageSources().mobAttack(this), SHOCKWAVE_DAMAGE)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.3, 0.6, dir.z * 0.3));
                    e.hurtMarked = true;
                }
            }
        }
        return hit.size();
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 30;
        private int chopCooldown;
        private Vec3 leap = Vec3.ZERO;

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
            return canUse() || combat(state()) && active();
        }

        @Override
        public void stop() {
            if (active() && combat(state())) {
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
                if (combat(s) && stateTicks > 20) {
                    setState(STATE_IDLE);
                }
                return;
            }
            double dist = distanceTo(target);
            switch (s) {
                case STATE_CLEAVE_WINDUP -> {
                    hold(target);
                    if (stateTicks % 3 == 0) {
                        sparks(level, position().add(0, 1.0, 0), 2, 0.3);
                    }
                    if (stateTicks >= CLEAVE_WINDUP) {
                        setState(STATE_CLEAVE);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
                    }
                }
                case STATE_CLEAVE -> {
                    getNavigation().stop();
                    if (stateTicks == 2) {
                        sweep(CLEAVE_REACH, 200, CLEAVE_DAMAGE, 1.1);
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.0, getZ(), 3, 1.2, 0.1, 1.2, 0);
                    }
                    if (stateTicks >= CLEAVE) {
                        finish(45 + random.nextInt(25));
                    }
                }
                case STATE_SLAM_WINDUP -> {
                    hold(target);
                    if (stateTicks % 2 == 0) {
                        sparks(level, position().add(0, 2.4, 0), 3, 0.25);
                    }
                    if (stateTicks >= SLAM_WINDUP) {
                        setState(STATE_SLAM);
                    }
                }
                case STATE_SLAM -> {
                    getNavigation().stop();
                    if (stateTicks == 3) {
                        playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
                        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
                        shockwave(level);
                    }
                    if (stateTicks >= SLAM) {
                        finish(55 + random.nextInt(25));
                    }
                }
                case STATE_LEAP_WINDUP -> {
                    hold(target);
                    if (stateTicks >= LEAP_WINDUP) {
                        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
                        double d = to.length();
                        leap = d > 0.01 ? to.scale(1 / d) : Vec3.directionFromRotation(0, yBodyRot);
                        double speed = Mth.clamp(d * 0.11, 0.45, 1.05);
                        setDeltaMovement(leap.x * speed, 0.62, leap.z * speed);
                        hasImpulse = true;
                        setState(STATE_LEAP);
                        playSound(SoundEvents.RAVAGER_ROAR, 0.5F, 1.8F);
                    }
                }
                case STATE_LEAP -> {
                    getNavigation().stop();
                    if (stateTicks > 3 && (onGround() || stateTicks >= LEAP_MAX)) {
                        sweep(LEAP_RADIUS, 360, LEAP_DAMAGE, 0.7);
                        playSound(SoundEvents.ANVIL_LAND, 0.9F, 0.8F);
                        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
                        sparks(level, position().add(0, 0.2, 0), 20, 1.0);
                        finish(45 + random.nextInt(25));
                    }
                }
                case STATE_CHOP -> {
                    hold(target);
                    if (stateTicks == 4) {
                        sweep(2.7, 110, CHOP_DAMAGE, 0.5);
                        playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 0.8F);
                    }
                    if (stateTicks >= CHOP) {
                        setState(STATE_IDLE);
                        chopCooldown = 18;
                    }
                }
                default -> approach(target, dist);
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

        private void approach(LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextMove--;
            chopCooldown--;
            boolean seen = hasLineOfSight(target);
            if (dist > 2.6 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else {
                getNavigation().stop();
            }
            if (nextMove <= 0 && seen) {
                float r = random.nextFloat();
                if (dist < CLEAVE_REACH && r < 0.5 && spend(COST_CLEAVE)) {
                    start(STATE_CLEAVE_WINDUP, SoundEvents.BEACON_POWER_SELECT, 0.8F);
                    return;
                }
                if (dist < SHOCKWAVE_LENGTH && r < 0.8 && spend(COST_SLAM)) {
                    start(STATE_SLAM_WINDUP, SoundEvents.BEACON_ACTIVATE, 0.7F);
                    return;
                }
                if (dist >= 4 && dist < 10 && onGround() && spend(COST_LEAP)) {
                    start(STATE_LEAP_WINDUP, SoundEvents.BEACON_POWER_SELECT, 0.6F);
                    return;
                }
                nextMove = 20;
            }
            if (active() && dist < 2.6 && chopCooldown <= 0 && seen && spend(COST_CHOP)) {
                start(STATE_CHOP, SoundEvents.PLAYER_ATTACK_WEAK, 0.8F);
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
