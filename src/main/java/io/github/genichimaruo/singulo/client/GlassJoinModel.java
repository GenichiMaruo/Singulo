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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

/**
 * 同じガラスの階段に接するガラス（ホワイトガラスパネル・黒色強化ガラス）の面は、階段に隠れずに見える所だけを、その形に沿った枠の絵で描く
 * （0.20.0。{@link GlassJoin}）。面を 1/4 ずつに分け、階段の面がない 1/4 だけ描く。絵は tools/building_variants.py の
 * glass_join_textures（見える 1/4 の組ごとの <ガラス>_join_<bits>）。
 */
final class GlassJoinModel extends BakedModelWrapper<BakedModel> {
    /** 向きごとの、見える 1/4 の組（bits: 1 左上・2 右上・4 左下・8 右下、UV で見て）。0 はふつうに描く。 */
    private static final ModelProperty<int[]> OPEN = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();

    private final Block glass;
    private final String name;
    private final Map<Integer, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private GlassJoinModel(BakedModel original, Block glass) {
        super(original);
        this.glass = glass;
        this.name = BuiltInRegistries.BLOCK.getKey(glass).getPath();
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block glass : new Block[] {SinguloBlocks.WHITE_GLASS_PANEL.get(), SinguloBlocks.BLACK_REINFORCED_GLASS.get(),
                SinguloBlocks.ANDROID_GATE_GLASS_PATTERNED.get(), SinguloBlocks.ANDROID_BARRIER_GLASS_PATTERNED.get()}) {
            for (BlockState state : glass.getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (k, model) -> new GlassJoinModel(model, glass));
            }
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        int[] open = null;
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof GlassJoin.Stairs stairs && stairs.base() == glass) {
                int covered = covered(n, d);
                if (covered != 0 && covered != 15) {
                    if (open == null) {
                        open = new int[6];
                    }
                    open[d.ordinal()] = 15 & ~covered;
                }
            }
        }
        return open == null ? data : data.derive().with(OPEN, open).build();
    }

    /** 向き d の隣の階段 stairs が、この面の 1/4 のどれを覆っているか（bits）。 */
    private static int covered(BlockState stairs, Direction d) {
        List<AABB> boxes = stairs.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
        int bits = 0;
        for (int[] cell : cells(d)) {
            // 隣の階段の、この面に接する層の 1/8 の箱の中心（隣のブロックの中の座標）
            double[] c = {cell[0] / 16.0 + 0.25, cell[1] / 16.0 + 0.25, cell[2] / 16.0 + 0.25};
            int axis = d.getAxis().ordinal();
            c[axis] = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.25 : 0.75;
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
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> plain = super.getQuads(state, side, rand, data, renderType);
        int[] open = data.get(OPEN);
        if (side == null || open == null || open[side.ordinal()] == 0 || plain.isEmpty()) {
            return plain;
        }
        int bits = open[side.ordinal()];
        return quads.computeIfAbsent(side.ordinal() * 16 + bits, k -> bake(side, bits));
    }

    /** 見える 1/4 だけの面（その組の枠の絵）。 */
    private List<BakedQuad> bake(Direction side, int bits) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(Singulo.id("block/" + name + "_join_" + bits));
        List<BakedQuad> out = new ArrayList<>(4);
        for (int[] cell : cells(side)) {
            if ((bits & quarter(side, cell)) == 0) {
                continue;
            }
            BlockElementFace face = new BlockElementFace(side, -1, "", new BlockFaceUV(uv(side, cell), 0));
            out.add(BAKERY.bakeQuad(new Vector3f(cell[0], cell[1], cell[2]), new Vector3f(cell[0] + 8, cell[1] + 8, cell[2] + 8), face,
                    sprite, side, BlockModelRotation.X0_Y0, null, true, io.github.genichimaruo.singulo.Singulo.id("generated_glass")));
        }
        return out;
    }
}
