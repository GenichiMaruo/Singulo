package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan;
import io.github.genichimaruo.singulo.ruin.LeviathanSegment;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * エルゴ球の鯨: 炉殻の板を鱗のように連ねた長い体。節ごとに黒い芯と4枚の鱗、節の継ぎ目に菫色の光の環。
 * <ul>
 *   <li>頭: 鱗を楔のように重ねた頭、水色の目、口の中で回る暗い渦。渦の吐息のためでは渦がふくらみ、景色がゆがむ</li>
 *   <li>尾: 光の鰭と、金色に光る芯（弱点）</li>
 *   <li>泳いだ跡: エルゴ球の帯（2本の淡い光の筋が、泳いだ向きへ流れる）</li>
 *   <li>突進の身構え: 継ぎ目の光が水色に燃え、頭に光が集まる</li>
 *   <li>出現: 床の炉心に黒い渦が開いて景色をゆがめ、頭が螺旋を描いて這い出す</li>
 *   <li>倒されたとき: 継ぎ目の光が消えていき、鱗が尾から剥がれて散る。体が渦へ沈み、最後に渦がつぶれて閃光と衝撃波の輪</li>
 * </ul>
 */
public class ErgosphereLeviathanRenderer extends EntityRenderer<ErgosphereLeviathan> {
    static final int SEAM = 0x8A5CFF;
    static final int SEAM_HOT = 0x6FE3FF;
    static final int CORE = 0xFFD27A;
    static final int EYE = 0x9BF5FF;

    public ErgosphereLeviathanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ErgosphereLeviathan entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(ErgosphereLeviathan e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float emerge = e.emergeProgress(partialTick);
        float death = e.deathProgress(partialTick);
        Vec3 origin = e.getPosition(partialTick);
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 core = Vec3.atBottomCenterOf(e.homePos()).add(0, 1.0, 0);
        boolean hasHome = !e.homePos().equals(net.minecraft.core.BlockPos.ZERO);
        float vortexEnd = (float) ErgosphereLeviathan.EMERGE_VORTEX / ErgosphereLeviathan.EMERGE_TICKS;
        if (hasHome && (emerge < 1 || death > 0)) {
            renderVortex(e, emerge, death, vortexEnd, t, core, origin, camera, pose, buffers);
        }
        if (emerge < vortexEnd) {
            super.render(e, yaw, partialTick, pose, buffers, light);
            return;                      // 頭はまだ炉心の中
        }
        int n = ErgosphereLeviathan.SEGMENTS;
        Vec3[] centers = new Vec3[n + 1];
        centers[0] = origin.add(0, e.getBbHeight() / 2, 0);
        LeviathanSegment[] parts = e.segments();
        for (int i = 0; i < n; i++) {
            centers[i + 1] = parts[i].getPosition(partialTick).add(0, ErgosphereLeviathan.radius(i), 0);
        }
        boolean windup = e.state() == ErgosphereLeviathan.DIVE_WINDUP || e.state() == ErgosphereLeviathan.DIVE;
        int seam = windup ? SEAM_HOT : SEAM;
        float seamAlpha = death > 0 ? Math.max(0, 1 - death * 1.4F) : 1;
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BlockState plate = ErgosphereLeviathan.plate();

        renderWake(e, origin, t, pose, buffers);
        // 尾から頭へ
        for (int i = n - 1; i >= 0; i--) {
            Vec3 at = centers[i + 1];
            Vec3 fwd = centers[i].subtract(at);
            float peel = peel(death, i, n);
            renderSegment(i, n, at.subtract(origin), fwd, ErgosphereLeviathan.radius(i), peel, seam, seamAlpha, t, camera,
                    pose, buffers, blocks, plate, light);
        }
        Vec3 headFwd = centers[0].subtract(centers[1]);
        if (e.deathTime <= 0 && e.isAlive()) {
            headFwd = Vec3.directionFromRotation(e.getViewXRot(partialTick), e.getViewYRot(partialTick));
        }
        renderHead(e, centers[0].subtract(origin), headFwd, peel(death, -1, n), windup, seamAlpha, t, partialTick, origin, camera,
                pose, buffers, blocks, plate, light);
        if (e.state() == ErgosphereLeviathan.BREATH_FIRE && e.isAlive()) {
            renderBreath(e, origin, t, pose, buffers);
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** 倒されたとき、i 番目の節の鱗が剥がれた割合（尾から順に。頭は i = -1 で最後）。 */
    static float peel(float death, int i, int n) {
        if (death <= 0) {
            return 0;
        }
        float start = (4 + 7F * (n - 1 - i)) / ErgosphereLeviathan.DEATH_TICKS;
        return Mth.clamp((death - start) / 0.1F, 0, 1);
    }

    /** +Z を dir に向ける回転。 */
    static Quaternionf orient(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        float yaw = (float) Math.atan2(d.x, d.z);
        float pitch = (float) -Math.asin(Mth.clamp(d.y, -1, 1));
        return new Quaternionf().rotateY(yaw).rotateX(pitch);
    }

    private static void renderSegment(int i, int n, Vec3 local, Vec3 fwd, float r, float peel, int seam, float seamAlpha, float t,
                                      Quaternionf camera, PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks,
                                      BlockState plate, int light) {
        Quaternionf q = orient(fwd);
        // 泳ぐうねり（節ごとに少しずつずれる）
        float roll = Mth.sin(t * 0.12F - i * 0.5F) * 8;
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(q);
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r * 0.62F, 0x050208, 0);
        if (peel < 1) {
            for (int k = 0; k < 4; k++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(k * 90 + (i % 2) * 45));
                float out = r * 0.55F + peel * 2.5F;
                pose.translate(0, out, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-14 - peel * 120));
                float s = 1 - peel;
                pose.scale(r * 1.25F * s, r * 0.3F * s, r * 1.35F * s);
                pose.translate(-0.5, 0, -0.5);
                blocks.renderSingleBlock(plate, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        // 別の描き方の入れ物を取ると前の入れ物は閉じられるので、光の入れ物は取り直す
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        int a = (int) ((150 + 60 * Mth.sin(t * 0.25F - i * 0.6F)) * seamAlpha);
        if (a > 4) {
            pose.pushPose();
            pose.translate(0, 0, ErgosphereLeviathan.SPACING * 0.45F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            FxDraw.ring(pose.last(), glow, r * 0.62F, r * 0.98F, seam, a, 0);
            pose.popPose();
        }
        if (i == n - 1) {
            // 尾: 光の鰭（縦と横）と、金色に光る芯（弱点）
            pose.pushPose();
            pose.translate(0, 0, -r * 0.8F);
            for (int k = 0; k < 2; k++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(k * 90 + Mth.sin(t * 0.2F) * 12));
                FxDraw.crescent(pose.last(), glow, 120, 240, 1.4F, 0.9F, seam, (int) (200 * seamAlpha));
                pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
        if (i == n - 1 && seamAlpha > 0.05F) {
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            FxDraw.billboard(pose, glow, camera, r * 2.6F + 0.2F * Mth.sin(t * 0.4F), CORE, (int) (210 * seamAlpha));
            FxDraw.billboard(pose, glow, camera, r * 1.2F, 0xFFFFFF, (int) (230 * seamAlpha));
            pose.popPose();
        }
    }

    private static void renderHead(ErgosphereLeviathan e, Vec3 local, Vec3 fwd, float peel, boolean windup, float seamAlpha, float t,
                                   float partialTick, Vec3 origin, Quaternionf camera, PoseStack pose, MultiBufferSource buffers,
                                   BlockRenderDispatcher blocks, BlockState plate, int light) {
        Quaternionf q = orient(fwd);
        float mouthOpen = mouthOpen(e, partialTick);
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(q);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.78F, 0x050208, 0);
        if (peel < 1) {
            // 鱗を楔のように重ねた頭（上・左右・下顎）
            float[][] plates = {{0, 0.62F, 1.7F, 0.34F, 2.0F, -8}, {68, 0.6F, 1.3F, 0.3F, 1.8F, -10}, {-68, 0.6F, 1.3F, 0.3F, 1.8F, -10},
                    {180, 0.55F + 0.25F * mouthOpen, 1.2F, 0.28F, 1.6F, -6 - 18 * mouthOpen}};
            for (float[] p : plates) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(p[0]));
                pose.translate(0, p[1] + peel * 2.5F, 0.15F);
                pose.mulPose(Axis.XP.rotationDegrees(p[5] - peel * 100));
                float s = 1 - peel;
                pose.scale(p[2] * s, p[3] * s, p[4] * s);
                pose.translate(-0.5, 0, -0.5);
                blocks.renderSingleBlock(plate, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        // 口の中の暗い渦
        float mouthR = 0.32F + 0.45F * mouthOpen;
        pose.pushPose();
        pose.translate(0, -0.05F, 1.0F);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), mouthR, 0x000000, 0);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * (6 + 20 * mouthOpen)));
        FxDraw.ring(pose.last(), glow, mouthR * 1.15F, mouthR * 1.9F, SEAM, (int) (180 * seamAlpha), 0);
        FxDraw.ring(pose.last(), glow, mouthR * 1.05F, mouthR * 1.3F, CORE, (int) (140 * seamAlpha * (0.3F + mouthOpen)), 0);
        pose.popPose();
        pose.popPose();

        // 目と、身構えの光（カメラを向く光は、頭の向きで回した位置に置く）
        glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        if (seamAlpha > 0.05F) {
            for (int side = -1; side <= 1; side += 2) {
                Vector3f eye = q.transform(new Vector3f(side * 0.55F, 0.32F, 0.72F));
                pose.pushPose();
                pose.translate(local.x + eye.x, local.y + eye.y, local.z + eye.z);
                FxDraw.billboard(pose, glow, camera, 0.55F, EYE, (int) (230 * seamAlpha));
                pose.popPose();
            }
        }
        if (windup) {
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            FxDraw.billboard(pose, glow, camera, 3.2F + 0.4F * Mth.sin(t * 0.8F), SEAM_HOT, 110);
            pose.popPose();
        }
        if (mouthOpen > 0.05F && e.isAlive()) {
            Vector3f m = q.transform(new Vector3f(0, -0.05F, 1.0F));
            GravitationalLensing.add(origin.add(local).add(m.x, m.y, m.z), 0.4F + 0.9F * mouthOpen, 0.4F + 0.4F * mouthOpen);
        }
    }

    /** 口の開き（0〜1）: 渦の吐息のためで開いていき、吐いている間は開いたまま。 */
    static float mouthOpen(ErgosphereLeviathan e, float partialTick) {
        if (e.state() == ErgosphereLeviathan.BREATH_CHARGE) {
            float left = Math.max(0, e.stateTicks() - partialTick);
            return Mth.clamp(1 - left / ErgosphereLeviathan.BREATH_CHARGE_TICKS, 0, 1);
        }
        return e.state() == ErgosphereLeviathan.BREATH_FIRE ? 1 : 0;
    }

    /** 渦の光線: 菫色のにじみと白い芯、まわりを巻く金の筋。 */
    private static void renderBreath(ErgosphereLeviathan e, Vec3 origin, float t, PoseStack pose, MultiBufferSource buffers) {
        Vec3 from = e.mouth().subtract(e.position());
        Vec3 to = e.aim().subtract(origin);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        FxDraw.beam(pose.last(), glow, from, to, 1.3F + 0.15F * Mth.sin(t * 2), SEAM, 120);
        FxDraw.beam(pose.last(), glow, from, to, 0.55F, CORE, 150);
        FxDraw.beam(pose.last(), glow, from, to, 0.2F, 0xFFFFFF, 255);
    }

    /** エルゴ球の帯: 少し前に泳いだ跡に沿って、2本の淡い光の筋。古いほど薄い。 */
    private static void renderWake(ErgosphereLeviathan e, Vec3 origin, float t, PoseStack pose, MultiBufferSource buffers) {
        if (!e.isAlive() || e.emergeTicks() > 0) {
            return;
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        Iterator<ErgosphereLeviathan.TrailPoint> it = e.trailIterator();
        ErgosphereLeviathan.TrailPoint prev = null;
        int k = 0;
        while (it.hasNext()) {
            ErgosphereLeviathan.TrailPoint p = it.next();
            int age = e.tickCount - p.tick();
            if (age > ErgosphereLeviathan.WAKE_TICKS) {
                break;
            }
            if (k++ % 4 != 0 || age < 6) {
                continue;
            }
            if (prev != null) {
                Vec3 side = p.dir().cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                int alpha = (int) (90 * (1 - (float) age / ErgosphereLeviathan.WAKE_TICKS));
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 off = side.scale(s * (1.3 + 0.2 * Math.sin(t * 0.3 + age * 0.2)));
                    FxDraw.beam(pose.last(), glow, prev.pos().add(off).subtract(origin), p.pos().add(off).subtract(origin), 0.12F,
                            SEAM, alpha);
                }
            }
            prev = p;
        }
    }

    /**
     * 床の炉心の渦。出現のあいだは開いて頭を吐き出し、だんだん閉じる。倒されたときは体を飲み込みながら広がり、
     * 最後につぶれて閃光と衝撃波の輪を残す。
     */
    private static void renderVortex(ErgosphereLeviathan e, float emerge, float death, float vortexEnd, float t, Vec3 core, Vec3 origin,
                                     Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        float size;
        if (death > 0) {
            float flashAt = (ErgosphereLeviathan.DEATH_TICKS - 8F) / ErgosphereLeviathan.DEATH_TICKS;
            if (death >= flashAt) {
                float c = (death - flashAt) / (1 - flashAt);
                VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                pose.pushPose();
                pose.translate(core.x - origin.x, core.y - origin.y + 0.5, core.z - origin.z);
                FxDraw.billboard(pose, glow, camera, 2 + 12 * c, 0xFFFFFF, (int) (255 * (1 - c)));
                FxDraw.billboard(pose, glow, camera, 4 + 16 * c, SEAM, (int) (200 * (1 - c)));
                pose.translate(0, -0.45, 0);
                FxDraw.ring(pose.last(), glow, 1 + 16 * c, 1.8F + 16 * c, SEAM_HOT, (int) (230 * (1 - c)), 0);
                pose.popPose();
                return;
            }
            float sink = (float) ErgosphereLeviathan.DEATH_WRITHE / ErgosphereLeviathan.DEATH_TICKS;
            size = death < sink ? death / sink * 0.6F : 0.6F + 0.4F * Math.min(1, (death - sink) / (flashAt - sink));
            if (death > flashAt - 0.08F) {
                size *= Math.max(0.05F, (flashAt - death) / 0.08F);
            }
        } else {
            size = emerge < vortexEnd ? emerge / vortexEnd : 1 - (emerge - vortexEnd) / (1 - vortexEnd);
        }
        if (size <= 0.01F) {
            return;
        }
        GravitationalLensing.add(core.add(0, 0.6, 0), 1.2F + 2.2F * size, 0.5F + 0.4F * size);
        pose.pushPose();
        pose.translate(core.x - origin.x, core.y - origin.y + 0.05, core.z - origin.z);
        pose.pushPose();
        pose.scale(1, 0.2F, 1);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 1.6F * size, 0x000000, 0);
        pose.popPose();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.mulPose(Axis.YP.rotationDegrees(t * 8));
        for (int k = 0; k < 3; k++) {
            float rr = (1.8F + 1.6F * k) * size;
            FxDraw.ring(pose.last(), glow, rr, rr + 0.35F, k == 0 ? CORE : SEAM, 200 - k * 50, 0);
        }
        pose.popPose();
    }
}
