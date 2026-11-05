package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Geometry-based effects remain readable without shader packs or screen flashes. */
public final class CosmicChamberRenderer implements BlockEntityRenderer<CosmicChamberBlockEntity> {
    private static final ResourceLocation WHITE=Singulo.id("textures/misc/white.png");
    public CosmicChamberRenderer(BlockEntityRendererProvider.Context context){}
    @Override public int getViewDistance(){return 96;}
    @Override public boolean shouldRenderOffScreen(CosmicChamberBlockEntity be){return true;}
    @Override public AABB getRenderBoundingBox(CosmicChamberBlockEntity be){return new AABB(be.getBlockPos()).inflate(14);}
    @Override public void render(CosmicChamberBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null||be.status()!=0)return;
        float time=(be.getLevel().getGameTime()%24000+partial)/20F;
        boolean detail=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))<48*48;
        pose.pushPose();pose.translate(0.5,0,0.5);pose.mulPose(Axis.YP.rotationDegrees(-be.back().toYRot()));
        // These custom types share BufferSource's fallback buffer. Keep one live consumer;
        // requesting a different type finishes and invalidates the preceding consumer.
        VertexConsumer solid=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        VertexConsumer glow=solid;
        if(be.kind()==CosmicChamberBlockEntity.RESERVOIR){
            if(!be.tank().isEmpty()){
                var fluid=be.tank().getFluid();var ext=IClientFluidTypeExtensions.of(fluid.getFluid());
                var still=ext.getStillTexture(fluid);
                var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still==null
                        ?net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation():still);
                VertexConsumer liquid=sprite.wrap(buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)));
                float fill=fluid.getAmount()/(float)CosmicChamberBlockEntity.FLUID_CAPACITY;
                int tint=fluid.getFluid()==net.minecraft.world.level.material.Fluids.WATER
                        ?ext.getTintColor(fluid.getFluid().defaultFluidState(),be.getLevel(),be.getBlockPos()):ext.getTintColor(fluid);
                float bottom=5.85F-Math.max(0.035F,4.75F*fill);
                int fluidLight=LightTexture.pack(Math.max(12,LightTexture.block(light)),LightTexture.sky(light));
                FxDraw.box(pose,liquid,-1.35F,bottom,0.65F,1.35F,5.85F,3.35F,fluidLight,tint&0xFFFFFF,210);
                glow=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
                FxDraw.box(pose,glow,-1.39F,5.87F,0.61F,1.39F,5.91F,3.39F,LightTexture.FULL_BRIGHT,0x72DFEF,110);
            }
            for(int i=0;i<4;i++){float x=i%2==0?-1.65F:1.65F,z=i<2?0.35F:3.65F;
                FxDraw.box(pose,glow,x,1,z,x+0.035F,5.9F,z+0.035F,LightTexture.FULL_BRIGHT,0x72DFEF,80);}
        }else if(be.kind()==CosmicChamberBlockEntity.ACCUMULATOR){
            float fill=be.stored()/(float)be.capacity(),spacing=Mth.lerp(fill,0.5F,0.13F);
            for(int i=0;i<8;i++){
                float y=3.45F+(i-3.5F)*spacing;
                FxDraw.box(pose,solid,-2.1F,y,0.9F,2.1F,y+0.08F,5.1F,light,FxDraw.lerpRgb(0x302B42,0x5A5070,fill));
                for(int edge=0;edge<2;edge++){
                    float x=edge==0?-2.12F:2.08F;
                    FxDraw.box(pose,glow,x,y,0.88F,x+0.04F,y+0.08F,5.12F,LightTexture.FULL_BRIGHT,0xA3DCEB,(int)(60+100*fill));
                }
            }
            if(detail&&be.lastOut()>0)for(int i=0;i<4;i++){
                float y=1+((time*1.2F+i*0.35F)%1)*4.5F,x=i%2==0?-2.65F:2.65F,z=i<2?0.35F:5.65F;
                FxDraw.box(pose,glow,x,y,z,x+0.08F,y+0.3F,z+0.08F,LightTexture.FULL_BRIGHT,0xD3F8FF,190);
            }
        }else if(be.core()){
            pose.pushPose();pose.translate(0,3.5,2);
            FxDraw.sphere(pose.last(),solid,0.32F,0x020307,LightTexture.FULL_BRIGHT);
            pose.mulPose(Axis.XP.rotationDegrees(65));FxDraw.ring(pose.last(),glow,0.38F,0.64F,0xA9DDFB,140,60);pose.popPose();
            float k=be.progress()/(float)Math.max(1,be.duration());
            if(!be.reserved().isEmpty()){
                float end=3+7*k,width=0.27F*(1-k)+0.025F;
                FxDraw.box(pose,glow,-width,3.5F-width,2.7F,width,3.5F+width,end,LightTexture.FULL_BRIGHT,0xB7E4FF,180);
                for(int i=0;i<(detail?6:2);i++){
                    float a=time*0.9F+i*Mth.TWO_PI/6,x=0.18F*Mth.cos(a),y=3.5F+0.18F*Mth.sin(a);
                    FxDraw.box(pose,glow,x,y,4,x+0.025F,y+0.025F,10.6F,LightTexture.FULL_BRIGHT,0x77CDE4,110);
                }
            }
            pose.pushPose();pose.translate(0,3.5,11);pose.mulPose(Axis.ZP.rotation(time*0.7F));
            FxDraw.ring(pose.last(),glow,0.8F,1.05F,0xADE5F6,160,80);
            FxDraw.box(pose,solid,-0.7F,-0.7F,-0.3F,0.7F,0.7F,0.3F,light,0x253C50);pose.popPose();
        }
        pose.popPose();
    }
}
