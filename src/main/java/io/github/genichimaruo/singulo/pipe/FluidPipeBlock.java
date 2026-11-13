package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.cable.CableProfile;
import io.github.genichimaruo.singulo.cable.ConduitCover;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.genichimaruo.singulo.compat.Capabilities;

/**
 * 液体パイプ（4段階）。隣のパイプ（段階を問わない）と、液体を扱うブロックと、水源に自動で接続する。
 * 運ぶ液体やガスの種類を問わない。どの面から出し入れするかは、つないだ装置の面の設定で決まる。
 * <p>
 * capacity は1 tick に運べる量（mB）。分かれ目のない区間は {@link FluidPipeNetwork} が1つにまとめ、
 * 経路のいちばん細いところで量が決まる（電線と同じ）。パイプ自体は tick しない。
 */
public class FluidPipeBlock extends BaseEntityBlock {
    private final long capacity;
    private final PipeProfile profile;

    public FluidPipeBlock(Properties properties, long capacity, PipeProfile profile) {
        super(properties);
        this.capacity = capacity;
        this.profile = profile;
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : CableBlock.CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state.setValue(CableBlock.STRAIGHT, CableProfile.Straight.NONE)
                .setValue(ConduitCover.PROPERTY, ConduitCover.State.NONE));
    }

    /** 1 tick に運べる量（mB）。Long.MAX_VALUE は上限なし。 */
    public long capacity() {
        return capacity;
    }

    public PipeProfile profile() {
        return profile;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @Nullable BlockGetter context,
                                java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add((capacity == Long.MAX_VALUE
                ? net.minecraft.network.chat.Component.translatable("tooltip.singulo.fluid_pipe.unlimited")
                : net.minecraft.network.chat.Component.translatable("tooltip.singulo.fluid_pipe.capacity", String.format("%,d", capacity)))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        super.appendHoverText(stack, context, lines, flag);
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CableBlock.CONNECTIONS.values().forEach(builder::add);
        builder.add(CableBlock.STRAIGHT, ConduitCover.PROPERTY);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = profile.core(state.getValue(CableBlock.STRAIGHT));
        for (var e : CableBlock.CONNECTIONS.entrySet()) {
            if (state.getValue(e.getValue())) {
                shape = Shapes.or(shape, profile.arm(e.getKey()));
            }
        }
        return ConduitCover.shape(state, shape);
    }

    // ------------------------------------------------------------------ カバー（{@link ConduitCover}）

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return ConduitCover.use(player.getItemInHand(hand), state, level, pos, player, hand, hit);
    }

    /** はめ込みの次の tick に、ブロック入りの状態にする（{@link ConduitCover#settle}）。 */
    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        ConduitCover.settle(state, level, pos);
    }

    /** ブロック入りのカバーは、はめ込んだブロックと同じ爆発への強さ（{@link ConduitCover#explosionResistance}）。 */
    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        return ConduitCover.explosionResistance(state, level, pos, explosion, super.getExplosionResistance(state, level, pos, explosion));
    }

    /** カバーがあれば、壊してもケーブルは残し、カバーを1つずつ外す（{@link ConduitCover#breakCover}）。 */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                       boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        return !ConduitCover.breakCover(state, level, pos, player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return ConduitCover.destroyProgress(state, player, level, pos, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ConduitCover.collision(state, super.getCollisionShape(state, level, pos, context));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return ConduitCover.occlusion(state);
    }

    @Override
    public net.minecraft.world.level.block.SoundType getSoundType(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos,
            @Nullable net.minecraft.world.entity.Entity entity) {
        if (ConduitCover.of(state) == ConduitCover.State.BLOCK && level.getBlockEntity(pos) instanceof ConduitCover.Holder holder
                && holder.coverState() != null) {
            return holder.coverState().getSoundType(level, pos, entity);
        }
        return super.getSoundType(state, level, pos, entity);
    }

    /** 枠だけ・カバーなしは光を通し、隣に陰を落とさない（ガラスのように）。ブロック入りはふつうのブロックと同じ。 */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return ConduitCover.passesLight(state) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return ConduitCover.passesLight(state) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return ConduitCover.passesLight(state) ? 1.0F : super.getShadeBrightness(state, level, pos);
    }


    private static BlockState connections(BlockState state, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(CableBlock.CONNECTIONS.get(d), connects(level, pos, d));
        }
        return CableBlock.withStraight(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.neighborChanged(neighborPos);
        }
        return CableBlock.withStraight(state.setValue(CableBlock.CONNECTIONS.get(dir), level instanceof Level l ? connects(l, pos, dir)
                : neighborState.getBlock() instanceof FluidPipeBlock));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // ブロックエンティティや面の設定だけが変わった場合も、接続を取り直す
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.neighborChanged(fromPos);
        }
        BlockState updated = connections(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        ConduitCover.dropOnRemove(state, level, pos, newState);
        // 別の段階のパイプに置き換えたときも、ネットワークを作り直す（区間の容量が変わる）
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    static boolean connects(Level level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (!level.isLoaded(other)) {
            return false;
        }
        BlockState state = level.getBlockState(other);
        return state.getBlock() instanceof FluidPipeBlock || FluidPipeNetwork.isSource(state)
                || Capabilities.get(level, Capabilities.FluidHandler.BLOCK, other, dir.getOpposite()) != null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }
}
