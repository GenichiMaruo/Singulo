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
 *   <li>頭の後ろに大きな光輪（二重の金の環と12本の光の放射）。背中に、端から端まで約12ブロックの大きな天使の翼。
 *       羽根を重ねた象牙の翼に金の先で、ゆっくり大きく羽ばたく</li>
 *   <li>胸に記録の結晶。体のまわりを、記録の小さな結晶が回る</li>
 *   <li>回想: 番人の技を使うとき、その番人の色のもやをまとう。終焉: 光輪が黒く反転し、翼に黒い縁が差し、頭の特異点が大きくなる</li>
 *   <li>出現: 金の光の柱と、すぼまる記録の環。黒い一点が開き、体が上から光で編まれ、翼が開き、光輪が灯って閃光</li>
 *   <li>ボス部屋: 床の光の環・柱の光・天球の記録・終焉の暗がりと降着円盤・重力潮汐の瓦礫・部屋を閉ざす力場（{@link #renderArena}）</li>
 *   <li>倒されたとき: 光輪が割れて散り、翼の羽根が一枚ずつ外れて舞い落ち、体が足元から金の塵になって消え、
 *       特異点がふくらんでからつぶれて閃光</li>
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
            unfold *= 1 - 0.3F * Mth.clamp((d - 0.1F) / 0.4F, 0, 1);
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
        if (unfold > 0.01F && d < 0.9F) {
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

        renderArena(e, phase, em, d, t, partialTick, origin, camera, pose, buffers);
        if (e.absorbTicks() > 0 && d <= 0) {
            renderAbsorb(e, t, partialTick, camera, pose, buffers);
        }
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

    /** 翼の大きさ（翼の付け根から先までの長さの倍率）。左右の端から端まで約12ブロック。 */
    static final float WING_SCALE = 1.3F;
    /** 翼の骨: 肩・肘・手首・手の先（右の翼の、付け根から見た位置。X が外、Y が上）。 */
    static final float[][] WING_BONE = {{0, 0}, {1.5F, 0.55F}, {3.0F, 0.85F}, {3.9F, 0.7F}};

    /**
     * 大きな天使の翼（一対）。骨（肩→肘→手首→手の先）に沿って、羽根を重ねて描く。
     * <ul>
     *   <li>初列風切: 手首から先に10枚。内側は下へ、外側ほど長く外へ扇のように開く</li>
     *   <li>次列・三列風切: 肘から手首、肩から肘に沿って垂れ下がる</li>
     *   <li>雨覆: 付け根を覆う短い羽根の2列と、光る前縁</li>
     * </ul>
     * ゆっくり大きく羽ばたき、打ち上げで初列風切が開く。象牙の羽根に金の先。回想からは羽根の縁に闇が差し、
     * 終焉では黒い羽根に金の先だけが残る。出現では背に畳まれた翼が開き、倒されると羽根が一枚ずつ外れて舞い落ちる。
     */
    private static void renderWings(int phase, float unfold, float d, float t, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        float beat = Mth.sin(t * 0.05F);
        float flicker = d > 0 ? (((int) (t * 2)) % 3 == 0 ? 0.55F : 1) : 1;
        // 開いたとき: 少し後ろへ流し、羽ばたきで上下する。畳んだとき: 背に沿って下へ
        float sweep = 22 + 58 * (1 - unfold);
        float roll = (18 + 10 * beat) * unfold - 68 * (1 - unfold);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.22F, 2.25F, -0.3F);
            pose.scale(side, 1, 1);                       // 左の翼は右の翼を鏡に映したもの
            pose.mulPose(Axis.YP.rotationDegrees(sweep));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));
            pose.scale(WING_SCALE, WING_SCALE, WING_SCALE);
            drawWing(phase, unfold, d, beat, flicker, pose, glow);
            pose.popPose();
        }
    }

    /** 右の翼を、付け根を原点に描く（X が外、Y が上、Z が前）。 */
    private static void drawWing(int phase, float unfold, float d, float beat, float flicker, PoseStack pose, VertexConsumer vc) {
        int k = 0;
        float open = 0.35F + 0.65F * unfold;
        // 初列風切（手首 → 手の先、10枚）
        for (int i = 0; i < 10; i++) {
            float u = i / 9F;
            float[] at = along(2, 3, u);
            float angle = (-62 + 77 * u) * open - 80 * (1 - open) + 7 * beat * u;
            feather(phase, k++, d, flicker, pose, vc, at[0], at[1], -0.06F, angle, 1.9F + 0.95F * u, 0.44F);
        }
        // 次列風切（肘 → 手首、12枚）
        for (int i = 0; i < 12; i++) {
            float u = i / 11F;
            float[] at = along(1, 2, u);
            float angle = -96 + 24 * u * open + jitter(i, 4);
            feather(phase, k++, d, flicker, pose, vc, at[0], at[1], -0.05F, angle, 1.85F - 0.1F * u, 0.42F);
        }
        // 三列風切（肩 → 肘、5枚）
        for (int i = 0; i < 5; i++) {
            float u = 0.12F + 0.2F * i;
            float[] at = along(0, 1, u);
            feather(phase, k++, d, flicker, pose, vc, at[0], at[1], -0.045F, -100 + jitter(i + 20, 4), 1.3F + 0.4F * u, 0.4F);
        }
        // 大雨覆（肩 → 手首、14枚）
        for (int i = 0; i < 14; i++) {
            float u = i / 13F;
            float[] at = alongArm(u * 0.86F);
            feather(phase, k++, d, flicker, pose, vc, at[0], at[1] - 0.05F, -0.02F, -92 + 20 * u + jitter(i + 40, 5), 0.95F, 0.34F);
        }
        // 小雨覆（前縁に沿って、10枚）
        for (int i = 0; i < 10; i++) {
            float u = i / 9F;
            float[] at = alongArm(u * 0.8F);
            feather(phase, k++, d, flicker, pose, vc, at[0], at[1] + 0.02F, 0.0F, -85 + 25 * u + jitter(i + 60, 6), 0.55F, 0.3F);
        }
        // 光る前縁（骨）
        if (d < 0.5F) {
            int a = (int) (230 * flicker * (1 - d * 2));
            int edge = phase == 3 ? GOLD : 0xFFFFFF;
            for (int b = 0; b < 3; b++) {
                Vec3 from = new Vec3(WING_BONE[b][0], WING_BONE[b][1] + 0.05, 0.01);
                Vec3 to = new Vec3(WING_BONE[b + 1][0], WING_BONE[b + 1][1] + 0.05, 0.01);
                FxDraw.beam(pose.last(), vc, from, to, 0.16F - 0.04F * b, edge, a);
            }
        }
    }

    /** 骨の b0 番目から b1 番目の点のあいだを u（0〜1）で分けた位置。 */
    private static float[] along(int b0, int b1, float u) {
        return new float[] {Mth.lerp(u, WING_BONE[b0][0], WING_BONE[b1][0]), Mth.lerp(u, WING_BONE[b0][1], WING_BONE[b1][1])};
    }

    /** 肩から手の先までの骨全体を u（0〜1）で分けた位置。 */
    private static float[] alongArm(float u) {
        float f = u * 3;
        int b = Math.min(2, (int) f);
        return along(b, b + 1, f - b);
    }

    /** 羽根ごとに少しずつ違う向き（いつも同じになる、ばらつき）。 */
    private static float jitter(int i, float amount) {
        return (float) (Math.sin(i * 12.9898) * 43758.5453 % 1) * amount;
    }

    /**
     * 羽根を1枚描く: (x, y) を付け根に、angle 度（+X から反時計回り）の向きへ長さ len。
     * 付け根は細く、中ほどで幅 width になり、先は丸くとがる。中央に光る羽軸。
     * 倒されたときは、羽根ごとに決まった時に外れ、回りながら舞い落ちて消える。
     */
    private static void feather(int phase, int k, float d, float flicker, PoseStack pose, VertexConsumer vc,
                                float x, float y, float z, float angle, float len, float width) {
        float alpha = flicker;
        pose.pushPose();
        pose.translate(x, y, z);
        if (d > 0) {
            float release = 0.06F + (float) ((k * 0.6180339887) % 1) * 0.5F;
            if (d > release) {
                float s = d - release;
                alpha *= Math.max(0, 1 - s / 0.3F);
                if (alpha <= 0.01F) {
                    pose.popPose();
                    return;
                }
                pose.translate(0.6F * s * Mth.sin(k * 1.7F), -9 * s * s - 1.2F * s, 0.8F * s * Mth.cos(k * 2.3F));
                pose.mulPose(Axis.XP.rotationDegrees(s * 400 * (k % 2 == 0 ? 1 : -1)));
            }
        }
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        int rootRgb, midRgb, tipRgb, rootA, midA, tipA, rachis;
        if (phase == 3) {
            rootRgb = 0x0C0A12; midRgb = 0x1A1426; tipRgb = GOLD;
            rootA = 235; midA = 215; tipA = 170; rachis = GOLD;
        } else {
            rootRgb = PALE; midRgb = IVORY; tipRgb = GOLD;
            rootA = 215; midA = 185; tipA = 70; rachis = 0xFFFFFF;
        }
        if (phase == 2) {
            // 闇の縁（少し大きく、後ろに）
            featherShape(pose, vc, len * 1.07F, width * 1.25F, -0.004F, 0x1A0A2A, (int) (150 * alpha), 0x1A0A2A, (int) (110 * alpha),
                    0x2A1040, (int) (40 * alpha));
        }
        featherShape(pose, vc, len, width, 0, rootRgb, (int) (rootA * alpha), midRgb, (int) (midA * alpha), tipRgb, (int) (tipA * alpha));
        // 羽軸
        featherShape(pose, vc, len * 0.92F, 0.035F, 0.003F, rachis, (int) (230 * alpha), rachis, (int) (180 * alpha), GOLD, (int) (40 * alpha));
        pose.popPose();
    }

    /** 羽根の形（+X 方向、長さ len・幅 width、両面）。付け根 → 幅が最大（25%〜70%）→ 丸い先。 */
    private static void featherShape(PoseStack pose, VertexConsumer vc, float len, float width, float z,
                                     int rootRgb, int rootA, int midRgb, int midA, int tipRgb, int tipA) {
        PoseStack.Pose last = pose.last();
        float h = width / 2;
        float x1 = len * 0.25F, x2 = len * 0.7F, x3 = len * 0.9F;
        int L = FxDraw.FULL_BRIGHT;
        int shoulderRgb = FxDraw.lerpRgb(midRgb, tipRgb, 0.5F);
        int shoulderA = (midA + tipA) / 2;
        for (int face = 0; face < 2; face++) {
            float nz = face == 0 ? 1 : -1;
            // 付け根 → 幅の広がり
            quadSym(vc, last, face, z, 0, 0.02F, x1, h * 0.85F, rootRgb, rootA, midRgb, midA, L, nz);
            // 中ほど
            quadSym(vc, last, face, z, x1, h * 0.85F, x2, h, midRgb, midA, shoulderRgb, shoulderA, L, nz);
            // 先へ丸くすぼまる
            quadSym(vc, last, face, z, x2, h, x3, h * 0.55F, shoulderRgb, shoulderA, tipRgb, tipA, L, nz);
            quadSym(vc, last, face, z, x3, h * 0.55F, len, 0.01F, tipRgb, tipA, tipRgb, 0, L, nz);
        }
    }

    /** X 軸に沿って上下対称な台形（xa で高さ ±ha、xb で ±hb）。 */
    private static void quadSym(VertexConsumer vc, PoseStack.Pose last, int face, float z, float xa, float ha, float xb, float hb,
                                int rgbA, int alphaA, int rgbB, int alphaB, int light, float nz) {
        if (face == 0) {
            vertex(vc, last, xa, -ha, z, rgbA, alphaA, light, 0, 0, nz);
            vertex(vc, last, xb, -hb, z, rgbB, alphaB, light, 0, 0, nz);
            vertex(vc, last, xb, hb, z, rgbB, alphaB, light, 0, 0, nz);
            vertex(vc, last, xa, ha, z, rgbA, alphaA, light, 0, 0, nz);
        } else {
            vertex(vc, last, xa, ha, z, rgbA, alphaA, light, 0, 0, nz);
            vertex(vc, last, xb, hb, z, rgbB, alphaB, light, 0, 0, nz);
            vertex(vc, last, xb, -hb, z, rgbB, alphaB, light, 0, 0, nz);
            vertex(vc, last, xa, -ha, z, rgbA, alphaA, light, 0, 0, nz);
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
                    // 狙いの的: 着く場所の輪が、放つ瞬間へ向けてすぼまる
                    float k = Mth.clamp(left / LastArchivist.JUDGE_WINDUP, 0, 1);
                    pose.pushPose();
                    pose.translate(to.x, to.y - 0.9, to.z);
                    pose.mulPose(Axis.YP.rotationDegrees(t * 6));
                    FxDraw.ring(pose.last(), glow, 0.5F + 2.6F * k, 0.65F + 2.6F * k, GOLD, 230, 230);
                    FxDraw.ring(pose.last(), glow, 0.2F, 0.35F, 0xFFFFFF, 200, 200);
                    pose.popPose();
                } else {
                    float k = e.flash() / (float) LastArchivist.FLASH_TICKS;
                    Vec3 far = halo.add(to.subtract(halo).normalize().scale(LastArchivist.JUDGE_RANGE));
                    FxDraw.beam(pose.last(), glow, halo, far, 0.8F * k + 0.2F, GOLD, (int) (160 * k));
                    FxDraw.beam(pose.last(), glow, halo, far, 0.25F * k + 0.05F, 0xFFFFFF, (int) (255 * k));
                    // 着いた所: 天まで届く光の柱と、床を走る爆ぜる輪
                    FxDraw.beam(pose.last(), glow, to.add(0, -1, 0), to.add(0, 16, 0), 2.2F * k + 0.3F, GOLD, (int) (150 * k));
                    FxDraw.beam(pose.last(), glow, to.add(0, -1, 0), to.add(0, 16, 0), 0.8F * k + 0.1F, 0xFFFFFF, (int) (230 * k));
                    float rr = (float) LastArchivist.JUDGE_SPLASH * (1.6F - k);
                    pose.pushPose();
                    pose.translate(to.x, to.y - 0.9, to.z);
                    FxDraw.ring(pose.last(), glow, Math.max(0, rr - 0.5F), rr, GOLD, (int) (240 * k), 0);
                    FxDraw.billboard(pose, glow, camera, 4.5F * k + 1, 0xFFFFFF, (int) (200 * k));
                    pose.popPose();
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
            // 輪の縁に立つ光の壁（跳び越える高さの目安）
            for (int i = 1; i <= 6; i++) {
                pose.translate(0, 0.28F, 0);
                FxDraw.ring(pose.last(), glow, Math.max(0, r - 0.12F), r, i < 3 ? 0xFFFFFF : HORIZON, 200 - i * 30, 200 - i * 30);
            }
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
                // 弾けた瞬間: 部屋じゅうを白く染める閃光
                if (k < 0.35F) {
                    float f = 1 - k / 0.35F;
                    FxDraw.billboard(pose, glow, camera, 40, 0xFFFFFF, (int) (170 * f));
                    FxDraw.billboard(pose, glow, camera, 12, GOLD, (int) (220 * f));
                }
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

    /**
     * ブラックホール爆弾を飲み込んで回復する: 頭の特異点がいったんふくらんでから飲み込み、光輪がまぶしく燃え上がる。
     * 胸から金の波紋が何重にも広がり、命の光（緑がかった金）が足元から体を昇っていく。
     */
    private static void renderAbsorb(LastArchivist e, float t, float partialTick, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        float k = Mth.clamp((e.absorbTicks() - partialTick) / LastArchivist.ABSORB_TICKS, 0, 1);    // 1 → 0
        float p = 1 - k;
        int life = 0xB8FF9A;
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        // 頭: 飲み込む瞬間にふくらんで、すっと戻る
        pose.pushPose();
        pose.translate(0, LastArchivist.HEAD_Y, 0);
        float gulp = p < 0.15F ? p / 0.15F : Math.max(0, 1 - (p - 0.15F) / 0.25F);
        if (gulp > 0) {
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.45F + 0.5F * gulp, 0x000000, 0);
        }
        VertexConsumer glow = glow(buffers);
        FxDraw.billboard(pose, glow, camera, 1.5F + 3 * gulp, 0xFFFFFF, (int) (220 * gulp));
        pose.popPose();
        // 光輪が燃え上がる
        pose.pushPose();
        pose.translate(0, LastArchivist.HALO_Y, -0.45F);
        FxDraw.billboard(pose, glow, camera, 3.5F + 3.5F * k, GOLD, (int) (200 * k));
        FxDraw.billboard(pose, glow, camera, 2.0F + 1.5F * k, 0xFFFFFF, (int) (230 * k));
        pose.popPose();
        // 胸から広がる金の波紋（3重）
        for (int i = 0; i < 3; i++) {
            float q = Mth.clamp(p * 1.6F - i * 0.25F, 0, 1);
            if (q <= 0 || q >= 1) {
                continue;
            }
            pose.pushPose();
            pose.translate(0, LastArchivist.CHEST_Y, 0);
            float r = 0.6F + 7 * q;
            FxDraw.ring(pose.last(), glow, Math.max(0, r - 0.35F), r, i == 1 ? life : GOLD, (int) (230 * (1 - q)), 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            FxDraw.ring(pose.last(), glow, Math.max(0, r - 0.25F), r, GOLD, (int) (160 * (1 - q)), 0);
            pose.popPose();
        }
        // 足元から体を昇る、命の光
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + t * 0.05;
            float r = 0.75F;
            float y0 = ((p * 2.2F + i * 0.13F) % 1F) * 3.2F;
            Vec3 from = new Vec3(Math.cos(a) * r, y0, Math.sin(a) * r);
            FxDraw.beam(pose.last(), glow, from, from.add(0, 0.9, 0), 0.07F, i % 2 == 0 ? life : GOLD, (int) (230 * k));
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ ボス部屋（最終実験施設）の演出

    /** 部屋の中心（力場の中心）から見た、床の上面の高さ。 */
    static final float ARENA_FLOOR = -0.45F;
    /** 天球に浮かぶ記録の文字の数。 */
    static final int GLYPHS = 150;

    /**
     * 記録者が目覚めている間の、部屋じゅうの演出（ホライズン・ウォーデンにはない、この部屋だけのもの）。
     * <ul>
     *   <li>床の光の環が灯って、内と外で逆向きに回る（刻まれた文字の板つき）</li>
     *   <li>8本の白い柱から天井へ光が昇る。終焉では光が記録者の頭の特異点へ曲がって吸い込まれる</li>
     *   <li>ドームの内側を、記録の文字が天球のように埋めて回る（回想で菫、終焉で金と黒）</li>
     *   <li>終焉: 部屋が暗くなり、天井に降着円盤が回る</li>
     *   <li>重力潮汐: 床の瓦礫と塵が浮き上がり、低重力の間は宙に漂う</li>
     *   <li>力場: 床から編み上がる金の格子の球。誰かがぶつかると光り、倒されるとほどける</li>
     * </ul>
     */
    private static void renderArena(LastArchivist e, int phase, float em, float d, float t, float partialTick, Vec3 origin,
                                    Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        java.util.Optional<net.minecraft.core.BlockPos> home = e.arenaHome();
        if (home.isEmpty()) {
            return;
        }
        Vec3 center = LastArchivist.shieldCenter(home.get());
        Vec3 off = center.subtract(origin);
        float live = em < 1 ? Mth.clamp((em - 0.15F) / 0.6F, 0, 1) : 1;
        if (d > 0) {
            live *= 1 - Mth.clamp(d / 0.45F, 0, 1);
        }
        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        // 終焉: 部屋を暗くする（内側から見る黒い膜）。先に描いて、ほかの光をその上に重ねる
        if (phase == 3 && live > 0.01F) {
            sphereShell(pose, buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE)), 14.6F, 0x05030A, (int) (110 * live), ARENA_FLOOR);
        }
        VertexConsumer glow = glow(buffers);
        if (live > 0.01F) {
            int ringRgb = phase == 1 ? GOLD : phase == 2 ? 0xB89CFF : GOLD;
            arenaRings(pose, glow, t, live, ringRgb, phase);
            pillarLights(e, pose, glow, t, live, phase, origin, center, partialTick);
            glyphSphere(pose, glow, t, live, phase);
            if (phase == 3) {
                pose.pushPose();
                pose.translate(0, 12.5F, 0);
                pose.mulPose(Axis.XP.rotationDegrees(8));
                pose.mulPose(Axis.YP.rotationDegrees(t * 1.5F));
                FxDraw.ring(pose.last(), glow, 3.0F, 6.5F, VIOLET, (int) (150 * live), 0);
                FxDraw.ring(pose.last(), glow, 6.5F, 9.0F, GOLD, (int) (90 * live), 0);
                FxDraw.ring(pose.last(), glow, 2.6F, 3.0F, 0xFFFFFF, (int) (220 * live), (int) (220 * live));
                pose.popPose();
            }
        }
        int tide = e.tideTicks();
        if (tide > 0) {
            tideDebris(pose, glow, t, tide, partialTick);
        }
        renderShield(e, pose, buffers, t, partialTick);
        pose.popPose();
    }

    /** 床の二重の光の環（内と外で逆に回り、文字の板が刻まれている）。 */
    private static void arenaRings(PoseStack pose, VertexConsumer vc, float t, float live, int rgb, int phase) {
        float speed = phase == 3 ? 2.2F : phase == 2 ? 1.3F : 0.8F;
        float[][] rings = {{7.0F, 1}, {12.0F, -1}};
        for (float[] r : rings) {
            pose.pushPose();
            pose.translate(0, ARENA_FLOOR + 0.04F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(t * speed * r[1]));
            FxDraw.ring(pose.last(), vc, r[0] - 0.35F, r[0], rgb, (int) (200 * live), (int) (60 * live));
            FxDraw.ring(pose.last(), vc, r[0] + 0.25F, r[0] + 0.35F, 0xFFFFFF, (int) (150 * live), (int) (150 * live));
            int marks = r[0] < 10 ? 16 : 28;
            for (int i = 0; i < marks; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(360F * i / marks));
                float w = (i % 3 == 0) ? 0.5F : 0.22F;
                FxDraw.box(pose, vc, r[0] - 0.9F, 0, -w / 2, r[0] - 0.55F, 0.03F, w / 2, FxDraw.FULL_BRIGHT, rgb, (int) (190 * live));
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** 8本の柱（半径12）から天井へ昇る光。終焉では柱の上から記録者の頭へ曲がる。 */
    private static void pillarLights(LastArchivist e, PoseStack pose, VertexConsumer vc, float t, float live, int phase, Vec3 origin,
                                     Vec3 center, float partialTick) {
        Vec3 head = e.getPosition(partialTick).add(0, LastArchivist.HEAD_Y, 0).subtract(center);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            Vec3 base = new Vec3(Math.round(Math.cos(a) * 12), ARENA_FLOOR, Math.round(Math.sin(a) * 12));
            Vec3 top = base.add(0, 9.4, 0);
            float flick = 0.75F + 0.25F * Mth.sin(t * 0.3F + k);
            int alpha = (int) (170 * live * flick);
            FxDraw.beam(pose.last(), vc, base, top, 0.5F, phase == 2 ? 0xB89CFF : PALE, alpha / 2);
            if (phase == 3) {
                // 光が特異点へ吸い込まれていく
                FxDraw.beam(pose.last(), vc, top, head, 0.18F, GOLD, alpha);
                FxDraw.beam(pose.last(), vc, top, head, 0.06F, 0xFFFFFF, alpha);
            } else {
                FxDraw.beam(pose.last(), vc, top, top.add(0, 5, 0), 0.3F, GOLD, alpha / 2);
            }
        }
    }

    /** ドームの内側を埋めて回る、記録の文字（天球）。 */
    private static void glyphSphere(PoseStack pose, VertexConsumer vc, float t, float live, int phase) {
        int rgb = phase == 1 ? PALE : phase == 2 ? 0xB89CFF : GOLD;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * (phase == 3 ? 0.6F : 0.25F)));
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < GLYPHS; i++) {
            double y = 1 - (i + 0.5) / GLYPHS;              // 上半分だけ（1 → 0）
            double rr = Math.sqrt(1 - y * y);
            double a = golden * i;
            float r = 13.4F;
            float x = (float) (Math.cos(a) * rr * r);
            float z = (float) (Math.sin(a) * rr * r);
            float yy = (float) (y * r * 0.95F) + ARENA_FLOOR + 0.5F;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.07F + i * 1.7F);
            pose.pushPose();
            pose.translate(x, yy, z);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(-a) + 90));
            float len = 0.35F + (i % 4) * 0.15F;
            FxDraw.box(pose, vc, -len / 2, -0.03F, -0.01F, len / 2, 0.03F, 0.01F, FxDraw.FULL_BRIGHT, rgb, (int) (40 + 150 * tw * live));
            if (i % 3 == 0) {
                FxDraw.box(pose, vc, -len / 2, -0.15F, -0.01F, len / 4, -0.11F, 0.01F, FxDraw.FULL_BRIGHT, rgb, (int) (30 + 110 * tw * live));
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /** 重力潮汐: 床の瓦礫と塵が浮き上がり、低重力の間は宙に漂う。 */
    private static void tideDebris(PoseStack pose, VertexConsumer vc, float t, int tide, float partialTick) {
        float left = tide - partialTick;
        boolean lifting = LastArchivist.tideLifting(tide);
        float settle = Math.min(1, left / 20F);                  // 終わり際に沈む
        for (int i = 0; i < 48; i++) {
            double a = i * 2.39996;
            float r = 2.5F + (i * 37 % 100) / 100F * 11.5F;
            float x = (float) Math.cos(a) * r;
            float z = (float) Math.sin(a) * r;
            float rise = lifting ? Math.min(1, (LastArchivist.TIDE_LOW + LastArchivist.TIDE_LIFT_FINAL - left) / 30F) : 1;
            float h = (0.8F + (i * 53 % 100) / 100F * 4.5F) * rise * settle + 0.25F * Mth.sin(t * 0.1F + i);
            float sz = 0.08F + (i % 5) * 0.05F;
            pose.pushPose();
            pose.translate(x, ARENA_FLOOR + h, z);
            pose.mulPose(Axis.YP.rotationDegrees(t * (2 + i % 7)));
            pose.mulPose(Axis.XP.rotationDegrees(t * (1 + i % 5)));
            FxDraw.box(pose, vc, -sz, -sz, -sz, sz, sz, sz, FxDraw.FULL_BRIGHT, i % 4 == 0 ? GOLD : 0x8A8090, 200);
            pose.popPose();
        }
    }

    /** 力場: 床から編み上がる金の格子の球。ぶつかると光り、倒されるとほどけて消える。 */
    private static void renderShield(LastArchivist e, PoseStack pose, MultiBufferSource buffers, float t, float partialTick) {
        int s = e.shieldTicks();
        if (s == 0) {
            return;
        }
        float k = s > 0 ? Mth.clamp((s + partialTick) / LastArchivist.SHIELD_RISE, 0, 1)
                : Mth.clamp(1 + (s - partialTick) / LastArchivist.SHIELD_RISE, 0, 1);
        if (k <= 0.01F) {
            return;
        }
        float r = (float) LastArchivist.SHIELD_RADIUS;
        float hit = e.shieldHit() / 10F;
        float top = ARENA_FLOOR + (r - ARENA_FLOOR) * (s > 0 ? k : 1);     // 張るときは床から上へ編み上がる
        int fade = (int) (255 * (s > 0 ? 1 : k));
        sphereShell(pose, glow(buffers), r, GOLD, (int) ((18 + 70 * hit) * fade / 255F), ARENA_FLOOR, top);
        VertexConsumer vc = glow(buffers);
        int line = (int) ((120 + 120 * hit) * fade / 255F);
        // 緯線（水平の環）
        for (int i = 0; i < 8; i++) {
            float y = ARENA_FLOOR + (r - ARENA_FLOOR) * i / 8F;
            if (y > top) {
                break;
            }
            float rr = (float) Math.sqrt(Math.max(0, r * r - y * y));
            pose.pushPose();
            pose.translate(0, y, 0);
            FxDraw.ring(pose.last(), vc, rr - 0.06F, rr, i == 0 ? 0xFFFFFF : GOLD, line, line);
            pose.popPose();
        }
        // 経線（床から天頂へ）。ゆっくり回る
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.4F));
        for (int m = 0; m < 16; m++) {
            double a = Math.PI * 2 * m / 16;
            Vec3 prev = null;
            for (int j = 0; j <= 12; j++) {
                double phi = Math.asin(Mth.clamp(ARENA_FLOOR / r, -1, 1)) + (Math.PI / 2 - Math.asin(Mth.clamp(ARENA_FLOOR / r, -1, 1))) * j / 12;
                double y = Math.sin(phi) * r;
                if (y > top + 0.01) {
                    break;
                }
                double rr = Math.cos(phi) * r;
                Vec3 q = new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
                if (prev != null) {
                    FxDraw.beam(pose.last(), vc, prev, q, 0.08F, GOLD, line);
                }
                prev = q;
            }
        }
        pose.popPose();
        // 編み上がっていく縁の光
        if (s > 0 && k < 1) {
            float rr = (float) Math.sqrt(Math.max(0, r * r - top * top));
            pose.pushPose();
            pose.translate(0, top, 0);
            FxDraw.ring(pose.last(), vc, Math.max(0, rr - 0.5F), rr, 0xFFFFFF, 240, 0);
            pose.popPose();
        }
    }

    private static void sphereShell(PoseStack pose, VertexConsumer vc, float r, int rgb, int alpha, float yMin) {
        sphereShell(pose, vc, r, rgb, alpha, yMin, r);
    }

    /** 半透明の球の殻（両面）。yMin〜yMax の高さだけ描く。 */
    private static void sphereShell(PoseStack pose, VertexConsumer vc, float r, int rgb, int alpha, float yMin, float yMax) {
        if (alpha <= 0) {
            return;
        }
        PoseStack.Pose last = pose.last();
        int lon = 32;
        int lat = 16;
        int L = FxDraw.FULL_BRIGHT;
        for (int j = 0; j < lat; j++) {
            float p0 = (float) (-Math.PI / 2 + Math.PI * j / lat);
            float p1 = (float) (-Math.PI / 2 + Math.PI * (j + 1) / lat);
            float y0 = Mth.sin(p0) * r;
            float y1 = Mth.sin(p1) * r;
            if (y1 < yMin || y0 > yMax) {
                continue;
            }
            float c0 = Mth.cos(p0) * r;
            float c1 = Mth.cos(p1) * r;
            for (int i = 0; i < lon; i++) {
                float a0 = Mth.TWO_PI * i / lon;
                float a1 = Mth.TWO_PI * (i + 1) / lon;
                float x00 = Mth.cos(a0) * c0, z00 = Mth.sin(a0) * c0;
                float x01 = Mth.cos(a1) * c0, z01 = Mth.sin(a1) * c0;
                float x10 = Mth.cos(a0) * c1, z10 = Mth.sin(a0) * c1;
                float x11 = Mth.cos(a1) * c1, z11 = Mth.sin(a1) * c1;
                for (int face = 0; face < 2; face++) {
                    float n = face == 0 ? 1 : -1;
                    if (face == 0) {
                        vertex(vc, last, x00, y0, z00, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x01, y0, z01, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x11, y1, z11, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x10, y1, z10, rgb, alpha, L, 0, n, 0);
                    } else {
                        vertex(vc, last, x10, y1, z10, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x11, y1, z11, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x01, y0, z01, rgb, alpha, L, 0, n, 0);
                        vertex(vc, last, x00, y0, z00, rgb, alpha, L, 0, n, 0);
                    }
                }
            }
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
