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
    /** 光の描き方（足し合わせの半透明。深度は書かない）の入れ物。 */
    static com.mojang.blaze3d.vertex.VertexConsumer glow(net.minecraft.client.renderer.MultiBufferSource buffers) {
        return GlowDepth.of(buffers, WHITE);
    }

    /** +Z を dir に向ける回転。 */
    static Quaternionf orient(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        float yaw = (float) Math.atan2(d.x, d.z);
        float pitch = (float) -Math.asin(Mth.clamp(d.y, -1, 1));
        return new Quaternionf().rotateY(yaw).rotateX(pitch);
    }

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
     * XZ 平面の扇形の帯（両面）。角度は +X から +Z へ測る（度。ワールドの atan2(z, x) と同じ）。fromDeg から toDeg まで。
     * 内側の縁の透明度 alphaIn、外側の縁の透明度 alphaOut。
     */
    static void sector(PoseStack.Pose last, VertexConsumer vc, float inner, float outer, float fromDeg, float toDeg,
                       int rgb, int alphaIn, int alphaOut) {
        int seg = Math.max(4, (int) (Math.abs(toDeg - fromDeg) / 6));
        for (int s = 0; s < seg; s++) {
            float a0 = (fromDeg + (toDeg - fromDeg) * s / seg) * Mth.DEG_TO_RAD;
            float a1 = (fromDeg + (toDeg - fromDeg) * (s + 1) / seg) * Mth.DEG_TO_RAD;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(vc, last, c0 * inner, 0, s0 * inner, rgb, alphaIn, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alphaIn, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alphaOut, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alphaOut, FULL_BRIGHT, 0, 1, 0);
            vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alphaOut, FULL_BRIGHT, 0, -1, 0);
            vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alphaOut, FULL_BRIGHT, 0, -1, 0);
            vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alphaIn, FULL_BRIGHT, 0, -1, 0);
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

    /**
     * from から to への四角い棒（太さ width の角柱。4つの側面と両端のふた）。座標は今の PoseStack の原点から。
     * 軸を通る面を持たないので、軸と同じ平面にある膜と重なってちらつかない。面の向き（表裏）は気にせず、両面を描く。
     */
    static void rod(PoseStack.Pose last, VertexConsumer vc, Vec3 from, Vec3 to, float width, int rgb, int light) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 dir = axis.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize().scale(width / 2);
        Vec3 b = dir.cross(a).normalize().scale(width / 2);
        // 断面の4つの角（順に回る）
        Vec3[] corner = {a.add(b), a.subtract(b), a.scale(-1).subtract(b), a.scale(-1).add(b)};
        for (int k = 0; k < 4; k++) {
            Vec3 c0 = corner[k], c1 = corner[(k + 1) % 4];
            Vec3 n = c0.add(c1).normalize();
            rodQuad(vc, last, from.add(c0), from.add(c1), to.add(c1), to.add(c0), n, rgb, light);
        }
        rodQuad(vc, last, from.add(corner[0]), from.add(corner[1]), from.add(corner[2]), from.add(corner[3]), dir.scale(-1), rgb, light);
        rodQuad(vc, last, to.add(corner[0]), to.add(corner[1]), to.add(corner[2]), to.add(corner[3]), dir, rgb, light);
    }

    /** 四角形を両面で描く（裏向きを落とす描き方でも、どちら側から見えるようにする）。 */
    private static void rodQuad(VertexConsumer vc, PoseStack.Pose last, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 n, int rgb, int light) {
        Vec3[] ps = {p0, p1, p2, p3};
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                Vec3 p = ps[side == 0 ? i : 3 - i];
                vertex(vc, last, (float) p.x, (float) p.y, (float) p.z, rgb, 255, light, (float) n.x * s, (float) n.y * s, (float) n.z * s);
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

    /** {@link #texBox} の絵の大きさ（ドット）。 */
    static final float TEX_SIZE = 32;

    /**
     * 模様を、長さに合わせた細かさで貼る箱（ブロック単位の座標。1/16 ブロックの長さに絵の1ドットを当てる）。
     * 面ごとに、その面の大きさのぶんだけ絵を切り出す（場所は面ごとにずらして、同じ模様が並ばないようにする）。
     * 縦（y）は絵の下へ向かう向き（筋の模様は、下の毛先ほど暗い）。
     */
    static void texBox(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                       int light, int rgb, int alpha) {
        texBox(pose, vc, x0, y0, z0, x1, y1, z1, light, rgb, alpha, false);
    }

    /**
     * zLong なら、前後（z）に長い物として貼る: 筋の模様が z の向きに走るよう、西・東の面は u を y、v を z にする
     * （下・上の面は、もとから v が z）。
     */
    static void texBox(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                       int light, int rgb, int alpha, boolean zLong) {
        PoseStack.Pose p = pose.last();
        float sx = Math.min(1, (x1 - x0) * 16 / TEX_SIZE), sy = Math.min(1, (y1 - y0) * 16 / TEX_SIZE),
                sz = Math.min(1, (z1 - z0) * 16 / TEX_SIZE);
        float ox = texOffset(x0 * 7 + z0 * 3 + y0, sx), oy = texOffset(y0 * 5 + x0 * 2, sy), oz = texOffset(z0 * 11 + y0 * 3, sz);
        // 下・上: u は x、v は z
        texQuad(vc, p, light, rgb, alpha, 0, -1, 0, x0, y0, z0, ox, oz, x1, y0, z0, ox + sx, oz, x1, y0, z1, ox + sx, oz + sz, x0, y0, z1, ox, oz + sz);
        texQuad(vc, p, light, rgb, alpha, 0, 1, 0, x0, y1, z1, ox, oz + sz, x1, y1, z1, ox + sx, oz + sz, x1, y1, z0, ox + sx, oz, x0, y1, z0, ox, oz);
        // 北・南: u は x、v は y
        texQuad(vc, p, light, rgb, alpha, 0, 0, -1, x0, y1, z0, ox, oy + sy, x1, y1, z0, ox + sx, oy + sy, x1, y0, z0, ox + sx, oy, x0, y0, z0, ox, oy);
        texQuad(vc, p, light, rgb, alpha, 0, 0, 1, x0, y0, z1, ox, oy, x1, y0, z1, ox + sx, oy, x1, y1, z1, ox + sx, oy + sy, x0, y1, z1, ox, oy + sy);
        if (zLong) {
            // 西・東: u は y、v は z
            texQuad(vc, p, light, rgb, alpha, -1, 0, 0, x0, y0, z0, oy, oz, x0, y0, z1, oy, oz + sz, x0, y1, z1, oy + sy, oz + sz, x0, y1, z0, oy + sy, oz);
            texQuad(vc, p, light, rgb, alpha, 1, 0, 0, x1, y1, z0, oy + sy, oz, x1, y1, z1, oy + sy, oz + sz, x1, y0, z1, oy, oz + sz, x1, y0, z0, oy, oz);
            return;
        }
        // 西・東: u は z、v は y
        texQuad(vc, p, light, rgb, alpha, -1, 0, 0, x0, y0, z0, oz, oy, x0, y0, z1, oz + sz, oy, x0, y1, z1, oz + sz, oy + sy, x0, y1, z0, oz, oy + sy);
        texQuad(vc, p, light, rgb, alpha, 1, 0, 0, x1, y1, z0, oz, oy + sy, x1, y1, z1, oz + sz, oy + sy, x1, y0, z1, oz + sz, oy, x1, y0, z0, oz, oy);
    }

    /** 絵の中の切り出し位置（0〜1 − span）。位置から決めるので、同じ箱はいつも同じ所を貼る。 */
    private static float texOffset(float seed, float span) {
        if (span >= 1) {
            return 0;
        }
        float f = seed * 12.9898F;
        f = f - (float) Math.floor(f);
        // 絵のドットの境目が箱の辺に来るよう、切り出しの始まりはドット単位
        int free = (int) Math.floor((1 - span) * TEX_SIZE + 1.0E-4F);
        return (float) Math.floor(f * (free + 1)) / TEX_SIZE;
    }

    private static void texQuad(VertexConsumer vc, PoseStack.Pose p, int light, int rgb, int alpha, float nx, float ny, float nz,
                                float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                                float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        uvVertex(vc, p, ax, ay, az, au, av, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, bx, by, bz, bu, bv, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, cx, cy, cz, cu, cv, light, rgb, alpha, nx, ny, nz);
        uvVertex(vc, p, dx, dy, dz, du, dv, light, rgb, alpha, nx, ny, nz);
    }

    /**
     * 三角柱（前の面の三角 a・b・c は x, y。前は z0、後ろは z1）。後ろの三角は重心へ backScale 倍に縮めるので、
     * 1 未満なら側面が斜めになって、後ろへなだらかに細る（猫の耳の裏の丸みなど）。面を落とさない描き方で使う。
     */
    static void prism(PoseStack pose, VertexConsumer vc, float ax, float ay, float bx, float by, float cx, float cy,
                      float z0, float z1, float backScale, int light, int rgb) {
        prism(pose, vc, ax, ay, bx, by, cx, cy, z0, z1, backScale, light, rgb, 255);
    }

    /** 透ける三角柱（alpha は 0〜255）。 */
    static void prism(PoseStack pose, VertexConsumer vc, float ax, float ay, float bx, float by, float cx, float cy,
                      float z0, float z1, float backScale, int light, int rgb, int alpha) {
        PoseStack.Pose p = pose.last();
        float mx = (ax + bx + cx) / 3, my = (ay + by + cy) / 3;
        float[] fx = {ax, bx, cx}, fy = {ay, by, cy};
        float[] bkx = new float[3], bky = new float[3];
        for (int i = 0; i < 3; i++) {
            bkx[i] = mx + (fx[i] - mx) * backScale;
            bky[i] = my + (fy[i] - my) * backScale;
        }
        float nz = z1 > z0 ? -1 : 1;
        // 絵は長さに合わせた細かさで（1/16 ブロックに1ドット）。前後の面は x・y の位置から、側面は辺の長さと奥行きから
        float minX = Math.min(ax, Math.min(bx, cx)), minY = Math.min(ay, Math.min(by, cy));
        float k = 16 / TEX_SIZE;
        float ou = texOffset(ax * 5 + ay * 3, Math.min(1, (Math.max(ax, Math.max(bx, cx)) - minX) * k));
        float ov = texOffset(ay * 7 + bx, Math.min(1, (Math.max(ay, Math.max(by, cy)) - minY) * k));
        float[] fu = new float[3], fv = new float[3], bu = new float[3], bv = new float[3];
        for (int i = 0; i < 3; i++) {
            fu[i] = Math.min(1, ou + (fx[i] - minX) * k);
            fv[i] = Math.min(1, ov + (fy[i] - minY) * k);
            bu[i] = Math.min(1, ou + (bkx[i] - minX) * k);
            bv[i] = Math.min(1, ov + (bky[i] - minY) * k);
        }
        texQuad(vc, p, light, rgb, alpha, 0, 0, nz, fx[0], fy[0], z0, fu[0], fv[0], fx[1], fy[1], z0, fu[1], fv[1],
                fx[2], fy[2], z0, fu[2], fv[2], fx[2], fy[2], z0, fu[2], fv[2]);
        texQuad(vc, p, light, rgb, alpha, 0, 0, -nz, bkx[0], bky[0], z1, bu[0], bv[0], bkx[2], bky[2], z1, bu[2], bv[2],
                bkx[1], bky[1], z1, bu[1], bv[1], bkx[1], bky[1], z1, bu[1], bv[1]);
        float depth = Math.min(1, Math.abs(z1 - z0) * k);
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            // 側面の向き: 辺に垂直で、重心から外へ（後ろへの傾きは小さいので、前後の成分は入れない）
            float ex = fx[j] - fx[i], ey = fy[j] - fy[i];
            float sx = ey, sy = -ex;
            if (sx * (fx[i] - mx) + sy * (fy[i] - my) < 0) {
                sx = -sx;
                sy = -sy;
            }
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            float edge = (float) Math.sqrt(ex * ex + ey * ey) * k;
            float eu = Math.min(1, edge), o = texOffset(fx[i] * 3 + fy[j] * 11, eu);
            texQuad(vc, p, light, rgb, alpha, sx / len, sy / len, 0, fx[i], fy[i], z0, o, 0, fx[j], fy[j], z0, o + eu, 0,
                    bkx[j], bky[j], z1, o + eu, depth, bkx[i], bky[i], z1, o, depth);
        }
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
        vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

}
