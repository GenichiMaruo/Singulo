package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.android.AndroidLaser;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 床から立つ光の柱。予告: 外から縮んでくる輪と、満ちていく円、回る刻み。柱: 白い芯と色の外殻、根元に広がる輪。
 */
public class AndroidLaserRenderer extends EntityRenderer<AndroidLaser> {
    public AndroidLaserRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AndroidLaser laser) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(AndroidLaser laser, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = laser.tickCount + partialTick;
        int rgb = laser.color();
        float r = (float) AndroidLaser.RADIUS;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0, 0.03, 0);
        if (t < AndroidLaser.WARN) {
            float k = t / AndroidLaser.WARN;
            float shrink = Mth.lerp(k, 2.4F, 1.0F) * r;
            FxDraw.ring(pose.last(), vc, shrink - 0.06F, shrink, rgb, 210, 120);
            FxDraw.ring(pose.last(), vc, 0, r * k, rgb, 60 + (int) (60 * k), 60 + (int) (60 * k));
            FxDraw.ring(pose.last(), vc, r - 0.05F, r, 0xFFFFFF, 160, 160);
            pose.mulPose(Axis.YP.rotationDegrees(t * 9));
            for (int i = 0; i < 8; i++) {
                float a = Mth.TWO_PI * i / 8;
                FxDraw.beam(pose.last(), vc, new Vec3(Mth.cos(a) * r * 0.75, 0, Mth.sin(a) * r * 0.75),
                        new Vec3(Mth.cos(a) * r, 0, Mth.sin(a) * r), 0.04F, rgb, 200);
            }
            if (k > 0.75F && (int) (t * 2) % 2 == 0) {
                FxDraw.ring(pose.last(), vc, 0, r, 0xFFFFFF, 90, 90);
            }
        } else {
            float b = t - AndroidLaser.WARN;
            float grow = Mth.clamp(b / 2, 0, 1);
            float fade = 1 - Mth.clamp((b - AndroidLaser.BEAM) / 6, 0, 1);
            float w = r * 2 * grow * (0.6F + 0.4F * fade);
            Vec3 top = new Vec3(0, AndroidLaser.HEIGHT * (0.6 + 0.4 * grow), 0);
            FxDraw.beam(pose.last(), vc, Vec3.ZERO, top, w, rgb, (int) (130 * fade));
            FxDraw.beam(pose.last(), vc, Vec3.ZERO, top, w * 0.35F, 0xFFFFFF, (int) (240 * fade));
            float ring = r * (1 + b * 0.25F);
            FxDraw.ring(pose.last(), vc, ring * 0.8F, ring, rgb, (int) (200 * fade), 0);
        }
        pose.popPose();
    }
}
