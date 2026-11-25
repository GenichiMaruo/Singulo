package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.MixedPanelSlab;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

/**
 * 白いパネル・黒色強化パネル・コラプサー強化パネル（と、その階段・ハーフブロック・重ねハーフブロック）の面のうち、となりの
 * 不透明なハーフブロック・階段に一部だけ覆われた面は、隠れずに見える所だけを、その形に沿った縁の絵で描く（0.23.0）。
 * 覆われた所は隣に隠れて見えないので描かない。ガラスの {@link GlassJoinModel} と同じく面を 1/4 ずつに分け、
 * 絵は tools/building_variants.py の panel_join_textures（見える 1/4 の組ごとの <パネル>_join_<bits>）。
 * 透けるもの（ガラスの階段など）が隣のときは、覆われた所も透けて見えるので、そのまま描く。
 */
final class PanelJoinModel extends BakedModelWrapper<BakedModel> {
    /** 面ごと: 0 は元の形のまま。それ以外は 1 + 見える 1/4 の組（下の層は下位4ビット、上の層は次の4ビット）。 */
    private static final ModelProperty<int[]> OPEN = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();

    /** 層: そのパネルの絵の名前と、その層の形（元のブロックなら full）。 */
    private record Layer(String texture, BlockState shape, boolean full) {
        int own(Direction face) {
            return full ? 15 : GlassJoinModel.quarters(shape, face, true);
        }

        /** 隣 neighbour で一部だけ覆われるなら、覆われずに見える 1/4 の組。変わらないなら -1。 */
        int clipped(BlockState neighbour, Direction face) {
            int covered = coverage(neighbour, face);
            if (covered == 0 || covered == 15) return -1;     // 覆われない・全部覆われる（全部ならバニラが面を消す）
            int own = own(face);
            if ((own & covered) == 0) return -1;
            return own & ~covered;
        }
    }

    /** 向き face の隣 neighbour が、こちらの面のどの 1/4 を覆うか（不透明なハーフブロック・階段だけ）。 */
    static int coverage(BlockState neighbour, Direction face) {
        Block b = neighbour.getBlock();
        if (!neighbour.canOcclude() || !(b instanceof SlabBlock || b instanceof StairBlock)) return 0;
        return GlassJoinModel.quarters(neighbour, face, false);
    }

    private final Layer first;
    @Nullable private final Layer second;
    private final Map<Integer, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private PanelJoinModel(BakedModel original, Layer first, @Nullable Layer second) {
        super(original);
        this.first = first;
        this.second = second;
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block panel : new Block[] {SinguloBlocks.WHITE_PANEL.get(), SinguloBlocks.BLACK_REINFORCED_PANEL.get(),
                SinguloBlocks.COLLAPSAR_REINFORCED_PANEL.get()}) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(panel).getPath();
            List<Block> blocks = new ArrayList<>(List.of(panel));
            for (String shape : new String[] {"_stairs", "_slab"}) {
                var variant = SinguloBlocks.VARIANTS.get(name + shape);
                if (variant != null) blocks.add(variant.get());
            }
            for (Block block : blocks) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    boolean full = block == panel || state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE;
                    event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                            (k, model) -> new PanelJoinModel(model, new Layer(name, state, full), null));
                }
            }
        }
        for (BlockState state : SinguloBlocks.MIXED_PANEL_SLAB.get().getStateDefinition().getPossibleStates()) {
            if (state.getValue(MixedPanelSlab.LOWER) == state.getValue(MixedPanelSlab.UPPER)) continue;
            BlockState lower = MixedPanelSlab.half(state, false), upper = MixedPanelSlab.half(state, true);
            Layer bottom = new Layer(state.getValue(MixedPanelSlab.LOWER).base, lower, false);
            Layer top = new Layer(state.getValue(MixedPanelSlab.UPPER).base, upper, false);
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (k, model) -> new PanelJoinModel(model, bottom, top));
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
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
        return open == null ? data : data.derive().with(OPEN, open).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> plain = super.getQuads(state, side, rand, data, renderType);
        int[] open = data.get(OPEN);
        if (side == null || open == null || open[side.ordinal()] == 0 || plain.isEmpty()) {
            return plain;
        }
        int bits = open[side.ordinal()] - 1;
        if (bits == 0) return List.of();
        return quads.computeIfAbsent(side.ordinal() * 256 + bits, k -> {
            List<BakedQuad> out = new ArrayList<>(4);
            if ((bits & 15) != 0) bake(side, bits & 15, first.texture(), out);
            if (second != null && (bits >>> 4) != 0) bake(side, bits >>> 4, second.texture(), out);
            return List.copyOf(out);
        });
    }

    /** 見える 1/4 だけの面（その組の縁の絵）。 */
    private void bake(Direction side, int bits, String name, List<BakedQuad> out) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(Singulo.id("block/" + name + "_join_" + bits));
        for (int[] cell : GlassJoinModel.cells(side)) {
            if ((bits & GlassJoinModel.quarter(side, cell)) == 0) {
                continue;
            }
            BlockElementFace face = new BlockElementFace(side, -1, "", new BlockFaceUV(GlassJoinModel.uv(side, cell), 0));
            out.add(BAKERY.bakeQuad(new Vector3f(cell[0], cell[1], cell[2]), new Vector3f(cell[0] + 8, cell[1] + 8, cell[2] + 8), face,
                    sprite, side, BlockModelRotation.X0_Y0, null, true));
        }
    }
}
