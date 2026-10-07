package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 大きさの決まったマルチブロック（縮退圧縮炉・カシミール空洞）のコントローラ。形は40 tick ごとに確かめる。 */
public abstract class FixedShapeControllerBlockEntity extends MachineBlockEntity {
    static final int CHECK_INTERVAL = 40;

    private int size;
    private boolean firstCheck = true;
    private long nextCheck;

    protected FixedShapeControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 形を調べ、形成できていれば大きさ、できていなければ 0 を返す。 */
    protected abstract int checkShape(Level level);

    @Override
    protected boolean canOperate(Level level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            int newSize = checkShape(level);
            if (newSize != size) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    FormationEffect.onChange(server, worldPosition, size > 0, newSize > 0, firstCheck, 5, 5, 5);
                }
                size = newSize;
                markDirty();
            }
            firstCheck = false;
        }
        return size > 0;
    }

    @Override
    public int structureSize() {
        return size;
    }

    /** 縮退圧縮炉（3×3×3）。 */
    public static class Compactor extends FixedShapeControllerBlockEntity {
        public Compactor(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.DEGENERATE_COMPACTOR_CONTROLLER.get(), pos, state);
        }

        @Override
        protected int checkShape(Level level) {
            return Structures.findCompactor(level, worldPosition, getBlockState().getBlock());
        }
    }

    /** カシミール空洞（5×5×5）。 */
    public static class Cavity extends FixedShapeControllerBlockEntity {
        public Cavity(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.CASIMIR_CAVITY_CONTROLLER.get(), pos, state);
        }

        @Override
        protected int checkShape(Level level) {
            return Structures.findCavity(level, worldPosition, getBlockState().getBlock());
        }
    }
}
