package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.block.GlassJoin;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * アンドロイド用のガラスの階段（0.20.0）。元のガラスと同じく、透過ガラスの階段はアンドロイドだけが通れ、ほかには階段の形でぶつかる
 * （遮断ガラスなら逆）。元のガラスや同じガラスの階段との境目は描かない（{@link GlassJoin}）。
 */
public class AndroidGlassStairs extends GlassJoin.Stairs {
    public AndroidGlassStairs(Supplier<Block> base, Properties properties) {
        super(base, properties);
    }

    boolean gate() {
        return gate(base());
    }

    /** base が透過ガラスか（遮断ガラスなら false）。 */
    static boolean gate(Block base) {
        return base instanceof AndroidGlassBlock glass && glass.gate();
    }

    static boolean android(CollisionContext context) {
        return context instanceof EntityCollisionContext e && e.getEntity() instanceof RuinAndroid;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean blocks = android(context) != gate();
        return blocks ? super.getCollisionShape(state, level, pos, context) : Shapes.empty();
    }

    /** 道探し: 通れる側には空いた所。ぶつかる側は、ふつうの階段と同じ。 */
    @Nullable
    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        boolean blocks = (mob instanceof RuinAndroid) != gate();
        return blocks ? super.getBlockPathType(state, level, pos, mob) : BlockPathTypes.OPEN;
    }
}
