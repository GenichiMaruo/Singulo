package io.github.genichimaruo.singulo.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 置いたカバーの枠（ケーブルの入っていない枠）。どこにでも置け、あとからケーブル・パイプを右クリックで入れると、そのケーブル・パイプが
 * 枠付きで入る（{@link ConduitCover}）。選ぶ形は1ブロックいっぱい、当たり判定はケーブルの枠と同じ中空の箱、光を通し隣を隠さない。
 * ほかのブロックで右クリックすると、ふつうに隣へ置ける（スニークすれば、ケーブルも隣へ置ける）。
 */
public class CoverFrameBlock extends Block {

    public CoverFrameBlock(Properties properties) {
        super(properties);
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item) || !ConduitCover.isConduit(item.getBlock())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            // 枠の場所に置いたとして、つながりを決める
            BlockPlaceContext ctx = BlockPlaceContext.at(new BlockPlaceContext(player, hand, stack, hit), pos, hit.getDirection());
            BlockState conduit = item.getBlock().getStateForPlacement(ctx);
            if (conduit == null) {
                return InteractionResult.FAIL;
            }
            level.setBlock(pos, conduit.setValue(ConduitCover.PROPERTY, ConduitCover.State.FRAME), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, conduit));
            var sound = conduit.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ConduitCover.HOLLOW;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
