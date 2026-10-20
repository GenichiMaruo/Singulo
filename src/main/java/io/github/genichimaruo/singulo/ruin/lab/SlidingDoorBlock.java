package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * スライドドア（2ブロックの高さ）。真ん中で2枚の扉が左右の壁の中へ滑って開く（扉は {@link SlidingDoorBlockEntity} と
 * その描画が動かし、ブロックのモデルは左右の枠と上の鴨居だけ）。右クリックで開き、通る人がいなくなると少しして自分で閉まる。
 * レッドストーンの信号が入っている間は開いたまま。研究所のドア（作れない）と、それに似せた作れるドアがある。
 */
public class SlidingDoorBlock extends AndroidCapsuleBlock implements EntityBlock {
    public static final MapCodec<SlidingDoorBlock> CODEC = simpleCodec(SlidingDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** 開けてから閉まりはじめるまで（tick）と、人がいたときに待ち直す間。 */
    public static final int CLOSE_DELAY = 50, RECHECK = 20;
    /** 閉じた扉（厚さ3、ブロックの真ん中）と、開いたときに残る左右の枠（幅1.5）。向きは north/south のとき。 */
    private static final VoxelShape CLOSED_NS = Block.box(0, 0, 6.5, 16, 16, 9.5), CLOSED_EW = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final VoxelShape OPEN_NS = Shapes.or(Block.box(0, 0, 5.5, 1.5, 16, 10.5), Block.box(14.5, 0, 5.5, 16, 16, 10.5));
    private static final VoxelShape OPEN_EW = Shapes.or(Block.box(5.5, 0, 0, 10.5, 16, 1.5), Block.box(5.5, 0, 14.5, 10.5, 16, 16));

    public SlidingDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        return state.getValue(OPEN) ? (ns ? OPEN_NS : OPEN_EW) : (ns ? CLOSED_NS : CLOSED_EW);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.AIR && state.getValue(OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos())
                || context.getLevel().hasNeighborSignal(context.getClickedPos().above());
        return state.setValue(POWERED, powered).setValue(OPEN, powered);
    }

    private static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** 上下の両方を開け閉めする（変わったときだけ音を鳴らす）。 */
    void setOpen(Level level, BlockPos pos, boolean open) {
        BlockPos low = lower(level.getBlockState(pos), pos);
        BlockState state = level.getBlockState(low);
        if (!state.is(this) || state.getValue(OPEN) == open) {
            return;
        }
        level.setBlock(low, state.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        BlockState up = level.getBlockState(low.above());
        if (up.is(this)) {
            level.setBlock(low.above(), up.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        level.playSound(null, low.above(), open ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35F,
                open ? 1.7F : 1.5F);
        if (open) {
            level.scheduleTick(low, this, CLOSE_DELAY);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            setOpen(level, pos, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 自分で閉まる: 信号がなく、ドアの中に誰もいなければ閉める（いれば少し待ち直す）。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN) || state.getValue(POWERED) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        AABB space = new AABB(pos).expandTowards(0, 1, 0);
        if (!level.getEntitiesOfClass(LivingEntity.class, space, LivingEntity::isAlive).isEmpty()) {
            level.scheduleTick(pos, this, RECHECK);
            return;
        }
        setOpen(level, pos, false);
    }

    /** レッドストーン: 上下どちらかに信号があれば開き、なくなると少しして閉まる。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        BlockPos low = lower(state, pos);
        boolean powered = level.hasNeighborSignal(low) || level.hasNeighborSignal(low.above());
        BlockState lowState = level.getBlockState(low);
        if (!lowState.is(this) || lowState.getValue(POWERED) == powered) {
            return;
        }
        level.setBlock(low, lowState.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        BlockState up = level.getBlockState(low.above());
        if (up.is(this)) {
            level.setBlock(low.above(), up.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (powered) {
            setOpen(level, low, true);
        } else {
            level.scheduleTick(low, this, RECHECK / 2);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlidingDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == SinguloBlockEntities.SLIDING_DOOR.get()
                ? (l, p, s, be) -> ((SlidingDoorBlockEntity) be).clientTick(s) : null;
    }
}
