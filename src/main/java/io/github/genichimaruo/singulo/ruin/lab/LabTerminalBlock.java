package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 記録端末。地下アンドロイド研究所の部屋に残る端末で、右クリックすると、そこに残された記録（職員の業務連絡・議事録・日記・
 * システムのログ…）を読める。番号（log）ごとに別の記録で、読み集めると、統括AIが暴走して職員が逃げ出したいきさつが分かる。
 */
public class LabTerminalBlock extends HorizontalDirectionalBlock {
    /** 記録の数（lab.singulo.log.N）。 */
    public static final int LOGS = 12;
    public static final IntegerProperty LOG = IntegerProperty.create("log", 0, 15);

    public LabTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOG, 0));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOG);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 記録の題と本文（チャット欄に出す）。 */
    public static Component[] lines(int log) {
        int n = Math.floorMod(log, LOGS);
        return new Component[] {Component.translatable("lab.singulo.log." + n + ".title").withStyle(ChatFormatting.GOLD),
                Component.translatable("lab.singulo.log." + n).withStyle(ChatFormatting.GRAY)};
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            for (Component line : lines(state.getValue(LOG))) {
                player.sendSystemMessage(line);
            }
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
