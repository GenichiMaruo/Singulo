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
import net.minecraft.world.level.block.TransparentBlock;
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

    /**
     * 0.23.0: 境目を描かないガラスの仲間か。この作品のガラス（白・黒・コラプサー・四芒星・遺構・研究所のガラス）と、その階段・
     * ハーフブロック・重ねハーフブロックは、種類が違っても同じガラスのように扱い、接する面を描かない。アンドロイド用のガラスは入らない
     * （つながって見える模様と、アンドロイドの通り抜けがあるので）。
     */
    public static boolean inFamily(BlockState state) {
        Block b = state.getBlock();
        return b instanceof Glass || b instanceof MixedGlassSlab || b instanceof MixedPanelGlassSlab
                || b instanceof Shaped shaped && shaped.base() instanceof Glass;
    }

    /**
     * Shape occupied by the requested glass material at this position. Glass of the shared family ({@link #inFamily})
     * counts as the same material, so the whole neighbouring shape is returned.
     */
    @Nullable public static BlockState matchingPart(BlockState state, Block glass) {
        if (state.is(glass) || shapedOf(state, glass)) return state;
        if (glass instanceof Glass && inFamily(state)) return state;
        if (!(state.getBlock() instanceof MixedGlassSlab)) return null;
        // 重ねたハーフブロックのうち、そのガラスの方（違うガラスどうしなので、合うのは多くても1つ）
        for (boolean top : new boolean[] {false, true}) {
            BlockState half = MixedGlassSlab.half(state, top);
            if (shapedOf(half, glass)) return half;
        }
        return null;
    }

    /** self の direction 側の面が、隣の adjacent の形ですき間なく覆われているか（覆われていれば描かない）。 */
    public static boolean covered(BlockState self, BlockState adjacent, Direction direction) {
        return net.minecraft.world.phys.shapes.Shapes.blockOccudes(self.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                adjacent.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), direction);
    }

    /**
     * 透けるガラス（ホワイトガラスパネル・黒色強化ガラスなど）。同じガラスの階段・ハーフブロックの、埋まった面との間も描かない。
     * 0.23.0: 種類の違うガラスの仲間（{@link #inFamily}）との間も描かない。
     */
    public static class Glass extends TransparentBlock {
        public Glass(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
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
        protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
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
        protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            Block glass = base.get();
            if (adjacent.is(glass)) {
                return true;
            }
            BlockState part = matchingPart(adjacent, glass);
            if (part != null) return covered(state, part, direction);
            return super.skipRendering(state, adjacent, direction);
        }
    }

    /** White panel, black reinforced and collapsar reinforced glass slabs share vanilla slab placement rules and stack with each other. */
    public static final class PairSlab extends Slab implements MixedPanelGlassSlab.Stackable {
        public PairSlab(Supplier<Block> base, Properties properties) { super(base, properties); }

        @Override public boolean fits(BlockState existing, BlockPlaceContext context) {
            return super.canBeReplaced(existing, context);
        }

        @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            // Vanilla checks the held item against "this", and the hit against the existing slab's shape.
            // 0.23.0: panel slabs stack onto glass slabs too ({@link MixedPanelGlassSlab}).
            return Block.byItem(context.getItemInHand().getItem()) instanceof MixedPanelGlassSlab.Stackable held && held.fits(state, context);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
            BlockState withPanel = MixedPanelGlassSlab.stack(existing, this);
            if (withPanel != null) return withPanel;
            if (existing.getBlock() instanceof PairSlab other && other != this && existing.getValue(TYPE) != SlabType.DOUBLE) {
                MixedGlassSlab.Glass mine = MixedGlassSlab.Glass.of(base()), theirs = MixedGlassSlab.Glass.of(other.base());
                if (mine != null && theirs != null && mine != theirs) {
                    boolean theirsOnTop = existing.getValue(TYPE) == SlabType.TOP;
                    return MixedGlassSlab.of(theirsOnTop ? mine : theirs, theirsOnTop ? theirs : mine);
                }
            }
            return super.getStateForPlacement(context);
        }
    }
}
