package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** Immutable GUI quadrilateral, preserving the extraction pose and clipping rectangle. */
final class GuiQuadState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final float[] xs, ys;
    private final int color;
    private final @Nullable ScreenRectangle scissor, bounds;
    GuiQuadState(GuiGraphicsExtractor graphics, float[] xs, float[] ys, int color) {
        this.pose = new Matrix3x2f(graphics.pose());
        this.xs = xs.clone(); this.ys = ys.clone(); this.color = color;
        this.scissor = graphics.peekScissorStack();
        float minX=xs[0],maxX=xs[0],minY=ys[0],maxY=ys[0];
        for (int i=1;i<4;i++) { minX=Math.min(minX,xs[i]);maxX=Math.max(maxX,xs[i]);minY=Math.min(minY,ys[i]);maxY=Math.max(maxY,ys[i]); }
        int x=(int)Math.floor(minX),y=(int)Math.floor(minY);
        ScreenRectangle area=new ScreenRectangle(x,y,(int)Math.ceil(maxX)-x,(int)Math.ceil(maxY)-y).transformMaxBounds(pose);
        this.bounds=scissor==null ? area : scissor.intersection(area);
    }
    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public @Nullable ScreenRectangle scissorArea() { return scissor; }
    @Override public @Nullable ScreenRectangle bounds() { return bounds; }
    @Override public void buildVertices(VertexConsumer consumer) {
        // Both windings retain the previous side selector's two-sided faces.
        for(int i:new int[]{0,3,2,1,0,1,2,3}) consumer.addVertexWith2DPose(pose,xs[i],ys[i]).setColor(color);
    }
}