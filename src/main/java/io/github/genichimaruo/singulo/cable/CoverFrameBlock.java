package io.github.genichimaruo.singulo.cable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
    public static final MapCodec<CoverFrameBlock> CODEC = simpleCodec(CoverFrameBlock::new);

    public CoverFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item) || !ConduitCover.isConduit(item.getBlock())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            // 枠の場所に置いたとして、つながりを決める
            BlockPlaceContext ctx = BlockPlaceContext.at(new BlockPlaceContext(player, hand, stack, hit), pos, hit.getDirection());
            BlockState conduit = item.getBlock().getStateForPlacement(ctx);
            if (conduit == null) {
                return ItemInteractionResult.FAIL;
            }
            level.setBlock(pos, conduit.setValue(ConduitCover.PROPERTY, ConduitCover.State.FRAME), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, conduit));
            var sound = conduit.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ConduitCover.HOLLOW;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
