package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.ruin.HorizonBolt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 光弾: 白く光る芯と、撃った側の色のにじみ（脈打つ）。 */
public class HorizonBoltRenderer extends EntityRenderer<HorizonBolt> {
    public HorizonBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HorizonBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = bolt.tickCount + partialTick;
        float pulse = 1 + 0.15F * Mth.sin(t * 1.3F);
        pose.pushPose();
        pose.translate(0, bolt.getBbHeight() / 2, 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        var camera = entityRenderDispatcher.cameraOrientation();
        float size = bolt.size();
        int rgb = bolt.color();
        int core = 0xFFFFFF;
        int mid = ((((rgb >> 16) & 0xFF) + 255) / 2 << 16) | ((((rgb >> 8) & 0xFF) + 255) / 2 << 8) | (((rgb & 0xFF) + 255) / 2);
        FxDraw.billboard(pose, vc, camera, 0.9F * pulse * size, rgb, 90);
        FxDraw.billboard(pose, vc, camera, 0.5F * pulse * size, mid, 180);
        FxDraw.billboard(pose, vc, camera, 0.24F * size, core, 255);
        pose.popPose();
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HorizonBolt bolt) {
        return FxDraw.WHITE;
    }
}
