package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.lang.reflect.Proxy;
import java.util.List;

/** Extracts nested item meshes through their original buffer wrappers, preserving print clipping and tinting. */
final class ItemDraw {
    private static final ItemDraw INSTANCE=new ItemDraw();
    static ItemDraw get(){return INSTANCE;}
    void renderStatic(ItemStack stack,ItemDisplayContext context,int light,int overlay,PoseStack pose,MultiBufferSource buffers,@Nullable Level level,int seed){
        if(stack.isEmpty())return;
        ItemStackRenderState state=new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state,stack.copy(),context,level,null,seed);
        state.submit(pose,collector(buffers,0),light,overlay,0);
    }
    /** Unknown third-party submit types are retained for vanilla submission rather than silently discarded. */
    private static SubmitNodeCollector collector(MultiBufferSource buffers,int order) {
        return (SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->{
            if(method.getName().equals("order"))return collector(buffers,(int)args[0]);
            if(method.getName().equals("submitItem")) {
                drawItem((PoseStack)args[0],(ItemDisplayContext)args[1],(int)args[2],(int)args[3],(int[])args[5],(List<BakedQuad>)args[6],(ItemStackRenderState.FoilType)args[7],buffers);
                return null;
            }
            if(method.getName().equals("submitCustomGeometry")) {
                ((SubmitNodeCollector.CustomGeometryRenderer)args[2]).render(((PoseStack)args[0]).last(),buffers.getBuffer((RenderType)args[1]));
                return null;
            }
            if(method.getDeclaringClass()==Object.class) {
                return switch(method.getName()){case "toString"->"Singulo extracted item geometry";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
            }
            Object[] frozen=args.clone();
            boolean[] poseArguments=new boolean[frozen.length];
            for(int i=0;i<frozen.length;i++) {
                if(frozen[i] instanceof PoseStack p){frozen[i]=new org.joml.Matrix4f(p.last().pose());poseArguments[i]=true;}
                else if(frozen[i] instanceof int[] a)frozen[i]=a.clone();
                else if(frozen[i] instanceof List<?> l)frozen[i]=List.copyOf(l);
            }
            ModelSubmissions.add((outer,output)->{
                Object[] call=frozen.clone();
                for(int i=0;i<call.length;i++)if(poseArguments[i]){var m=(org.joml.Matrix4f)call[i];var p=new PoseStack();p.mulPose(outer.last().pose());p.mulPose(m);call[i]=p;}
                try {method.invoke(order==0?output:output.order(order),call);}
                catch(ReflectiveOperationException e){throw new IllegalStateException("Could not submit nested item geometry: "+method.getName(),e);}
            });
            return null;
        });
    }
    private static void drawItem(PoseStack pose,ItemDisplayContext context,int light,int overlay,int[] tints,List<BakedQuad> quads,ItemStackRenderState.FoilType foil,MultiBufferSource buffers) {
        var instance=new QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
        PoseStack.Pose decal=null;
        if(foil==ItemStackRenderState.FoilType.SPECIAL){decal=pose.last().copy();if(context==ItemDisplayContext.GUI)com.mojang.math.MatrixUtil.mulComponentWise(decal.pose(),.5F);else if(context.firstPerson())com.mojang.math.MatrixUtil.mulComponentWise(decal.pose(),.75F);}
        for(var quad:quads){
            var material=quad.materialInfo();var type=material.itemRenderType();int index=material.tintIndex();
            instance.setColor(material.isTinted()&&index>=0&&index<tints.length?tints[index]:-1);
            if(foil!=ItemStackRenderState.FoilType.NONE){VertexConsumer out=buffers.getBuffer(ItemFeatureRenderer.getFoilRenderType(type,true));if(decal!=null)out=new SheetedDecalTextureGenerator(out,decal,.0078125F);out.putBakedQuad(pose.last(),quad,instance);}
            buffers.getBuffer(type).putBakedQuad(pose.last(),quad,instance);
        }
    }
}
