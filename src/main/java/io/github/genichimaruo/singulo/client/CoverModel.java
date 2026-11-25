package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.cable.ConduitCover;
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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * ブロックをはめ込んだカバー（{@link ConduitCover}）の見た目: ケーブル・パイプの代わりに、はめ込んだブロックのモデルを、その向きのまま描く
 * （面の隠し方・光・粒子の絵も、そのブロックのもの）。ブロックがまだ届いていない間は、もとのモデル（ケーブルと枠）のまま。
 * 包むのは、カバーがブロック入りとガラス入りの状態のモデル。
 * <p>ガラス入り（0.23.0）: もとのモデル（ケーブル・パイプと枠）を描いたまま、ガラスのモデルの各面を、枠の棒の内側（12×12 ドット）に
 * 縮め、1ドット奥へ下げた板として足す（枠の棒の外の面と重ならず、枠と中身が見える）。絵はガラスの中央の 12×12 ドットを使う。
 */
final class CoverModel extends BakedModelWrapper<BakedModel> {
    private CoverModel(BakedModel original) {
        super(original);
    }

    /** 電線・パイプの全種類の、ブロック入りの状態のモデルを包む。 */
    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block block : conduits()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!ConduitCover.inlaid(state)) {
                    continue;
                }
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                event.getModels().computeIfPresent(key, (k, model) -> new CoverModel(model));
            }
        }
        PANES.clear();
    }

    /** 枠の内側の大きさ（枠の棒は太さ2ドット）と、板を奥へ下げる量。 */
    private static final float PANE_SCALE = 12.0F / 16.0F, PANE_INSET = 1.0F / 16.0F;
    /** ガラスの面 → 枠にはめた板（モデルを焼き直すと捨てる）。 */
    private static final Map<BakedQuad, BakedQuad> PANES = new ConcurrentHashMap<>();

    private static boolean glassIn(BlockState state) {
        return ConduitCover.of(state) == ConduitCover.State.GLASS;
    }

    /** ガラスの面 quad を、面の中心へ向けて位置と絵の座標を縮め（中央の絵を使う）、面の向きの奥へ下げた板。 */
    private static BakedQuad pane(BakedQuad quad) {
        return PANES.computeIfAbsent(quad, q -> {
            int[] v = q.getVertices().clone();
            int stride = v.length / 4;
            float cx = 0, cy = 0, cz = 0, cu = 0, cv = 0;
            for (int i = 0; i < 4; i++) {
                int o = i * stride;
                cx += Float.intBitsToFloat(v[o]) / 4;
                cy += Float.intBitsToFloat(v[o + 1]) / 4;
                cz += Float.intBitsToFloat(v[o + 2]) / 4;
                cu += Float.intBitsToFloat(v[o + 4]) / 4;
                cv += Float.intBitsToFloat(v[o + 5]) / 4;
            }
            var n = q.getDirection().getNormal();
            for (int i = 0; i < 4; i++) {
                int o = i * stride;
                v[o] = Float.floatToRawIntBits(cx + (Float.intBitsToFloat(v[o]) - cx) * PANE_SCALE - n.getX() * PANE_INSET);
                v[o + 1] = Float.floatToRawIntBits(cy + (Float.intBitsToFloat(v[o + 1]) - cy) * PANE_SCALE - n.getY() * PANE_INSET);
                v[o + 2] = Float.floatToRawIntBits(cz + (Float.intBitsToFloat(v[o + 2]) - cz) * PANE_SCALE - n.getZ() * PANE_INSET);
                v[o + 4] = Float.floatToRawIntBits(cu + (Float.intBitsToFloat(v[o + 4]) - cu) * PANE_SCALE);
                v[o + 5] = Float.floatToRawIntBits(cv + (Float.intBitsToFloat(v[o + 5]) - cv) * PANE_SCALE);
            }
            return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
        });
    }

    /** はめ込んだブロックの色付け（草のような色の変わるブロック）を、カバーにも使う。 */
    static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof ConduitCover.Holder holder && holder.coverState() != null) {
                return Minecraft.getInstance().getBlockColors().getColor(holder.coverState(), level, pos, tint);
            }
            return 0xFFFFFFFF;
        }, conduits());
    }

    private static Block[] conduits() {
        return new Block[] {SinguloBlocks.COPPER_WIRE.get(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(), SinguloBlocks.TOPOLOGICAL_WIRE.get(),
                SinguloBlocks.HORIZON_BUS.get(), SinguloBlocks.FLUID_PIPE.get(), SinguloBlocks.VACUUM_JACKETED_PIPE.get(),
                SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get()};
    }

    @Nullable
    private static BlockState cover(ModelData data) {
        return data.get(ConduitCover.MODEL_STATE);
    }

    private static BakedModel modelOf(BlockState cover) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(cover);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        BlockState cover = cover(data);
        if (cover == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        BakedModel model = modelOf(cover);
        boolean glassType = renderType == null || model.getRenderTypes(cover, rand, ModelData.EMPTY).contains(renderType);
        if (!glassIn(state)) {
            return glassType ? model.getQuads(cover, side, rand, ModelData.EMPTY, renderType) : List.of();
        }
        // ガラス入り: ケーブルと枠（もとのモデルの描き方のとき）に、枠の内側のガラスの板を足す
        List<BakedQuad> frame = renderType == null || super.getRenderTypes(state, rand, data).contains(renderType)
                ? super.getQuads(state, side, rand, data, renderType) : List.of();
        if (!glassType) {
            return frame;
        }
        List<BakedQuad> out = new ArrayList<>(frame);
        for (BakedQuad quad : model.getQuads(cover, side, rand, ModelData.EMPTY, renderType)) {
            out.add(pane(quad));
        }
        return out;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState cover = cover(data);
        if (cover == null) {
            return super.getRenderTypes(state, rand, data);
        }
        ChunkRenderTypeSet glass = modelOf(cover).getRenderTypes(cover, rand, ModelData.EMPTY);
        return glassIn(state) ? ChunkRenderTypeSet.union(super.getRenderTypes(state, rand, data), glass) : glass;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState cover = cover(data);
        return cover == null ? super.getParticleIcon(data) : modelOf(cover).getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        // Forge 1.20.1 does not expose ModelData to the AO query. Covered states use full-block lighting.
        return (ConduitCover.of(state) == ConduitCover.State.BLOCK && !glassIn(state)) || super.useAmbientOcclusion(state, renderType);
    }
}
