package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.MixedPanelSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;


/**
 * 白いパネル・黒色強化パネル・コラプサー強化パネル（と、その階段・ハーフブロック・重ねハーフブロック）の面のうち、となりの
 * 不透明なハーフブロック・階段に一部だけ覆われた面は、隠れずに見える所だけを、その形に沿った縁の絵で描く（0.23.0）。
 * 覆われた所は隣に隠れて見えないので描かない。ガラスの {@link GlassJoinModel} と同じく面を 1/4 ずつに分け、
 * 絵は tools/building_variants.py の panel_join_textures（見える 1/4 の組ごとの <パネル>_join_<bits>）。
 * 透けるもの（ガラスの階段など）が隣のときは、覆われた所も透けて見えるので、そのまま描く。
 */
final class PanelJoinModel extends DelegateBlockStateModel {
    /** 面ごと: 0 は元の形のまま。それ以外は 1 + 見える 1/4 の組（下の層は下位4ビット、上の層は次の4ビット）。 */

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
    private record Key(Direction side, int bits, BakedQuad.MaterialInfo material) {}
    private final Map<Key, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private PanelJoinModel(BlockStateModel original, Layer first, @Nullable Layer second) {
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
                    event.getBakingResult().blockStateModels().computeIfPresent(state,
                            (k, model) -> new PanelJoinModel(model, new Layer(name, state, full), null));
                }
            }
        }
        for (BlockState state : SinguloBlocks.MIXED_PANEL_SLAB.get().getStateDefinition().getPossibleStates()) {
            if (state.getValue(MixedPanelSlab.LOWER) == state.getValue(MixedPanelSlab.UPPER)) continue;
            BlockState lower = MixedPanelSlab.half(state, false), upper = MixedPanelSlab.half(state, true);
            Layer bottom = new Layer(state.getValue(MixedPanelSlab.LOWER).base, lower, false);
            Layer top = new Layer(state.getValue(MixedPanelSlab.UPPER).base, upper, false);
            event.getBakingResult().blockStateModels().computeIfPresent(state, (k, model) -> new PanelJoinModel(model, bottom, top));
        }
    }

    private int[] geometry(BlockAndTintGetter level, BlockPos pos) {
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

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int[] open = geometry(level, pos);
        if (open == null) {
            super.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> originals = new ArrayList<>();
        super.collectParts(level, pos, state, random, originals);
        if (originals.isEmpty()) return;
        // Multipart models contribute one part per half. Replace the combined face once.
        parts.add(mapPart(originals.getFirst(), side -> {
            List<BakedQuad> plain = originals.stream().flatMap(original -> original.getQuads(side).stream()).toList();
            if (side == null || open[side.ordinal()] == 0 || plain.isEmpty()) return plain;
            int bits = open[side.ordinal()] - 1;
            if (bits == 0) return List.of();
            return quads.computeIfAbsent(new Key(side, bits, plain.getFirst().materialInfo()), key -> {
                var out = new ArrayList<BakedQuad>(4);
                if ((bits & 15) != 0) bake(side, bits & 15, first.texture(), key.material(), out);
                if (second != null && (bits >>> 4) != 0) bake(side, bits >>> 4, second.texture(), key.material(), out);
                return List.copyOf(out);
            });
        }));
    }

    static BlockStateModelPart mapPart(BlockStateModelPart source, java.util.function.Function<Direction, List<BakedQuad>> faces) {
        return new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(Direction direction) { return faces.apply(direction); }
            @Override public boolean useAmbientOcclusion() { return source.useAmbientOcclusion(); }
            @Override public Material.Baked particleMaterial() { return source.particleMaterial(); }
            @Override public int materialFlags() { return source.materialFlags(); }
        };
    }

    private static final ModelBaker.Interner INTERNER = new ModelBaker.Interner() {
        @Override public Vector3fc vector(Vector3fc vector) { return vector; }
        @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) { return material; }
    };

    static void bake(Direction side, int bits, String texture, BakedQuad.MaterialInfo base, List<BakedQuad> out) {
        var sprite = GlassModelBase.sprite(Singulo.id("block/" + texture + "_join_" + bits));
        var material = new BakedQuad.MaterialInfo(sprite, base.layer(), base.itemRenderType(), base.tintIndex(),
                base.shade(), base.lightEmission(), base.ambientOcclusion());
        for (int[] c : GlassJoinModel.cells(side)) {
            if ((bits & GlassJoinModel.quarter(side, c)) == 0) continue;
            float[] uv = GlassJoinModel.uv(side, c);
            out.add(FaceBakery.bakeQuad(INTERNER, new Vector3f(c[0], c[1], c[2]),
                    new Vector3f(c[0] + 8, c[1] + 8, c[2] + 8), new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]),
                    com.mojang.math.Quadrant.R0, material, side, new ModelState() {}, null));
        }
    }
}
