package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * ティプラー・シリンダーの円柱: コアの上（高さ1〜6）で縦に回る白い円柱。縞の濃淡で回転が見える。
 * 動いている間は縞が明るく光る。
 */
public class TiplerCylinderRenderer implements BlockEntityRenderer<TiplerCylinderBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int SIDES = 16;
    private static final float RADIUS = 0.42F;
    private static final float BOTTOM = 1.0F;
    private static final float TOP = 6.0F;

    public TiplerCylinderRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TiplerCylinderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        float spin = be.spin();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(be.angle(partialTick)));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE));
        PoseStack.Pose last = pose.last();
        int lit = spin > 0.05F ? LightTexture.FULL_BRIGHT : light;
        for (int i = 0; i < SIDES; i++) {
            float a0 = Mth.TWO_PI * i / SIDES;
            float a1 = Mth.TWO_PI * (i + 1) / SIDES;
            float x0 = Mth.cos(a0) * RADIUS;
            float z0 = Mth.sin(a0) * RADIUS;
            float x1 = Mth.cos(a1) * RADIUS;
            float z1 = Mth.sin(a1) * RADIUS;
            float nx = Mth.cos((a0 + a1) / 2);
            float nz = Mth.sin((a0 + a1) / 2);
            // 白と淡い灰の縞。回っている間は淡い青白く光る
            int shade = i % 2 == 0 ? 236 : (int) Mth.lerp(spin, 190, 160);
            int blue = i % 2 == 0 ? 240 : (int) Mth.lerp(spin, 196, 255);
            vertex(vc, last, x0, BOTTOM, z0, shade, blue, lit, nx, 0, nz);
            vertex(vc, last, x1, BOTTOM, z1, shade, blue, lit, nx, 0, nz);
            vertex(vc, last, x1, TOP, z1, shade, blue, lit, nx, 0, nz);
            vertex(vc, last, x0, TOP, z0, shade, blue, lit, nx, 0, nz);
            // 上と下のふた
            vertex(vc, last, 0, TOP, 0, 236, 240, lit, 0, 1, 0);
            vertex(vc, last, x0, TOP, z0, 236, 240, lit, 0, 1, 0);
            vertex(vc, last, x1, TOP, z1, 236, 240, lit, 0, 1, 0);
            vertex(vc, last, 0, TOP, 0, 236, 240, lit, 0, 1, 0);
            vertex(vc, last, 0, BOTTOM, 0, 236, 240, lit, 0, -1, 0);
            vertex(vc, last, x1, BOTTOM, z1, 236, 240, lit, 0, -1, 0);
            vertex(vc, last, x0, BOTTOM, z0, 236, 240, lit, 0, -1, 0);
            vertex(vc, last, 0, BOTTOM, 0, 236, 240, lit, 0, -1, 0);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int shade, int blue,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor(shade, shade, blue, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(last, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(TiplerCylinderBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 7, 0).inflate(1);
    }
}
