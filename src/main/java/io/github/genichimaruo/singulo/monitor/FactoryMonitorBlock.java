package io.github.genichimaruo.singulo.monitor;

import io.github.genichimaruo.singulo.network.MonitorRequestPayload;
import io.github.genichimaruo.singulo.ruin.lab.LabDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 机のモニター（0.21.0 から使える）: 使うと工場モニターの画面（{@link FactoryMonitor}）を開く。 */
public class FactoryMonitorBlock extends LabDecorBlock {

    public FactoryMonitorBlock(Properties properties, VoxelShape shape) {
        super(properties, shape, null, null);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            MonitorRequestPayload.send(serverPlayer, pos, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
