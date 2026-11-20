package io.github.genichimaruo.singulo.block;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

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

    private static final java.util.Map<BlockState, Integer> OCTANTS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 階段を 2×2×2 の区画に分けたとき、ガラスで埋まっている区画（0.21.0。水に浸かった階段の水を、空いている区画だけに描くため）。
     * 区画 (x, y, z)（それぞれ 0 か 1）が埋まっていれば、ビット x + 2y + 4z が立つ。
     */
    public static int filledOctants(BlockState stairs) {
        return OCTANTS.computeIfAbsent(stairs, s -> {
            var boxes = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
            int bits = 0;
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) * 0.5 + 0.25, y = (i >> 1 & 1) * 0.5 + 0.25, z = (i >> 2 & 1) * 0.5 + 0.25;
                for (var box : boxes) {
                    if (box.contains(x, y, z)) {
                        bits |= 1 << i;
                        break;
                    }
                }
            }
            return bits;
        });
    }

    /** 元のガラスの形を変えたもの（階段・ハーフブロック）。 */
    public interface Shaped {
        Block base();
    }

    /** s が base のガラスの階段・ハーフブロックか。 */
    public static boolean shapedOf(BlockState s, Block base) {
        return s.getBlock() instanceof Shaped shaped && shaped.base() == base;
    }

    /** Shape occupied by the requested glass material at this position. */
    @Nullable public static BlockState matchingPart(BlockState state, Block glass) {
        if (state.is(glass) || shapedOf(state, glass)) return state;
        if (!(state.getBlock() instanceof MixedGlassSlab)) return null;
        boolean black = glass == SinguloBlocks.BLACK_REINFORCED_GLASS.get();
        if (!black && glass != SinguloBlocks.WHITE_GLASS_PANEL.get()) return null;
        return MixedGlassSlab.half(state, state.getValue(MixedGlassSlab.BLACK_ON_TOP) == black);
    }

    /** self の direction 側の面が、隣の adjacent の形ですき間なく覆われているか（覆われていれば描かない）。 */
    public static boolean covered(BlockState self, BlockState adjacent, Direction direction) {
        return net.minecraft.world.phys.shapes.Shapes.blockOccudes(self.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                adjacent.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), direction);
    }

    /** 透けるガラス（ホワイトガラスパネル・黒色強化ガラス）。同じガラスの階段・ハーフブロックの、埋まった面との間も描かない。 */
    public static class Glass extends net.minecraft.world.level.block.GlassBlock {
        public Glass(Properties properties) {
            super(properties);
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            BlockState part = matchingPart(adjacent, this);
            if (part != null) return fullFace(part, direction.getOpposite());
            return super.skipRendering(state, adjacent, direction);
        }
    }

    /** ガラスの階段。元のガラスとの間と、同じガラスの階段・ハーフブロックに覆われた面を描かない。 */
    public static class Stairs extends StairBlock implements Shaped {
        private final Supplier<Block> base;

        public Stairs(Supplier<Block> base, Properties properties) {
            super(base.get().defaultBlockState(), properties);
            this.base = base;
        }

        @Override
        public Block base() {
            return base.get();
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            Block glass = base.get();
            if (adjacent.is(glass)) {
                return true;
            }
            BlockState part = matchingPart(adjacent, glass);
            if (part != null) return covered(state, part, direction);
            return super.skipRendering(state, adjacent, direction);
        }
    }

    /** ガラスのハーフブロック（0.21.0）。元のガラスとの間と、同じガラスの階段・ハーフブロックに覆われた面を描かない。 */
    public static class Slab extends net.minecraft.world.level.block.SlabBlock implements Shaped {
        private final Supplier<Block> base;

        public Slab(Supplier<Block> base, Properties properties) {
            super(properties);
            this.base = base;
        }

        @Override
        public Block base() {
            return base.get();
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            Block glass = base.get();
            if (adjacent.is(glass)) {
                return true;
            }
            BlockState part = matchingPart(adjacent, glass);
            if (part != null) return covered(state, part, direction);
            return super.skipRendering(state, adjacent, direction);
        }
    }

    /** White panel and black reinforced glass share vanilla slab placement rules. */
    public static final class PairSlab extends Slab {
        public PairSlab(Supplier<Block> base, Properties properties) { super(base, properties); }

        private boolean fits(BlockState existing, BlockPlaceContext context) {
            return super.canBeReplaced(existing, context);
        }

        @Override public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            // Vanilla checks the held item against "this", and the hit against the existing slab's shape.
            return Block.byItem(context.getItemInHand().getItem()) instanceof PairSlab held && held.fits(state, context);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
            if (existing.getBlock() instanceof PairSlab other && other != this && existing.getValue(TYPE) != SlabType.DOUBLE) {
                Block top = existing.getValue(TYPE) == SlabType.TOP ? other.base() : base();
                return SinguloBlocks.MIXED_GLASS_SLAB.get().defaultBlockState()
                        .setValue(MixedGlassSlab.BLACK_ON_TOP, top == SinguloBlocks.BLACK_REINFORCED_GLASS.get());
            }
            return super.getStateForPlacement(context);
        }
    }
}
