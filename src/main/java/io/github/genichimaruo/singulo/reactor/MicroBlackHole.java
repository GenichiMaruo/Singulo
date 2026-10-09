package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ブラックホール爆弾が開く、小さなブラックホール（6秒ほどで蒸発する）。まわりのものを強く引き寄せ、地平線に触れたものを消し、
 * 近くのブロックと液体を次々に飲み込む（シールドの守りの中のブロックは飲み込まない）。最後は閃光と衝撃を出して消える。
 */
public class MicroBlackHole extends Entity {
    public static final int LIFE = 120;
    public static final float MAX_HORIZON = 0.5F;
    static final double PULL_RADIUS = 10;
    static final double EAT_RADIUS = 3.0;
    /** 1 tick に飲み込むブロックの数。 */
    static final int EAT_PER_TICK = 4;

    public MicroBlackHole(EntityType<? extends MicroBlackHole> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** 地平線の半径（生まれてすぐ大きくなり、最後に縮んで消える）。 */
    public float horizon(float partialTick) {
        float t = tickCount + partialTick;
        float grow = Math.min(1F, t / 10F);
        float fade = Math.min(1F, Math.max(0F, (LIFE - t) / 20F));
        return 0.08F + (MAX_HORIZON - 0.08F) * grow * fade;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(2.5);
                level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + d.x, getY() + d.y, getZ() + d.z, -d.x * 0.08, -d.y * 0.08, -d.z * 0.08);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position();
        double horizon = horizon(0);
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(BlockPos.containing(c)).inflate(PULL_RADIUS),
                e -> e != this && EventHorizonTargets.affected(e) && !(e instanceof MicroBlackHole))) {
            if (PenroseReactorBlockEntity.touches(e.getBoundingBox(), c, horizon + 0.15)) {
                EventHorizon.consume(level, e);
                continue;
            }
            Vec3 v = PenroseReactorBlockEntity.pulledVelocity(e.getDeltaMovement(), e.getBoundingBox().getCenter(), c, 3.5, 1.3);
            if (v != null && e.getBoundingBox().getCenter().distanceTo(c) <= PULL_RADIUS) {
                e.setDeltaMovement(v);
                e.hurtMarked = true;
                e.fallDistance = 0;
            }
        }
        // 近くのブロックと液体を、近いものから毎 tick いくつか飲み込む
        if (tickCount >= 3 && tickCount < LIFE - 15 && !ShieldTowerBlockEntity.shielded(level, c, 4)) {
            BlockPos at = BlockPos.containing(c);
            int r = (int) Math.ceil(EAT_RADIUS);
            java.util.List<BlockPos> near = new java.util.ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
                if (Vec3.atCenterOf(p).distanceToSqr(c) > EAT_RADIUS * EAT_RADIUS) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) {
                    continue;
                }
                near.add(p.immutable());
            }
            near.sort(java.util.Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(c)));
            for (int i = 0; i < Math.min(EAT_PER_TICK, near.size()); i++) {
                swallow(level, near.get(i));
            }
        }
        if (tickCount >= LIFE) {
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.2, 0.2, 0.2, 0.35);
            level.explode(this, c.x, c.y, c.z, 2.5F, Level.ExplosionInteraction.NONE);
            discard();
        }
    }

    /** ブロックを飲み込む。液体は消し、水に浸かったブロックは水を抜いてから飲み込む。 */
    private static void swallow(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
            return;
        }
        if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            level.setBlock(p, s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false), 3);
        }
        level.destroyBlock(p, false);
        // 壊した跡に液体が残ったら（流れ込んだものなど）それも消す
        if (!level.getFluidState(p).isEmpty()) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
