package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.GravityDebris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** 重力の澱の瓦礫: 回りながら飛ぶ建材の塊と、うしろに引く菫色の光。 */
public class GravityDebrisRenderer extends EntityRenderer<GravityDebris> {
    public GravityDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityDebris entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(GravityDebris e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.3, 0);
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        FxDraw.billboard(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE)), camera, 1.1F,
                GravityRemnantRenderer.RIM, 90);
        pose.mulPose(Axis.YP.rotationDegrees(t * 17));
        pose.mulPose(Axis.XP.rotationDegrees(t * 11));
        pose.scale(0.55F, 0.55F, 0.55F);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.block(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }
}
