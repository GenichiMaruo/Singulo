package io.github.genichimaruo.singulo.ruin;

import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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

/**
 * 白い警備ドローン。遺構の警備機ドックからだけ出撃し、近づいたプレイヤーに体当たりする。
 * 強さは3段階（地表観測拠点=1、研究棟=2、封鎖培養施設と最終実験施設=3）。何も落とさないので、トラップで稼げない。
 */
public class SecurityDrone extends Monster {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SecurityDrone.class, EntityDataSerializers.INT);
    static final double[] HEALTH = {10, 24, 40};
    static final double[] DAMAGE = {3, 5, 8};

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
    }

    public int tier() {
        return entityData.get(TIER);
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
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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

    /** 目標へまっすぐ飛び、触れたら体当たりする。 */
    private final class ChargeGoal extends Goal {
        private int cooldown;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getMoveControl().setWantedPosition(target.getX(), target.getEyeY() - 0.4, target.getZ(), 1.2);
            getLookControl().setLookAt(target, 30, 30);
            if (cooldown > 0) {
                cooldown--;
            } else if (distanceToSqr(target) < 2.5) {
                doHurtTarget(target);
                cooldown = 20;
            }
        }
    }
}
