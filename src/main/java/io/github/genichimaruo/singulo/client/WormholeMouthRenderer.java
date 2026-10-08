package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ワームホールの口: 台座の上に浮かぶ黒い球（喉）と、その周りを回る薄紫の光の輪。喉の大きさに比例して大きくなる。
 * 重力レンズを小さく掛けて、周りの景色をゆがめる。
 */
public class WormholeMouthRenderer implements BlockEntityRenderer<WormholeMouthBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int LAT = 10;
    private static final int LON = 16;

    public WormholeMouthRenderer(BlockEntityRendererProvider.Context context) {}

    public static float throatRadius(int size) {
        return 0.2F + 0.15F * size;
    }

    @Override
    public void render(WormholeMouthBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        int size = be.size();
        if (size <= 0 || be.getLevel() == null) {
            return;
        }
        float r = throatRadius(size);
        float time = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0.5);
        sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(WHITE)), r);
        pose.mulPose(Axis.YP.rotationDegrees(time * 4));
        pose.mulPose(Axis.XP.rotationDegrees(20));
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        ShieldTowerRenderer.ring(glow, pose.last(), r * 1.15F, r * 2.0F, 200, LightTexture.FULL_BRIGHT);
        pose.popPose();
        Vec3 c = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0);
        GravitationalLensing.add(c, r * GravitationalLensing.EINSTEIN_PER_HORIZON, 0.6F);
    }

    private static void sphere(PoseStack.Pose last, VertexConsumer vc, float r) {
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / LAT - Mth.HALF_PI;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON;
                float p1 = Mth.TWO_PI * (j + 1) / LON;
                v(vc, last, r, t0, p0);
                v(vc, last, r, t1, p0);
                v(vc, last, r, t1, p1);
                v(vc, last, r, t0, p1);
            }
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi) {
        float x = Mth.cos(theta) * Mth.cos(phi);
        float y = Mth.sin(theta);
        float z = Mth.cos(theta) * Mth.sin(phi);
        vc.addVertex(last, x * r, y * r, z * r).setColor(0, 0, 0, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0).setNormal(last, x, y, z);
    }

    @Override
    public AABB getRenderBoundingBox(WormholeMouthBlockEntity be) {
        // 口が画面の外に出ても、周りの重力レンズが画面にかかっている間は描く
        return new AABB(be.getBlockPos()).inflate(8);
    }

    @Override
    public boolean shouldRenderOffScreen(WormholeMouthBlockEntity be) {
        return true;
    }
}
