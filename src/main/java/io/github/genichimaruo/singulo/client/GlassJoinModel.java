package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.block.MixedGlassSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Vector3f;

/**
 * 同じガラスの階段に接するガラス（ホワイトガラスパネル・黒色強化ガラス）の面は、階段に隠れずに見える所だけを、その形に沿った枠の絵で描く
 * （0.20.0。{@link GlassJoin}）。面を 1/4 ずつに分け、階段の面がない 1/4 だけ描く。絵は tools/building_variants.py の
 * glass_join_textures（見える 1/4 の組ごとの <ガラス>_join_<bits>）。
 * 0.21.0: 階段・ハーフブロックの面も同じ。同じガラスの階段・ハーフブロックが一部を覆う面は、自分の形があって覆われていない 1/4 だけを描く
 * （階段の側面にハーフブロックが当たるときなど）。
 */
final class GlassJoinModel extends GlassModelBase {
    /** 向きごとの、見える 1/4 の組（bits: 1 左上・2 右上・4 左下・8 右下、UV で見て）。0 はふつうに描く。 */

    private record Layer(Block glass, BlockState shape, String texture) {
        Layer(Block glass, BlockState shape) { this(glass, shape, BuiltInRegistries.BLOCK.getKey(glass).getPath()); }

        int own(Direction face) { return shape.is(glass) ? 15 : quarters(shape, face, true); }

        int clipped(BlockState neighbour, Direction face) {
            BlockState part = GlassJoin.matchingPart(neighbour, glass);
            if (part == null) return -1;
            int own = own(face);
            int covered = part.is(glass) ? 15 : quarters(part, face, false);
            return (own & covered) == 0 ? -1 : own & ~covered;
        }
    }

    private static final Map<BlockState, int[]> FACE_MASKS = new ConcurrentHashMap<>();
    private final Layer first;
    @Nullable private final Layer second;
    private final Map<Integer, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private GlassJoinModel(BlockStateModel original, Layer first, @Nullable Layer second) {
        super(original);
        this.first = first;
        this.second = second;
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block glass : new Block[] {SinguloBlocks.WHITE_GLASS_PANEL.get(), SinguloBlocks.BLACK_REINFORCED_GLASS.get(),
                SinguloBlocks.ANDROID_GATE_GLASS_PATTERNED.get(), SinguloBlocks.ANDROID_BARRIER_GLASS_PATTERNED.get()}) {
            List<Block> blocks = new ArrayList<>(List.of(glass));
            for (var variant : SinguloBlocks.VARIANTS.values()) {
                if (GlassJoin.shapedOf(variant.get().defaultBlockState(), glass)) blocks.add(variant.get());
            }
            for (Block block : blocks) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    event.getBakingResult().blockStateModels().computeIfPresent(state, (k, model) -> new GlassJoinModel(model, new Layer(glass, state), null));
                }
            }
        }
        for (BlockState state : SinguloBlocks.MIXED_GLASS_SLAB.get().getStateDefinition().getPossibleStates()) {
            BlockState lower = MixedGlassSlab.half(state, false), upper = MixedGlassSlab.half(state, true);
            Layer bottom = new Layer(((GlassJoin.Shaped) lower.getBlock()).base(), lower);
            Layer top = new Layer(((GlassJoin.Shaped) upper.getBlock()).base(), upper);
            event.getBakingResult().blockStateModels().computeIfPresent(state, (k, model) -> new GlassJoinModel(model, bottom, top));
        }
    }

    private int[] geometry(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int[] open = null;
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            int a = first.clipped(n, d);
            int b = second == null ? -1 : second.clipped(n, d);
            if (a < 0 && b < 0) continue;
            if (open == null) open = new int[6];
            if (a < 0) a = first.own(d);
            if (b < 0) b = second == null ? 0 : second.own(d);
            open[d.ordinal()] = 1 + a + (b << 4);
        }
        return open;
    }

    /**
     * 向き d の面の 1/4 のうち、shape（階段・ハーフブロック）の 1/8 の箱がある所（bits）。self なら自分の面の側の層、
     * そうでなければ向き d の隣にあるものとして、この面に接する層を見る。
     */
    private static int quarters(BlockState shape, Direction d, boolean self) {
        int[] masks = FACE_MASKS.computeIfAbsent(shape, s -> {
            int[] result = new int[12];
            for (Direction face : Direction.values()) {
                result[face.ordinal() * 2] = computeQuarters(s, face, false);
                result[face.ordinal() * 2 + 1] = computeQuarters(s, face, true);
            }
            return result;
        });
        return masks[d.ordinal() * 2 + (self ? 1 : 0)];
    }

    private static int computeQuarters(BlockState shape, Direction d, boolean self) {
        List<AABB> boxes = shape.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
        int bits = 0;
        for (int[] cell : cells(d)) {
            // この面に接する層の 1/8 の箱の中心（そのブロックの中の座標）
            double[] c = {cell[0] / 16.0 + 0.25, cell[1] / 16.0 + 0.25, cell[2] / 16.0 + 0.25};
            int axis = d.getAxis().ordinal();
            boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            c[axis] = positive == self ? 0.75 : 0.25;
            for (AABB box : boxes) {
                if (box.contains(c[0], c[1], c[2])) {
                    bits |= quarter(d, cell);
                    break;
                }
            }
        }
        return bits;
    }

    /** 面 d に接する4つの 1/8 の箱の、始まりの座標（0 か 8）。 */
    private static List<int[]> cells(Direction d) {
        List<int[]> out = new ArrayList<>(4);
        int axis = d.getAxis().ordinal();
        int at = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 8 : 0;
        for (int a = 0; a <= 8; a += 8) {
            for (int b = 0; b <= 8; b += 8) {
                int[] cell = new int[3];
                int k = 0;
                for (int i = 0; i < 3; i++) {
                    cell[i] = i == axis ? at : k++ == 0 ? a : b;
                }
                out.add(cell);
            }
        }
        return out;
    }

    /** 1/8 の箱の面 d の UV（面の位置どおり。tools/building_variants.py の _face_uv と同じ）。 */
    private static float[] uv(Direction d, int[] f) {
        int[] t = {f[0] + 8, f[1] + 8, f[2] + 8};
        return switch (d) {
            case DOWN -> new float[] {f[0], 16 - t[2], t[0], 16 - f[2]};
            case UP -> new float[] {f[0], f[2], t[0], t[2]};
            case NORTH -> new float[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case SOUTH -> new float[] {f[0], 16 - t[1], t[0], 16 - f[1]};
            case WEST -> new float[] {f[2], 16 - t[1], t[2], 16 - f[1]};
            case EAST -> new float[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /** その 1/8 の箱の面が、UV のどの 1/4 か（bit）。 */
    private static int quarter(Direction d, int[] cell) {
        float[] r = uv(d, cell);
        return 1 << ((r[0] >= 8 ? 1 : 0) + (r[1] >= 8 ? 2 : 0));
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int[] open = geometry(level, pos, state);
        List<BlockStateModelPart> originals = new ArrayList<>();
        original.collectParts(level, pos, state, random, originals);
        for (BlockStateModelPart originalPart : originals) {
            parts.add(part(side -> {
                List<BakedQuad> plain = originalPart.getQuads(side);
                if (side == null || open == null || open[side.ordinal()] == 0 || plain.isEmpty()) return plain;
                int bits = open[side.ordinal()];
                return quads.computeIfAbsent(side.ordinal() * 256 + bits, k -> {
                    List<BakedQuad> out = new ArrayList<>(4);
                    if ((bits & 15) != 0) bake(side, bits & 15, first.texture(), out);
                    if (second != null && (bits >>> 4) != 0) bake(side, bits >>> 4, second.texture(), out);
                    return List.copyOf(out);
                });
            }));
        }
    }

    /** 見える 1/4 だけの面（その組の枠の絵）。 */
    private void bake(Direction side, int bits, String name, List<BakedQuad> out) {
        TextureAtlasSprite sprite = sprite(Singulo.id("block/" + name + "_join_" + bits));
        for (int[] cell : cells(side)) {
            if ((bits & quarter(side, cell)) == 0) {
                continue;
            }
            out.add(bakeQuad(new Vector3f(cell[0], cell[1], cell[2]), new Vector3f(cell[0] + 8, cell[1] + 8, cell[2] + 8),
                    uv(side, cell), sprite, side));
        }
    }
}
