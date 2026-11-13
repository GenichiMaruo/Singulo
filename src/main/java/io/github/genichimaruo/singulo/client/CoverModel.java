package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.cable.ConduitCover;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
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
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * ブロックをはめ込んだカバー（{@link ConduitCover}）の見た目: ケーブル・パイプの代わりに、はめ込んだブロックのモデルを、その向きのまま描く
 * （面の隠し方・光・粒子の絵も、そのブロックのもの）。ブロックがまだ届いていない間は、もとのモデル（ケーブルと枠）のまま。
 * 包むのは、カバーがブロック入りの状態のモデルだけ。
 */
final class CoverModel extends BakedModelWrapper<BakedModel> {
    private CoverModel(BakedModel original) {
        super(original);
    }

    /** 電線・パイプの全種類の、ブロック入りの状態のモデルを包む。 */
    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block block : conduits()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (ConduitCover.of(state) != ConduitCover.State.BLOCK) {
                    continue;
                }
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                event.getModels().computeIfPresent(key, (k, model) -> new CoverModel(model));
            }
        }
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
        if (renderType != null && !model.getRenderTypes(cover, rand, ModelData.EMPTY).contains(renderType)) {
            return List.of();
        }
        return model.getQuads(cover, side, rand, ModelData.EMPTY, renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState cover = cover(data);
        return cover == null ? super.getRenderTypes(state, rand, data) : modelOf(cover).getRenderTypes(cover, rand, ModelData.EMPTY);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState cover = cover(data);
        return cover == null ? super.getParticleIcon(data) : modelOf(cover).getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState cover = cover(data);
        return cover == null ? super.useAmbientOcclusion(state, data, renderType) : modelOf(cover).useAmbientOcclusion(cover, ModelData.EMPTY, renderType);
    }
}
