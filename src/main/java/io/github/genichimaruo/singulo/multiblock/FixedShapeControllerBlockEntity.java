package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 大きさの決まったマルチブロック（縮退圧縮炉・C空洞）のコントローラ。形は40 tick ごとに確かめる。 */
public abstract class FixedShapeControllerBlockEntity extends MachineBlockEntity {
    static final int CHECK_INTERVAL = 40;

    private int size;
    private final PortLinks ports = new PortLinks();
    private boolean firstCheck = true;
    private long nextCheck;

    protected FixedShapeControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract Blueprints.Kind kind();

    @Override
    protected boolean canOperate(Level level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            Shapes.Found found = Shapes.find(kind(), level, worldPosition);
            int newSize = found == null ? 0 : found.size();
            ports.update(level, worldPosition, found == null ? List.of() : found.ports());
            if (newSize != size) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    FormationEffect.onChange(server, worldPosition, size > 0, newSize > 0, firstCheck, 4, 1, 5);
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

    @Override
    public void onChunkUnloaded() {
        ports.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        ports.onRemoved(level);
        super.setRemoved();
    }

    /** 縮退圧縮炉（5×5×5）。 */
    public static class Compactor extends FixedShapeControllerBlockEntity {
        public Compactor(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.DEGENERATE_COMPACTOR_CONTROLLER.get(), pos, state);
        }

        @Override
        protected Blueprints.Kind kind() {
            return Blueprints.Kind.DEGENERATE_COMPACTOR;
        }
    }

    /** C空洞（5×5×5）。 */
    public static class Cavity extends FixedShapeControllerBlockEntity {
        public Cavity(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.CASIMIR_CAVITY_CONTROLLER.get(), pos, state);
        }

        @Override
        protected Blueprints.Kind kind() {
            return Blueprints.Kind.CASIMIR_CAVITY;
        }
    }
}
