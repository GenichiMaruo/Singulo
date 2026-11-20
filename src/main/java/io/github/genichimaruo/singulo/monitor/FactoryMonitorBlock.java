package io.github.genichimaruo.singulo.monitor;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.network.MonitorRequestPayload;
import io.github.genichimaruo.singulo.ruin.lab.LabDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 机のモニター（0.21.0 から使える）: 使うと工場モニターの画面（{@link FactoryMonitor}）を開く。 */
public class FactoryMonitorBlock extends LabDecorBlock {
    public static final MapCodec<FactoryMonitorBlock> CODEC = simpleCodec(p -> new FactoryMonitorBlock(p, net.minecraft.world.phys.shapes.Shapes.block()));

    public FactoryMonitorBlock(Properties properties, VoxelShape shape) {
        super(properties, shape, null, null);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            MonitorRequestPayload.send(serverPlayer, pos, true);
        }
        return io.github.genichimaruo.singulo.compat.Interactions.sidedSuccess(level.isClientSide());
    }
}
