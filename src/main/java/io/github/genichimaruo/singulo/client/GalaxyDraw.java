package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 創世炉が生む銀河（核の上・夜空・エピローグで同じ形）。種（seed）から腕の数・巻き方・色が決まる。
 * 星はカメラの方を向いた小さな菱形で、内側ほど速く回る（差動回転）。
 * additive なら加算（RenderType.eyes、透明度は効かないので色の明るさに掛ける）、そうでなければ半透明で描く。
 */
final class GalaxyDraw {
    /** 星1つ: 半径（銀河の半径を1として）・角度・厚み方向の位置・大きさ・色・またたきの速さと位相。 */
    record Star(float r, float angle, float y, float size, int rgb, float twinkle, float phase) {}

    /** 銀河の見た目（種ごとに一度だけ作る）。 */
    record Shape(Star[] stars, int coreRgb, int armRgb, float tilt) {}

    private static final Map<Long, Shape> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Shape> eldest) {
            return size() > 24;
        }
    };
    private static final int[] ARM_COLORS = {0xA8C8FF, 0xC8A8FF, 0x9FF0FF, 0xFFB8D8, 0xB8FFD8};
    private static final int CORE = 0xFFE6B8;
    private static final int HII = 0xFF78B0;
    static final int STARS = 760;

    private GalaxyDraw() {}

    static Shape shape(long seed) {
        return CACHE.computeIfAbsent(seed, GalaxyDraw::build);
    }

    private static Shape build(long seed) {
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + 7);
        int arms = 2 + rnd.nextInt(3);
        float twist = 3.4F + rnd.nextFloat() * 2.4F;
        float base = rnd.nextFloat() * Mth.TWO_PI;
        int armRgb = ARM_COLORS[rnd.nextInt(ARM_COLORS.length)];
        int coreRgb = FxDraw.lerpRgb(CORE, 0xFFFFFF, rnd.nextFloat() * 0.4F);
        Star[] stars = new Star[STARS];
        for (int i = 0; i < STARS; i++) {
            float roll = rnd.nextFloat();
            float r;
            float a;
            float y;
            int rgb;
            if (roll < 0.24F) {
                // 中心のふくらみ（暖かい色の星が密に集まる）
                r = Math.min(0.32F, Math.abs((float) rnd.nextGaussian()) * 0.12F);
                a = rnd.nextFloat() * Mth.TWO_PI;
                y = (float) rnd.nextGaussian() * 0.06F * (1 - r * 2);
                rgb = FxDraw.lerpRgb(coreRgb, 0xFFFFFF, rnd.nextFloat() * 0.5F);
            } else if (roll < 0.9F) {
                // 渦の腕（外へ行くほど腕の色に、ところどころ星の生まれる赤い雲）
                r = 0.08F + (float) Math.pow(rnd.nextFloat(), 0.85) * 0.92F;
                int arm = i % arms;
                a = base + Mth.TWO_PI * arm / arms + r * twist + (float) rnd.nextGaussian() * 0.32F * (1.1F - r * 0.6F);
                y = (float) rnd.nextGaussian() * 0.025F;
                rgb = rnd.nextFloat() < 0.06F ? HII : FxDraw.lerpRgb(coreRgb, armRgb, Math.min(1, r * 1.6F));
                if (rnd.nextFloat() < 0.25F) {
                    rgb = FxDraw.lerpRgb(rgb, 0xFFFFFF, 0.6F);
                }
            } else {
                // 腕のあいだの古い星
                r = rnd.nextFloat() * 1.05F;
                a = rnd.nextFloat() * Mth.TWO_PI;
                y = (float) rnd.nextGaussian() * 0.04F;
                rgb = FxDraw.lerpRgb(0xFFD8B0, 0xFFFFFF, rnd.nextFloat() * 0.5F);
            }
            float size = 0.008F + rnd.nextFloat() * 0.014F;
            if (rnd.nextFloat() < 0.04F) {
                size *= 2.4F;                                            // ひときわ明るい星
            }
            stars[i] = new Star(r, a, y, size, rgb, 0.05F + rnd.nextFloat() * 0.12F, rnd.nextFloat() * Mth.TWO_PI);
        }
        return new Shape(stars, coreRgb, armRgb, 10 + rnd.nextFloat() * 50);
    }

    /**
     * 銀河を描く。円盤は今の PoseStack の原点が中心で、tilt で傾ける（星の位置だけを回し、星の形はカメラに向ける）。
     * right・up はカメラの右と上（今の PoseStack の座標で、長さ1）。heat は生まれたての熱さ（1で星がみな白く大きい）。
     */
    static void draw(PoseStack.Pose last, VertexConsumer vc, Vector3f right, Vector3f up, Quaternionf tilt, long seed,
                     float radius, float spin, float brightness, float time, float heat, boolean additive) {
        if (brightness <= 0.003F || radius <= 0.001F) {
            return;
        }
        Shape shape = shape(seed);
        Vector3f p = new Vector3f();
        float starScale = radius * (1 + heat * 1.5F);
        for (Star s : shape.stars()) {
            // 差動回転: 内側ほど速く回る
            float a = s.angle() + spin * (1.6F - s.r());
            float rr = s.r() * radius;
            p.set(Mth.cos(a) * rr, s.y() * radius, Mth.sin(a) * rr);
            tilt.transform(p);
            float tw = 0.7F + 0.3F * Mth.sin(time * s.twinkle() + s.phase());
            float b = brightness * tw;
            int rgb = heat > 0 ? FxDraw.lerpRgb(s.rgb(), 0xFFFFFF, heat) : s.rgb();
            diamond(vc, last, p, right, up, s.size() * starScale, rgb, b, additive);
        }
    }

    /** 中心のふくらみと、円盤のかすかな光（星の下に敷く）。 */
    static void glow(PoseStack pose, VertexConsumer vc, Quaternionf camera, Quaternionf tilt, long seed, float radius,
                     float brightness, float heat, boolean additive) {
        if (brightness <= 0.003F || radius <= 0.001F) {
            return;
        }
        Shape shape = shape(seed);
        int core = FxDraw.lerpRgb(shape.coreRgb(), 0xFFFFFF, heat);
        softGlow(pose, vc, camera, radius * 0.75F, core, brightness * 0.55F, additive);
        softGlow(pose, vc, camera, radius * 0.28F, 0xFFFFFF, brightness * 0.8F, additive);
        pose.pushPose();
        pose.mulPose(tilt);
        int arm = FxDraw.lerpRgb(shape.armRgb(), 0xFFFFFF, heat);
        softRing(pose.last(), vc, radius * 0.05F, radius * 1.05F, arm, brightness * 0.16F, 0, additive);
        pose.popPose();
    }

    /** カメラの方を向いた丸い光（中心が明るく、縁で消える）。 */
    static void softGlow(PoseStack pose, VertexConsumer vc, Quaternionf camera, float size, int rgb, float k, boolean additive) {
        if (!additive) {
            FxDraw.billboard(pose, vc, camera, size, rgb, (int) (255 * Mth.clamp(k, 0, 1)));
            return;
        }
        pose.pushPose();
        pose.mulPose(camera);
        PoseStack.Pose last = pose.last();
        float r = size * 0.6F;
        int seg = 20;
        float[][] bands = {{0F, 0.32F, 1F, 0.55F}, {0.32F, 1F, 0.55F, 0F}};
        for (float[] b : bands) {
            int ci = scale(rgb, k * b[2]);
            int co = scale(rgb, k * b[3]);
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg;
                float a1 = Mth.TWO_PI * (i + 1) / seg;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                vertex(vc, last, c0 * r * b[0], s0 * r * b[0], 0, ci, 255);
                vertex(vc, last, c0 * r * b[1], s0 * r * b[1], 0, co, 255);
                vertex(vc, last, c1 * r * b[1], s1 * r * b[1], 0, co, 255);
                vertex(vc, last, c1 * r * b[0], s1 * r * b[0], 0, ci, 255);
            }
        }
        pose.popPose();
    }

    /** XZ 平面の輪（両面）。内側の明るさ kIn から外側の kOut へ。 */
    static void softRing(PoseStack.Pose last, VertexConsumer vc, float inner, float outer, int rgb, float kIn, float kOut,
                         boolean additive) {
        if (!additive) {
            FxDraw.ring(last, vc, inner, outer, rgb, (int) (255 * Mth.clamp(kIn, 0, 1)), (int) (255 * Mth.clamp(kOut, 0, 1)));
            return;
        }
        int ci = scale(rgb, kIn);
        int co = scale(rgb, kOut);
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg;
            float a1 = Mth.TWO_PI * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(vc, last, c0 * inner, 0, s0 * inner, ci, 255);
            vertex(vc, last, c0 * outer, 0, s0 * outer, co, 255);
            vertex(vc, last, c1 * outer, 0, s1 * outer, co, 255);
            vertex(vc, last, c1 * inner, 0, s1 * inner, ci, 255);
            vertex(vc, last, c1 * inner, 0, s1 * inner, ci, 255);
            vertex(vc, last, c1 * outer, 0, s1 * outer, co, 255);
            vertex(vc, last, c0 * outer, 0, s0 * outer, co, 255);
            vertex(vc, last, c0 * inner, 0, s0 * inner, ci, 255);
        }
    }

    /** 加算で描くので、透明度は色の明るさにする。 */
    static int scale(int rgb, float k) {
        k = Mth.clamp(k, 0, 1);
        return ((int) (((rgb >> 16) & 0xFF) * k) << 16) | ((int) (((rgb >> 8) & 0xFF) * k) << 8) | (int) ((rgb & 0xFF) * k);
    }

    /** カメラの方を向いた菱形の星。 */
    static void diamond(VertexConsumer vc, PoseStack.Pose last, Vector3f c, Vector3f right, Vector3f up, float size, int rgb,
                        float brightness, boolean additive) {
        int col = additive ? scale(rgb, brightness) : rgb;
        int alpha = additive ? 255 : (int) (255 * Mth.clamp(brightness, 0, 1));
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        vertex(vc, last, c.x - rx, c.y - ry, c.z - rz, col, alpha);
        vertex(vc, last, c.x - ux, c.y - uy, c.z - uz, col, alpha);
        vertex(vc, last, c.x + rx, c.y + ry, c.z + rz, col, alpha);
        vertex(vc, last, c.x + ux, c.y + uy, c.z + uz, col, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int rgb, int alpha) {
        vc.addVertex(last, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FxDraw.FULL_BRIGHT).setNormal(last, 0, 0, 1);
    }
}
