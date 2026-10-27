package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * リアクターの炉心に崩されて吸い込まれていくブロック（見た目だけ。当たり判定も落とし物もない）。
 * 渦を巻きながら速さを増して中心へ落ち、地平線に触れると消える。描画では、中心に近いほど引き伸ばされて細くなる。
 * 保存しない（読み込み直したら消えている）。
 */
public class InfallingBlock extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(InfallingBlock.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Vector3f> TARGET = SynchedEntityData.defineId(InfallingBlock.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> START = SynchedEntityData.defineId(InfallingBlock.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HORIZON = SynchedEntityData.defineId(InfallingBlock.class, EntityDataSerializers.FLOAT);
    /** 吸い込まれきるまでの上限（これを過ぎたら消す）。 */
    static final int LIFETIME = 120;

    public InfallingBlock(EntityType<? extends InfallingBlock> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public InfallingBlock(Level level, Vec3 at, BlockState block, Vec3 target, double horizon) {
        this(SinguloEntities.INFALLING_BLOCK.get(), level);
        setPos(at.x, at.y, at.z);
        entityData.set(BLOCK, block);
        entityData.set(TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        entityData.set(START, (float) Math.max(0.5, at.distanceTo(target)));
        entityData.set(HORIZON, (float) horizon);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK, Blocks.STONE.defaultBlockState());
        builder.define(TARGET, new Vector3f());
        builder.define(START, 1F);
        builder.define(HORIZON, 0.6F);
    }

    public BlockState block() {
        return entityData.get(BLOCK);
    }

    public Vec3 target() {
        Vector3f t = entityData.get(TARGET);
        return new Vec3(t.x, t.y, t.z);
    }

    /** 吸い込まれた割合（0: 崩れたばかり、1: 地平線に届いた）。描画の伸び縮みに使う。 */
    public float fall(float partialTick) {
        Vec3 p = getPosition(partialTick);
        float horizon = entityData.get(HORIZON);
        float start = entityData.get(START);
        return (float) Math.max(0, Math.min(1, 1 - (p.distanceTo(target()) - horizon) / Math.max(0.1, start - horizon)));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 c = target();
        Vec3 to = c.subtract(position());
        double r = to.length();
        if (level() instanceof ServerLevel level) {
            if (r <= entityData.get(HORIZON) + 0.1 || tickCount > LIFETIME) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                discard();
                return;
            }
        }
        if (r < 1e-3) {
            return;
        }
        // 中心へ速さを増しながら落ち、横へ渦を巻く（近いほど速く回る）
        double speed = Math.min(r, 0.05 + 0.012 * tickCount + 0.25 / Math.max(0.6, r));
        Vec3 radial = to.scale(1 / r);
        Vec3 swirl = new Vec3(-radial.z, 0, radial.x);
        if (swirl.lengthSqr() < 1e-4) {
            swirl = new Vec3(1, 0, 0);
        }
        Vec3 step = radial.scale(speed).add(swirl.normalize().scale(speed * 0.6));
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
