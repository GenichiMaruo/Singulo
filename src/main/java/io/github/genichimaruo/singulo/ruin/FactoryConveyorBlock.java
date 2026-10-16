package io.github.genichimaruo.singulo.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 工場のコンベア。動いている（running）間は、上に乗ったもの（プレイヤーも）を facing の向きへ流す。
 * 廃アンドロイド工場の中央管制ホールでは、ファウンドリが戦いの間だけ動かし、ときどき向きを変える（変える前は枠の灯が赤く光る: warn）。
 * 止まっているときは、ただの床。
 */
public class FactoryConveyorBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty WARN = BooleanProperty.create("warn");
    /** 1 tick に流す速さ（ブロック）。歩けば逆らえるくらい。 */
    public static final double SPEED = 0.11;

    public FactoryConveyorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false).setValue(WARN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING, WARN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** 上に乗ったものを流す（プレイヤーは自分の側で動くので、クライアントでも押す）。 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(RUNNING) && !entity.isSteppingCarefully() && !(entity instanceof Player p && p.getAbilities().flying)) {
            Direction d = state.getValue(FACING);
            Vec3 v = entity.getDeltaMovement();
            double along = v.x * d.getStepX() + v.z * d.getStepZ();
            if (along < SPEED) {
                double push = Math.min(SPEED - along, SPEED * 0.5);
                entity.setDeltaMovement(v.add(d.getStepX() * push, 0, d.getStepZ() * push));
            }
        }
        super.stepOn(level, pos, state, entity);
    }
}
