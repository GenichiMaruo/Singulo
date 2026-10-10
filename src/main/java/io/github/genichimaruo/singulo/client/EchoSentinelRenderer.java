package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 残響の番人: 光の線でできた警備ホログラム（人の約1.3倍）。
 * <ul>
 *   <li>裾の広がった外套の下半身は宙に浮いて先細り、胴・肩当て・兜と、垂らした両腕の先の光の刃</li>
 *   <li>胸に記録結晶（八面体）、頭の後ろに回る光輪。走査線が下から上へ流れ続ける</li>
 *   <li>残っている投影器が多いほど薄く、ちらつき、横にずれる（実体化すると濃く、安定する）</li>
 *   <li>背後へ跳んで構える間は、刃が伸びて光の輪が体へ縮んでいく</li>
 *   <li>投影陣: 足元の光の陣（外の輪と、立ち上がりまでの時間を示して縮む内の輪、回る紋）。立ち上がると光の柱</li>
 *   <li>出現: 下から上へ、走査の輪が昇るのに合わせて組み上がる</li>
 *   <li>倒されたとき: 激しくちらつき、横にずれながら足元からほどけていく。最後に宙に残った記録結晶が膨らみ、閃光とともに砕ける</li>
 * </ul>
 * 分身は見た目がほぼ同じで、胸の記録結晶だけが少し暗い。
 */
public class EchoSentinelRenderer extends EntityRenderer<EchoSentinel> {
    static final int RGB = 0x8FEAFF;
    static final int DEEP = 0x2C8FB8;
    static final int CORE = 0xE8FFFF;
    static final int CIRCLE = 0xBFF4FF;

    public EchoSentinelRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EchoSentinel entity) {
        return FxDraw.WHITE;
    }

    /** 投影陣は体から離れた所に映るので、体が見えていなくても描く。 */
    @Override
    public boolean shouldRender(EchoSentinel e, Frustum frustum, double x, double y, double z) {
        return e.circleTicks() > 0 || e.circleFlash() > 0 || super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(EchoSentinel e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float emerge = e.emergeProgress(partialTick);
        float death = e.isMinion() ? 0 : e.deathProgress(partialTick);
        int projectors = e.projectorCount();
        // 実体の濃さ: 投影器が4つ残っていると薄く、0で濃い
        float solid = 1 - projectors / 4F;
        float flicker = 0.85F + 0.15F * Mth.sin(t * 0.9F) * Mth.sin(t * 0.37F + 1.3F);
        if (projectors > 0 && ((int) (t * 3 + e.getId() * 7) % (11 - projectors)) == 0) {
            flicker *= 0.45F;                                 // ときどき大きくちらつく
        }
        if (death > 0 && ((int) (t * 5)) % 3 == 0) {
            flicker *= 0.3F;                                  // ほどけている間は激しくちらつく
        }
        int alpha = (int) ((70 + 140 * solid) * flicker);
        if (e.hurtTime > 0 && death == 0) {
            alpha = Math.min(255, alpha + 60);
        }
        // 組み上がっている高さ（出現中）と、ほどけた高さ（倒されたとき。足元から上へ）
        float built = emerge < 1 ? Mth.clamp((emerge - 0.15F) / 0.75F, 0, 1) * 2.5F : 2.5F;
        float floor = death > 0 ? 2.45F * Mth.clamp(death / 0.75F, 0, 1) : 0;
        float jitter = projectors > 0 ? (Mth.sin(t * 2.3F) * Mth.sin(t * 0.7F)) * 0.04F * projectors : 0;
        if (death > 0) {
            jitter += Mth.sin(t * 7.1F) * Mth.cos(t * 3.3F) * 0.18F * death;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        int full = LightTexture.FULL_BRIGHT;

        pose.pushPose();
        float bob = Mth.sin(t * 0.08F) * 0.06F;
        pose.translate(jitter, bob, 0);
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        if (alpha > 4 && floor < 2.4F) {
            body(pose, vc, full, alpha, built, floor, t, e.strikeTicks(), e.isMinion(), death);
        }
        if (death > 0) {
            crystalDeath(pose, vc, death, t);
        }
        pose.popPose();

        // 走査線（体を下から上へ流れる薄い輪）
        if (alpha > 4 && death == 0) {
            for (int k = 0; k < 2; k++) {
                float y = ((t * 0.04F + k * 0.5F) % 1F) * Math.min(built, 2.5F);
                pose.pushPose();
                pose.translate(0, y + bob, 0);
                FxDraw.ring(pose.last(), vc, 0.32F, 0.38F, 0xFFFFFF, alpha / 2, 0);
                pose.popPose();
            }
        }
        // ほどけていく境目の光の輪
        if (death > 0 && floor < 2.4F) {
            pose.pushPose();
            pose.translate(jitter, floor + bob, 0);
            FxDraw.ring(pose.last(), vc, 0.35F, 0.6F, RGB, 230, 0);
            FxDraw.ring(pose.last(), vc, 0.62F, 0.66F, 0xFFFFFF, 180, 180);
            pose.popPose();
        }
        // 出現: 組み上がる高さに走査の輪
        if (emerge < 1 && built < 2.5F) {
            pose.pushPose();
            pose.translate(0, built, 0);
            FxDraw.ring(pose.last(), vc, 0.5F, 0.75F, RGB, 230, 0);
            FxDraw.ring(pose.last(), vc, 0.8F, 0.86F, 0xFFFFFF, 160, 160);
            pose.popPose();
        }
        // 構え: 光の輪が体へ縮む
        int strike = e.strikeTicks();
        if (strike > 0 && death == 0) {
            float k = (strike - partialTick) / EchoSentinel.STRIKE_WINDUP;
            pose.pushPose();
            pose.translate(0, 1.2, 0);
            FxDraw.gyroRings(pose, vc, 0.5F + 1.6F * k, 0.05F, t * 12, 0xFFFFFF, (int) (200 * (1 - k) + 40));
            pose.popPose();
        }
        renderCircle(e, partialTick, t, pose, vc);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** 倒されたとき: 宙に残った記録結晶が速く回りながら膨らみ、最後に閃光とともに砕ける。 */
    private static void crystalDeath(PoseStack pose, VertexConsumer vc, float death, float t) {
        float swell = death < 0.75F ? 1 : 1 + (death - 0.75F) * 6;
        pose.pushPose();
        pose.translate(0, 1.57, 0.24);
        pose.mulPose(Axis.YP.rotationDegrees(t * (4 + 40 * death)));
        if (death < 0.96F) {
            FxDraw.octahedron(pose.last(), vc, 0.09F * swell, 0.14F * swell, CORE, 255);
            // ひび（結晶のまわりで瞬く細い光）
            if (death > 0.75F) {
                FxDraw.gyroRings(pose, vc, 0.2F * swell, 0.015F, t * 20, 0xFFFFFF, 220);
            }
        }
        pose.popPose();
        if (death > 0.9F) {
            float k = (death - 0.9F) / 0.1F;
            pose.pushPose();
            pose.translate(0, 1.57, 0.24);
            org.joml.Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            FxDraw.billboard(pose, vc, camera, 0.6F + 3.5F * k, 0xFFFFFF, (int) (255 * (1 - k * 0.6F)));
            FxDraw.billboard(pose, vc, camera, 1.0F + 5F * k, RGB, (int) (180 * (1 - k)));
            pose.popPose();
        }
    }

    /** 投影陣: 足元の光の陣と、立ち上がった光の柱。 */
    private static void renderCircle(EchoSentinel e, float partialTick, float t, PoseStack pose, VertexConsumer vc) {
        int c = e.circleTicks();
        int flash = e.circleFlash();
        if (c <= 0 && flash <= 0) {
            return;
        }
        Vec3 off = e.circleCenter().subtract(e.getPosition(partialTick));
        float r = (float) e.circleRadius();
        pose.pushPose();
        pose.translate(off.x, off.y + 0.06, off.z);
        if (c > 0) {
            float k = (c - partialTick) / EchoSentinel.CIRCLE_WINDUP;     // 1 → 0
            int a = (int) (120 + 120 * (1 - k));
            FxDraw.ring(pose.last(), vc, r - 0.12F, r, CIRCLE, a, a);
            FxDraw.ring(pose.last(), vc, 0, r, RGB, (int) (40 * (1 - k)), (int) (90 * (1 - k)));
            float inner = r * k;
            FxDraw.ring(pose.last(), vc, Math.max(0, inner - 0.08F), inner, 0xFFFFFF, 220, 220);
            // 回る紋（陣の縁に並ぶ小さな光の板）
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 3));
            for (int i = 0; i < 8; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(i * 45));
                FxDraw.box(pose, vc, r - 0.5F, 0, -0.07F, r - 0.3F, 0.02F, 0.07F, LightTexture.FULL_BRIGHT, 0xFFFFFF, a);
                pose.popPose();
            }
            pose.popPose();
        } else {
            float k = (flash - partialTick) / 8F;
            FxDraw.ring(pose.last(), vc, 0, r, 0xFFFFFF, (int) (200 * k), (int) (120 * k));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + t * 0.05;
                Vec3 base = new Vec3(Math.cos(a) * r * 0.55, 0, Math.sin(a) * r * 0.55);
                FxDraw.beam(pose.last(), vc, base, base.add(0, 6, 0), r * 0.5F, RGB, (int) (150 * k));
            }
            FxDraw.beam(pose.last(), vc, Vec3.ZERO, new Vec3(0, 8, 0), r * 0.9F, 0xFFFFFF, (int) (180 * k));
        }
        pose.popPose();
    }

    /** 体（足元が原点、+Z が前）。built より上と、floor より下は描かない。 */
    private static void body(PoseStack pose, VertexConsumer vc, int light, int alpha, float built, float floor, float t, int strike,
                             boolean minion, float death) {
        // 外套の裾: 下ほど細い段を重ねる（宙に浮いた下半身）
        for (int i = 0; i < 6; i++) {
            float y0 = 0.15F + i * 0.17F;
            float w = 0.10F + i * 0.045F;
            part(pose, vc, light, -w, y0, -w * 0.7F, w, y0 + 0.13F, w * 0.7F, built, floor, i % 2 == 0 ? RGB : DEEP,
                    (int) (alpha * (0.45F + i * 0.08F)));
        }
        // 腰帯と胴
        part(pose, vc, light, -0.30F, 1.17F, -0.19F, 0.30F, 1.25F, 0.19F, built, floor, 0xFFFFFF, alpha);
        part(pose, vc, light, -0.27F, 1.25F, -0.17F, 0.27F, 1.80F, 0.17F, built, floor, RGB, (int) (alpha * 0.8F));
        // 胸の板（前）と襟
        part(pose, vc, light, -0.20F, 1.40F, 0.17F, 0.20F, 1.74F, 0.21F, built, floor, DEEP, alpha);
        part(pose, vc, light, -0.22F, 1.80F, -0.15F, 0.22F, 1.86F, 0.15F, built, floor, 0xFFFFFF, alpha);
        // 肩当て（外へ張り出した板）
        for (int s = -1; s <= 1; s += 2) {
            part(pose, vc, light, s < 0 ? -0.50F : 0.27F, 1.68F, -0.20F, s < 0 ? -0.27F : 0.50F, 1.80F, 0.20F, built, floor, RGB, alpha);
            part(pose, vc, light, s < 0 ? -0.54F : 0.42F, 1.60F, -0.16F, s < 0 ? -0.42F : 0.54F, 1.68F, 0.16F, built, floor, DEEP, alpha);
            // 腕（垂らした上腕・前腕）と光の刃
            float ax0 = s < 0 ? -0.46F : 0.34F;
            float ax1 = ax0 + 0.12F;
            part(pose, vc, light, ax0, 1.20F, -0.07F, ax1, 1.60F, 0.07F, built, floor, RGB, (int) (alpha * 0.8F));
            part(pose, vc, light, ax0 - 0.01F, 0.86F, -0.08F, ax1 + 0.01F, 1.20F, 0.08F, built, floor, DEEP, alpha);
            float blade = strike > 0 ? 0.95F : 0.55F;
            part(pose, vc, light, ax0 + 0.03F, 0.86F - blade, -0.015F, ax1 - 0.03F, 0.86F, 0.015F, built, floor, 0xFFFFFF,
                    strike > 0 ? 255 : (int) (alpha * 0.9F));
        }
        // 兜（とがった額飾りつき）と目の帯
        part(pose, vc, light, -0.15F, 1.88F, -0.15F, 0.15F, 2.16F, 0.15F, built, floor, RGB, (int) (alpha * 0.85F));
        part(pose, vc, light, -0.13F, 2.00F, 0.15F, 0.13F, 2.04F, 0.17F, built, floor, 0xFFFFFF, Math.min(255, alpha + 60));
        part(pose, vc, light, -0.03F, 2.16F, 0.05F, 0.03F, 2.32F, 0.11F, built, floor, DEEP, alpha);
        // 胸の記録結晶（倒されたときは crystalDeath が描く）
        if (built > 1.55F && death == 0) {
            pose.pushPose();
            pose.translate(0, 1.57, 0.24);
            pose.mulPose(Axis.YP.rotationDegrees(t * 4));
            FxDraw.octahedron(pose.last(), vc, 0.09F, 0.14F, minion ? RGB : CORE, minion ? (int) (alpha * 0.6F) : Math.min(255, alpha + 90));
            pose.popPose();
        }
        // 頭の後ろの光輪
        if (built > 2.0F && floor < 2.0F) {
            pose.pushPose();
            pose.translate(0, 2.05, -0.22);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.YP.rotationDegrees(t * 2));
            FxDraw.ring(pose.last(), vc, 0.26F, 0.30F, RGB, alpha, alpha);
            FxDraw.ring(pose.last(), vc, 0.36F, 0.37F, 0xFFFFFF, alpha / 2, alpha / 2);
            pose.popPose();
        }
    }

    /** 箱を built と floor の高さで切って描く。 */
    private static void part(PoseStack pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1,
                             float built, float floor, int rgb, int alpha) {
        float lo = Math.max(y0, floor);
        float hi = Math.min(y1, built);
        if (lo >= hi) {
            return;
        }
        FxDraw.box(pose, vc, x0, lo, z0, x1, hi, z1, light, rgb, Mth.clamp(alpha, 0, 255));
    }
}
