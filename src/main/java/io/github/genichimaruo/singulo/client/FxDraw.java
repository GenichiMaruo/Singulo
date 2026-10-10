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
        vc.vertex(last.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), nx, ny, nz).endVertex();
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

    /**
     * カメラの方を向いた丸い光（中心が原点、見た目の大きさ size）。中心がいちばん明るく、外へ向かってなめらかに消える。
     * 白いテクスチャに頂点の色と透明度で描くので、四角い板にならないよう扇形を重ねて描く。
     */
    static void billboard(PoseStack pose, VertexConsumer vc, Quaternionf camera, float size, int rgb, int alpha) {
        pose.pushPose();
        pose.mulPose(camera);
        PoseStack.Pose last = pose.last();
        float r = size * 0.6F;
        int seg = 20;
        // 内側（芯: 明るさそのまま → 半分）と外側（半分 → 0）の2つの帯
        float[][] bands = {{0F, 0.32F, 1F, 0.55F}, {0.32F, 1F, 0.55F, 0F}};
        for (float[] b : bands) {
            for (int k = 0; k < seg; k++) {
                float a0 = Mth.TWO_PI * k / seg;
                float a1 = Mth.TWO_PI * (k + 1) / seg;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                int ai = (int) (alpha * b[2]);
                int ao = (int) (alpha * b[3]);
                vertex(vc, last, c0 * r * b[0], s0 * r * b[0], 0, rgb, ai, FULL_BRIGHT, 0, 0, 1);
                vertex(vc, last, c0 * r * b[1], s0 * r * b[1], 0, rgb, ao, FULL_BRIGHT, 0, 0, 1);
                vertex(vc, last, c1 * r * b[1], s1 * r * b[1], 0, rgb, ao, FULL_BRIGHT, 0, 0, 1);
                vertex(vc, last, c1 * r * b[0], s1 * r * b[0], 0, rgb, ai, FULL_BRIGHT, 0, 0, 1);
            }
        }
        pose.popPose();
    }

    /** カメラの方を向いた光の筋（中心から両端へ細く消える。angle は画面内の向き、度）。 */
    static void flare(PoseStack pose, VertexConsumer vc, Quaternionf camera, float length, float width, float angle, int rgb, int alpha) {
        pose.pushPose();
        pose.mulPose(camera);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        PoseStack.Pose last = pose.last();
        float h = width / 2;
        for (int side = -1; side <= 1; side += 2) {
            float tip = side * length / 2;
            vertex(vc, last, 0, -h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
            vertex(vc, last, tip, 0, 0, rgb, 0, FULL_BRIGHT, 0, 0, 1);
            vertex(vc, last, tip, 0, 0, rgb, 0, FULL_BRIGHT, 0, 0, 1);
            vertex(vc, last, 0, h, 0, rgb, alpha, FULL_BRIGHT, 0, 0, 1);
        }
        pose.popPose();
    }

    /** 中心を囲んで回る3本の光の輪（ジャイロスコープのように、それぞれ別の軸で傾いて回る）。 */
    static void gyroRings(PoseStack pose, VertexConsumer vc, float radius, float width, float time, int rgb, int alpha) {
        float[][] axes = {{1, 0, 0}, {0, 0, 1}, {0.7F, 0, 0.7F}};
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * (3 + k * 1.7F) + k * 60));
            pose.mulPose(new org.joml.Quaternionf().rotationAxis((50 + k * 35) * Mth.DEG_TO_RAD, axes[k][0], axes[k][1], axes[k][2]));
            ring(pose.last(), vc, radius - width / 2, radius + width / 2, rgb, alpha, alpha);
            pose.popPose();
        }
    }

    /** 光る正八面体（結晶）。中心が原点、頂点までの長さ size。 */
    static void octahedron(PoseStack.Pose last, VertexConsumer vc, float size, float tall, int rgb, int alpha) {
        float[][] eq = {{size, 0, 0}, {0, 0, size}, {-size, 0, 0}, {0, 0, -size}};
        for (int k = 0; k < 4; k++) {
            float[] a = eq[k], b = eq[(k + 1) % 4];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float py = sgn * tall;
                float nx = (a[0] + b[0]) / 2, ny = sgn * size * 0.6F, nz = (a[2] + b[2]) / 2;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int shade = sgn > 0 ? alpha : (int) (alpha * 0.75F);
                vertex(vc, last, 0, py, 0, rgb, shade, FULL_BRIGHT, nx / l, ny / l, nz / l);
                vertex(vc, last, sgn > 0 ? b[0] : a[0], 0, sgn > 0 ? b[2] : a[2], rgb, shade, FULL_BRIGHT, nx / l, ny / l, nz / l);
                vertex(vc, last, sgn > 0 ? a[0] : b[0], 0, sgn > 0 ? a[2] : b[2], rgb, shade, FULL_BRIGHT, nx / l, ny / l, nz / l);
                vertex(vc, last, 0, py, 0, rgb, shade, FULL_BRIGHT, nx / l, ny / l, nz / l);
            }
        }
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

    // ------------------------------------------------------------------ 箱（テクスチャを各面に1枚ずつ貼る）

    static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            int light, int rgb) {
        box(pose, vc, x0, y0, z0, x1, y1, z1, light, rgb, 255);
    }

    static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            int light, int rgb, int alpha) {
        PoseStack.Pose p = pose.last();
        // 下・上・北・南・西・東
        quad(vc, p, light, rgb, alpha, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vc, p, light, rgb, alpha, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(vc, p, light, rgb, alpha, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        quad(vc, p, light, rgb, alpha, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, rgb, alpha, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vc, p, light, rgb, alpha, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
    }

    /**
     * 1ドットを1ドットのまま貼る箱（ブロックのモデルの既定の UV と同じ決め方）。
     * 形は (x0..x1, y0..y1, z0..z1)（今の PoseStack の座標、ブロック単位）、絵の場所は (px0..px1, py0..py1, pz0..pz1)
     * （ブロックの中のドット座標。箱が動いても、閉じた位置のドットを貼り続ける）。
     */
    static void pixelBox(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                         float px0, float py0, float pz0, float px1, float py1, float pz1, int light, int rgb) {
        PoseStack.Pose p = pose.last();
        float u0 = px0 / 16, u1 = px1 / 16, w0 = pz0 / 16, w1 = pz1 / 16;
        float vTop = (16 - py1) / 16, vBottom = (16 - py0) / 16;
        // 下・上: u は x、v は z
        uvQuad(vc, p, light, rgb, 0, -1, 0, x0, y0, z0, u0, w0, x1, y0, z0, u1, w0, x1, y0, z1, u1, w1, x0, y0, z1, u0, w1);
        uvQuad(vc, p, light, rgb, 0, 1, 0, x0, y1, z1, u0, w1, x1, y1, z1, u1, w1, x1, y1, z0, u1, w0, x0, y1, z0, u0, w0);
        // 北・南: u は x、v は上から
        uvQuad(vc, p, light, rgb, 0, 0, -1, x0, y1, z0, u1, vTop, x1, y1, z0, u0, vTop, x1, y0, z0, u0, vBottom, x0, y0, z0, u1, vBottom);
        uvQuad(vc, p, light, rgb, 0, 0, 1, x0, y0, z1, u0, vBottom, x1, y0, z1, u1, vBottom, x1, y1, z1, u1, vTop, x0, y1, z1, u0, vTop);
        // 西・東: u は z、v は上から
        uvQuad(vc, p, light, rgb, -1, 0, 0, x0, y0, z0, w0, vBottom, x0, y0, z1, w1, vBottom, x0, y1, z1, w1, vTop, x0, y1, z0, w0, vTop);
        uvQuad(vc, p, light, rgb, 1, 0, 0, x1, y1, z0, w1, vTop, x1, y1, z1, w0, vTop, x1, y0, z1, w0, vBottom, x1, y0, z0, w1, vBottom);
    }

    private static void uvQuad(VertexConsumer vc, PoseStack.Pose p, int light, int rgb, float nx, float ny, float nz,
                               float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                               float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        uvVertex(vc, p, ax, ay, az, au, av, light, rgb, 255, nx, ny, nz);
        uvVertex(vc, p, bx, by, bz, bu, bv, light, rgb, 255, nx, ny, nz);
        uvVertex(vc, p, cx, cy, cz, cu, cv, light, rgb, 255, nx, ny, nz);
        uvVertex(vc, p, dx, dy, dz, du, dv, light, rgb, 255, nx, ny, nz);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, int light, int rgb, int alpha, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        uvVertex(vc, p, ax, ay, az, 0, 1, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, bx, by, bz, 1, 1, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, cx, cy, cz, 1, 0, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, dx, dy, dz, 0, 0, light, rgb, alpha, nx, ny, nz);
    }

    private static void uvVertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light,
                               int rgb, int alpha, float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }

    static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

}
