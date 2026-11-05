package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class PipeSurveyRenderer {
    private static List<FluidPipeBlockEntity> cached=List.of();
    private static long refreshed=Long.MIN_VALUE;
    private static Object world;
    static void clear(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world){cached=List.of();world=null;refreshed=Long.MIN_VALUE;}}
    static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!SurveyGlassesItem.wearing(mc.player))return;
        long now=mc.level.getGameTime();
        if(world!=mc.level||refreshed==Long.MIN_VALUE||now-refreshed>=10){
            world=mc.level;refreshed=now;
            cached=FluidPipeBlockEntity.clientPipes().stream().filter(p->p.getLevel()==mc.level&&!p.isRemoved())
                    .filter(p->p.getBlockPos().distToCenterSqr(mc.player.position())<=24*24)
                    .sorted(Comparator.comparingDouble(p->p.getBlockPos().distToCenterSqr(mc.player.position()))).limit(128).toList();
        }
        PoseStack pose=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();VertexConsumer vc=buffers.getBuffer(RenderType.lines());
        for(var pipe:cached){
            if(pipe.isRemoved()||!pipe.fresh())continue;
            if(pipe.visualState()==2)LevelRenderer.renderLineBox(pose,vc,new AABB(pipe.getBlockPos()).deflate(0.22),1,0.55F,0.25F,0.9F);
            if(pipe.visualState()!=1)continue;
            Vec3 c=Vec3.atCenterOf(pipe.getBlockPos());
            for(Direction d:Direction.values()){
                if((pipe.outMask()&(1<<d.get3DDataValue()))!=0)arrow(pose,vc,c,d,false,now);
                if((pipe.inMask()&(1<<d.get3DDataValue()))!=0)arrow(pose,vc,c,d,true,now);
            }
        }
        pose.popPose();buffers.endBatch(RenderType.lines());
    }
    private static void arrow(PoseStack pose,VertexConsumer vc,Vec3 center,Direction side,boolean incoming,long now){
        Vec3 d=Vec3.atLowerCornerOf(side.getNormal());
        Vec3 offset=side.getAxis()==Direction.Axis.Y?new Vec3(0.23,0,0):new Vec3(0,0.23,0);
        Vec3 start=center.add(offset).add(d.scale(incoming?0.45:0)),end=center.add(offset).add(d.scale(incoming?0:0.45));
        line(pose,vc,start,end);
        Vec3 direction=end.subtract(start).normalize();double k=0.35+(now%20)/20.0*0.5;
        Vec3 tip=start.lerp(end,k),tail=tip.subtract(direction.scale(0.13));
        Vec3 across=direction.cross(side.getAxis()==Direction.Axis.Y?new Vec3(0,0,1):new Vec3(0,1,0)).normalize().scale(0.06);
        line(pose,vc,tail.add(across),tip);line(pose,vc,tail.subtract(across),tip);
    }
    private static void line(PoseStack pose,VertexConsumer vc,Vec3 a,Vec3 b){
        Vec3 n=b.subtract(a).normalize();
        vc.addVertex(pose.last(),(float)a.x,(float)a.y,(float)a.z).setColor(120,235,255,255).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
        vc.addVertex(pose.last(),(float)b.x,(float)b.y,(float)b.z).setColor(120,235,255,255).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
    }
    static void hud(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.player==null||mc.level==null||!SurveyGlassesItem.wearing(mc.player)
                ||!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FluidPipeBlockEntity pipe))return;
        Component text=!pipe.fresh()?Component.translatable("gui.singulo.pipe.stale"):
                pipe.visualState()==1?Component.translatable("gui.singulo.pipe.flow",pipe.visualAmount()):
                Component.translatable("gui.singulo.pipe."+(pipe.visualState()==2?"blocked":"idle"));
        var g=event.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2+12,y=mc.getWindow().getGuiScaledHeight()/2+12;
        int width=Math.max(40,Math.min(240,mc.getWindow().getGuiScaledWidth()-x-8));
        Component detail=pipe.fresh()&&pipe.visualState()==1?(pipe.mixed()?Component.translatable("gui.singulo.pipe.mixed"):pipe.visualFluid().getHoverName()):null;
        int textHeight=mc.font.split(text,width).size()*mc.font.lineHeight;
        int height=textHeight+(detail==null?0:mc.font.split(detail,width).size()*mc.font.lineHeight+3);
        g.fill(x-4,y-4,x+width+4,y+height+4,0xDF101927);
        g.renderOutline(x-4,y-4,width+8,height+8,0xFF405D70);
        g.drawWordWrap(mc.font,text,x,y,width,0xFFB9E7F2);
        if(detail!=null)g.drawWordWrap(mc.font,detail,x,y+textHeight+3,width,0xFFD6F0FF);
    }
}
