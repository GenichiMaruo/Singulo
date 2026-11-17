package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

/**
 * つながって見えるアンドロイド透過ガラス（無地）とその階段（0.20.0）。ブロックも階段も 1/8 の箱の集まりとして扱い、
 * 同じガラス（となりのブロック・階段も）の箱どうしが接する面は描かない。縁（元の1ドットの縁の絵）は、面の端の向こうに同じガラスの箱が
 * ないときだけ描く（元のガラスの「つながっていない向きに縁」と同じ決まり）。ブロックと階段がつながって、1枚のガラスに見える。
 */
final class ConnectedGlassModel extends BakedModelWrapper<BakedModel> {
    /**
     * まわりの 1/8 の箱（-1〜2 の 4×4×4 = 64ビット。0〜1 が自分、ほかはとなり・斜めのブロックの、自分に近い層）に、
     * 同じガラスが入っているか。斜めも見るのは、内側にへこんだ角の1ドットを描くため。
     */
    private static final ModelProperty<Long> CELLS = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();
    /** 縁を面から少し浮かせる量（元の縁のモデルと同じ）。 */
    private static final float LIFT = 0.012F;

    private final Block glass;
    /** 絵の名前（中身 block/<name>、縁 block/<name>_edge）。 */
    private final String name;
    private final Map<Long, Map<Direction, List<BakedQuad>>> quads = new ConcurrentHashMap<>();
    private final List<BakedQuad> none = List.of();

    private ConnectedGlassModel(BakedModel original, Block glass, String name) {
        super(original);
        this.glass = glass;
        this.name = name;
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block glass : new Block[] {SinguloBlocks.ANDROID_GATE_GLASS.get(), SinguloBlocks.ANDROID_BARRIER_GLASS.get()}) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(glass).getPath();
            Block stairs = SinguloBlocks.VARIANTS.get(name + "_stairs").get();
            for (Block block : new Block[] {glass, stairs}) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                            (k, m) -> new ConnectedGlassModel(m, glass, name));
                }
            }
        }
    }

    /** s が、このガラス（ブロックか階段）なら、入っている 1/8 の箱（ビット x*4 + y*2 + z）。ほかは 0。 */
    private int cells(BlockState s) {
        if (s.is(glass)) {
            return 0xFF;
        }
        if (!(s.getBlock() instanceof GlassJoin.Stairs stairs) || stairs.base() != glass) {
            return 0;
        }
        List<AABB> boxes = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
        int bits = 0;
        for (int i = 0; i < 8; i++) {
            double x = (i >> 2) * 0.5 + 0.25, y = ((i >> 1) & 1) * 0.5 + 0.25, z = (i & 1) * 0.5 + 0.25;
            for (AABB box : boxes) {
                if (box.contains(x, y, z)) {
                    bits |= 1 << i;
                    break;
                }
            }
        }
        return bits;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        int[] blocks = new int[27];
        for (int bx = -1; bx <= 1; bx++) {
            for (int by = -1; by <= 1; by++) {
                for (int bz = -1; bz <= 1; bz++) {
                    BlockState s = bx == 0 && by == 0 && bz == 0 ? state : level.getBlockState(pos.offset(bx, by, bz));
                    blocks[(bx + 1) * 9 + (by + 1) * 3 + (bz + 1)] = cells(s);
                }
            }
        }
        return data.derive().with(CELLS, grid(blocks)).build();
    }

    /** ブロックごとの 1/8 の箱（27個、自分は真ん中）から、まわりの 4×4×4 の表を作る。 */
    private static long grid(int[] blocks) {
        long key = 0;
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 2; z++) {
                    int b = (Math.floorDiv(x, 2) + 1) * 9 + (Math.floorDiv(y, 2) + 1) * 3 + (Math.floorDiv(z, 2) + 1);
                    int bit = Math.floorMod(x, 2) * 4 + Math.floorMod(y, 2) * 2 + Math.floorMod(z, 2);
                    if ((blocks[b] >> bit & 1) != 0) {
                        key |= 1L << index(x, y, z);
                    }
                }
            }
        }
        return key;
    }

    private static int index(int x, int y, int z) {
        return (x + 1) * 16 + (y + 1) * 4 + (z + 1);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        if (state == null || renderType != null && !getRenderTypes(state, rand, data).contains(renderType)) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        Long key = data.get(CELLS);
        long k;
        if (key != null) {
            k = key;
        } else {
            int[] alone = new int[27];
            alone[13] = cells(state);
            k = grid(alone);
        }
        Map<Direction, List<BakedQuad>> all = quads.computeIfAbsent(k, this::bake);
        List<BakedQuad> out = all.get(side);
        return out == null ? none : out;
    }

    /** 1/8 の箱 (x, y, z)（-1〜2。0〜1 が自分）に同じガラスが入っているか。表の外は入っていない。 */
    private static boolean occupied(long key, int x, int y, int z) {
        if (x < -1 || x > 2 || y < -1 || y > 2 || z < -1 || z > 2) {
            return false;
        }
        return (key >> index(x, y, z) & 1) != 0;
    }

    private Map<Direction, List<BakedQuad>> bake(long key) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite fill = atlas.apply(Singulo.id("block/" + name));
        TextureAtlasSprite edge = atlas.apply(Singulo.id("block/" + name + "_edge"));
        Map<Direction, List<BakedQuad>> out = new java.util.HashMap<>();
        for (int i = 0; i < 8; i++) {
            int cx = i >> 2, cy = (i >> 1) & 1, cz = i & 1;
            if (!occupied(key, cx, cy, cz)) {
                continue;
            }
            float[] from = {cx * 8, cy * 8, cz * 8};
            float[] to = {cx * 8 + 8, cy * 8 + 8, cz * 8 + 8};
            for (Direction n : Direction.values()) {
                int nx = cx + n.getStepX(), ny = cy + n.getStepY(), nz = cz + n.getStepZ();
                if (occupied(key, nx, ny, nz)) {
                    continue;                                 // 同じガラスの箱が接している面は描かない
                }
                boolean outer = nx < 0 || nx > 1 || ny < 0 || ny > 1 || nz < 0 || nz > 1;
                Direction cull = outer ? n : null;
                List<BakedQuad> list = out.computeIfAbsent(cull, d -> new ArrayList<>());
                list.add(quad(from, to, n, cull, uv(n, from, to), fill));
                // 縁: 面の端の向こう（となりの箱）に同じガラスがない向きだけ
                java.util.Set<Direction> edges = java.util.EnumSet.noneOf(Direction.class);
                for (Direction e : Direction.values()) {
                    if (e.getAxis() != n.getAxis() && !occupied(key, cx + e.getStepX(), cy + e.getStepY(), cz + e.getStepZ())) {
                        edges.add(e);
                    }
                }
                for (Direction e : edges) {
                    float[] f = from.clone(), t = to.clone();
                    int ea = e.getAxis().ordinal();
                    if (e.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                        f[ea] = t[ea] - 1;
                    } else {
                        t[ea] = f[ea] + 1;
                    }
                    // 角で2本の縁が重なると、同じ高さの面どうしがちらつく。軸の番号が大きい向きの縁は、角の1ドットを
                    // もう1本（番号が小さい向きの縁）にゆずって短くする
                    int la = 3 - ea - n.getAxis().ordinal();     // 縁の長さの向きの軸
                    if (ea > la) {
                        for (Direction end : Direction.values()) {
                            if (end.getAxis().ordinal() == la && edges.contains(end)) {
                                if (end.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    t[la] -= 1;
                                } else {
                                    f[la] += 1;
                                }
                            }
                        }
                    }
                    int na = n.getAxis().ordinal();
                    if (n.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                        t[na] += LIFT;
                    } else {
                        f[na] -= LIFT;
                    }
                    float[] r = uv(n, f, t);
                    // 縁の絵は左の1列（と上の1行）に縁の色がある
                    float[] strip = r[2] - r[0] > r[3] - r[1] ? new float[] {r[0], 0, r[2], 1} : new float[] {0, r[1], 1, r[3]};
                    list.add(quad(f, t, n, cull, strip, edge));
                }
                // 内側にへこんだ角: 面の2つの端の向こうはどちらも同じガラスで（縁がない）、その間の斜めの箱にはないとき、
                // となりの2本の縁がそこで途切れるので、角の1ドットを縁の色にする
                for (Direction e1 : Direction.values()) {
                    for (Direction e2 : Direction.values()) {
                        if (e1.getAxis() == n.getAxis() || e2.getAxis() == n.getAxis() || e1.getAxis().ordinal() >= e2.getAxis().ordinal()
                                || edges.contains(e1) || edges.contains(e2)
                                // となりの2つの箱も、同じ向きの面が見えている（その縁がここへ来る）
                                || occupied(key, cx + e1.getStepX() + n.getStepX(), cy + e1.getStepY() + n.getStepY(), cz + e1.getStepZ() + n.getStepZ())
                                || occupied(key, cx + e2.getStepX() + n.getStepX(), cy + e2.getStepY() + n.getStepY(), cz + e2.getStepZ() + n.getStepZ())
                                || occupied(key, cx + e1.getStepX() + e2.getStepX(), cy + e1.getStepY() + e2.getStepY(),
                                cz + e1.getStepZ() + e2.getStepZ())) {
                            continue;
                        }
                        float[] f = from.clone(), t = to.clone();
                        for (Direction e : new Direction[] {e1, e2}) {
                            int ea = e.getAxis().ordinal();
                            if (e.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                f[ea] = t[ea] - 1;
                            } else {
                                t[ea] = f[ea] + 1;
                            }
                        }
                        int na = n.getAxis().ordinal();
                        if (n.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                            t[na] += LIFT;
                        } else {
                            f[na] -= LIFT;
                        }
                        list.add(quad(f, t, n, cull, new float[] {0, 0, 1, 1}, edge));
                    }
                }
            }
        }
        return out;
    }

    private static BakedQuad quad(float[] from, float[] to, Direction face, @Nullable Direction cull, float[] uv, TextureAtlasSprite sprite) {
        return BAKERY.bakeQuad(new Vector3f(from[0], from[1], from[2]), new Vector3f(to[0], to[1], to[2]),
                new BlockElementFace(cull, -1, "", new BlockFaceUV(uv, 0)), sprite, face, BlockModelRotation.X0_Y0, null, true);
    }

    /** 箱の面 d の UV（面の位置どおり。Minecraft の既定と同じ）。 */
    private static float[] uv(Direction d, float[] f, float[] t) {
        return switch (d) {
            case DOWN -> new float[] {f[0], 16 - t[2], t[0], 16 - f[2]};
            case UP -> new float[] {f[0], f[2], t[0], t[2]};
            case NORTH -> new float[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case SOUTH -> new float[] {f[0], 16 - t[1], t[0], 16 - f[1]};
            case WEST -> new float[] {f[2], 16 - t[1], t[2], 16 - f[1]};
            case EAST -> new float[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }
}
