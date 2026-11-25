package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.block.MixedPanelGlassSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;


/**
 * ガラスとパネルの重ねハーフブロック（{@link MixedPanelGlassSlab}、0.23.0）の面のうち、隣に一部だけ隠れる所の描き方。
 * ガラスの半分はほかのガラスと同じく（{@link GlassJoinModel}）、パネルの半分はほかのパネルと同じく（{@link PanelJoinModel}）、
 * 隠れずに見える 1/4 だけを、見える形に沿った枠の絵（{@code <ガラスかパネル>_join_<bits>}）で描く。全部隠れる半分の面は描かない。
 * <ul>
 *   <li>ガラスの半分を隠すのは、この作品のガラス（{@link GlassJoin#matchingPart}）と、不透明な物。</li>
 *   <li>パネルの半分を隠すのは、不透明な物だけ。</li>
 * </ul>
 * 模型はガラスの半分（透ける描き方）とパネルの半分（不透明な描き方）の組（multipart）なので、描き方でどちらの半分かがわかる。
 */
final class MixedPanelGlassModel extends DelegateBlockStateModel {
    /** 向きごとに [ガラスの半分, パネルの半分]: 0 は元のまま、1 は描かない、2 + bits は見える 1/4 の組だけ枠の絵で描く。 */

    private final String glassTexture, panelTexture;
    private record Key(Direction side, int bits, boolean glass, BakedQuad.MaterialInfo material) {}
    private final Map<Key, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private MixedPanelGlassModel(BlockStateModel original, BlockState state) {
        super(original);
        boolean glassOnTop = state.getValue(MixedPanelGlassSlab.GLASS_ON_TOP);
        glassTexture = textureOf(MixedPanelGlassSlab.half(state, glassOnTop));
        panelTexture = textureOf(MixedPanelGlassSlab.half(state, !glassOnTop));
    }

    private static String textureOf(BlockState slab) {
        return BuiltInRegistries.BLOCK.getKey(((MixedPanelGlassSlab.Stackable) slab.getBlock()).base()).getPath();
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (BlockState state : SinguloBlocks.MIXED_PANEL_GLASS_SLAB.get().getStateDefinition().getPossibleStates()) {
            event.getBakingResult().blockStateModels().computeIfPresent(state, (k, model) -> new MixedPanelGlassModel(model, state));
        }
    }

    /** 向き d の隣 n が、こちらの面のどの 1/4 を、向こうが見えないように覆うか。 */
    private static int opaque(BlockState n, Direction d) {
        if (n.getBlock() instanceof MixedPanelGlassSlab) {
            return GlassJoinModel.quarters(MixedPanelGlassSlab.half(n, !n.getValue(MixedPanelGlassSlab.GLASS_ON_TOP)), d, false);
        }
        return n.canOcclude() ? GlassJoinModel.quarters(n, d, false) : 0;
    }

    /** 半分 slab の向き d の面の描き方（0 元のまま・1 描かない・2 + bits 見える 1/4 だけ）。 */
    private static int face(BlockState slab, boolean glass, BlockState n, Direction d) {
        int own = GlassJoinModel.quarters(slab, d, true);
        if (own == 0) return 0;                                       // その半分には、この向きの面がない
        int covered = opaque(n, d);
        if (glass) {
            BlockState part = GlassJoin.matchingPart(n, ((MixedPanelGlassSlab.Stackable) slab.getBlock()).base());
            if (part != null) covered |= GlassJoinModel.quarters(part, d, false);
        }
        int visible = own & ~covered;
        return visible == own ? 0 : visible == 0 ? 1 : 2 + visible;
    }

    private int[] geometry(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        boolean glassOnTop = state.getValue(MixedPanelGlassSlab.GLASS_ON_TOP);
        BlockState glass = MixedPanelGlassSlab.half(state, glassOnTop), panel = MixedPanelGlassSlab.half(state, !glassOnTop);
        int[] faces = null;
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            int g = face(glass, true, n, d), p = face(panel, false, n, d);
            if (g == 0 && p == 0) continue;
            if (faces == null) faces = new int[12];
            faces[d.ordinal() * 2] = g;
            faces[d.ordinal() * 2 + 1] = p;
        }
        return faces;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int[] faces = geometry(level, pos, state);
        if (faces == null) {
            super.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> originals = new ArrayList<>();
        super.collectParts(level, pos, state, random, originals);
        for (var original : originals) {
            parts.add(PanelJoinModel.mapPart(original, side -> {
                var plain = original.getQuads(side);
                if (side == null || plain.isEmpty()) return plain;
                var out = new ArrayList<BakedQuad>();
                boolean bakedGlass = false, bakedPanel = false;
                for (BakedQuad quad : plain) {
                    boolean glass = quad.materialInfo().layer().translucent();
                    int code = faces[side.ordinal() * 2 + (glass ? 0 : 1)];
                    if (code == 0) out.add(quad);
                    if (code <= 1 || (glass ? bakedGlass : bakedPanel)) continue;
                    if (glass) bakedGlass = true;
                    else bakedPanel = true;
                    out.addAll(quads.computeIfAbsent(new Key(side, code - 2, glass, quad.materialInfo()), key -> {
                        var clipped = new ArrayList<BakedQuad>();
                        PanelJoinModel.bake(side, key.bits(), glass ? glassTexture : panelTexture, key.material(), clipped);
                        return List.copyOf(clipped);
                    }));
                }
                return out;
            }));
        }
    }
}
