package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** Extracts the mod's procedural animated mob geometry and layers before submission. */
abstract class ExtractedMobRenderer<T extends Mob,M extends ExtractedEntityModel<T>>
        extends SnapshotEntityRenderer<T> implements ExtractedLayerParent<T,M> {
    protected M model;
    private final List<ExtractedLayer<T,M>> layers=new ArrayList<>();
    protected ExtractedMobRenderer(EntityRendererProvider.Context context,M model,float shadow) {
        super(context);this.model=model;this.shadowRadius=shadow;
    }
    @Override public M getModel(){return model;}
    protected void addLayer(ExtractedLayer<T,M> layer){layers.add(layer);}
    public abstract Identifier getTextureLocation(T entity);
    protected void scale(T entity,PoseStack pose,float partialTick) {}
    protected void setupRotations(T entity,PoseStack pose,float age,float bodyYaw,float partialTick,float entityScale) {
        if(entity.isFullyFrozen()) bodyYaw+=(float)(Math.cos(entity.tickCount*3.25)*Math.PI*0.4);
        pose.mulPose(Axis.YP.rotationDegrees(180-bodyYaw));
        if(entity.deathTime>0){float death=Mth.sqrt(((entity.deathTime+partialTick-1)/20F)*1.6F);pose.mulPose(Axis.ZP.rotationDegrees(Math.min(1,death)*90));}
        else if(entity.isAutoSpinAttack()){pose.mulPose(Axis.XP.rotationDegrees(-90-entity.getXRot()));pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partialTick)*-75));}
        else if(entity.hasCustomName()) {
            String name=net.minecraft.ChatFormatting.stripFormatting(entity.getName().getString());
            if("Dinnerbone".equals(name)||"Grumm".equals(name)){pose.translate(0,(entity.getBbHeight()+0.1F)/entityScale,0);pose.mulPose(Axis.ZP.rotationDegrees(180));}
        }
    }
    @Override public void render(T entity,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        float body=Mth.rotLerp(partialTick,entity.yBodyRotO,entity.yBodyRot);
        float head=Mth.rotLerp(partialTick,entity.yHeadRotO,entity.yHeadRot);
        float headYaw=Mth.wrapDegrees(head-body), pitch=Mth.lerp(partialTick,entity.xRotO,entity.getXRot());
        float age=entity.tickCount+partialTick;
        float walk=entity.walkAnimation.position(partialTick),speed=Math.min(1,entity.walkAnimation.speed(partialTick));
        if(entity.isPassenger()||!entity.isAlive()){walk=0;speed=0;}
        pose.pushPose();
        float size=entity.getScale();pose.scale(size,size,size);
        setupRotations(entity,pose,age,body,partialTick,size);
        pose.scale(-1,-1,1);scale(entity,pose,partialTick);pose.translate(0,-1.501F,0);
        model.attackTime=entity.getAttackAnim(partialTick);
        model.setupAnim(entity,walk,speed,age,headYaw,pitch);
        int overlay=OverlayTexture.pack(OverlayTexture.u(0),OverlayTexture.v(entity.hurtTime>0||entity.deathTime>0));
        if(!entity.isInvisible()) model.renderToBuffer(pose,buffers.getBuffer(RenderTypes.entityCutout(getTextureLocation(entity))),light,overlay);
        else if(!entity.isInvisibleTo(Minecraft.getInstance().player)) model.renderToBuffer(pose,buffers.getBuffer(RenderTypes.entityTranslucent(getTextureLocation(entity))),light,overlay,0x26FFFFFF);
        for(var layer:layers) layer.render(pose,buffers,light,entity,walk,speed,partialTick,age,headYaw,pitch);
        pose.popPose();
    }
}