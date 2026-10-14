package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** アンドロイド作業指令台。置いた人が持ち主になる。 */
public class WorkConsoleBlock extends SimpleMachineBlock<WorkConsoleBlockEntity> {
    public WorkConsoleBlock(Properties properties) {
        super(properties, SinguloBlockEntities.ANDROID_WORK_CONSOLE, WorkConsoleBlockEntity::new, WorkConsoleBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof WorkConsoleBlockEntity console) {
            console.setOwner(player.getUUID());
        }
    }
}
