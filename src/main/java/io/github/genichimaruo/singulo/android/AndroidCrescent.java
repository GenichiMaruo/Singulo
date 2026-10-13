package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 光剣型の斬撃波。三日月の光が {@link #RANGE} ブロックまでまっすぐ飛び、通った相手を一度ずつ斬る。壁に当たると消える。
 */
public class AndroidCrescent extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AndroidCrescent.class, EntityDataSerializers.INT);
    public static final double SPEED = 0.9;
    public static final double RANGE = 12;
    public static final double HALF_WIDTH = 1.2;
    static final float DAMAGE = 5;

    @Nullable
    private UUID ownerId;
    /** 護衛の技か（出したアンドロイドがいなくなっても、当たる相手をこれで決める）。 */
    private boolean guardSide;
    private final Set<UUID> hit = new HashSet<>();

    public AndroidCrescent(EntityType<? extends AndroidCrescent> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void shoot(ServerLevel level, RuinAndroid owner, Vec3 dir) {
        AndroidCrescent c = new AndroidCrescent(SinguloEntities.ANDROID_CRESCENT.get(), level);
        c.ownerId = owner.getUUID();
        c.guardSide = owner.isGuard();
        c.entityData.set(COLOR, owner.accent());
        Vec3 from = owner.position().add(0, 1.0, 0).add(dir.multiply(1, 0, 1).normalize().scale(0.6));
        c.setPos(from);
        c.setDeltaMovement(dir.scale(SPEED));
        c.setYRot((float) (Math.atan2(-dir.x, dir.z) * 180 / Math.PI));
        c.setXRot((float) (-Math.asin(dir.y) * 180 / Math.PI));
        level.addFreshEntity(c);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        if (!level().isClientSide && level() instanceof ServerLevel level) {
            if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                    != HitResult.Type.MISS || tickCount * SPEED > RANGE) {
                discard();
                return;
            }
            Entity owner = ownerId == null ? null : level.getEntity(ownerId);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(getDeltaMovement())
                    .inflate(HALF_WIDTH, 0.4, HALF_WIDTH), e -> !hit.contains(e.getUUID()))) {
                if (!RuinAndroid.canHit(guardSide, owner, e)) {
                    continue;
                }
                hit.add(e.getUUID());
                var source = owner instanceof LivingEntity le ? damageSources().mobAttack(le) : damageSources().magic();
                e.hurt(source, DAMAGE);
            }
        }
        setPos(to);
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
