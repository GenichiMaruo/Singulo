package io.github.genichimaruo.singulo.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
final class ShapeLines {
    static void renderLineBox(PoseStack pose,VertexConsumer out,AABB box,float r,float g,float b,float a){
        int color=net.minecraft.util.ARGB.colorFromFloat(a,r,g,b);
        net.minecraft.client.renderer.ShapeRenderer.renderShape(pose,out,net.minecraft.world.phys.shapes.Shapes.create(box),0,0,0,color,1);
    }
}