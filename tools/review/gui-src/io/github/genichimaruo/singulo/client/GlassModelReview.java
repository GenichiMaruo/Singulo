package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Development-only checks using the actual baked models and stitched client atlas. */
final class GlassModelReview {
    private static int checks;
    private static List<BlockStateModelPart> parts(Minecraft mc, BlockState state, Map<BlockPos, BlockState> neighbors) {
        var model = mc.getModelManager().getBlockStateModelSet().get(state);
        if (!(model instanceof GlassModelBase)) throw new IllegalStateException("Glass model wrapper missing: " + state);
        var world = (BlockAndTintGetter) java.lang.reflect.Proxy.newProxyInstance(GlassModelReview.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")) {
                        BlockPos p=(BlockPos)args[0];
                        return p.equals(BlockPos.ZERO) ? state : neighbors.getOrDefault(p, Blocks.AIR.defaultBlockState());
                    }
                    return method.invoke(BlockAndTintGetter.EMPTY, args);
                });
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(world, BlockPos.ZERO, state, RandomSource.create(0), parts);
        if (parts.isEmpty()) throw new IllegalStateException("No glass parts");
        for (var part : parts) {
            if ((part.materialFlags() & BakedQuad.FLAG_TRANSLUCENT)==0) throw new IllegalStateException("Opaque glass part");
            for (Direction face : faces()) for (var quad : part.getQuads(face)) {
                var sprite=quad.materialInfo().sprite();
                if (sprite.contents().name().getPath().equals("missingno")) throw new IllegalStateException("Unstitched glass sprite");
                if ((quad.materialInfo().flags() & BakedQuad.FLAG_TRANSLUCENT)==0) throw new IllegalStateException("Opaque glass quad");
                for(int v=0;v<4;v++) if(!quad.position(v).isFinite()) throw new IllegalStateException("Invalid glass vertex");
            }
        }
        checks++;
        return parts;
    }
    private static List<Direction> faces() {
        var faces=new ArrayList<Direction>(Arrays.asList(Direction.values()));faces.add(null);return faces;
    }
    private static int count(List<BlockStateModelPart> parts, Direction face) {return parts.stream().mapToInt(p->p.getQuads(face).size()).sum();}
    static void check(Minecraft mc, String phase) {
        checks=0;
        Block[] glasses={SinguloBlocks.ANDROID_GATE_GLASS.get(),SinguloBlocks.ANDROID_BARRIER_GLASS.get()};
        for(Block glass:glasses) {
            var state=glass.defaultBlockState();
            var isolated=parts(mc,state,Map.of());
            for(Direction d:Direction.values()) {
                if(count(isolated,d)==0)throw new IllegalStateException("Isolated glass face absent");
                if(count(parts(mc,state,Map.of(BlockPos.ZERO.relative(d),state)),d)!=0)throw new IllegalStateException("Same-glass contact still rendered");
                var other=(glass==glasses[0]?glasses[1]:glasses[0]).defaultBlockState();
                if(count(parts(mc,state,Map.of(BlockPos.ZERO.relative(d),other)),d)==0)throw new IllegalStateException("Different glass incorrectly joined");
            }
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(glass).getPath();
            Block stair=SinguloBlocks.VARIANTS.get(id+"_stairs").get();
            for(var state0:stair.getStateDefinition().getPossibleStates()) {
                if(state0.getValue(StairBlock.WATERLOGGED))continue;
                if(count(parts(mc,state0,Map.of()),null)==0)throw new IllegalStateException("Stair interior uncull geometry absent");
                for(Direction d:Direction.values()) {
                    var joined=parts(mc,state0,Map.of(BlockPos.ZERO.relative(d),state));
                    if(count(joined,d)!=0)throw new IllegalStateException("Glass/stair contact still rendered");
                    if(count(joined,null)==0)throw new IllegalStateException("Stair interior lost beside full glass");
                }
            }
        }
        int partial=0;
        for(Block glass:new Block[]{SinguloBlocks.WHITE_GLASS_PANEL.get(),SinguloBlocks.BLACK_REINFORCED_GLASS.get(),
                SinguloBlocks.ANDROID_GATE_GLASS_PATTERNED.get(),SinguloBlocks.ANDROID_BARRIER_GLASS_PATTERNED.get()}) {
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(glass).getPath();
            Block stair=SinguloBlocks.VARIANTS.get(id+"_stairs").get();
            for(var state:stair.getStateDefinition().getPossibleStates()) {
                if(state.getValue(StairBlock.WATERLOGGED))continue;
                for(Direction d:Direction.values())for(var part:parts(mc,glass.defaultBlockState(),Map.of(BlockPos.ZERO.relative(d),state)))
                    for(var quad:part.getQuads(d))if(quad.materialInfo().sprite().contents().name().getPath().contains("_join_"))partial++;
            }
        }
        if(partial==0)throw new IllegalStateException("No partial glass/stair join textures");
        Singulo.LOGGER.info("SINGULO_GLASS_MODELS_COMPLETE {} checks={} partial={} isolated same different upper lower inner outer unculled atlas translucent",phase,checks,partial);
    }
}
