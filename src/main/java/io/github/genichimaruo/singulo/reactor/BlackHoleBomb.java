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
    /** 捕らえて吸い込もうとしている最後の記録者（いる間は開かない）。 */
    @javax.annotation.Nullable
    private java.util.UUID captor;

    /** 最後の記録者に捕らえられる（着弾せず、記録者の頭の特異点へ吸い込まれていく）。 */
    public void capture(net.minecraft.world.entity.Entity by) {
        captor = by.getUUID();
        setNoGravity(true);
    }

    public boolean captured() {
        return captor != null;
    }

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
        if (captor != null && level() instanceof net.minecraft.server.level.ServerLevel server) {
            // 渦を巻きながら記録者の頭へ吸い込まれていく（ぶつかっても開かない）
            if (!(server.getEntity(captor) instanceof io.github.genichimaruo.singulo.ruin.LastArchivist a) || !a.isAlive()) {
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 30, 0.3, 0.3, 0.3, 0.3);
                discard();
                return;
            }
            Vec3 head = a.position().add(0, io.github.genichimaruo.singulo.ruin.LastArchivist.HEAD_Y, 0);
            Vec3 to = head.subtract(position());
            double d = to.length();
            if (d < 1.0) {
                a.absorbBomb(server, this);
                discard();
                return;
            }
            Vec3 dir = to.scale(1 / d);
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            double speed = Math.min(0.9, 0.25 + tickCount * 0.02);
            setDeltaMovement(dir.scale(speed).add(side.scale(Math.min(0.5, d * 0.06))));
            setPos(position().add(getDeltaMovement()));
            hasImpulse = true;
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.05);
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            return;
        }
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (captor != null) {
            return;                                     // 捕らえられている間は、何に当たっても開かない
        }
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
