package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** 光る輪・黒い球・光の玉・ビームを描く小さな道具（すべて白いテクスチャに頂点の色を付けて描く）。 */
final class FxDraw {
    static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private FxDraw() {}

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int rgb, int alpha,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
    }

    /** 中心が原点の球（緯度・経度の帯で）。 */
    static void sphere(PoseStack.Pose last, VertexConsumer vc, float r, int rgb, int light) {
        int lat = 10;
        int lon = 14;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon;
                float p1 = Mth.TWO_PI * (j + 1) / lon;
                sphereVertex(vc, last, r, t0, p0, rgb, light);
                sphereVertex(vc, last, r, t1, p0, rgb, light);
                sphereVertex(vc, last, r, t1, p1, rgb, light);
                sphereVertex(vc, last, r, t0, p1, rgb, light);
            }
        }
    }

    private static void sphereVertex(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi, int rgb, int light) {
        float x = Mth.cos(theta) * Mth.cos(phi);
        float y = Mth.sin(theta);
        float z = Mth.cos(theta) * Mth.sin(phi);
        vertex(vc, last, x * r, y * r, z * r, rgb, 255, light, x, y, z);
    }

    /** XZ 平面の輪（両面）。内側の色 inner と外側の色 outer、外側は透明に近づける。 */
    static void ring(PoseStack.Pose last, VertexConsumer vc, float inner, float outer, int rgb, int alphaIn, int alphaOut) {
        int seg = 40;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg;
            float a1 = Mth.TWO_PI * (s + 1) / seg;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            vertex(vc, last, c0 * inner, 0, s0 * inner, rgb, alphaIn, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alphaOut, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alphaOut, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alphaIn, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alphaIn, FULL_BRIGHT, 0, -1, 0);
            vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alphaOut, FULL_BRIGHT, 0, -1, 0);
            vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alphaOut, FULL_BRIGHT, 0, -1, 0);
            vertex(vc, last, c0 * inner, 0, s0 * inner, rgb, alphaIn, FULL_BRIGHT, 0, -1, 0);
        }
    }

    /**
     * 水平な三日月（XZ 平面、中心が原点、正面は -Z）。角度は正面から測る（正が +X 側）。
     * fromDeg から toDeg まで、両端が細く真ん中が太い（太さ maxWidth）。外側の縁ほど透明に近づける。
     */
    static void crescent(PoseStack.Pose last, VertexConsumer vc, float fromDeg, float toDeg, float radius, float maxWidth,
                         int rgb, int alpha) {
        int seg = 28;
        for (int k = 0; k < seg; k++) {
            float u0 = (float) k / seg;
            float u1 = (float) (k + 1) / seg;
            float a0 = (fromDeg + (toDeg - fromDeg) * u0) * Mth.DEG_TO_RAD;
            float a1 = (fromDeg + (toDeg - fromDeg) * u1) * Mth.DEG_TO_RAD;
            float w0 = maxWidth * (float) Math.pow(Mth.sin(Mth.PI * u0), 0.8);
            float w1 = maxWidth * (float) Math.pow(Mth.sin(Mth.PI * u1), 0.8);
            float i0 = radius - w0 / 2;
            float o0 = radius + w0 / 2;
            float i1 = radius - w1 / 2;
            float o1 = radius + w1 / 2;
            float s0 = Mth.sin(a0);
            float c0 = -Mth.cos(a0);
            float s1 = Mth.sin(a1);
            float c1 = -Mth.cos(a1);
            int out = alpha / 4;
            for (int side = 0; side < 2; side++) {
                float ny = side == 0 ? 1 : -1;
                if (side == 0) {
                    vertex(vc, last, s0 * i0, 0, c0 * i0, rgb, alpha, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s0 * o0, 0, c0 * o0, rgb, out, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s1 * o1, 0, c1 * o1, rgb, out, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s1 * i1, 0, c1 * i1, rgb, alpha, FULL_BRIGHT, 0, ny, 0);
                } else {
                    vertex(vc, last, s1 * i1, 0, c1 * i1, rgb, alpha, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s1 * o1, 0, c1 * o1, rgb, out, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s0 * o0, 0, c0 * o0, rgb, out, FULL_BRIGHT, 0, ny, 0);
                    vertex(vc, last, s0 * i0, 0, c0 * i0, rgb, alpha, FULL_BRIGHT, 0, ny, 0);
                }
            }
        }
    }

    /** カメラの方を向いた光の板（中心が原点、一辺 size）。 */
    static void billboard(PoseStack pose, VertexConsumer vc, Quaternionf camera, float size, int rgb, int alpha) {
        pose.pushPose();
        pose.mulPose(camera);
        PoseStack.Pose last = pose.last();
        float h = size / 2;
        vertex(vc, last, -h, -h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
        vertex(vc, last, h, -h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
        vertex(vc, last, h, h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
        vertex(vc, last, -h, h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
        pose.popPose();
    }

    /** from から to への光の帯（向きを変えた4枚の板を重ねて、どこから見ても太さが出るように）。座標は今の PoseStack の原点から。 */
    static void beam(PoseStack.Pose last, VertexConsumer vc, Vec3 from, Vec3 to, float width, int rgb, int alpha) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 dir = axis.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up).normalize();
        Vec3 side2 = dir.cross(side).normalize();
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 * k;
            Vec3 o = side.scale(Math.cos(a)).add(side2.scale(Math.sin(a))).scale(width / 2);
            Vec3 n = side2.scale(Math.cos(a)).subtract(side.scale(Math.sin(a)));
            float nx = (float) n.x;
            float ny = (float) n.y;
            float nz = (float) n.z;
            vertex(vc, last, (float) (from.x - o.x), (float) (from.y - o.y), (float) (from.z - o.z), rgb, alpha, FULL_BRIGHT, nx, ny, nz);
            vertex(vc, last, (float) (from.x + o.x), (float) (from.y + o.y), (float) (from.z + o.z), rgb, alpha, FULL_BRIGHT, nx, ny, nz);
            vertex(vc, last, (float) (to.x + o.x), (float) (to.y + o.y), (float) (to.z + o.z), rgb, alpha, FULL_BRIGHT, nx, ny, nz);
            vertex(vc, last, (float) (to.x - o.x), (float) (to.y - o.y), (float) (to.z - o.z), rgb, alpha, FULL_BRIGHT, nx, ny, nz);
            vertex(vc, last, (float) (to.x - o.x), (float) (to.y - o.y), (float) (to.z - o.z), rgb, alpha, FULL_BRIGHT, -nx, -ny, -nz);
            vertex(vc, last, (float) (to.x + o.x), (float) (to.y + o.y), (float) (to.z + o.z), rgb, alpha, FULL_BRIGHT, -nx, -ny, -nz);
            vertex(vc, last, (float) (from.x + o.x), (float) (from.y + o.y), (float) (from.z + o.z), rgb, alpha, FULL_BRIGHT, -nx, -ny, -nz);
            vertex(vc, last, (float) (from.x - o.x), (float) (from.y - o.y), (float) (from.z - o.z), rgb, alpha, FULL_BRIGHT, -nx, -ny, -nz);
        }
    }
}
