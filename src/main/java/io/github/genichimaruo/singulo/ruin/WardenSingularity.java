package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ホライズン・ウォーデン（フェーズ3）が展開する小型の特異点。黒い球と重力レンズで見える。
 * LIFETIME のあいだ、半径 RADIUS の中のものを中心へ引き寄せ、10 tick ごとに防具を無視する潮汐ダメージを与える。
 */
public class WardenSingularity extends Entity {
    public static final int LIFETIME = 100;
    public static final double RADIUS = 3.5;
    public static final float DAMAGE = 5.0F;
    /** 育つ・しぼむのにかける tick。 */
    public static final int GROW = 10;

    @Nullable
    private Entity owner;

    public WardenSingularity(EntityType<? extends WardenSingularity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public WardenSingularity(Level level, Entity owner, Vec3 at) {
        this(SinguloEntities.WARDEN_SINGULARITY.get(), level);
        this.owner = owner;
        setPos(at);
    }

    /** 見かけの大きさ（0〜1）。出てくるときに育ち、消える前にしぼむ。 */
    public float size(float partialTick) {
        float t = tickCount + partialTick;
        return Math.max(0, Math.min(1, Math.min(t / GROW, (LIFETIME - t) / GROW)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount >= LIFETIME) {
            discard();
            return;
        }
        Vec3 c = position();
        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, RADIUS / 2, 0.6, RADIUS / 2, 0.02);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIUS),
                e -> e != owner && e.isAlive() && !RuinGuards.isGuard(e))) {
            Vec3 toCenter = c.subtract(e.position());
            if (toCenter.lengthSqr() > RADIUS * RADIUS) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().add(toCenter.normalize().scale(0.08)));
            e.hurtMarked = true;
            if (tickCount % 10 == 0) {
                e.hurt(SinguloDamageTypes.tidal(level, owner), DAMAGE);
            }
        }
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
