package io.github.genichimaruo.singulo.mixin;
import io.github.genichimaruo.singulo.client.RayleighLighting;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Tag after NeoForge's alternative AO calculator has finished normalizing natural light. */
@Mixin(EnhancedBlockModelLighter.class)
public abstract class RayleighQuadLightMixin {
    @Unique private static void singulo$tag(BlockAndTintGetter level,BlockPos pos,BakedQuad quad,QuadInstance output){
        double x=0,y=0,z=0;
        for(int i=0;i<4;i++){var p=quad.position(i);x+=p.x();y+=p.y();z+=p.z();}
        var normal=quad.direction();
        BlockPos sample=BlockPos.containing(pos.getX()+x/4+normal.getStepX()*0.001,pos.getY()+y/4+normal.getStepY()*0.001,pos.getZ()+z/4+normal.getStepZ()*0.001);
        for(int i=0;i<4;i++)output.setLightCoords(i,RayleighLighting.tag(level,sample,output.getLightCoords(i)));
    }
    @Inject(method="prepareQuadAmbientOcclusion",at=@At("RETURN"))
    private void singulo$ao(BlockAndTintGetter level,BlockState state,BlockPos pos,BakedQuad quad,QuadInstance output,CallbackInfo ci){singulo$tag(level,pos,quad,output);}
    @Inject(method="prepareQuadFlat",at=@At("RETURN"))
    private void singulo$flat(BlockAndTintGetter level,BlockState state,BlockPos pos,int light,BakedQuad quad,QuadInstance output,CallbackInfo ci){singulo$tag(level,pos,quad,output);}
}
