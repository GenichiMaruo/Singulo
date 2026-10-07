package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
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
 * イベントホライズン・シールド発生塔の見た目: オベリスクの先端に浮かんで回るリングと、守りが始まると上から降りてくる
 * 半透明のドーム（半径はシールドの半径）。ドームは縁（地面に近いところ）ほど濃く見え、コアのシールドは白く光る。
 */
public class ShieldTowerRenderer implements BlockEntityRenderer<ShieldTowerBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int LAT = 16;
    private static final int LON = 48;
    private static final float RING_HEIGHT = 10.0F;

    public ShieldTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShieldTowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        boolean active = be.shownProtection() > 0;
        boolean core = be.shownProtection() >= 5;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));

        // 先端のリング
        pose.pushPose();
        pose.translate(0.5, RING_HEIGHT + Mth.sin(time * 0.05F) * 0.15F, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * (active ? 3 : 0.5F)));
        pose.mulPose(Axis.XP.rotationDegrees(15));
        ring(vc, pose.last(), 1.0F, 1.5F, active ? 255 : 140, active ? LightTexture.FULL_BRIGHT : light);
        pose.popPose();

        float progress = be.domeProgress(partialTick);
        if (progress <= 0) {
            return;
        }
        float r = be.shownRadius();
        int red = core ? 230 : 120;
        int green = core ? 240 : 210;
        int blue = 255;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        PoseStack.Pose last = pose.last();
        // 天頂から progress × 90° まで降ろす
        float maxTheta = Mth.HALF_PI * progress;
        for (int i = 0; i < LAT; i++) {
            float t0 = maxTheta * i / LAT;
            float t1 = maxTheta * (i + 1) / LAT;
            int a0 = alpha(t0, time);
            int a1 = alpha(t1, time);
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON;
                float p1 = Mth.TWO_PI * (j + 1) / LON;
                // 外側と内側の両方から見えるよう、両面に描く
                vertex(vc, last, r, t0, p0, red, green, blue, a0);
                vertex(vc, last, r, t1, p0, red, green, blue, a1);
                vertex(vc, last, r, t1, p1, red, green, blue, a1);
                vertex(vc, last, r, t0, p1, red, green, blue, a0);
                vertex(vc, last, r, t0, p1, red, green, blue, a0);
                vertex(vc, last, r, t1, p1, red, green, blue, a1);
                vertex(vc, last, r, t1, p0, red, green, blue, a1);
                vertex(vc, last, r, t0, p0, red, green, blue, a0);
            }
        }
        pose.popPose();
    }

    /** 縁ほど濃く、ゆっくり降りる明るい帯が流れる。 */
    private static int alpha(float theta, float time) {
        float rim = theta / Mth.HALF_PI;
        float band = Mth.sin(theta * 6 - time * 0.05F) * 0.5F + 0.5F;
        return (int) (12 + 40 * rim * rim + 14 * band);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi,
                               int red, int green, int blue, int alpha) {
        float x = Mth.sin(theta) * Mth.cos(phi);
        float y = Mth.cos(theta);
        float z = Mth.sin(theta) * Mth.sin(phi);
        vc.addVertex(last, x * r, y * r, z * r).setColor(red, green, blue, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, x, y, z);
    }

    /** 平たいリング（両面）。 */
    static void ring(VertexConsumer vc, PoseStack.Pose last, float inner, float outer, int bright, int light) {
        int seg = 32;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg;
            float a1 = Mth.TWO_PI * (s + 1) / seg;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            for (int side = 0; side < 2; side++) {
                float ny = side == 0 ? 1 : -1;
                float[][] q = side == 0
                        ? new float[][]{{c0 * inner, s0 * inner}, {c0 * outer, s0 * outer}, {c1 * outer, s1 * outer}, {c1 * inner, s1 * inner}}
                        : new float[][]{{c1 * inner, s1 * inner}, {c1 * outer, s1 * outer}, {c0 * outer, s0 * outer}, {c0 * inner, s0 * inner}};
                for (float[] p : q) {
                    vc.addVertex(last, p[0], 0, p[1]).setColor(bright, bright, 255, 230).setUv(0, 0)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, ny, 0);
                }
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ShieldTowerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(ShieldTowerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(12, be.shownRadius()));
    }
}
