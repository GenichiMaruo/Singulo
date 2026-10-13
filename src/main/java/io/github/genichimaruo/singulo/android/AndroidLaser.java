package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * アンドロイドが床から立てる光の柱。まず床に予告の輪が出て（{@link #WARN} tick、縮みながら光が満ちる）、
 * そのあと {@link #BEAM} tick だけ柱が立つ。柱の中（半径 {@link #RADIUS}、高さ {@link #HEIGHT}）にいると一度だけ傷つく。
 */
public class AndroidLaser extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AndroidLaser.class, EntityDataSerializers.INT);
    public static final int WARN = 22;
    public static final int BEAM = 9;
    public static final double RADIUS = 0.9;
    public static final double HEIGHT = 4.0;
    static final float DAMAGE = 5;

    @Nullable
    private UUID ownerId;
    /** 護衛の技か（出したアンドロイドがいなくなっても、当たる相手をこれで決める）。 */
    private boolean guardSide;
    private final Set<UUID> hit = new HashSet<>();

    public AndroidLaser(EntityType<? extends AndroidLaser> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** at の真下の床に光柱を立てる。 */
    public static void spawn(ServerLevel level, RuinAndroid owner, Vec3 at) {
        AndroidLaser laser = new AndroidLaser(SinguloEntities.ANDROID_LASER.get(), level);
        laser.ownerId = owner.getUUID();
        laser.guardSide = owner.isGuard();
        laser.entityData.set(COLOR, owner.accent());
        BlockPos p = BlockPos.containing(at.x, at.y + 1, at.z);
        for (int i = 0; i < 6 && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty(); i++) {
            p = p.below();
        }
        laser.setPos(at.x, p.getY(), at.z);
        level.addFreshEntity(laser);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount == WARN) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.9F, 2.0F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.7F, 1.6F);
        }
        if (tickCount >= WARN && tickCount < WARN + BEAM) {
            Entity owner = ownerId == null ? null : level.getEntity(ownerId);
            AABB box = new AABB(getX() - RADIUS, getY(), getZ() - RADIUS, getX() + RADIUS, getY() + HEIGHT, getZ() + RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !hit.contains(e.getUUID()))) {
                if (!RuinAndroid.canHit(guardSide, owner, e)) {
                    continue;
                }
                if (e.position().multiply(1, 0, 1).distanceTo(position().multiply(1, 0, 1)) > RADIUS + e.getBbWidth() / 2) {
                    continue;
                }
                hit.add(e.getUUID());
                var source = owner instanceof LivingEntity le ? damageSources().mobAttack(le) : damageSources().magic();
                e.hurt(source, DAMAGE);
            }
        }
        if (tickCount >= WARN + BEAM + 6) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
