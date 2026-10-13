package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 光弾（ホライズン・ウォーデンと警備ドローンが撃つ）。重力を受けずにまっすぐ飛び、当たった相手に魔法ダメージを与える。
 * 追いかける光弾は、狙った相手へゆるく曲がる。見た目は光る球（色と大きさは撃った側が決める、専用の描画）。
 */
public class HorizonBolt extends ThrowableItemProjectile {
    /** ウォーデンの光弾のダメージ。 */
    public static final float DAMAGE = 10.0F;
    static final int LIFETIME = 60;
    /** 追いかけるときに、1 tick で向きを変えられる割合。 */
    static final double HOMING_TURN = 0.08;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(HorizonBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(HorizonBolt.class, EntityDataSerializers.FLOAT);

    @Nullable
    private Entity homingTarget;
    private float damage = DAMAGE;
    /** 防具と盾を無視する光弾か（ウォーデンのフェーズ2から）。 */
    private boolean piercing;
    private boolean armored;

    public HorizonBolt(EntityType<? extends HorizonBolt> type, Level level) {
        super(type, level);
    }

    public HorizonBolt(Level level, LivingEntity shooter) {
        super(SinguloEntities.HORIZON_BOLT.get(), shooter, level);
    }

    /** 相手を追いかける光弾にする。 */
    public HorizonBolt homing(Entity target) {
        this.homingTarget = target;
        return this;
    }

    /** 防具と盾を無視する光弾にする。 */
    /** 防具で減る弾にする（普通の飛び道具のダメージ）。既定は魔法（防具を貫く）。 */
    public HorizonBolt armored(boolean on) {
        this.armored = on;
        return this;
    }

    public HorizonBolt piercing(boolean on) {
        this.piercing = on;
        return this;
    }

    /** 色（0xRRGGBB）・大きさ（1 が標準）・ダメージを決める。 */
    /** 当たったときのダメージだけを変える。 */
    public HorizonBolt power(float damage) {
        this.damage = damage;
        return this;
    }

    public HorizonBolt style(int rgb, float size, float damage) {
        entityData.set(COLOR, rgb);
        entityData.set(SIZE, size);
        this.damage = damage;
        return this;
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(COLOR, 0x78D2F0);
        entityData.define(SIZE, 1.0F);
    }

    @Override
    protected Item getDefaultItem() {
        return BuiltInRegistries.ITEM.get(Singulo.id("jet_condensate"));
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && homingTarget != null && homingTarget.isAlive()) {
            Vec3 v = getDeltaMovement();
            double speed = v.length();
            Vec3 want = homingTarget.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed);
            setDeltaMovement(v.lerp(want, HOMING_TURN).normalize().scale(speed));
            hasImpulse = true;
        }
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
        if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** ダメージと当たり方（防具を貫くか・防具で減るか）を保存する。読み込み直しても同じ強さで当たる。 */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putBoolean("armored", armored);
        tag.putBoolean("piercing", piercing);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 0.5.0 より前に保存された光弾は、ダメージを持たない（既定の値のまま）
        if (tag.contains("damage")) {
            damage = tag.getFloat("damage");
        }
        armored = tag.getBoolean("armored");
        piercing = tag.getBoolean("piercing");
    }

    public float damage() {
        return damage;
    }

    public boolean armored() {
        return armored;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hit = result.getEntity();
        if (hit != getOwner() && !RuinGuards.isGuard(hit)) {
            hit.hurt(piercing ? io.github.genichimaruo.singulo.registry.SinguloDamageTypes.tidal(level(), getOwner())
                    : armored ? damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null)
                    : damageSources().indirectMagic(this, getOwner()), damage);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof HorizonBolt) && !(target instanceof WardenSingularity)
                && !(target instanceof SecurityDrone) && !(target instanceof HorizonWarden) && !(target instanceof RuinBoss);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            }
            discard();
        }
    }
}
