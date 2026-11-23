package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 異常場の発生器の場。上の {@value #WIDTH}×{@value #HEIGHT}×{@value #WIDTH} の空間（中心が発生器の真上）にいる生き物に、
 * {@value #INTERVAL} tick ごとに場の効果を与え直す。場を出ると、効果はすぐ切れる（短い時間だけ与えるので）。
 */
public class AnomalyEmitterBlockEntity extends BlockEntity {
    public static final int WIDTH = 5, HEIGHT = 7;
    static final int INTERVAL = 4;
    /** 効果の長さ（tick）。場を出てから切れるまで。 */
    static final int DURATION = 10;
    /** 重い場で毎回加える、下向きの速さ（ブロック/tick）。 */
    static final double HEAVY_PULL = 0.09;

    public AnomalyEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANOMALY_EMITTER.get(), pos, state);
    }

    public AABB area() {
        double r = WIDTH / 2.0;
        return new AABB(worldPosition.getX() + 0.5 - r, worldPosition.getY() + 1, worldPosition.getZ() + 0.5 - r,
                worldPosition.getX() + 0.5 + r, worldPosition.getY() + 1 + HEIGHT, worldPosition.getZ() + 0.5 + r);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnomalyEmitterBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0 || !state.hasProperty(AnomalyEmitterBlock.FIELD)) {
            return;
        }
        AnomalyEmitterBlock.Field field = state.getValue(AnomalyEmitterBlock.FIELD);
        ServerLevel server = (ServerLevel) level;
        AABB area = be.area();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
            apply(e, field);
        }
        if (level.getGameTime() % 12 == 0) {
            be.particles(server, field, area);
        }
    }

    /** 場の効果を1回ぶん与える。 */
    public static void apply(LivingEntity e, AnomalyEmitterBlock.Field field) {
        if (field.gravity() && (e.getType().is(SinguloTags.GRAVITY_IMMUNE) || e instanceof Player p && p.getAbilities().flying)) {
            return;
        }
        switch (field) {
            case LIFT -> e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, DURATION, 1, true, false, true));
            case HEAVY -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 1, true, false, true));
                if (!e.onGround()) {
                    Vec3 v = e.getDeltaMovement();
                    e.setDeltaMovement(v.x, Math.min(v.y, 0.2) - HEAVY_PULL, v.z);
                    e.hurtMarked = true;
                }
            }
            case SLOW -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 2, true, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, DURATION, 1, true, false, true));
            }
            case FAST -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1, true, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, DURATION, 0, true, false, true));
            }
        }
    }

    private void particles(ServerLevel level, AnomalyEmitterBlock.Field field, AABB area) {
        Vec3 c = area.getCenter();
        double w = WIDTH / 2.0 - 0.3;
        switch (field) {
            case LIFT -> level.sendParticles(ParticleTypes.END_ROD, c.x, area.minY + 0.3, c.z, 4, w, 0.2, w, 0.06);
            case HEAVY -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, area.maxY - 0.5, c.z, 6, w, 0.5, w, 0.02);
            case SLOW -> level.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y, c.z, 4, w, HEIGHT / 3.0, w, 0.0);
            case FAST -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 5, w, HEIGHT / 3.0, w, 0.1);
        }
    }
}
