package io.github.genichimaruo.singulo.hazard;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ストレンジレット（段階4〜の危険）。毎秒1個ずつ、周りのブロックをストレンジ物質に変えていく。
 * 広がるのは生まれた場所から strangeletMaxRadius（既定8）以内、合計 strangeletMaxBlocks（既定512）個まで。
 * 空気・液体・壊せないブロック・イベントホライズン・シールドの範囲には広がらない。止まると自分もストレンジ物質になる。
 * 磁気瓶で封じ込めて回収できる。
 */
public class StrangeletBlockEntity extends BlockEntity {
    static final int INTERVAL = 20;
    static final int TRIES = 8;

    private int converted;
    private int idle;

    public StrangeletBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.STRANGELET.get(), pos, state);
    }

    public int converted() {
        return converted;
    }

    public static int maxBlocks() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.STRANGELET_MAX_BLOCKS.get() : 512;
    }

    public static int maxRadius() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.STRANGELET_MAX_RADIUS.get() : 8;
    }

    public static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.STRANGELET_ENABLED.get();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StrangeletBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        if (!enabled() || be.converted >= maxBlocks()) {
            be.decay(server);
            return;
        }
        if (!be.spread(server)) {
            // 広がる先がない状態が続いたら止まる
            if (++be.idle >= 30) {
                be.decay(server);
            }
        } else {
            be.idle = 0;
        }
    }

    /** 1個変える。変えられたら true。 */
    public boolean spread(ServerLevel level) {
        int r = Math.min(maxRadius(), 1 + converted / 6);
        for (int i = 0; i < TRIES; i++) {
            BlockPos p = worldPosition.offset(level.random.nextInt(2 * r + 1) - r, level.random.nextInt(2 * r + 1) - r,
                    level.random.nextInt(2 * r + 1) - r);
            if (p.equals(worldPosition) || worldPosition.distSqr(p) > (double) r * r || !canConvert(level, p)) {
                continue;
            }
            level.setBlock(p, SinguloBlocks.SIMPLE.get("strange_matter").get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, p, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 0.8F, 0.6F);
            converted++;
            setChanged();
            return true;
        }
        return false;
    }

    static boolean canConvert(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null
                || s.is(SinguloBlocks.SIMPLE.get("strange_matter").get()) || s.is(SinguloBlocks.STRANGELET.get())) {
            return false;
        }
        return !ShieldTowerBlockEntity.shielded(level, Vec3.atCenterOf(p), 4);
    }

    /** 力を使い果たして、自分もストレンジ物質になる。 */
    private void decay(ServerLevel level) {
        level.setBlock(worldPosition, SinguloBlocks.SIMPLE.get("strange_matter").get().defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("converted", converted);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        converted = tag.getInt("converted");
    }
}
