package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.cable.ConduitCover;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
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
                if (ConduitCover.of(state) == ConduitCover.State.BLOCK) {
                    event.getBakingResult().blockStateModels().computeIfPresent(state, (key, model) -> new CoverModel(model));
                }
            }
        }
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
    }    private static @Nullable BlockState cover(BlockAndTintGetter level, BlockPos pos) {
        return level.getModelData(pos).get(ConduitCover.MODEL_STATE);
    }
    private static BlockStateModel modelOf(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }
    @Override public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                       RandomSource random, List<BlockStateModelPart> parts) {
        BlockState cover = cover(level, pos);
        if (cover == null) super.collectParts(level, pos, state, random, parts);
        else modelOf(cover).collectParts(level, pos, cover, random, parts);
    }
    @Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState cover = cover(level, pos);
        return cover == null ? super.particleMaterial(level, pos, state) : modelOf(cover).particleMaterial(level, pos, cover);
    }
    @Override public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState cover = cover(level, pos);
        return cover == null ? super.materialFlags(level, pos, state) : modelOf(cover).materialFlags(level, pos, cover);
    }
    private static Block[] conduits() {
        return new Block[] {SinguloBlocks.COPPER_WIRE.get(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(), SinguloBlocks.TOPOLOGICAL_WIRE.get(),
                SinguloBlocks.HORIZON_BUS.get(), SinguloBlocks.FLUID_PIPE.get(), SinguloBlocks.VACUUM_JACKETED_PIPE.get(),
                SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get()};
    }


}
