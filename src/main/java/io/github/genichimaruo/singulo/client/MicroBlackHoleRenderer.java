package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.reactor.MicroBlackHole;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** ブラックホール爆弾が開いた小さなブラックホール: 黒い地平線・降着円盤・景色の歪み。 */
public class MicroBlackHoleRenderer extends EntityRenderer<MicroBlackHole> {
    public MicroBlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MicroBlackHole hole, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float horizon = hole.horizon(partialTick);
        float time = hole.tickCount + partialTick;
        pose.pushPose();
        PenroseReactorRenderer.renderHole(pose, buffers, horizon, 0.7F, time * 3, hole.getPosition(partialTick), horizon * 8);
        pose.popPose();
    }

    @Override
    public boolean shouldRender(MicroBlackHole hole, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(MicroBlackHole hole) {
        return FxDraw.WHITE;
    }
}
