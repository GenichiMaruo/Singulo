package io.github.genichimaruo.singulo.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Compatibility boundary retaining Singulo's saved field layout, not a second save format. */
public abstract class LegacyBlockEntity extends BlockEntity {
    protected LegacyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override protected final void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadAdditional(ValueIoCompat.read(input), input.lookup());
    }
    @Override protected final void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueIoCompat.write(output, this::saveAdditional);
    }
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {}
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {}

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && state.getBlock() instanceof RemovalAwareBlock aware) {
            aware.beforeEntityRemoval(state, level, pos, level.getBlockState(pos), false);
        } else {
            super.preRemoveSideEffects(pos, state);
        }
    }
    @Override public void setRemoved() {
        if (level != null && level.isClientSide() && getBlockState().getBlock() instanceof RemovalAwareBlock aware) {
            aware.beforeEntityRemoval(getBlockState(), level, worldPosition, level.getBlockState(worldPosition), false);
        }
        super.setRemoved();
    }
}
