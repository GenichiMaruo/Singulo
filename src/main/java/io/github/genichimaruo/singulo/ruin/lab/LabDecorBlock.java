package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 研究所の小物（机のモニター・コーヒーメーカー・書類の山・ごみ箱）。置くとこちらを向く。
 * 右クリックで鳴って一言出るものもある（コーヒーメーカー: まだ動く）。
 */
public class LabDecorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LabDecorBlock> CODEC = simpleCodec(p -> new LabDecorBlock(p, Shapes.block(), null, null));

    private final VoxelShape shape;
    @Nullable
    private final String useMessage;
    @Nullable
    private final SoundEvent useSound;

    public LabDecorBlock(Properties properties, VoxelShape shape, @Nullable String useMessage, @Nullable SoundEvent useSound) {
        super(properties);
        this.shape = shape;
        this.useMessage = useMessage;
        this.useSound = useSound;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (useMessage == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(useMessage), true);
            if (useSound != null) {
                level.playSound(null, pos, useSound, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
