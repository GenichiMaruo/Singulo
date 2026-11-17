package io.github.genichimaruo.singulo.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 同じガラスどうしの境目を描かない（0.20.0）。バニラのガラスは同じブロックとの間の面だけを消すので、ガラスとその階段の間にも
 * 枠が出ていた。ここでは、ガラスの階段と元のガラス、同じガラスの階段どうしの間の面も消す。
 * 相手の面が部分だけ（階段の上の段の側など）なら、消すと穴が見えるので消さない。
 */
public final class GlassJoin {
    private GlassJoin() {}

    /** 階段 stairs の、toward 側の面がすき間なく埋まっているか（階段の形は状態だけで決まる）。 */
    public static boolean fullFace(BlockState stairs, Direction toward) {
        return Block.isFaceFull(stairs.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), toward);
    }

    /** 透けるガラス（ホワイトガラスパネル・黒色強化ガラス）。同じガラスの階段の、埋まった面との間も描かない。 */
    public static class Glass extends GlassBlock {
        public Glass(Properties properties) {
            super(properties);
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            if (adjacent.getBlock() instanceof Stairs stairs && stairs.base() == this) {
                return fullFace(adjacent, direction.getOpposite());
            }
            return super.skipRendering(state, adjacent, direction);
        }
    }

    /** ガラスの階段。元のガラスとの間と、同じガラスの階段の埋まった面との間を描かない。 */
    public static class Stairs extends StairBlock {
        private final Supplier<Block> base;

        public Stairs(Supplier<Block> base, Properties properties) {
            super(base.get().defaultBlockState(), properties);
            this.base = base;
        }

        public Block base() {
            return base.get();
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            Block glass = base.get();
            if (adjacent.is(glass)) {
                return true;
            }
            if (adjacent.getBlock() instanceof Stairs stairs && stairs.base() == glass) {
                return fullFace(adjacent, direction.getOpposite());
            }
            return super.skipRendering(state, adjacent, direction);
        }
    }
}
