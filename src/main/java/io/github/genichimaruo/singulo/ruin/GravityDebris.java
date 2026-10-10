package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 重力の澱が殻から撃ち出す瓦礫（ブロックの塊）。当たると砕け、当たった相手を傷つける。 */
public class GravityDebris extends ThrowableProjectile {
    private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(GravityDebris.class,
            EntityDataSerializers.BLOCK_STATE);
    public static final float DAMAGE = 8;
    static final int LIFETIME = 80;

    public GravityDebris(EntityType<? extends GravityDebris> type, Level level) {
        super(type, level);
    }

    public GravityDebris(Level level, LivingEntity shooter, BlockState block) {
        super(SinguloEntities.GRAVITY_DEBRIS.get(), shooter, level);
        entityData.set(BLOCK, block);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
    }

    public BlockState block() {
        return entityData.get(BLOCK);
    }

    @Override
    protected float getGravity() {
        return 0.01F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() instanceof LivingEntity target && !RuinGuards.isGuard(target)) {
            target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), getX(), getY(), getZ(), 24, 0.3, 0.3, 0.3, 0.15);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_debris.impact"), 1.5F, 1.0F);
            discard();
        }
    }
}
