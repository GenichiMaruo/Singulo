package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 研究所の飲み物の自動販売機（2ブロックの高さ。上の見本の棚が光る）。右クリックしても、売り切れ。 */
public class VendingMachineBlock extends AndroidCapsuleBlock {

    public VendingMachineBlock(Properties properties) {
        super(properties);
    }


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("lab.singulo.vending"), true);
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
