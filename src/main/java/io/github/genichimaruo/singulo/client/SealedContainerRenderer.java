package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.SealedContainerBlockEntity;
import io.github.genichimaruo.singulo.ruin.SealedContainerBlockEntity.Phase;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 封印コンテナの動く部分: 蓋の4枚の板、中の台、継ぎ目の光、読み取りの光の輪、浮かぶ中身。
 * <p>
 * 解錠: 光の輪が上から下へ走って鍵を読み取り（SCAN）、4面の継ぎ目が順に灯り（UNLOCK）、冷気が噴いたあと（VENT）、
 * 蓋の板が少し浮いてから、花びらのように外へ倒れながら回る（OPEN）。中から台がせり上がり、中身が光の柱の中で回る（RISE）。
 * 封印はその逆（台が沈み、板が戻り、継ぎ目の光が落ち着く）。時刻は SealedContainerBlockEntity の T_* と同じ。
 */
public class SealedContainerRenderer implements BlockEntityRenderer<SealedContainerBlockEntity> {
    /** 段階の色（art16.SEAL_COLORS と同じ）。 */
    static final int[] COLORS = {0x6ECDEE, 0xBEA0FF, 0xFAB654, 0xFF6E96};
    private static final ResourceLocation BASE = Singulo.id("textures/block/sealed_container_base.png");

    public SealedContainerRenderer(BlockEntityRendererProvider.Context context) {}

    private static float clamp01(float v) {
        return Mth.clamp(v, 0, 1);
    }

    /** なめらかに始まり、なめらかに止まる。 */
    private static float ease(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    @Override
    public void render(SealedContainerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int tier = be.tier();
        int color = COLORS[Mth.clamp(tier, 1, 4) - 1];
        Phase phase = be.phase();
        float t = be.phaseTime(partialTick);
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000) + partialTick;

        // 蓋の開き（0 閉 〜 1 開）、台の高さ（0 〜 1）、継ぎ目の明るさ（面ごと）
        float lid;
        float rise;
        float[] seam = new float[4];
        switch (phase) {
            case SEALED -> {
                lid = 0;
                rise = 0;
                float pulse = 0.35F + 0.15F * Mth.sin(time * 0.08F);
                java.util.Arrays.fill(seam, pulse);
            }
            case UNSEALING -> {
                lid = clamp01((t - SealedContainerBlockEntity.T_OPEN)
                        / (SealedContainerBlockEntity.T_OPEN_END - SealedContainerBlockEntity.T_OPEN));
                rise = ease((t - SealedContainerBlockEntity.T_RISE)
                        / (SealedContainerBlockEntity.T_RISE_END - SealedContainerBlockEntity.T_RISE));
                for (int k = 0; k < 4; k++) {
                    float on = clamp01((t - SealedContainerBlockEntity.T_UNLOCK - k * 3) / 3F);
                    seam[k] = 0.35F + 0.65F * on;
                }
            }
            case SEALING -> {
                rise = 1 - ease(t / 16F);
                lid = 1 - clamp01((t - 12) / 28F);
                float dim = clamp01((t - 36) / 12F);
                java.util.Arrays.fill(seam, 1.0F - 0.6F * dim);
            }
            default -> {
                lid = 1;
                rise = 1;
                java.util.Arrays.fill(seam, 0.8F + 0.1F * Mth.sin(time * 0.1F));
            }
        }

        pose.pushPose();
        drawSeams(pose, buffers, color, seam);
        drawPlates(pose, buffers, tier, lid, light);
        drawPedestal(pose, buffers, color, rise, light);
        if (phase == Phase.UNSEALING && t >= SealedContainerBlockEntity.T_SCAN && t < SealedContainerBlockEntity.T_SCAN_END) {
            float k = (t - SealedContainerBlockEntity.T_SCAN) / (SealedContainerBlockEntity.T_SCAN_END - SealedContainerBlockEntity.T_SCAN);
            drawScan(pose, buffers, color, 1.15F - k * 1.0F, 1 - Math.abs(k * 2 - 1) * 0.4F);
        } else if (phase == Phase.SEALING && t >= 36 && t < SealedContainerBlockEntity.SEAL_TICKS) {
            float k = (t - 36) / (SealedContainerBlockEntity.SEAL_TICKS - 36);
            drawScan(pose, buffers, color, 0.15F + k * 1.0F, 1 - k * 0.6F);
        }
        if (rise > 0.6F) {
            drawContents(be, pose, buffers, color, rise, time);
        }
        pose.popPose();
    }

    /** 4面の縦の継ぎ目と、上の縁の光（面ごとの明るさ）。 */
    private static void drawSeams(PoseStack pose, MultiBufferSource buffers, int color, float[] seam) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        float lo = 4 / 16F;
        float hi = 12 / 16F;
        float e = 0.002F;
        // 北・東・南・西の順（解錠のとき、この順に灯る）
        float[][] faces = {{7.5F, 1 - e, 8.5F, 1 - e}, {15 + e, 7.5F, 15 + e, 8.5F}, {8.5F, 15 + e, 7.5F, 15 + e}, {1 - e, 8.5F, 1 - e, 7.5F}};
        for (int k = 0; k < 4; k++) {
            int rgb = FxDraw.lerpRgb(0x203040, color, seam[k]);
            int alpha = (int) (120 + 135 * seam[k]);
            float x0 = faces[k][0] / 16F;
            float z0 = faces[k][1] / 16F;
            float x1 = faces[k][2] / 16F;
            float z1 = faces[k][3] / 16F;
            quad(pose.last(), vc, x0, lo, z0, x1, lo, z1, x1, hi, z1, x0, hi, z0, rgb, alpha);
        }
        // 上の縁の光の帯
        float avg = (seam[0] + seam[1] + seam[2] + seam[3]) / 4;
        int rgb = FxDraw.lerpRgb(0x203040, color, avg);
        int alpha = (int) (90 + 120 * avg);
        float y0 = 12.2F / 16F;
        float y1 = 12.8F / 16F;
        float a = 1 / 16F - e;
        float b = 15 / 16F + e;
        quad(pose.last(), vc, a, y0, a, b, y0, a, b, y1, a, a, y1, a, rgb, alpha);
        quad(pose.last(), vc, b, y0, b, a, y0, b, a, y1, b, b, y1, b, rgb, alpha);
        quad(pose.last(), vc, a, y0, b, a, y0, a, a, y1, a, a, y1, b, rgb, alpha);
        quad(pose.last(), vc, b, y0, a, b, y0, b, b, y1, b, b, y1, a, rgb, alpha);
    }

    /** 蓋の4枚の板。lid が 0 で閉、1 で開（少し浮く → 外へ倒れながら回る）。 */
    private static void drawPlates(PoseStack pose, MultiBufferSource buffers, int tier, float lid, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(Singulo.id("textures/block/sealed_container_lid_" + tier + ".png")));
        float lift = ease(lid * 3) * 1.5F / 16F;
        float out = ease((lid - 0.3F) / 0.7F);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                pose.pushPose();
                // 外側の角を軸にして倒す
                float cx = (8 + 7 * sx) / 16F;
                float cz = (8 + 7 * sz) / 16F;
                pose.translate(cx + sx * out * 2.5F / 16F, 13 / 16F + lift - out * 1.5F / 16F, cz + sz * out * 2.5F / 16F);
                pose.mulPose(Axis.YP.rotationDegrees(-sx * sz * 25 * out));
                // 倒す軸は、外向き（対角線）に直交する水平な線
                org.joml.Vector3f axis = new org.joml.Vector3f(-sz, 0, sx).normalize();
                pose.mulPose(new org.joml.Quaternionf().fromAxisAngleDeg(axis, -70 * out));
                float x0 = sx > 0 ? -7 / 16F : 0;
                float z0 = sz > 0 ? -7 / 16F : 0;
                FxDraw.box(pose, vc, x0, 0, z0, x0 + 7 / 16F, 2 / 16F, z0 + 7 / 16F, light, 0xFFFFFF);
                pose.popPose();
            }
        }
    }

    /** 中の台: 暗い柱と、光る天板。 */
    private static void drawPedestal(PoseStack pose, MultiBufferSource buffers, int color, float rise, int light) {
        if (rise <= 0) {
            return;
        }
        float top = (4 + 7 * rise) / 16F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(BASE));
        FxDraw.box(pose, vc, 5.5F / 16F, 4 / 16F, 5.5F / 16F, 10.5F / 16F, top, 10.5F / 16F, light, 0xFFFFFF);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        float y = top + 0.003F;
        quad(pose.last(), glow, 6 / 16F, y, 6 / 16F, 6 / 16F, y, 10 / 16F, 10 / 16F, y, 10 / 16F, 10 / 16F, y, 6 / 16F,
                color, (int) (200 * rise));
    }

    /** 読み取りの光の輪（高さ y、強さ k）。 */
    private static void drawScan(PoseStack pose, MultiBufferSource buffers, int color, float y, float k) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0.5, y, 0.5);
        FxDraw.ring(pose.last(), vc, 0.5F, 0.72F, color, (int) (230 * k), 0);
        FxDraw.ring(pose.last(), vc, 0.6F, 0.64F, 0xFFFFFF, (int) (255 * k), (int) (120 * k));
        pose.popPose();
    }

    /** 中身: 台の上の光の柱の中で、3つまでがゆっくり回る。 */
    private static void drawContents(SealedContainerBlockEntity be, PoseStack pose, MultiBufferSource buffers, int color, float rise,
                                     float time) {
        float k = clamp01((rise - 0.6F) / 0.4F);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0.5, 11 / 16F, 0.5);
        // 光の柱（上ほど薄い4枚の板）
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(45 + i * 90 + time * 1.5F));
            quadV(pose.last(), glow, -0.18F, 0, 0.18F, 0.55F, color, (int) (110 * k), 0);
            pose.popPose();
        }
        pose.popPose();
        List<ItemStack> items = be.shownItems();
        if (items.isEmpty() || be.getLevel() == null) {
            return;
        }
        var renderer = Minecraft.getInstance().getItemRenderer();
        int n = items.size();
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            float a = time * 2.0F + i * 360F / n;
            float r = n == 1 ? 0 : 0.14F;
            pose.translate(0.5 + Mth.cos(a * Mth.DEG_TO_RAD) * r, 0.92 + 0.03 * Mth.sin(time * 0.1F + i), 0.5 + Mth.sin(a * Mth.DEG_TO_RAD) * r);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3 + i * 40));
            float s = 0.32F * k * (n == 1 ? 1.3F : 1);
            pose.scale(s, s, s);
            renderer.renderStatic(items.get(i), ItemDisplayContext.FIXED, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                    be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int rgb, int alpha) {
        vertex(vc, p, ax, ay, az, rgb, alpha);
        vertex(vc, p, bx, by, bz, rgb, alpha);
        vertex(vc, p, cx, cy, cz, rgb, alpha);
        vertex(vc, p, dx, dy, dz, rgb, alpha);
        vertex(vc, p, dx, dy, dz, rgb, alpha);
        vertex(vc, p, cx, cy, cz, rgb, alpha);
        vertex(vc, p, bx, by, bz, rgb, alpha);
        vertex(vc, p, ax, ay, az, rgb, alpha);
    }

    /** 縦の板（x0〜x1、高さ 0〜h）。下は alphaBottom、上は alphaTop。 */
    private static void quadV(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float h, int rgb, int alphaBottom,
                              int alphaTop) {
        vertex(vc, p, x0, y0, 0, rgb, alphaBottom);
        vertex(vc, p, x1, y0, 0, rgb, alphaBottom);
        vertex(vc, p, x1, h, 0, rgb, alphaTop);
        vertex(vc, p, x0, h, 0, rgb, alphaTop);
        vertex(vc, p, x0, h, 0, rgb, alphaTop);
        vertex(vc, p, x1, h, 0, rgb, alphaTop);
        vertex(vc, p, x1, y0, 0, rgb, alphaBottom);
        vertex(vc, p, x0, y0, 0, rgb, alphaBottom);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, int alpha) {
        vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FxDraw.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    @Override
    public AABB getRenderBoundingBox(SealedContainerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5, 0.6, 0.5);
    }
}
