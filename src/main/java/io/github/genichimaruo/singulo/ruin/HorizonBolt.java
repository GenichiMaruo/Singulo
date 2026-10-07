package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** ホライズン・ウォーデンの光弾。重力を受けずにまっすぐ飛び、当たった相手に魔法ダメージを与える。 */
public class HorizonBolt extends ThrowableItemProjectile {
    public static final float DAMAGE = 8.0F;
    static final int LIFETIME = 60;

    public HorizonBolt(EntityType<? extends HorizonBolt> type, Level level) {
        super(type, level);
    }

    public HorizonBolt(Level level, LivingEntity shooter) {
        super(SinguloEntities.HORIZON_BOLT.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return BuiltInRegistries.ITEM.get(Singulo.id("jet_condensate"));
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (result.getEntity() != getOwner()) {
            result.getEntity().hurt(damageSources().indirectMagic(this, getOwner()), DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }
}
