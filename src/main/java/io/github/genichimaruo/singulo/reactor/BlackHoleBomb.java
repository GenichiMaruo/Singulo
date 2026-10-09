package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 投げたブラックホール爆弾。当たった所で小さなブラックホールを開く。 */
public class BlackHoleBomb extends ThrowableItemProjectile {
    public BlackHoleBomb(EntityType<? extends BlackHoleBomb> type, Level level) {
        super(type, level);
    }

    public BlackHoleBomb(Level level, LivingEntity owner) {
        super(SinguloEntities.BLACK_HOLE_BOMB.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return SinguloItems.BLACK_HOLE_BOMB.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) {
            return;
        }
        Vec3 at = result.getLocation();
        if (result instanceof BlockHitResult block) {
            // ブロックに当たったら、面から少し離れた所に開く
            at = at.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(0.6));
        }
        MicroBlackHole hole = new MicroBlackHole(SinguloEntities.MICRO_BLACK_HOLE.get(), level());
        hole.setPos(at);
        level().addFreshEntity(hole);
        level().playSound(null, at.x, at.y, at.z, io.github.genichimaruo.singulo.registry.SinguloSounds.BLACK_HOLE_FORMATION.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.4F);
        discard();
    }
}
