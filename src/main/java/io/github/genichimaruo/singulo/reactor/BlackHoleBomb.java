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

/**
 * 投げたブラックホール爆弾。当たった所で小さなブラックホールを開く。
 * ホライズン・ウォーデンに打ち返された爆弾は、そのウォーデンの近く（BOMB_SAFE_DISTANCE 以内）に落ちると開かずに消える。
 */
public class BlackHoleBomb extends ThrowableItemProjectile {
    /** 打ち返したウォーデン。 */
    @javax.annotation.Nullable
    private java.util.UUID deflector;

    public BlackHoleBomb(EntityType<? extends BlackHoleBomb> type, Level level) {
        super(type, level);
    }

    public BlackHoleBomb(Level level, LivingEntity owner) {
        super(SinguloEntities.BLACK_HOLE_BOMB.get(), owner, level);
    }

    /** ウォーデンが打ち返した印をつける（同じウォーデンはもう打ち返さない）。 */
    public void markDeflected(net.minecraft.world.entity.Entity by) {
        deflector = by.getUUID();
    }

    public boolean deflectedBy(net.minecraft.world.entity.Entity by) {
        return by.getUUID().equals(deflector);
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
        if (deflector != null && level() instanceof net.minecraft.server.level.ServerLevel server
                && server.getEntity(deflector) instanceof io.github.genichimaruo.singulo.ruin.HorizonWarden warden
                && warden.distanceToSqr(at) < io.github.genichimaruo.singulo.ruin.HorizonWarden.BOMB_SAFE_DISTANCE
                * io.github.genichimaruo.singulo.ruin.HorizonWarden.BOMB_SAFE_DISTANCE) {
            // ウォーデンの近くでは開かせない（打ち返しが壁に当たった場合など）。光って消える
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 40, 0.4, 0.4, 0.4, 0.3);
            server.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level().playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 1.4F);
            discard();
            return;
        }
        MicroBlackHole hole = new MicroBlackHole(SinguloEntities.MICRO_BLACK_HOLE.get(), level());
        hole.setPos(at);
        level().addFreshEntity(hole);
        level().playSound(null, at.x, at.y, at.z, io.github.genichimaruo.singulo.registry.SinguloSounds.BLACK_HOLE_FORMATION.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.4F);
        discard();
    }
}
