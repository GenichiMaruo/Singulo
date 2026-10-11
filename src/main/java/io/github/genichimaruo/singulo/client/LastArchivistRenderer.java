package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.LastArchivist;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 最後の記録者: 神々しさと、ブラックホールの暗さ・神秘を併せ持つ姿（約3.4ブロック）。
 * <ul>
 *   <li>象牙の衣（金の縁取り）。裾は闇へ溶け、足元で降着の帳（黒い円盤と傾いた金と菫の輪）が回る</li>
 *   <li>頭は光を飲む黒い球（事象の地平線）。縁に細い金の光子の環がいつもこちらを向いて光り、まわりの景色がゆがむ</li>
 *   <li>頭の後ろに大きな光輪（二重の金の環と12本の光の放射）。背中から六枚の光の翼が扇のように開き、ゆっくり羽ばたく</li>
 *   <li>胸に記録の結晶。体のまわりを、記録の小さな結晶が回る</li>
 *   <li>回想: 番人の技を使うとき、その番人の色のもやをまとう。終焉: 光輪が黒く反転し、翼に黒い縁が差し、頭の特異点が大きくなる</li>
 *   <li>出現: 金の光の柱と、すぼまる記録の環。黒い一点が開き、体が上から光で編まれ、翼が開き、光輪が灯って閃光</li>
 *   <li>倒されたとき: 光輪が割れて散り、体が足元から金の塵になって消え、特異点がふくらんでからつぶれて閃光</li>
 * </ul>
 */
public class LastArchivistRenderer extends EntityRenderer<LastArchivist> {
    static final int IVORY = 0xECE4D2;
    static final int GOLD = 0xFFD27A;
    static final int PALE = 0xFFF2C8;
    static final int VIOLET = 0x6A3CFF;
    static final int CRYSTAL = 0x8FF0FF;
    static final int ECHO = 0x7FE8FF;
    static final int HORIZON = 0xBFD8FF;
    /** 衣の高さ: 裾・肩・襟。 */
    static final float HEM_Y = 0.35F, SHOULDER_Y = 2.0F, COLLAR_Y = 2.45F;
    /** 足元から見た床（コンソールの上面）の高さ。 */
    static final float FLOOR = -(1 + LastArchivist.HOVER_F) + 0.08F;

    public LastArchivistRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(LastArchivist entity) {
        return FxDraw.WHITE;
    }

    @Override
    public boolean shouldRender(LastArchivist entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;                    // 光輪・翼・技の光が体より大きく広がるので、体の箱で間引かない
    }

    @Override
    public void render(LastArchivist e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float em = e.emergeProgress(partialTick);
        float d = e.deathProgress(partialTick);
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        int phase = e.phase();
        Vec3 origin = e.getPosition(partialTick);

        // 体のどこまでが見えているか（出現: 上から編まれる／倒されたとき: 足元から塵になる）
        float revealFrom = 0;            // この高さより上だけ描く
        float revealTo = 3.4F;           // この高さより下だけ描く
        if (em < 1) {
            float b = Mth.clamp((em - 0.4F) / 0.35F, 0, 1);
            revealFrom = LastArchivist.HEAD_Y_F * (1 - b);
            if (em < 0.4F) {
                revealFrom = 9;
            }
        }
        if (d > 0) {
            float b = Mth.clamp((d - 0.2F) / 0.4F, 0, 1);
            revealFrom = Math.max(revealFrom, (float) LastArchivist.HEAD_Y * b);
        }
        float unfold = em < 1 ? Mth.clamp((em - 0.75F) / 0.17F, 0, 1) : 1;
        if (d > 0) {
            unfold *= 1 - Mth.clamp((d - 0.1F) / 0.4F, 0, 1);
        }
        float haloOn = em < 1 ? Mth.clamp((em - 0.8F) / 0.12F, 0, 1) : 1;

        if (em < 0.4F) {
            renderSummoning(e, em, t, camera, pose, buffers);
        }
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.translate(0, 0.08F * Mth.sin(t * 0.06F), 0);

        renderVeil(e, phase, em, d, t, origin, camera, pose, buffers);
        if (revealFrom < 3.0F) {
            renderRobe(e, phase, revealFrom, t, pose, buffers, light);
            if (em >= 0.4F && em < 0.75F || d > 0.2F && d < 0.6F) {
                renderScanRing(revealFrom, em, d, pose, buffers);
            }
        }
        if (unfold > 0.01F) {
            renderWings(phase, unfold, d, t, pose, buffers);
        }
        if (d < 0.6F && em >= 0.55F) {
            renderChest(e, t, d, camera, pose, buffers);
        }
        if (d < 0.05F) {
            if (haloOn > 0) {
                renderHalo(phase, haloOn, t, pose, buffers);
            }
        } else if (d < 0.3F) {
            renderBrokenHalo(phase, (d - 0.05F) / 0.25F, pose, buffers);
        }
        pose.popPose();

        renderHead(e, phase, em, d, t, origin, camera, pose, buffers);
        if (d <= 0 && em >= 1) {
            renderMoves(e, phase, t, partialTick, origin, camera, pose, buffers);
            renderRecords(t, camera, pose, buffers);
        }
        if (em >= 0.92F && em < 1) {
            float c = (em - 0.92F) / 0.08F;
            VertexConsumer glow = glow(buffers);
            pose.pushPose();
            pose.translate(0, LastArchivist.CHEST_Y, 0);
            FxDraw.billboard(pose, glow, camera, 2 + 9 * c, 0xFFFFFF, (int) (240 * (1 - c)));
            pose.translate(0, FLOOR - LastArchivist.CHEST_Y, 0);
            FxDraw.ring(pose.last(), glow, 1 + 10 * c, 1.7F + 10 * c, GOLD, (int) (220 * (1 - c)), 0);
            pose.popPose();
        }
        if (d >= 0.92F) {
            float c = (d - 0.92F) / 0.08F;
            VertexConsumer glow = glow(buffers);
            pose.pushPose();
            pose.translate(0, LastArchivist.HEAD_Y, 0);
            FxDraw.billboard(pose, glow, camera, 1 + 10 * c, 0xFFFFFF, (int) (255 * (1 - c)));
            FxDraw.billboard(pose, glow, camera, 3 + 14 * c, GOLD, (int) (200 * (1 - c)));
            pose.translate(0, FLOOR - LastArchivist.HEAD_Y, 0);
            FxDraw.ring(pose.last(), glow, 1 + 12 * c, 1.6F + 12 * c, PALE, (int) (220 * (1 - c)), 0);
            pose.popPose();
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    static VertexConsumer glow(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
    }

    // ------------------------------------------------------------------ 出現の前半

    /** 金の光の柱と、すぼまりながら回る記録の環。 */
    private static void renderSummoning(LastArchivist e, float em, float t, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        float k = Mth.clamp(em / 0.2F, 0, 1);
        float fade = em < 0.3F ? 1 : 1 - (em - 0.3F) / 0.1F;
        FxDraw.beam(pose.last(), glow, new Vec3(0, FLOOR, 0), new Vec3(0, 12, 0), 0.2F + 1.0F * k, GOLD, (int) (110 * fade));
        FxDraw.beam(pose.last(), glow, new Vec3(0, FLOOR, 0), new Vec3(0, 12, 0), 0.08F + 0.3F * k, 0xFFFFFF, (int) (200 * fade));
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(0, 0.6F + i * 0.9F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(t * (4 + i * 2) * (i % 2 == 0 ? 1 : -1)));
            float r = 6 * (1 - Mth.clamp(em / 0.4F, 0, 1)) + 0.8F;
            FxDraw.ring(pose.last(), glow, r, r + 0.12F, i == 1 ? CRYSTAL : GOLD, (int) (200 * fade), (int) (60 * fade));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ 衣

    /** 衣: 裾から肩へすぼまる筒（裾がゆらぐ）、胴、金の縁取り。from より下は描かない（編まれていない／塵になった）。 */
    private static void renderRobe(LastArchivist e, int phase, float from, float t, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE));
        int seg = 18;
        // 衣の下半分（裾は闇へ溶ける）
        frustum(pose, vc, seg, HEM_Y, 0.88F, SHOULDER_Y, 0.42F, from, t, 0.09F, 0x2A1E3A, 120, IVORY, 255, light);
        // 胴と襟
        frustum(pose, vc, seg, SHOULDER_Y, 0.42F, COLLAR_Y, 0.34F, from, t, 0, IVORY, 255, IVORY, 255, light);
        // 袖（左右に開き、手のひらを上へ）
        float lift = e.move() == LastArchivist.JUDGMENT ? 1 : e.move() == LastArchivist.NOVA ? 0.6F : 0.25F;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.38F, 2.25F, 0.02F);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (155 - 70 * lift + 4 * Mth.sin(t * 0.07F))));
            if (2.25F > from) {
                frustum(pose, vc, 10, 0, 0.13F, 1.0F, 0.2F, -9, t, 0, IVORY, 255, IVORY, 255, light);
            }
            pose.popPose();
        }
        VertexConsumer glow = glow(buffers);
        // 金の縁取り（裾・腰・襟）
        float[][] trims = {{HEM_Y + 0.02F, 0.9F}, {1.25F, 0.66F}, {COLLAR_Y, 0.36F}};
        for (float[] tr : trims) {
            if (tr[0] < from) {
                continue;
            }
            pose.pushPose();
            pose.translate(0, tr[0], 0);
            FxDraw.ring(pose.last(), glow, tr[1], tr[1] + 0.07F, GOLD, 230, 230);
            pose.popPose();
        }
        // 手のひらの光
        if (2.25F > from) {
            for (int side = -1; side <= 1; side += 2) {
                float a = (side * (155 - 70 * lift)) * Mth.DEG_TO_RAD;
                float hx = side * 0.38F - Mth.sin(a) * 1.05F;
                float hy = 2.25F + Mth.cos(a) * 1.05F;
                pose.pushPose();
                pose.translate(hx, hy, 0.05F);
                FxDraw.billboard(pose, glow, Minecraft.getInstance().gameRenderer.getMainCamera().rotation(), 0.45F + 0.1F * Mth.sin(t * 0.3F),
                        phase == 3 ? VIOLET : GOLD, 220);
                pose.popPose();
            }
        }
    }

    /**
     * 上下にすぼまる筒（y0 で半径 r0、y1 で r1）。wave は裾のゆらぎ（y0 の側だけ）。clipFrom より下は描かない。
     * 色は下端 rgb0/a0 から上端 rgb1/a1 へ変わる。
     */
    static void frustum(PoseStack pose, VertexConsumer vc, int seg, float y0, float r0, float y1, float r1, float clipFrom, float t,
                        float wave, int rgb0, int a0, int rgb1, int a1, int light) {
        if (y1 <= clipFrom) {
            return;
        }
        float k0 = clipFrom > y0 ? (clipFrom - y0) / (y1 - y0) : 0;
        float yb = y0 + (y1 - y0) * k0;
        int rgbB = FxDraw.lerpRgb(rgb0, rgb1, k0);
        int aB = (int) (a0 + (a1 - a0) * k0);
        PoseStack.Pose last = pose.last();
        for (int s = 0; s < seg; s++) {
            float p0 = Mth.TWO_PI * s / seg;
            float p1 = Mth.TWO_PI * (s + 1) / seg;
            float w0 = 1 + wave * Mth.sin(3 * p0 + t * 0.15F) * (1 - k0);
            float w1 = 1 + wave * Mth.sin(3 * p1 + t * 0.15F) * (1 - k0);
            float rb0 = (r0 + (r1 - r0) * k0) * w0;
            float rb1 = (r0 + (r1 - r0) * k0) * w1;
            float nx0 = Mth.cos(p0), nz0 = Mth.sin(p0), nx1 = Mth.cos(p1), nz1 = Mth.sin(p1);
            vertex(vc, last, nx0 * rb0, yb, nz0 * rb0, rgbB, aB, light, nx0, 0, nz0);
            vertex(vc, last, nx0 * r1, y1, nz0 * r1, rgb1, a1, light, nx0, 0, nz0);
            vertex(vc, last, nx1 * r1, y1, nz1 * r1, rgb1, a1, light, nx1, 0, nz1);
            vertex(vc, last, nx1 * rb1, yb, nz1 * rb1, rgbB, aB, light, nx1, 0, nz1);
            // 内側（両面に見えるように）
            vertex(vc, last, nx1 * rb1, yb, nz1 * rb1, rgbB, aB, light, -nx1, 0, -nz1);
            vertex(vc, last, nx1 * r1, y1, nz1 * r1, rgb1, a1, light, -nx1, 0, -nz1);
            vertex(vc, last, nx0 * r1, y1, nz0 * r1, rgb1, a1, light, -nx0, 0, -nz0);
            vertex(vc, last, nx0 * rb0, yb, nz0 * rb0, rgbB, aB, light, -nx0, 0, -nz0);
        }
    }

    static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int rgb, int alpha, int light,
                       float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
    }

    /** 出現で編まれていく・倒されて塵になっていく境目に走る金の環。 */
    private static void renderScanRing(float y, float em, float d, PoseStack pose, MultiBufferSource buffers) {
        if (em >= 1 && d <= 0) {
            return;
        }
        VertexConsumer glow = glow(buffers);
        float r = y < SHOULDER_Y ? 0.88F + (0.42F - 0.88F) * (y - HEM_Y) / (SHOULDER_Y - HEM_Y) : 0.42F;
        pose.pushPose();
        pose.translate(0, Math.max(y, 0), 0);
        FxDraw.ring(pose.last(), glow, Math.max(0.2F, r), Math.max(0.2F, r) + 0.25F, PALE, 240, 0);
        FxDraw.ring(pose.last(), glow, Math.max(0.2F, r) + 0.25F, Math.max(0.2F, r) + 0.6F, GOLD, 120, 0);
        pose.popPose();
    }

    // ------------------------------------------------------------------ 降着の帳（足元）

    private static void renderVeil(LastArchivist e, int phase, float em, float d, float t, Vec3 origin, Quaternionf camera, PoseStack pose,
                                   MultiBufferSource buffers) {
        float size = (phase == 3 ? 1.5F : 1.0F) * Mth.clamp((em - 0.4F) / 0.3F, 0, 1) * (1 - Mth.clamp((d - 0.15F) / 0.3F, 0, 1));
        if (size < 0.02F) {
            return;
        }
        pose.pushPose();
        pose.translate(0, 0.15F, 0);
        pose.pushPose();
        pose.scale(1, 0.18F, 1);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.75F * size, 0x000000, 0);
        pose.popPose();
        VertexConsumer glow = glow(buffers);
        pose.mulPose(Axis.ZP.rotationDegrees(14));
        pose.mulPose(Axis.YP.rotationDegrees(t * 5));
        FxDraw.ring(pose.last(), glow, 0.85F * size, 1.35F * size, GOLD, 200, 0);
        FxDraw.ring(pose.last(), glow, 1.35F * size, 2.0F * size, VIOLET, 140, 0);
        pose.popPose();
    }

    // ------------------------------------------------------------------ 翼

    /**
     * 六枚の光の翼: 背中から上・中・下の3対が扇のように開く。1枚は5本の光の羽根（付け根が明るく、先へ消える）。
     * 回想からは羽根の縁に闇が差し、終焉では黒い羽根の芯に金の縁が残る。
     */
    private static void renderWings(int phase, float unfold, float d, float t, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        float flap = Mth.sin(t * 0.08F) * 6;
        float[] lift = {38, 8, -24};
        float[] length = {2.7F, 2.3F, 1.8F};
        float flicker = d > 0 ? (((int) (t * 2)) % 3 == 0 ? 0.4F : 1) : 1;
        for (int pair = 0; pair < 3; pair++) {
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 0.18F, 2.3F - pair * 0.18F, -0.28F);
                // 閉じているときは背中に沿って下を向き、開くと横へ広がる
                pose.mulPose(Axis.YP.rotationDegrees(side * (-15 - 55 * unfold)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (lift[pair] * unfold - 80 * (1 - unfold) + flap * (pair == 0 ? 1 : 0.6F))));
                for (int f = 0; f < 5; f++) {
                    pose.pushPose();
                    pose.mulPose(Axis.ZP.rotationDegrees(side * (f - 2) * 9 * unfold));
                    float len = length[pair] * (0.7F + 0.075F * (4 - Math.abs(f - 2) * 2)) * (0.3F + 0.7F * unfold);
                    int alpha = (int) (220 * flicker);
                    if (phase >= 2) {
                        // 闇の縁（少し大きく、後ろに）
                        blade(pose, glow, side, len * 1.08F, 0.36F, phase == 3 ? 0x000000 : 0x1A0A2A, (int) (alpha * 0.8F), 0x1A0A2A, 0);
                    }
                    int core = phase == 3 ? 0x101018 : PALE;
                    blade(pose, glow, side, len, 0.28F, core, alpha, phase == 3 ? GOLD : GOLD, 0);
                    blade(pose, glow, side, len * 0.85F, 0.08F, phase == 3 ? GOLD : 0xFFFFFF, alpha, GOLD, 0);
                    pose.popPose();
                }
                pose.popPose();
            }
        }
    }

    /** 付け根から外（side 方向の X）へ伸びる、両端の細い菱形の羽根（両面）。 */
    static void blade(PoseStack pose, VertexConsumer vc, int side, float len, float width, int rootRgb, int rootAlpha, int tipRgb, int tipAlpha) {
        PoseStack.Pose last = pose.last();
        float x1 = side * len;
        float xm = side * len * 0.4F;
        float h = width / 2;
        int midRgb = FxDraw.lerpRgb(rootRgb, tipRgb, 0.4F);
        int midAlpha = (int) (rootAlpha * 0.8F);
        int L = FxDraw.FULL_BRIGHT;
        for (int face = 0; face < 2; face++) {
            float nz = face == 0 ? 1 : -1;
            if (face == 0) {
                vertex(vc, last, 0, 0, 0, rootRgb, rootAlpha, L, 0, 0, nz);
                vertex(vc, last, xm, -h, 0, midRgb, midAlpha, L, 0, 0, nz);
                vertex(vc, last, x1, 0, 0, tipRgb, tipAlpha, L, 0, 0, nz);
                vertex(vc, last, xm, h, 0, midRgb, midAlpha, L, 0, 0, nz);
            } else {
                vertex(vc, last, xm, h, 0, midRgb, midAlpha, L, 0, 0, nz);
                vertex(vc, last, x1, 0, 0, tipRgb, tipAlpha, L, 0, 0, nz);
                vertex(vc, last, xm, -h, 0, midRgb, midAlpha, L, 0, 0, nz);
                vertex(vc, last, 0, 0, 0, rootRgb, rootAlpha, L, 0, 0, nz);
            }
        }
    }

    // ------------------------------------------------------------------ 光輪

    /** 頭の後ろの光輪: 二重の金の環と、12本の光の放射。on で内の環 → 外の環 → 放射の順に灯る。終焉では黒く反転する。 */
    private static void renderHalo(int phase, float on, float t, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        boolean dark = phase == 3;
        int ring = dark ? 0x000000 : GOLD;
        int rim = dark ? GOLD : PALE;
        pose.pushPose();
        pose.translate(0, LastArchivist.HALO_Y, -0.45F);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.6F));
        float inner = Mth.clamp(on / 0.35F, 0, 1);
        float outer = Mth.clamp((on - 0.35F) / 0.3F, 0, 1);
        float spokes = Mth.clamp((on - 0.65F) / 0.35F, 0, 1);
        if (inner > 0) {
            FxDraw.ring(pose.last(), glow, 0.92F, 1.02F, ring, (int) (235 * inner), (int) (235 * inner));
            FxDraw.ring(pose.last(), glow, 1.02F, 1.06F, rim, (int) (255 * inner), (int) (255 * inner));
        }
        if (outer > 0) {
            FxDraw.ring(pose.last(), glow, 1.28F, 1.4F, ring, (int) (235 * outer), (int) (235 * outer));
            FxDraw.ring(pose.last(), glow, 1.4F, 1.62F, rim, (int) (120 * outer), 0);
        }
        if (spokes > 0) {
            for (int k = 0; k < 12; k++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(k * 30));
                // 放射は輪の面（XZ）に沿った細い光。外へ消える
                pose.mulPose(Axis.XP.rotationDegrees(90));
                blade(pose, glow, 1, 2.2F * spokes + 1.0F, 0.07F, dark ? GOLD : 0xFFFFFF, (int) (200 * spokes), GOLD, 0);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** 倒されたとき: 光輪が6つの弧に割れ、外へ散りながら沈み、消える。 */
    private static void renderBrokenHalo(int phase, float k, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        int ring = phase == 3 ? 0x000000 : GOLD;
        pose.pushPose();
        pose.translate(0, LastArchivist.HALO_Y, -0.45F);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        for (int i = 0; i < 6; i++) {
            float a = i * 60 + 30;
            float rad = a * Mth.DEG_TO_RAD;
            pose.pushPose();
            // 弧の向きへ散り、少し下（輪の面では +Z）へ落ちる
            pose.translate(Mth.cos(rad) * 2.2F * k, 0, Mth.sin(rad) * 2.2F * k + 1.5F * k * k);
            arc(pose, glow, 1.28F, 1.42F, a - 24, a + 24, ring, (int) (230 * (1 - k)));
            arc(pose, glow, 0.92F, 1.02F, a - 20, a + 20, ring, (int) (200 * (1 - k)));
            pose.popPose();
        }
        pose.popPose();
    }

    /** XZ 平面の弧（中心が原点から外れていてもよいよう、原点を中心とした弧を描く）。 */
    static void arc(PoseStack pose, VertexConsumer vc, float inner, float outer, float fromDeg, float toDeg, int rgb, int alpha) {
        PoseStack.Pose last = pose.last();
        int seg = 8;
        for (int s = 0; s < seg; s++) {
            float a0 = (fromDeg + (toDeg - fromDeg) * s / seg) * Mth.DEG_TO_RAD;
            float a1 = (fromDeg + (toDeg - fromDeg) * (s + 1) / seg) * Mth.DEG_TO_RAD;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int face = 0; face < 2; face++) {
                float ny = face == 0 ? 1 : -1;
                int L = FxDraw.FULL_BRIGHT;
                if (face == 0) {
                    vertex(vc, last, c0 * inner, 0, s0 * inner, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alpha, L, 0, ny, 0);
                } else {
                    vertex(vc, last, c1 * inner, 0, s1 * inner, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c1 * outer, 0, s1 * outer, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c0 * outer, 0, s0 * outer, rgb, alpha, L, 0, ny, 0);
                    vertex(vc, last, c0 * inner, 0, s0 * inner, rgb, alpha, L, 0, ny, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 胸の記録の結晶

    private static void renderChest(LastArchivist e, float t, float d, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        float fade = d > 0.4F ? 1 - (d - 0.4F) / 0.2F : 1;
        pose.pushPose();
        pose.translate(0, LastArchivist.CHEST_Y, 0.3F);
        FxDraw.billboard(pose, glow, camera, 0.9F + 0.1F * Mth.sin(t * 0.2F), CRYSTAL, (int) (160 * fade));
        pose.mulPose(Axis.YP.rotationDegrees(t * 3));
        FxDraw.octahedron(pose.last(), glow, 0.16F, 0.26F, CRYSTAL, (int) (240 * fade));
        pose.popPose();
    }

    // ------------------------------------------------------------------ 頭（事象の地平線）

    /**
     * 頭の黒い球と、こちらを向いた金の光子の環、まわりの重力レンズ。出現では黒い一点として開き、
     * 倒されたときはふくらんで体の光を飲み込み、最後に一点へつぶれる。終焉では大きい。
     */
    private static void renderHead(LastArchivist e, int phase, float em, float d, float t, Vec3 origin, Quaternionf camera, PoseStack pose,
                                   MultiBufferSource buffers) {
        float r = phase == 3 ? 0.45F : 0.3F;
        if (em < 1) {
            r *= Mth.clamp((em - 0.2F) / 0.2F, 0, 1);
        }
        if (d > 0) {
            if (d < 0.6F) {
                r *= 1;
            } else if (d < 0.85F) {
                r *= 1 + 3 * (d - 0.6F) / 0.25F;
            } else if (d < 0.92F) {
                r *= 4 * (1 - (d - 0.85F) / 0.07F) + 0.05F;
            } else {
                r = 0;
            }
        }
        if (r < 0.01F) {
            return;
        }
        pose.pushPose();
        pose.translate(0, LastArchivist.HEAD_Y, 0);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, 0);
        VertexConsumer glow = glow(buffers);
        // 光子の環（いつもこちらを向く）
        pose.pushPose();
        pose.mulPose(camera);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FxDraw.ring(pose.last(), glow, r * 1.08F, r * 1.3F, GOLD, 240, 0);
        FxDraw.ring(pose.last(), glow, r * 1.3F, r * 2.2F, phase == 3 ? VIOLET : PALE, 110, 0);
        pose.popPose();
        if (d >= 0.6F && d < 0.92F) {
            FxDraw.gyroRings(pose, glow, r * 2.2F + 2 * (1 - (d - 0.6F) / 0.32F), 0.05F, t * 10, GOLD, 220);
        }
        pose.popPose();
        float strength = phase == 3 ? 0.9F : 0.55F;
        GravitationalLensing.add(origin.add(0, LastArchivist.HEAD_Y, 0), r * 2.4F, d > 0.6F ? 0.95F : strength);
    }

    // ------------------------------------------------------------------ 技

    private static void renderMoves(LastArchivist e, int phase, float t, float partialTick, Vec3 origin, Quaternionf camera, PoseStack pose,
                                    MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        int move = e.move();
        float left = Math.max(0, e.moveTicks() - partialTick);
        Vec3 halo = e.haloCenter().subtract(e.position());
        // 裁きの光: 狙いの細い線、放つと太い光の柱
        if (move == LastArchivist.JUDGMENT || e.flash() > 0) {
            List<Vec3> points = e.judgePoints();
            for (Vec3 p : points) {
                Vec3 to = p.subtract(origin);
                if (move == LastArchivist.JUDGMENT) {
                    int a = (int) (90 + 90 * Mth.sin(t * 0.8F));
                    FxDraw.beam(pose.last(), glow, halo, to, 0.05F, GOLD, a);
                } else {
                    float k = e.flash() / (float) LastArchivist.FLASH_TICKS;
                    Vec3 far = halo.add(to.subtract(halo).normalize().scale(LastArchivist.JUDGE_RANGE));
                    FxDraw.beam(pose.last(), glow, halo, far, 0.8F * k + 0.2F, GOLD, (int) (160 * k));
                    FxDraw.beam(pose.last(), glow, halo, far, 0.25F * k + 0.05F, 0xFFFFFF, (int) (255 * k));
                }
            }
        }
        // 回想: その番人の色のもや
        int recall = move == LastArchivist.RECALL_ECHO ? ECHO : move == LastArchivist.RECALL_GRAVITY ? VIOLET
                : move == LastArchivist.RECALL_HORIZON ? HORIZON : -1;
        if (recall >= 0) {
            pose.pushPose();
            pose.translate(0, 1.6F, 0);
            FxDraw.billboard(pose, glow, camera, 4.2F + 0.3F * Mth.sin(t * 0.5F), recall, 90);
            pose.popPose();
        }
        // 地平線の衝撃波の輪（床）
        if (move == LastArchivist.RECALL_HORIZON && left <= LastArchivist.HORIZON_SPREAD) {
            float r = (float) LastArchivist.horizonRadius(Math.round(left));
            pose.pushPose();
            pose.translate(0, FLOOR, 0);
            FxDraw.ring(pose.last(), glow, Math.max(0, r - 0.8F), r, HORIZON, 230, 0);
            pose.popPose();
        }
        // 新星: 光が内へ集まり、殻が外へ広がる
        if (move == LastArchivist.NOVA) {
            pose.pushPose();
            pose.translate(0, LastArchivist.CHEST_Y, 0);
            if (left > LastArchivist.NOVA_SPREAD) {
                float c = (left - LastArchivist.NOVA_SPREAD) / LastArchivist.NOVA_CHARGE;
                FxDraw.gyroRings(pose, glow, 0.6F + 6 * c, 0.06F, t * 12, GOLD, 220);
                FxDraw.billboard(pose, glow, camera, 1.5F + 2 * (1 - c), 0xFFFFFF, (int) (200 * (1 - c)));
            } else {
                float r = (float) LastArchivist.novaRadius(Math.round(left));
                float k = 1 - left / LastArchivist.NOVA_SPREAD;
                for (int i = -3; i <= 3; i++) {
                    float y = r * i / 4F;
                    float rr = (float) Math.sqrt(Math.max(0, r * r - y * y));
                    pose.pushPose();
                    pose.translate(0, y, 0);
                    FxDraw.ring(pose.last(), glow, Math.max(0, rr - 0.6F), rr, i == 0 ? 0xFFFFFF : GOLD, (int) (200 * (1 - k * 0.6F)), 0);
                    pose.popPose();
                }
                // 足元の目（ここにいれば当たらない）
                pose.pushPose();
                pose.translate(0, FLOOR - LastArchivist.CHEST_Y, 0);
                FxDraw.ring(pose.last(), glow, (float) LastArchivist.NOVA_EYE - 0.15F, (float) LastArchivist.NOVA_EYE, CRYSTAL, 220, 220);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** まわりを回る、記録の小さな結晶。 */
    private static void renderRecords(float t, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        for (int i = 0; i < 6; i++) {
            float a = t * 0.03F + i * Mth.TWO_PI / 6;
            pose.pushPose();
            pose.translate(Mth.cos(a) * 1.7F, 1.6F + 0.4F * Mth.sin(t * 0.05F + i), Mth.sin(a) * 1.7F);
            pose.mulPose(Axis.YP.rotationDegrees(t * 4 + i * 40));
            FxDraw.octahedron(pose.last(), glow, 0.08F, 0.14F, CRYSTAL, 220);
            FxDraw.billboard(pose, glow, camera, 0.4F, CRYSTAL, 70);
            pose.popPose();
        }
    }
}
