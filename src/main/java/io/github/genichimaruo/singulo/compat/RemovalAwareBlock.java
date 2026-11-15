package io.github.genichimaruo.singulo.compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** Invoked while the old block entity is still available, before inventory removal. */
public interface RemovalAwareBlock {
    void beforeEntityRemoval(BlockState oldState, Level level, BlockPos pos, BlockState replacement, boolean moved);
}
