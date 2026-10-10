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
    /** 円柱の半径と、軸の根元（格納筒の底の内側）からの下端・上端の高さ。中の 3×3×7 に収まる。 */
    private static final float RADIUS = 1.1F;
    private static final float BOTTOM = 0.05F;
    private static final float TOP = 6.95F;

    public TiplerCylinderRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TiplerCylinderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!be.shownFormed()) {
            return;                                                     // 格納筒ができるまでは円柱を描かない
        }
        float spin = be.spin();
        net.minecraft.core.BlockPos axis = be.axis();
        pose.pushPose();
        pose.translate(axis.getX() - be.getBlockPos().getX() + 0.5, axis.getY() - be.getBlockPos().getY(),
                axis.getZ() - be.getBlockPos().getZ() + 0.5);
        if (spin > 0.05F && be.getLevel() != null) {
            timeMotes(be, pose, buffers, partialTick, spin);
        }
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
        vc.vertex(last.pose(), x, y, z).color(shade, shade, blue, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(last.normal(), nx, ny, nz).endVertex();
    }

    /**
     * 時間の粒: 円柱のまわりを、回転より速く巡りながら昇っていく紫の光の粒（時間が速く流れている）。
     * 円柱の上下の端には、時の輪がゆっくり脈打つ。
     */
    private static void timeMotes(TiplerCylinderBlockEntity be, PoseStack pose, MultiBufferSource buffers, float partialTick, float spin) {
        float time = be.getLevel().getGameTime() + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int i = 0; i < 18; i++) {
            float f = (time * 0.015F * (1 + spin) + i / 18F) % 1F;
            float ang = time * 0.12F * (1 + spin) + i * 2.4F;
            float r = RADIUS + 0.18F;
            pose.pushPose();
            pose.translate(Mth.cos(ang) * r, BOTTOM + f * (TOP - BOTTOM), Mth.sin(ang) * r);
            FxDraw.billboard(pose, glow, camera, 0.12F, i % 3 == 0 ? 0xFFFFFF : 0xC8A0FF, (int) (230 * Mth.sin(f * Mth.PI) * spin));
            pose.popPose();
        }
        for (float y : new float[]{BOTTOM + 0.05F, TOP - 0.05F}) {
            float a = 0.5F + 0.5F * Mth.sin(time * 0.2F + y);
            pose.pushPose();
            pose.translate(0, y, 0);
            FxDraw.ring(pose.last(), glow, RADIUS + 0.05F, RADIUS + 0.3F, 0xB890FF, (int) (160 * a * spin), 0);
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TiplerCylinderBlockEntity be) {
        return true;
    }

    public AABB getRenderBoundingBox(TiplerCylinderBlockEntity be) {
        return new AABB(be.axis()).expandTowards(0, 8, 0).inflate(3);
    }
}
