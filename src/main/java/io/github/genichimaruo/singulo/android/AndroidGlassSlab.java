package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.block.GlassJoin;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * アンドロイド用のガラスのハーフブロック（0.21.0）。階段（{@link AndroidGlassStairs}）と同じく、透過ガラスはアンドロイドだけが通れ、
 * ほかにはハーフブロックの形でぶつかる（遮断ガラスなら逆）。元のガラスや同じガラスの階段・ハーフブロックとの境目は描かない（{@link GlassJoin}）。
 */
public class AndroidGlassSlab extends GlassJoin.Slab {
    public AndroidGlassSlab(Supplier<Block> base, Properties properties) {
        super(base, properties);
    }

    boolean gate() {
        return AndroidGlassStairs.gate(base());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean blocks = AndroidGlassStairs.android(context) != gate();
        return blocks ? super.getCollisionShape(state, level, pos, context) : Shapes.empty();
    }

    /** 道探し: 通れる側には空いた所。ぶつかる側は、ふつうのハーフブロックと同じ。 */
    @Nullable
    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        boolean blocks = (mob instanceof RuinAndroid) != gate();
        return blocks ? super.getBlockPathType(state, level, pos, mob) : PathType.OPEN;
    }
}
