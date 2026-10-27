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

import static io.github.genichimaruo.singulo.client.FxDraw.glow;
import static io.github.genichimaruo.singulo.client.FxDraw.orient;

/**
 * エルゴ球の大蛇。体力で3つの姿を描き分ける。
 * <ol>
 *   <li>装甲: 節ごとに黒い芯と4枚の炉殻の鱗、継ぎ目に菫色の光の環。尾に光の鰭と金色の芯（弱点）。脱皮の前は鱗が震える</li>
 *   <li>骨格: 鱗が弾け飛び、光の背骨と、傾いた肋の輪だけになる。頭は黒い頭骨と光の顎</li>
 *   <li>龍: 光を飲む黒い体に金の光子の環。首から金と菫の降着の翼が六枚ずつ広がり、頭に光の角、尾は小さな特異点</li>
 * </ol>
 * <ul>
 *   <li>出現: 炉心の渦から小さな蛇が這い出して泳ぐ。真下から巨大な顎が噴き上がって丸呑みにし、螺旋を描いて昇る</li>
 *   <li>技: 渦の吐息の光線、尾の薙ぎ払いの光、とぐろの光の輪、相対論的ジェット（2本の回る光線）、帯（泳いだ跡の光の筋）</li>
 *   <li>覚醒した個体: 背に光の背びれが並び、目が紅い</li>
 *   <li>倒されたとき: 継ぎ目の光が消え、体が尾から崩れて渦へ沈み、最後に渦がつぶれて閃光と衝撃波の輪</li>
 * </ul>
 */
public class ErgosphereLeviathanRenderer extends EntityRenderer<ErgosphereLeviathan> {
    static final int SEAM = 0x8A5CFF;
    static final int SEAM_HOT = 0x6FE3FF;
    static final int CORE = 0xFFD27A;
    static final int EYE = 0x9BF5FF;
    static final int BONE = 0xBFF6FF;
    static final int GOLD = 0xFFC860;
    static final int AWAKE = 0xFF3C78;

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
        float emergeT = emerge * ErgosphereLeviathan.EMERGE_TICKS;
        float death = e.deathProgress(partialTick);
        Vec3 origin = e.getPosition(partialTick);
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 core = Vec3.atBottomCenterOf(e.homePos()).add(0, 1.0, 0);
        boolean hasHome = !e.homePos().equals(net.minecraft.core.BlockPos.ZERO);
        int state = e.state();
        int form = e.form();
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BlockState plate = ErgosphereLeviathan.plate();
        if (hasHome) {
            renderVortex(e, emerge, emergeT, death, state, t, partialTick, core, origin, camera, pose, buffers);
            if (emerge < 1 && emergeT >= ErgosphereLeviathan.EMERGE_VORTEX && emergeT < ErgosphereLeviathan.SWALLOW + 8) {
                renderHatchling(e, emergeT, t, core, origin, camera, pose, buffers, blocks, plate, light);
            }
        }
        boolean underground = emerge < 1 && emergeT < ErgosphereLeviathan.SWALLOW
                || state == ErgosphereLeviathan.ASCEND && e.stateTicks() > ErgosphereLeviathan.ASCEND_TICKS - ErgosphereLeviathan.ASCEND_BURST;
        if (underground) {
            super.render(e, yaw, partialTick, pose, buffers, light);
            return;
        }
        int n = ErgosphereLeviathan.SEGMENTS;
        Vec3[] centers = new Vec3[n + 1];
        centers[0] = origin.add(0, e.getBbHeight() / 2, 0);
        LeviathanSegment[] parts = e.segments();
        for (int i = 0; i < n; i++) {
            centers[i + 1] = parts[i].getPosition(partialTick).add(0, ErgosphereLeviathan.radius(i), 0);
        }
        boolean windup = state == ErgosphereLeviathan.DIVE_WINDUP || state == ErgosphereLeviathan.DIVE;
        int seam = windup ? SEAM_HOT : SEAM;
        float seamAlpha = death > 0 ? Math.max(0, 1 - death * 1.4F) : 1;
        // 脱皮の前は鱗が震え、継ぎ目が白く明滅する
        float shake = 0;
        if (state == ErgosphereLeviathan.MOLT && e.stateTicks() > ErgosphereLeviathan.MOLT_BURST) {
            float k = 1 - (e.stateTicks() - ErgosphereLeviathan.MOLT_BURST) / (float) (ErgosphereLeviathan.MOLT_TICKS - ErgosphereLeviathan.MOLT_BURST);
            shake = 0.05F + 0.25F * k;
            if (((int) (t * 2)) % 3 == 0) {
                seam = 0xFFFFFF;
            }
        }
        renderWake(e, form, origin, t, pose, buffers);
        // 尾から頭へ
        for (int i = n - 1; i >= 0; i--) {
            Vec3 at = centers[i + 1];
            Vec3 fwd = centers[i].subtract(at);
            float fade = fade(death, i, n);
            Vec3 local = at.subtract(origin);
            if (shake > 0) {
                local = local.add(Mth.sin(t * 3.1F + i) * shake, Mth.cos(t * 2.7F + i) * shake, Mth.sin(t * 2.3F - i) * shake);
            }
            float r = ErgosphereLeviathan.radius(i);
            switch (form) {
                case 1 -> armorSegment(i, n, local, fwd, r, fade, seam, seamAlpha, t, camera, pose, buffers, blocks, plate, light);
                case 2 -> skeletonSegment(i, n, local, centers[i].subtract(origin), fwd, r, fade, seamAlpha, t, camera, pose, buffers);
                default -> voidSegment(e, i, n, local, fwd, r, fade, seamAlpha, t, origin, camera, pose, buffers);
            }
            if (e.awakened() && fade < 1) {
                dorsalFin(local, fwd, r, fade, t, i, pose, buffers);
            }
        }
        // 脱皮の閃光（鱗が弾けた直後）
        if (state == ErgosphereLeviathan.MOLT && e.stateTicks() <= ErgosphereLeviathan.MOLT_BURST) {
            float k = 1 - Mth.clamp((ErgosphereLeviathan.MOLT_BURST - e.stateTicks() + partialTick) / 16F, 0, 1);
            if (k > 0) {
                VertexConsumer g = glow(buffers);
                for (int i = 0; i < n; i += 2) {
                    Vec3 l = centers[i + 1].subtract(origin);
                    pose.pushPose();
                    pose.translate(l.x, l.y, l.z);
                    FxDraw.billboard(pose, g, camera, 3 + 3 * (1 - k), 0xFFFFFF, (int) (220 * k));
                    pose.popPose();
                }
            }
        }
        Vec3 headFwd = centers[0].subtract(centers[1]);
        if (e.deathTime <= 0 && e.isAlive()) {
            headFwd = Vec3.directionFromRotation(e.getViewXRot(partialTick), e.getViewYRot(partialTick));
        }
        renderHead(e, form, centers[0].subtract(origin), headFwd, fade(death, -1, n), windup, seamAlpha, emergeT, emerge, t, partialTick,
                origin, camera, pose, buffers, blocks, plate, light);
        if (form == 3 && death < 0.8F) {
            renderWings(centers[1].subtract(origin), centers[0].subtract(centers[2]), seamAlpha, t, e.awakened(), pose, buffers);
        }
        if (e.isAlive()) {
            renderMoves(e, state, form, centers, origin, t, partialTick, camera, pose, buffers);
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** 倒されたとき、i 番目の節が崩れた割合（尾から順に。頭は i = -1 で最後）。 */
    static float fade(float death, int i, int n) {
        if (death <= 0) {
            return 0;
        }
        float start = (4 + 7F * (n - 1 - i)) / ErgosphereLeviathan.DEATH_TICKS;
        return Mth.clamp((death - start) / 0.1F, 0, 1);
    }

    /**
     * 鱗の板を1枚描く: 体の軸（Z）のまわりに roll 度回し、lift だけ外へ、z だけ前へ出し、pitch 度起こして、
     * sx × sy × sz の大きさのブロックとして描く。
     */
    static void plate(PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks, BlockState state, int light, float roll,
                      float lift, float z, float pitch, float sx, float sy, float sz) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(0, lift, z);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ------------------------------------------------------------------ 姿1: 装甲

    private static void armorSegment(int i, int n, Vec3 local, Vec3 fwd, float r, float peel, int seam, float seamAlpha, float t,
                                     Quaternionf camera, PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks,
                                     BlockState plate, int light) {
        Quaternionf q = orient(fwd);
        float roll = Mth.sin(t * 0.12F - i * 0.5F) * 8;
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(q);
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r * 0.62F, 0x050208, 0);
        if (peel < 1) {
            float s = 1 - peel;
            for (int k = 0; k < 4; k++) {
                plate(pose, buffers, blocks, plate, light, k * 90 + (i % 2) * 45, r * 0.55F + peel * 2.5F, 0, -14 - peel * 120,
                        r * 1.25F * s, r * 0.3F * s, r * 1.35F * s);
            }
        }
        // 別の描き方の入れ物を取ると前の入れ物は閉じられるので、光の入れ物は取り直す
        VertexConsumer glow = glow(buffers);
        int a = (int) ((150 + 60 * Mth.sin(t * 0.25F - i * 0.6F)) * seamAlpha);
        if (a > 4) {
            pose.pushPose();
            pose.translate(0, 0, ErgosphereLeviathan.SPACING * 0.45F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            FxDraw.ring(pose.last(), glow, r * 0.62F, r * 0.98F, seam, a, 0);
            pose.popPose();
        }
        if (i == n - 1) {
            tailFin(pose, glow, r * 0.8F, 1.4F, 0.9F, 0.2F, 12, seam, (int) (200 * seamAlpha), t);
        }
        pose.popPose();
        if (i == n - 1 && seamAlpha > 0.05F) {
            tailCore(local, r * 2.6F + 0.2F * Mth.sin(t * 0.4F), r * 1.2F, (int) (210 * seamAlpha), (int) (230 * seamAlpha), camera, pose, glow);
        }
    }

    /** 尾びれ: 尾の back だけ後ろに、十字に重ねた2枚の光の三日月が、speed の速さで swing 度ゆらぐ。 */
    private static void tailFin(PoseStack pose, VertexConsumer glow, float back, float outer, float inner, float speed, float swing, int rgb,
                                int alpha, float t) {
        pose.pushPose();
        pose.translate(0, 0, -back);
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 90 + Mth.sin(t * speed) * swing));
            FxDraw.crescent(pose.last(), glow, 120, 240, outer, inner, rgb, alpha);
            pose.popPose();
        }
        pose.popPose();
    }

    /** 尾の芯: 金のにじみと白い芯。 */
    private static void tailCore(Vec3 local, float outer, float inner, int outerAlpha, int innerAlpha, Quaternionf camera, PoseStack pose,
                                 VertexConsumer glow) {
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        FxDraw.billboard(pose, glow, camera, outer, CORE, outerAlpha);
        FxDraw.billboard(pose, glow, camera, inner, 0xFFFFFF, innerAlpha);
        pose.popPose();
    }

    // ------------------------------------------------------------------ 姿2: 骨格

    /** 光の背骨（前の節まで）と、傾いた2本の肋の輪、小さな黒い芯。 */
    private static void skeletonSegment(int i, int n, Vec3 local, Vec3 next, Vec3 fwd, float r, float fade, float alpha, float t,
                                        Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        float s = 1 - fade;
        if (s <= 0.01F) {
            return;
        }
        Quaternionf q = orient(fwd);
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(q);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r * 0.35F * s, 0x020104, 0);
        VertexConsumer glow = glow(buffers);
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.3F - i * 0.7F);
        for (int k = -1; k <= 1; k += 2) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 25 + Mth.sin(t * 0.1F + i) * 6));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            FxDraw.ring(pose.last(), glow, r * 0.8F * s, r * 0.92F * s, BONE, (int) (220 * alpha * pulse), (int) (160 * alpha * pulse));
            FxDraw.ring(pose.last(), glow, r * 0.92F * s, r * 1.25F * s, SEAM, (int) (90 * alpha), 0);
            pose.popPose();
        }
        if (i == n - 1) {
            tailFin(pose, glow, r * 0.8F, 1.4F, 0.9F, 0.2F, 12, SEAM_HOT, (int) (200 * alpha * s), t);
        }
        pose.popPose();
        // 背骨（この節から頭側の節まで）
        FxDraw.beam(pose.last(), glow, local, next, 0.22F * s, BONE, (int) (230 * alpha));
        FxDraw.beam(pose.last(), glow, local, next, 0.6F * s, SEAM, (int) (80 * alpha));
        if (i == n - 1 && alpha > 0.05F) {
            tailCore(local, r * s * 2.6F + 0.2F * Mth.sin(t * 0.4F), r * s * 1.2F, (int) (210 * alpha), (int) (230 * alpha), camera, pose, glow);
        }
    }

    // ------------------------------------------------------------------ 姿3: 龍

    /** 光を飲む黒い体と金の光子の環、まわりの菫のにじみ。尾は小さな特異点（景色がゆがむ）。 */
    private static void voidSegment(ErgosphereLeviathan e, int i, int n, Vec3 local, Vec3 fwd, float r, float fade, float alpha, float t,
                                    Vec3 origin, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        float s = 1 - fade;
        if (s <= 0.01F) {
            return;
        }
        Quaternionf q = orient(fwd);
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r * 0.8F * s, 0x000000, 0);
        VertexConsumer glow = glow(buffers);
        FxDraw.billboard(pose, glow, camera, r * 3.0F * s, 0x3A1670, (int) (120 * alpha));
        pose.mulPose(q);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * 4 + i * 20));
        FxDraw.ring(pose.last(), glow, r * 0.82F * s, r * 0.95F * s, GOLD, (int) (230 * alpha), (int) (230 * alpha));
        FxDraw.ring(pose.last(), glow, r * 0.95F * s, r * 1.5F * s, SEAM, (int) (110 * alpha), 0);
        pose.popPose();
        if (i == n - 1) {
            // 尾の先の特異点と、傾いた降着の輪
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            pose.mulPose(q);
            pose.translate(0, 0, -r * 1.4F);
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.45F * s, 0x000000, 0);
            VertexConsumer g = glow(buffers);
            pose.mulPose(Axis.XP.rotationDegrees(70));
            pose.mulPose(Axis.YP.rotationDegrees(t * 9));
            FxDraw.ring(pose.last(), g, 0.55F * s, 1.2F * s, CORE, (int) (220 * alpha), 0);
            pose.popPose();
            Vector3f tip = q.transform(new Vector3f(0, 0, -r * 1.4F));
            GravitationalLensing.add(origin.add(local).add(tip.x, tip.y, tip.z), 0.8F * s, 0.6F);
        }
    }

    /** 龍の翼: 首から左右へ、金から菫へ消える6本ずつの光の羽根が扇に広がり、ゆっくり羽ばたく。 */
    private static void renderWings(Vec3 neck, Vec3 fwd, float alpha, float t, boolean awakened, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        Vec3 flat = new Vec3(fwd.x, 0, fwd.z);
        Quaternionf q = orient(flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat);
        float flap = Mth.sin(t * 0.09F) * 18;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(neck.x, neck.y + 0.4, neck.z);
            pose.mulPose(q);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (12 + flap)));
            for (int f = 0; f < 6; f++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-side * (8 + f * 13)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (f * 6 - 10)));
                float len = 6.0F - Math.abs(f - 1.5F) * 0.7F;
                LastArchivistRenderer.blade(pose, glow, side, len, 0.9F, awakened ? AWAKE : GOLD, (int) (200 * alpha), SEAM, 0);
                LastArchivistRenderer.blade(pose, glow, side, len * 0.9F, 0.25F, 0xFFFFFF, (int) (220 * alpha), GOLD, 0);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ 覚醒した個体の背びれ

    private static void dorsalFin(Vec3 local, Vec3 fwd, float r, float fade, float t, int i, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(orient(fwd));
        pose.translate(0, r * 0.75F, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(-30 + Mth.sin(t * 0.2F - i * 0.5F) * 8));
        float s = 1 - fade;
        LastArchivistRenderer.blade(pose, glow, 1, r * 1.6F * s + 0.3F, 0.5F * s, AWAKE, 220, CORE, 0);
        pose.popPose();
    }

    // ------------------------------------------------------------------ 頭

    /** 頭の上と左右の板（回し・外へ・大きさ x y z・起こし）。下顎は開き方で変わるので別に描く。 */
    private static final float[][] HEAD_PLATES = {{0, 0.62F, 1.7F, 0.34F, 2.0F, -8}, {68, 0.6F, 1.3F, 0.3F, 1.8F, -10},
            {-68, 0.6F, 1.3F, 0.3F, 1.8F, -10}};

    /** 鱗を楔のように重ねた頭（上・左右・下顎）。peel は剥がれた割合（外へ飛び、小さくなる）。 */
    static void headPlates(PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks, BlockState state, int light,
                           float jawLift, float jawPitch, float peel) {
        float s = 1 - peel;
        for (float[] p : HEAD_PLATES) {
            plate(pose, buffers, blocks, state, light, p[0], p[1] + peel * 2.5F, 0.15F, p[5] - peel * 100, p[2] * s, p[3] * s, p[4] * s);
        }
        plate(pose, buffers, blocks, state, light, 180, jawLift + peel * 2.5F, 0.15F, jawPitch - peel * 100, 1.2F * s, 0.28F * s, 1.6F * s);
    }

    private static void renderHead(ErgosphereLeviathan e, int form, Vec3 local, Vec3 fwd, float peel, boolean windup, float alpha,
                                   float emergeT, float emerge, float t, float partialTick, Vec3 origin, Quaternionf camera, PoseStack pose,
                                   MultiBufferSource buffers, BlockRenderDispatcher blocks, BlockState plate, int light) {
        Quaternionf q = orient(fwd);
        float mouthOpen = mouthOpen(e, emerge, emergeT, partialTick);
        float s = 1 - peel;
        pose.pushPose();
        pose.translate(local.x, local.y, local.z);
        pose.mulPose(q);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), (form == 2 ? 0.6F : 0.78F) * s,
                form == 3 ? 0x000000 : 0x050208, 0);
        if (form == 1 && peel < 1) {
            headPlates(pose, buffers, blocks, plate, light, 0.55F + 0.25F * mouthOpen, -6 - 18 * mouthOpen, peel);
        }
        VertexConsumer glow = glow(buffers);
        if (form == 2) {
            // 光の顎（上下の三日月が開く）
            for (int k = -1; k <= 1; k += 2) {
                pose.pushPose();
                pose.translate(0, k * (0.25F + 0.35F * mouthOpen), 0.4F);
                pose.mulPose(Axis.XP.rotationDegrees(k * (8 + 25 * mouthOpen)));
                FxDraw.crescent(pose.last(), glow, 150, 210, 1.2F * s, 0.35F * s, BONE, (int) (230 * alpha));
                pose.popPose();
            }
        }
        if (form == 3) {
            // 光の角（後ろ上へ2本）と、頭の上の金の三日月の冠
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 0.35F, 0.5F, 0);
                pose.mulPose(Axis.YP.rotationDegrees(side * 90 + side * 30));
                pose.mulPose(Axis.ZP.rotationDegrees(side * 25));
                LastArchivistRenderer.blade(pose, glow, side, 3.0F * s, 0.35F * s, e.awakened() ? AWAKE : GOLD, (int) (240 * alpha), 0xFFFFFF, 0);
                pose.popPose();
            }
            pose.pushPose();
            pose.translate(0, 0.6F, -0.1F);
            FxDraw.crescent(pose.last(), glow, 140, 220, 0.9F * s, 0.3F * s, GOLD, (int) (230 * alpha));
            pose.popPose();
        }
        // 口の中の暗い渦
        float mouthR = (0.32F + 0.45F * mouthOpen) * s;
        pose.pushPose();
        pose.translate(0, -0.05F, form == 2 ? 0.7F : 1.0F);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), mouthR, 0x000000, 0);
        glow = glow(buffers);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * (6 + 20 * mouthOpen)));
        FxDraw.ring(pose.last(), glow, mouthR * 1.15F, mouthR * 1.9F, form == 3 ? GOLD : SEAM, (int) (180 * alpha), 0);
        FxDraw.ring(pose.last(), glow, mouthR * 1.05F, mouthR * 1.3F, CORE, (int) (140 * alpha * (0.3F + mouthOpen)), 0);
        pose.popPose();
        pose.popPose();

        // 目と、身構えの光（カメラを向く光は、頭の向きで回した位置に置く）
        glow = glow(buffers);
        int eye = e.awakened() ? AWAKE : form == 3 ? GOLD : EYE;
        if (alpha > 0.05F) {
            for (int side = -1; side <= 1; side += 2) {
                Vector3f p = q.transform(new Vector3f(side * 0.5F, 0.3F, form == 2 ? 0.45F : 0.72F));
                pose.pushPose();
                pose.translate(local.x + p.x, local.y + p.y, local.z + p.z);
                FxDraw.billboard(pose, glow, camera, 0.55F, eye, (int) (230 * alpha));
                pose.popPose();
            }
        }
        if (windup) {
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            FxDraw.billboard(pose, glow, camera, 3.2F + 0.4F * Mth.sin(t * 0.8F), form == 3 ? GOLD : SEAM_HOT, 110);
            pose.popPose();
        }
        if (mouthOpen > 0.05F && e.isAlive()) {
            Vector3f m = q.transform(new Vector3f(0, -0.05F, 1.0F));
            GravitationalLensing.add(origin.add(local).add(m.x, m.y, m.z), 0.4F + 0.9F * mouthOpen, 0.4F + 0.4F * mouthOpen);
        }
    }

    /** 口の開き（0〜1）: 吐息のため・吐いている間・噛みつき・出現で小さな蛇を飲み込むとき。 */
    static float mouthOpen(ErgosphereLeviathan e, float emerge, float emergeT, float partialTick) {
        if (emerge < 1) {
            if (emergeT >= ErgosphereLeviathan.SWALLOW && emergeT < ErgosphereLeviathan.SWALLOW_END) {
                float k = (emergeT - ErgosphereLeviathan.SWALLOW) / (ErgosphereLeviathan.SWALLOW_END - ErgosphereLeviathan.SWALLOW);
                return k < 0.45F ? 1 : Math.max(0, 1 - (k - 0.45F) / 0.2F);
            }
            return 0;
        }
        return switch (e.state()) {
            case ErgosphereLeviathan.BREATH_CHARGE -> Mth.clamp(1 - Math.max(0, e.stateTicks() - partialTick)
                    / ErgosphereLeviathan.BREATH_CHARGE_TICKS, 0, 1);
            case ErgosphereLeviathan.BREATH_FIRE, ErgosphereLeviathan.BITE -> 1;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ 技の光

    static final int VOID = 0x7A3CFF;

    /**
     * 重力の裂け目: 予告のあいだは床に3本の光の帯（点滅しながら太くなる）。裂けると、線に沿って黒紫の光の壁が立ち上がり、
     * 中心に白い裂け目が走って、ゆっくり消えていく。
     */
    private static void riftWalls(ErgosphereLeviathan e, Vec3 origin, float t, float left, PoseStack pose, VertexConsumer glow) {
        int length = ErgosphereLeviathan.RIFT_WARN + ErgosphereLeviathan.RIFT_GAP;
        float elapsed = ErgosphereLeviathan.RIFT_VOLLEYS * length - left;
        float inVolley = elapsed % length;
        Vec3 c = Vec3.atBottomCenterOf(e.homePos()).add(0, 1.05, 0);
        double a = e.riftAngle();
        Vec3 along = new Vec3(Math.cos(a), 0, Math.sin(a));
        Vec3 across = new Vec3(-along.z, 0, along.x);
        double reach = ErgosphereLeviathan.RIFT_REACH;
        for (int k = -1; k <= 1; k++) {
            Vec3 mid = c.add(across.scale(k * ErgosphereLeviathan.RIFT_SPACING));
            Vec3 p0 = mid.add(along.scale(-reach)).subtract(origin);
            Vec3 p1 = mid.add(along.scale(reach)).subtract(origin);
            if (inVolley < ErgosphereLeviathan.RIFT_WARN) {
                float k2 = inVolley / ErgosphereLeviathan.RIFT_WARN;
                boolean blink = (int) (inVolley / 2) % 2 == 0 || k2 > 0.7F;
                FxDraw.beam(pose.last(), glow, p0, p1, (float) ErgosphereLeviathan.RIFT_WIDTH * 2 * (0.3F + 0.7F * k2),
                        k2 > 0.7F ? 0xFF5AD8 : VOID, blink ? (int) (60 + 150 * k2) : 30);
                FxDraw.beam(pose.last(), glow, p0, p1, 0.08F, 0xFFFFFF, (int) (120 * k2));
            } else {
                // 裂けた: 線に沿って立ち上がる光の壁
                float fade = 1 - (inVolley - ErgosphereLeviathan.RIFT_WARN) / ErgosphereLeviathan.RIFT_GAP;
                float h = 6.5F * (0.4F + 0.6F * fade);
                for (double s = -reach; s <= reach; s += 1.0) {
                    Vec3 b = mid.add(along.scale(s)).subtract(origin);
                    float wob = 0.6F + 0.4F * Mth.sin(t * 1.3F + (float) s);
                    FxDraw.beam(pose.last(), glow, b, b.add(0, h * wob, 0), 0.9F * fade, VOID, (int) (200 * fade));
                }
                FxDraw.beam(pose.last(), glow, p0, p1, 0.6F * fade, 0xFFFFFF, (int) (255 * fade));
                FxDraw.beam(pose.last(), glow, p0.add(0, 0.05, 0), p1.add(0, 0.05, 0), 2.4F * fade, 0x1A0030, (int) (180 * fade));
            }
        }
    }

    /**
     * 引き潮: 床一面に、回りながら中心へ流れ込む4本の渦の腕と、外から内へ縮んでいく光の輪。
     * 炉心の上には黒い球と、天井へ伸びる紫の渦の柱。
     */
    private static void tideVortex(ErgosphereLeviathan e, Vec3 origin, float t, float left, PoseStack pose, VertexConsumer glow) {
        Vec3 c = Vec3.atBottomCenterOf(e.homePos()).add(0, 1.08, 0);
        float elapsed = ErgosphereLeviathan.TIDE_TICKS - left;
        float in = Mth.clamp(elapsed / 15F, 0, 1) * Mth.clamp(left / 15F, 0, 1);
        double spin = t * 0.12;
        double arena = ErgosphereLeviathan.ARENA - 2;
        for (int arm = 0; arm < 4; arm++) {
            Vec3 prev = null;
            for (double r = arena; r >= 0.8; r -= 0.9) {
                double ang = spin + arm * Math.PI / 2 + r * 0.22;
                Vec3 q = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r).subtract(origin);
                if (prev != null) {
                    boolean inner = r < ErgosphereLeviathan.TIDE_CORE_RADIUS;
                    FxDraw.beam(pose.last(), glow, prev, q, (float) (0.25 + r * 0.04), inner ? 0xFF5AD8 : VOID, (int) (170 * in));
                }
                prev = q;
            }
        }
        pose.pushPose();
        Vec3 cc = c.subtract(origin);
        pose.translate(cc.x, cc.y, cc.z);
        for (int i = 0; i < 5; i++) {
            float r = (float) (arena * (1 - ((elapsed * 0.012F + i / 5F) % 1F)));
            FxDraw.ring(pose.last(), glow, Math.max(0, r - 0.6F), r, VOID, 0, (int) (150 * in));
        }
        FxDraw.ring(pose.last(), glow, 0, (float) ErgosphereLeviathan.TIDE_CORE_RADIUS, 0xFF5AD8, (int) (90 * in), (int) (200 * in));
        pose.translate(0, 1.6, 0);
        FxDraw.sphere(pose.last(), glow, 1.2F + 0.2F * Mth.sin(t * 0.4F), 0x1A0030, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        pose.popPose();
        // 天井へ伸びる渦の柱（ねじれながら細くなる）
        for (int i = 0; i < 6; i++) {
            double ang = spin * 2 + i * Math.PI / 3;
            Vec3 bottom = c.add(Math.cos(ang) * 2.2, 0.2, Math.sin(ang) * 2.2).subtract(origin);
            Vec3 top = c.add(Math.cos(ang + 1.2) * 0.6, 14, Math.sin(ang + 1.2) * 0.6).subtract(origin);
            FxDraw.beam(pose.last(), glow, bottom, top, 0.35F, VOID, (int) (160 * in));
        }
    }

    /** 炉心の再点火で、炉心から放つ光の筋の数。 */
    static final int REIGNITE_RAYS = 144;

    /**
     * 炉心の再点火: 炉心から部屋じゅうへ放射状の光の筋。筋はブロック（封じ込め柱など）で止まるので、柱の後ろに影ができる。
     * 溜めているあいだは細く薄い筋がゆっくり回り（どこが陰になるか見える）、放つ瞬間に太く白い光の束が部屋を満たして消えていく。
     * 炉心には光の球、床には広がる光の輪。
     */
    private static void reigniteRays(ErgosphereLeviathan e, Vec3 origin, float t, float left, PoseStack pose, VertexConsumer glow) {
        Vec3 core = Vec3.atBottomCenterOf(e.homePos()).add(0, 2.0, 0);
        float elapsed = ErgosphereLeviathan.REIGNITE_TICKS - left;
        boolean charging = elapsed < ErgosphereLeviathan.REIGNITE_CHARGE;
        float k = charging ? elapsed / ErgosphereLeviathan.REIGNITE_CHARGE
                : Math.max(0, 1 - (elapsed - ErgosphereLeviathan.REIGNITE_CHARGE)
                        / (ErgosphereLeviathan.REIGNITE_TICKS - ErgosphereLeviathan.REIGNITE_CHARGE));
        double spin = charging ? t * 0.004 : 0;
        for (int i = 0; i < REIGNITE_RAYS; i++) {
            double a = Math.PI * 2 * i / REIGNITE_RAYS + spin;
            for (double y : charging ? new double[] {0} : new double[] {-0.8, 0, 1.2}) {
                Vec3 from = core.add(0, y, 0);
                Vec3 to = from.add(Math.cos(a) * ErgosphereLeviathan.ARENA, 0, Math.sin(a) * ErgosphereLeviathan.ARENA);
                net.minecraft.world.phys.HitResult hit = e.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
                Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
                if (charging) {
                    FxDraw.beam(pose.last(), glow, from.subtract(origin), end.subtract(origin), 0.04F + 0.1F * k, GOLD,
                            (int) (30 + 110 * k * (0.7F + 0.3F * Mth.sin(t * 0.5F + i))));
                } else {
                    FxDraw.beam(pose.last(), glow, from.subtract(origin), end.subtract(origin), 0.25F + 0.9F * k, GOLD, (int) (200 * k));
                    FxDraw.beam(pose.last(), glow, from.subtract(origin), end.subtract(origin), 0.1F + 0.35F * k, 0xFFFFFF, (int) (255 * k));
                }
            }
        }
        // 炉心の光の球と、床に広がる光の輪
        pose.pushPose();
        Vec3 c = core.subtract(origin);
        pose.translate(c.x, c.y, c.z);
        float sphere = charging ? 0.4F + 2.2F * k : 1.5F + 2.5F * (1 - k);
        FxDraw.sphere(pose.last(), glow, sphere, charging ? GOLD : 0xFFFFFF, 0xF000F0);
        if (!charging) {
            float r = 2 + (ErgosphereLeviathan.ARENA - 2) * (1 - k);
            pose.translate(0, -0.95, 0);
            FxDraw.ring(pose.last(), glow, Math.max(0, r - 2.5F), r, GOLD, (int) (40 * k), (int) (220 * k));
        }
        pose.popPose();
    }

    private static void renderMoves(ErgosphereLeviathan e, int state, int form, Vec3[] centers, Vec3 origin, float t, float partialTick,
                                    Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer glow = glow(buffers);
        float left = Math.max(0, e.stateTicks() - partialTick);
        if (state == ErgosphereLeviathan.REIGNITE && !e.homePos().equals(net.minecraft.core.BlockPos.ZERO)) {
            reigniteRays(e, origin, t, left, pose, glow);
        }
        if (state == ErgosphereLeviathan.RIFT && !e.homePos().equals(net.minecraft.core.BlockPos.ZERO)) {
            riftWalls(e, origin, t, left, pose, glow);
        }
        if (state == ErgosphereLeviathan.TIDE && !e.homePos().equals(net.minecraft.core.BlockPos.ZERO)) {
            tideVortex(e, origin, t, left, pose, glow);
        }
        if (state == ErgosphereLeviathan.BREATH_FIRE) {
            Vec3 from = e.mouth().subtract(e.position());
            Vec3 to = e.aim().subtract(origin);
            FxDraw.beam(pose.last(), glow, from, to, 1.3F + 0.15F * Mth.sin(t * 2), form == 3 ? GOLD : SEAM, 120);
            FxDraw.beam(pose.last(), glow, from, to, 0.55F, CORE, 150);
            FxDraw.beam(pose.last(), glow, from, to, 0.2F, 0xFFFFFF, 255);
        }
        if (state == ErgosphereLeviathan.TAIL_WHIP) {
            Vec3 tail = centers[ErgosphereLeviathan.SEGMENTS].subtract(origin);
            pose.pushPose();
            pose.translate(tail.x, tail.y, tail.z);
            if (left > ErgosphereLeviathan.WHIP_STRIKE) {
                FxDraw.billboard(pose, glow, camera, 2.5F + Mth.sin(t * 1.5F), CORE, 200);
            } else {
                float k = 1 - left / ErgosphereLeviathan.WHIP_STRIKE;
                pose.mulPose(Axis.YP.rotationDegrees(-t * 20));
                FxDraw.ring(pose.last(), glow, (float) ErgosphereLeviathan.WHIP_RANGE * k, (float) ErgosphereLeviathan.WHIP_RANGE * k + 0.8F,
                        CORE, (int) (230 * (1 - k)), 0);
            }
            pose.popPose();
        }
        if (state == ErgosphereLeviathan.COIL) {
            Vec3 c = Vec3.atBottomCenterOf(e.homePos()).add(0, 1.05, 0).subtract(origin);
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            pose.mulPose(Axis.YP.rotationDegrees(-t * 12));
            FxDraw.ring(pose.last(), glow, (float) ErgosphereLeviathan.COIL_REACH - 0.4F, (float) ErgosphereLeviathan.COIL_REACH, SEAM, 150, 150);
            for (int k = 0; k < 8; k++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(k * 45));
                FxDraw.crescent(pose.last(), glow, 0, 30, (float) ErgosphereLeviathan.COIL_REACH * 0.7F, 0.6F, BONE, 140);
                pose.popPose();
            }
            pose.popPose();
        }
        if (state == ErgosphereLeviathan.JET) {
            Vec3 head = centers[0].subtract(origin);
            boolean firing = left <= ErgosphereLeviathan.JET_TICKS - ErgosphereLeviathan.JET_WINDUP;
            for (Vec3 dir : ErgosphereLeviathan.jetDirections(e.jetYaw(), e.jetPitch())) {
                Vec3 end = head.add(dir.scale(ErgosphereLeviathan.JET_RANGE + 12));
                if (firing) {
                    FxDraw.beam(pose.last(), glow, head, end, 1.6F + 0.2F * Mth.sin(t * 3), GOLD, 130);
                    FxDraw.beam(pose.last(), glow, head, end, 0.4F, 0xFFFFFF, 255);
                } else {
                    FxDraw.beam(pose.last(), glow, head, end, 0.06F, GOLD, (int) (120 + 100 * Mth.sin(t)));
                }
            }
        }
    }

    /** エルゴ球の帯: 少し前に泳いだ跡に沿って、2本の淡い光の筋。古いほど薄い（龍は金色）。 */
    private static void renderWake(ErgosphereLeviathan e, int form, Vec3 origin, float t, PoseStack pose, MultiBufferSource buffers) {
        if (!e.isAlive() || e.emergeTicks() > 0 || e.transforming()) {
            return;
        }
        VertexConsumer glow = glow(buffers);
        int rgb = form == 3 ? GOLD : form == 2 ? BONE : SEAM;
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
                    FxDraw.beam(pose.last(), glow, prev.pos().add(off).subtract(origin), p.pos().add(off).subtract(origin), 0.12F, rgb, alpha);
                }
            }
            prev = p;
        }
    }

    // ------------------------------------------------------------------ 出現の小さな蛇

    /** 小さな蛇の節の数と、節の間隔（出現の tick で、跡をさかのぼる幅）。 */
    private static final int HATCH_SEGMENTS = 7;
    private static final float HATCH_LAG = 2.2F;

    /**
     * 出現の前半に炉心の渦から飛び出す小さな蛇。大蛇の装甲の姿をそのまま小さくした幼体で、
     * 鱗の板を重ねた細い体（尾へ細くなる）、節の継ぎ目を頭から尾へ走る菫の光、背の小さな棘、
     * 楔の板を重ねた頭と光る目、口の小さな渦、光る尾びれと尾の芯を持つ。泳ぐと体が左右にくねる。
     * 渦が閉じたあと頼りなく漂って炉心の上に止まり、真下から噴き上がった大蛇の顎へ吸い込まれて消える。
     * 黒い体と鱗を先にまとめて描き、光はあとでまとめて描く（描き方を何度も切り替えない）。
     */
    private static void renderHatchling(ErgosphereLeviathan e, float emergeT, float t, Vec3 core, Vec3 origin, Quaternionf camera,
                                        PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks, BlockState plate, int light) {
        float swallow = Mth.clamp((emergeT - ErgosphereLeviathan.SWALLOW) / 5F, 0, 1);
        float scale = Mth.clamp((emergeT - ErgosphereLeviathan.EMERGE_VORTEX) / 8F, 0, 1) * (1 - swallow);
        if (scale <= 0.01F) {
            return;
        }
        Vec3 mouth = e.position().add(0, e.getBbHeight(), 0);
        // 節の位置: 頭の跡をさかのぼり、進む向きと直角に左右へくねらせる（尾ほど大きく）
        Vec3[] at = new Vec3[HATCH_SEGMENTS];
        for (int k = 0; k < HATCH_SEGMENTS; k++) {
            float back = emergeT - k * HATCH_LAG;
            Vec3 p = ErgosphereLeviathan.hatchlingPos(core, back);
            Vec3 ahead = ErgosphereLeviathan.hatchlingPos(core, back + 1).subtract(p);
            Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
            if (side.lengthSqr() > 1e-6) {
                p = p.add(side.normalize().scale(Mth.sin(t * 0.55F - k * 0.9F) * 0.06F * k * scale));
            }
            at[k] = p.lerp(mouth, Math.min(1, swallow * (1 + k * 0.25F)));
        }
        // 体の節（尾から頭へ）の向きと太さ
        Quaternionf[] turn = new Quaternionf[HATCH_SEGMENTS];
        float[] radius = new float[HATCH_SEGMENTS];
        for (int k = 1; k < HATCH_SEGMENTS; k++) {
            turn[k] = orient(at[k - 1].subtract(at[k])).rotateZ(Mth.sin(t * 0.3F - k) * 10 * Mth.DEG_TO_RAD);
            radius[k] = Mth.lerp((k - 1) / (float) (HATCH_SEGMENTS - 2), 0.3F, 0.11F) * scale;
        }
        Vec3 head = at[0].subtract(origin);
        Quaternionf q = orient(at[0].subtract(at[1]));
        float hr = 0.36F * scale;
        float jaw = 0.5F + 0.5F * Mth.sin(t * 0.35F);

        // ---- 黒い体と鱗の板
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
        for (int k = HATCH_SEGMENTS - 1; k >= 1; k--) {
            Vec3 local = at[k].subtract(origin);
            float r = radius[k];
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            pose.mulPose(turn[k]);
            FxDraw.sphere(pose.last(), solid, r * 0.7F, 0x0A0612, 0);
            // 上と左右の3枚。若いので小さく、少し開き気味
            for (int s = -1; s <= 1; s++) {
                plate(pose, buffers, blocks, plate, light, s * 70, r * 0.6F, 0, -18, r * 1.3F, r * 0.32F, r * 1.6F);
            }
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        pose.mulPose(q);
        FxDraw.sphere(pose.last(), solid, hr * 0.75F, 0x0A0612, 0);
        pose.pushPose();
        pose.scale(hr, hr, hr);
        headPlates(pose, buffers, blocks, plate, light, 0.55F + 0.15F * jaw, -6 - 12 * jaw, 0);       // 下顎は小さく開け閉めする
        pose.popPose();
        pose.pushPose();
        pose.translate(0, -0.05F * hr, 1.0F * hr);
        FxDraw.sphere(pose.last(), solid, hr * (0.3F + 0.15F * jaw), 0x000000, 0);           // 口の小さな渦の黒
        pose.popPose();
        pose.popPose();

        // ---- 光
        VertexConsumer glow = glow(buffers);
        for (int k = HATCH_SEGMENTS - 1; k >= 1; k--) {
            Vec3 local = at[k].subtract(origin);
            float r = radius[k];
            float wave = 0.5F + 0.5F * Mth.sin(t * 0.5F - k * 0.8F);
            pose.pushPose();
            pose.translate(local.x, local.y, local.z);
            pose.mulPose(turn[k]);
            // 継ぎ目の光（頭から尾へ脈が走る）
            pose.pushPose();
            pose.translate(0, 0, r * 0.75F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            FxDraw.ring(pose.last(), glow, r * 0.7F, r * 1.05F, wave > 0.85F ? SEAM_HOT : SEAM, (int) (110 + 120 * wave), 0);
            pose.popPose();
            // 背の小さな棘
            pose.pushPose();
            pose.translate(0, r * 0.85F, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.mulPose(Axis.YP.rotationDegrees(-35));
            LastArchivistRenderer.blade(pose, glow, 1, r * 1.3F, r * 0.5F, SEAM, (int) (200 * scale), SEAM_HOT, 0);
            pose.popPose();
            if (k == HATCH_SEGMENTS - 1) {
                tailFin(pose, glow, r * 0.9F, 0.45F * scale, 0.28F * scale, 0.6F, 20, SEAM, (int) (210 * scale), t);
            }
            pose.popPose();
            if (k == HATCH_SEGMENTS - 1) {
                tailCore(local, r * 3F + 0.05F * Mth.sin(t * 0.7F), r * 1.3F, (int) (200 * scale), (int) (220 * scale), camera, pose, glow);
            }
        }
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        pose.mulPose(q);
        pose.pushPose();
        pose.translate(0, -0.05F * hr, 1.0F * hr);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * 12));
        FxDraw.ring(pose.last(), glow, hr * 0.35F, hr * 0.7F, SEAM, (int) (190 * scale), 0);
        pose.popPose();
        // 小さな角（後ろ上へ2本）
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.35F * hr, 0.6F * hr, -0.2F * hr);
            pose.mulPose(Axis.YP.rotationDegrees(side * 90 + side * 35));
            pose.mulPose(Axis.ZP.rotationDegrees(side * 30));
            LastArchivistRenderer.blade(pose, glow, side, hr * 1.4F, hr * 0.35F, SEAM_HOT, (int) (220 * scale), 0xFFFFFF, 0);
            pose.popPose();
        }
        pose.popPose();
        // 目（カメラを向く光。芯は白く、ときどきまばたく）
        float blink = (t % 50) < 3 ? 0.2F : 1F;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f ep = q.transform(new Vector3f(side * 0.5F * hr, 0.3F * hr, 0.72F * hr));
            pose.pushPose();
            pose.translate(head.x + ep.x, head.y + ep.y, head.z + ep.z);
            FxDraw.billboard(pose, glow, camera, 0.32F * scale, EYE, (int) (230 * scale * blink));
            FxDraw.billboard(pose, glow, camera, 0.12F * scale, 0xFFFFFF, (int) (255 * scale * blink));
            pose.popPose();
        }
        // 体のまわりの淡いにじみと、泳いだ跡の光の粒
        for (int k = 0; k < HATCH_SEGMENTS; k += 2) {
            Vec3 l = at[k].subtract(origin);
            pose.pushPose();
            pose.translate(l.x, l.y, l.z);
            FxDraw.billboard(pose, glow, camera, 1.1F * scale, 0x3A1670, (int) (60 * scale));
            pose.popPose();
        }
        for (int k = 0; k < 6; k++) {
            float back = emergeT - HATCH_SEGMENTS * HATCH_LAG - k * 2.5F;
            Vec3 l = ErgosphereLeviathan.hatchlingPos(core, back).lerp(mouth, swallow).subtract(origin);
            pose.pushPose();
            pose.translate(l.x, l.y + 0.1 * Mth.sin(t * 0.4F + k), l.z);
            FxDraw.billboard(pose, glow, camera, 0.12F * scale * (1 - k / 6F), SEAM_HOT, (int) (170 * scale * (1 - k / 6F)));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ 炉心の渦

    /**
     * 床の炉心の渦。出現・再臨（潜る〜噴き上がる）のあいだと、倒されたときに描く。
     * 出現と再臨の噴き上がりでは閃光、倒されたときは最後に渦がつぶれて閃光と衝撃波の輪。
     */
    private static void renderVortex(ErgosphereLeviathan e, float emerge, float emergeT, float death, int state, float t, float partialTick,
                                     Vec3 core, Vec3 origin, Quaternionf camera, PoseStack pose, MultiBufferSource buffers) {
        float size = 0;
        float flash = 0;
        if (death > 0) {
            float flashAt = (ErgosphereLeviathan.DEATH_TICKS - 8F) / ErgosphereLeviathan.DEATH_TICKS;
            if (death >= flashAt) {
                float c = (death - flashAt) / (1 - flashAt);
                VertexConsumer glow = glow(buffers);
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
        } else if (emerge < 1) {
            // 渦が開いて小さな蛇を吐き出し、すぐ閉じる。しばらく何もなく、前ぶれなく大きく開いて大蛇が噴き上がる
            if (emergeT < ErgosphereLeviathan.EMERGE_VORTEX) {
                size = emergeT / ErgosphereLeviathan.EMERGE_VORTEX * 0.6F;
            } else if (emergeT < ErgosphereLeviathan.RIFT_CLOSE) {
                float c = (emergeT - ErgosphereLeviathan.EMERGE_VORTEX) / (ErgosphereLeviathan.RIFT_CLOSE - ErgosphereLeviathan.EMERGE_VORTEX);
                size = 0.6F * (1 - c * c * (3 - 2 * c));
            } else if (emergeT < ErgosphereLeviathan.RIFT_SNAP) {
                size = 0;
            } else if (emergeT < ErgosphereLeviathan.SWALLOW) {
                size = 1.3F * Mth.clamp((emergeT - ErgosphereLeviathan.RIFT_SNAP) / 1.5F, 0, 1);
                flash = Mth.clamp((emergeT - ErgosphereLeviathan.RIFT_SNAP) / (ErgosphereLeviathan.SWALLOW - ErgosphereLeviathan.RIFT_SNAP), 0, 1);
            } else {
                size = 1.3F * (1 - (emergeT - ErgosphereLeviathan.SWALLOW) / (ErgosphereLeviathan.EMERGE_TICKS - ErgosphereLeviathan.SWALLOW));
                flash = Mth.clamp(1 - (emergeT - ErgosphereLeviathan.SWALLOW) / 12F, 0, 1);
            }
        } else if (state == ErgosphereLeviathan.SUBMERGE) {
            size = 0.5F + 0.9F * (1 - e.stateTicks() / (float) ErgosphereLeviathan.SUBMERGE_TICKS);
        } else if (state == ErgosphereLeviathan.ASCEND) {
            int since = ErgosphereLeviathan.ASCEND_TICKS - e.stateTicks();
            if (since < ErgosphereLeviathan.ASCEND_BURST) {
                size = 1.4F + 0.3F * Mth.sin(t * 0.5F);
            } else {
                size = Math.max(0, 1.4F - (since - ErgosphereLeviathan.ASCEND_BURST) / 20F);
                flash = Mth.clamp(1 - (since - ErgosphereLeviathan.ASCEND_BURST) / 14F, 0, 1);
            }
        }
        if (size <= 0.01F && flash <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(core.x - origin.x, core.y - origin.y + 0.05, core.z - origin.z);
        if (size > 0.01F) {
            GravitationalLensing.add(core.add(0, 0.6, 0), 1.2F + 2.2F * size, 0.5F + 0.4F * Math.min(1, size));
            pose.pushPose();
            pose.scale(1, 0.2F, 1);
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 1.6F * size, 0x000000, 0);
            pose.popPose();
        }
        VertexConsumer glow = glow(buffers);
        if (flash > 0) {
            FxDraw.billboard(pose, glow, camera, 3 + 10 * (1 - flash), 0xFFFFFF, (int) (230 * flash));
            FxDraw.ring(pose.last(), glow, 1 + 12 * (1 - flash), 1.8F + 12 * (1 - flash), SEAM_HOT, (int) (220 * flash), 0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(t * 8));
        for (int k = 0; k < 3; k++) {
            float rr = (1.8F + 1.6F * k) * size;
            FxDraw.ring(pose.last(), glow, rr, rr + 0.35F, k == 0 ? CORE : SEAM, 200 - k * 50, 0);
        }
        pose.popPose();
    }
}
