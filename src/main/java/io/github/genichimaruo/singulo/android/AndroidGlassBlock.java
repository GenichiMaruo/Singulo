package io.github.genichimaruo.singulo.android;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * アンドロイド用のガラス。
 *
 * <ul>
 * <li>遮断ガラス（gate = false）: アンドロイドだけが通れない。プレイヤーやほかの生き物はすり抜ける。</li>
 * <li>透過ガラス（gate = true）: アンドロイドだけが通れる。ほかにとってはふつうのガラス。</li>
 * </ul>
 * アンドロイドの道探しにも効く（遮断はふさがった所、透過は空いた所として探す）。
 * 無地のもの（connected）は、同じガラスが隣り合う向きを状態に持ち、つながった側の縁を消す（大きな1枚のガラスに見える）。
 */
public class AndroidGlassBlock extends HalfTransparentBlock {
    private final boolean gate;
    private final boolean connected;

    public AndroidGlassBlock(Properties properties, boolean gate, boolean connected) {
        super(properties);
        this.gate = gate;
        this.connected = connected;
        BlockState s = stateDefinition.any();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    /** 6方向のつながり（使うのは無地のガラスだけ。模様のガラスはいつもつながらない）。 */
    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.SOUTH, PipeBlock.EAST, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    public boolean gate() {
        return gate;
    }

    private static boolean android(CollisionContext context) {
        return context instanceof EntityCollisionContext e && e.getEntity() instanceof RuinAndroid;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean blocks = android(context) != gate;
        return blocks ? Shapes.block() : Shapes.empty();
    }

    /** 道探し: アンドロイドには、遮断はふさがった所、透過は空いた所。ほかの生き物には逆。 */
    @Nullable
    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        boolean blocks = (mob instanceof RuinAndroid) != gate;
        return blocks ? BlockPathTypes.BLOCKED : BlockPathTypes.OPEN;
    }

    // ------------------------------------------------------------------ つながり（無地のガラス）

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = defaultBlockState();
        if (!connected) {
            return s;
        }
        for (Direction d : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),
                    context.getLevel().getBlockState(context.getClickedPos().relative(d)).is(this));
        }
        return s;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        if (!connected) {
            return state;
        }
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), neighbor.is(this));
    }
}
