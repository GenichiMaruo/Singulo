package io.github.genichimaruo.singulo.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 回転流の床。沈んだ炉心区画の外周の環状回廊の床で、壊せない。止まった炉の回転に引きずられた時空（フレームドラッグ）が、
 * 上に乗ったもの（プレイヤーも。しゃがんでも止まらない）を facing の向きへ流し続ける。区画（segment）ごとの制動器
 * （{@link DragBrakeBlock}）を下ろすと、その区画の床は止まる（active=false）。
 */
public class FrameDragFloorBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 3);
    /** 1 tick に流す速さ（ブロック）。歩いて逆らうと遅く、走れば進めるくらい。 */
    public static final double SPEED = 0.13;

    public FrameDragFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, true).setValue(SEGMENT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, SEGMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** 上に乗ったものを流す（プレイヤーは自分の側で動くので、クライアントでも押す）。 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(ACTIVE) && !(entity instanceof Player p && p.getAbilities().flying)) {
            Direction d = state.getValue(FACING);
            Vec3 v = entity.getDeltaMovement();
            double along = v.x * d.getStepX() + v.z * d.getStepZ();
            if (along < SPEED) {
                double push = Math.min(SPEED - along, SPEED * 0.55);
                entity.setDeltaMovement(v.add(d.getStepX() * push, 0, d.getStepZ() * push));
            }
        }
        super.stepOn(level, pos, state, entity);
    }
}
