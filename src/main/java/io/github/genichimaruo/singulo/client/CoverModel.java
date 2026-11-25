package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.cable.ConduitCover;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

/** Delegates covered conduit geometry, materials and particles to the inserted block. */
final class CoverModel extends DelegateBlockStateModel {
    private CoverModel(BlockStateModel original) { super(original); }
    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (Block block : conduits()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (ConduitCover.inlaid(state)) {
                    event.getBakingResult().blockStateModels().computeIfPresent(state, (key, model) -> new CoverModel(model));
                }
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
            Vector3f center = new Vector3f();
            float cu = 0, cv = 0;
            for (int i = 0; i < 4; i++) {
                center.add(q.position(i));
                cu += net.minecraft.client.model.geom.builders.UVPair.unpackU(q.packedUV(i)) * 0.25F;
                cv += net.minecraft.client.model.geom.builders.UVPair.unpackV(q.packedUV(i)) * 0.25F;
            }
            center.mul(0.25F);
            var normal = q.direction().getUnitVec3i();
            Vector3f inset = new Vector3f(normal.getX(), normal.getY(), normal.getZ()).mul(PANE_INSET);
            Vector3f[] positions = new Vector3f[4];
            long[] uv = new long[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = new Vector3f(q.position(i)).sub(center).mul(PANE_SCALE).add(center).sub(inset);
                uv[i] = net.minecraft.client.model.geom.builders.UVPair.pack(cu + (net.minecraft.client.model.geom.builders.UVPair.unpackU(q.packedUV(i)) - cu) * PANE_SCALE,
                        cv + (net.minecraft.client.model.geom.builders.UVPair.unpackV(q.packedUV(i)) - cv) * PANE_SCALE);
            }
            return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uv[0], uv[1], uv[2], uv[3],
                    q.direction(), q.materialInfo(), q.bakedNormals(), q.bakedColors());
        });
    }

    static void registerColors(RegisterColorHandlersEvent.BlockTintSources event) {
        // Reserve independent delegates for material tint indices, not only vanilla's first layer.
        var tints = new java.util.ArrayList<net.minecraft.client.color.block.BlockTintSource>();
        int count = 1;
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            count = Math.max(count, event.getBlockColors().getTintSources(block.defaultBlockState()).size());
        }
        for (int index=0; index<count; index++) {
            final int layer=index;
            tints.add(new net.minecraft.client.color.block.BlockTintSource() {
                @Override public int color(BlockState state) { return 0xFFFFFFFF; }
                @Override public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    BlockState cover = cover(level,pos);
                    if(cover==null) return 0xFFFFFFFF;
                    var tint=event.getBlockColors().getTintSource(cover,layer);
                    return tint==null ? 0xFFFFFFFF : tint.colorInWorld(cover,level,pos);
                }
            });
        }
        event.register(tints, conduits());
    }

    private static @Nullable BlockState cover(BlockAndTintGetter level, BlockPos pos) {
        return level.getModelData(pos).get(ConduitCover.MODEL_STATE);
    }
    private static BlockStateModel modelOf(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }
    @Override public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                       RandomSource random, List<BlockStateModelPart> parts) {
        BlockState cover = cover(level, pos);
        if (cover == null) {
            super.collectParts(level, pos, state, random, parts);
        } else if (!glassIn(state)) {
            modelOf(cover).collectParts(level, pos, cover, random, parts);
        } else {
            super.collectParts(level, pos, state, random, parts);
            var inlay = new ArrayList<BlockStateModelPart>();
            modelOf(cover).collectParts(random, inlay);
            for (var part : inlay) {
                parts.add(PanelJoinModel.mapPart(part, side -> part.getQuads(side).stream().map(CoverModel::pane).toList()));
            }
        }
    }
    @Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState cover = cover(level, pos);
        return (cover == null || glassIn(state)) ? super.particleMaterial(level, pos, state) : modelOf(cover).particleMaterial(level, pos, cover);
    }
    @Override public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState cover = cover(level, pos);
        int frame = super.materialFlags(level, pos, state);
        if (cover == null) return frame;
        int coverFlags = modelOf(cover).materialFlags(level, pos, cover);
        return glassIn(state) ? frame | coverFlags : coverFlags;
    }
    private static Block[] conduits() {
        return new Block[] {SinguloBlocks.COPPER_WIRE.get(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(), SinguloBlocks.TOPOLOGICAL_WIRE.get(),
                SinguloBlocks.HORIZON_BUS.get(), SinguloBlocks.FLUID_PIPE.get(), SinguloBlocks.VACUUM_JACKETED_PIPE.get(),
                SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get()};
    }


}
