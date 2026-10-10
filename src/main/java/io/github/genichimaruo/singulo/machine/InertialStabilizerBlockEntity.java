package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;

/**
 * 慣性スタビライザー（ティア3）。半径16ブロック内の爆発でブロックが壊れないようにする（防爆のみ。
 * モブやエンティティへのダメージは防がない）。境界を張るフィールドなので、電力は半径の二乗に比例する。
 */
public class InertialStabilizerBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 3;
    public static final int RADIUS = 16;
    /** 電力 = FE_PER_RADIUS_SQ × 半径²（半径16で 512 FE/t）。 */
    public static final int FE_PER_RADIUS_SQ = 2;

    /** 稼働中のスタビライザー（ワールドごと）。 */
    private static final Map<Level, Set<InertialStabilizerBlockEntity>> ACTIVE = new WeakHashMap<>();

    private boolean active;

    public InertialStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.INERTIAL_STABILIZER.get(), pos, state, 100_000, 10_000, 0);
    }

    @Override
    public Kind kind() {
        return Kind.INERTIAL_STABILIZER;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return FE_PER_RADIUS_SQ * RADIUS * RADIUS;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean nowActive) {
        if (nowActive == active) {
            return;
        }
        active = nowActive;
        Set<InertialStabilizerBlockEntity> set = ACTIVE.computeIfAbsent(level,
                l -> Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        if (active) {
            set.add(this);
        } else {
            set.remove(this);
        }
    }

    @Override
    protected int displayValue() {
        return RADIUS;
    }

    public boolean protects(BlockPos pos) {
        return active && !isRemoved() && worldPosition.distSqr(pos) <= (double) RADIUS * RADIUS;
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            Set<InertialStabilizerBlockEntity> set = ACTIVE.get(level);
            if (set != null) {
                set.remove(this);
            }
        }
        active = false;
        super.setRemoved();
    }

    /** 爆発で壊れるブロックの一覧から、守られている範囲のものを外す。 */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Set<InertialStabilizerBlockEntity> set = ACTIVE.get(event.getLevel());
        if (set == null || set.isEmpty()) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> {
            for (InertialStabilizerBlockEntity s : set) {
                if (s.protects(pos)) {
                    return true;
                }
            }
            return false;
        });
    }
}
