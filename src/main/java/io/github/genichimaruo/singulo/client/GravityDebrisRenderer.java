package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.GravityDebris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/** 重力の澱の瓦礫: 回りながら飛ぶ建材の塊と、うしろに引く菫色の光。 */
public class GravityDebrisRenderer extends SnapshotEntityRenderer<GravityDebris> {
    public GravityDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

     public Identifier getTextureLocation(GravityDebris entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(GravityDebris e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.3, 0);
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        FxDraw.billboard(pose, GlowDepth.of(buffers, FxDraw.WHITE), camera, 1.1F,
                GravityRemnantRenderer.RIM, 90);
        pose.mulPose(Axis.YP.rotationDegrees(t * 17));
        pose.mulPose(Axis.XP.rotationDegrees(t * 11));
        pose.scale(0.55F, 0.55F, 0.55F);
        pose.translate(-0.5, -0.5, -0.5);
        BlockDraw.get().renderSingleBlock(e.block(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Vanilla label and leash submission is handled by SnapshotEntityRenderer.submit.
    }
}
