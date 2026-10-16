package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.FoundryOverseer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 製造統括AI「ファウンドリ」: 鋼の檻に囲まれた暗い球のコアと、前を向く赤い単眼。天井へ伸びる4本のケーブル。
 * <ul>
 *   <li>製造ラインが残っている間は、コアのまわりに橙の守りの光と回る輪（ラインが多いほど濃い）。檻の灯はラインの数だけ点く</li>
 *   <li>ロボットアーム: 床に赤い印（狙いが止まると明るくなる）、天井から降りてくるピストンと爪</li>
 *   <li>プレス: 床の影の丸と赤い縁、天井から下がってくる鋼の板（最後に一気に落ちる）</li>
 *   <li>溶接レーザー: 床になぞる扇の印。なぞる間は、単眼から床へ光が落ち、床すれすれの光線が回る</li>
 *   <li>出現: 天井からコアが降りてきて、最後に単眼が開く。倒されると単眼がちらついて消え、コアが沈む</li>
 *   <li>覚醒した個体: 檻と単眼が紅く、守りの光も紅い</li>
 * </ul>
 */
public class FoundryOverseerRenderer extends EntityRenderer<FoundryOverseer> {
    static final int STEEL = 0x8C96A6, DARK = 0x2A2E36, CABLE = 0x23262C, EYE = 0xFF3B2E, ORANGE = 0xFF9A3C, HOT = 0xFFE2B0;
    static final int AWAKENED = 0xFF4060;
    /** 体の中心の高さ（足から）。 */
    static final float CY = 1.2F;
    /** 天井の高さ（足から）。ホールの天井は床から13ブロック。 */
    static final float CEIL = (float) (13 - FoundryOverseer.HOVER);
    static final float FLOOR = (float) -FoundryOverseer.HOVER;

    public FoundryOverseerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(FoundryOverseer entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(FoundryOverseer e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float emerge = e.emergeProgress(partialTick);
        float death = e.deathProgress(partialTick);
        float descend = 1 - ease(Mth.clamp(emerge / 0.8F, 0, 1));
        float lift = 5 * descend - 1.6F * death + 0.08F * Mth.sin(t * 0.08F);
        boolean awakened = e.awakened();
        int accent = awakened ? AWAKENED : ORANGE;
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 origin = e.getPosition(partialTick);

        // ---- 不透明な部分（ケーブル・コア・檻・爪・プレス）
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0, CY + lift, 0);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            Vec3 top = new Vec3(Math.cos(a) * 2.0, CEIL - CY - lift, Math.sin(a) * 2.0);
            FxDraw.beam(pose.last(), solid, new Vec3(Math.cos(a) * 0.5, 0.5, Math.sin(a) * 0.5), top, 0.16F, CABLE, 255);
        }
        FxDraw.sphere(pose.last(), solid, 0.85F, DARK, light);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot)));
        // 単眼の台（前 +Z）
        FxDraw.box(pose, solid, -0.38F, -0.38F, 0.6F, 0.38F, 0.38F, 0.86F, light, STEEL);
        // 鋼の檻: ゆっくり回る8本の柱と、上下の枠
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.6F));
        int cage = awakened ? 0x8A3A44 : STEEL;
        for (int k = 0; k < 8; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 45 + 22.5F));
            FxDraw.box(pose, solid, -0.07F, -0.95F, 1.08F, 0.07F, 0.95F, 1.2F, light, cage);
            FxDraw.box(pose, solid, -0.46F, 0.95F, 1.0F, 0.46F, 1.07F, 1.2F, light, cage);
            FxDraw.box(pose, solid, -0.46F, -1.07F, 1.0F, 0.46F, -0.95F, 1.2F, light, cage);
            pose.popPose();
        }
        pose.popPose();
        pose.popPose();
        for (int arm = 0; arm < 2; arm++) {
            if (e.armTicks(arm) > 0) {
                armSolid(e, arm, partialTick, pose, solid, light, origin);
            }
        }
        if (e.pressTicks() > 0) {
            pressSolid(e, partialTick, pose, solid, light, origin);
        }

        // ---- 光る部分（単眼・守りの光・灯・印・レーザー）
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0, CY + lift, 0);
        int lines = e.lineCount();
        if (lines > 0 && death == 0) {
            int a = (int) ((30 + 25 * lines) * emerge);
            FxDraw.billboard(pose, glow, camera, 3.8F + 0.4F * lines, accent, a);
            FxDraw.gyroRings(pose, glow, 1.55F, 0.05F, t * 1.5F, accent, Math.min(255, a * 2));
        }
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot)));
        // 単眼: 出現の最後に開き、倒されるとちらついて消える
        float open = Mth.clamp((emerge - 0.8F) / 0.2F, 0, 1);
        if (death > 0) {
            open *= (1 - death) * (((int) (t * 3)) % 4 == 0 ? 0.2F : 1F);
        }
        if (open > 0.01F) {
            int eye = awakened ? AWAKENED : EYE;
            FxDraw.box(pose, glow, -0.22F, -0.22F * open, 0.86F, 0.22F, 0.22F * open, 0.92F, FxDraw.FULL_BRIGHT, eye, 255);
            pose.pushPose();
            pose.translate(0, 0, 0.95F);
            FxDraw.billboard(pose, glow, camera, 1.1F * open, eye, (int) (200 * open));
            FxDraw.billboard(pose, glow, camera, 0.4F * open, 0xFFFFFF, (int) (180 * open));
            pose.popPose();
        }
        // 檻の灯（動いているラインの数だけ）
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.6F));
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 90 + 22.5F));
            int c = k < lines ? accent : 0x40302A;
            FxDraw.box(pose, glow, -0.08F, 0.3F, 1.2F, 0.08F, 0.46F, 1.24F, FxDraw.FULL_BRIGHT, c, k < lines ? 255 : 160);
            pose.popPose();
        }
        pose.popPose();
        for (int arm = 0; arm < 2; arm++) {
            if (e.armTicks(arm) > 0) {
                armGlow(e, arm, partialTick, pose, glow, origin);
            }
        }
        if (e.pressTicks() > 0) {
            pressGlow(e, partialTick, pose, glow, origin);
        }
        if (e.laserTicks() > 0) {
            laser(e, partialTick, pose, glow, camera, lift, origin);
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    private static float ease(float k) {
        return k * k * (3 - 2 * k);
    }

    // ------------------------------------------------------------------ ロボットアーム

    /** 爪の高さ（足から）: 狙いの7割までは天井に、そこから狙った床（ground、足から）へ降り、つかんだら上がっていく。 */
    private static float clawY(FoundryOverseer e, int arm, float partialTick, float ground) {
        float left = e.armTicks(arm) - partialTick;
        float low = ground + 0.35F, high = CEIL - 1.6F;
        if (left > FoundryOverseer.ARM_HOLD) {
            float k = 1 - (left - FoundryOverseer.ARM_HOLD) / FoundryOverseer.ARM_AIM;
            return k < 0.7F ? high : Mth.lerp(ease((k - 0.7F) / 0.3F), high, low);
        }
        return Mth.lerp(ease(1 - left / FoundryOverseer.ARM_HOLD), low, high);
    }

    private void armSolid(FoundryOverseer e, int arm, float partialTick, PoseStack pose, VertexConsumer vc, int light, Vec3 origin) {
        Vec3 at = e.armTarget(arm).subtract(origin);
        float y = clawY(e, arm, partialTick, (float) at.y);
        pose.pushPose();
        pose.translate(at.x, 0, at.z);
        FxDraw.box(pose, vc, -0.5F, CEIL - 0.9F, -0.5F, 0.5F, CEIL - 0.3F, 0.5F, light, DARK);       // 台車
        FxDraw.box(pose, vc, -0.14F, y + 0.7F, -0.14F, 0.14F, CEIL - 0.9F, 0.14F, light, STEEL);    // ピストン
        FxDraw.box(pose, vc, -0.4F, y + 0.4F, -0.4F, 0.4F, y + 0.75F, 0.4F, light, DARK);           // 爪の付け根
        float spread = e.armTicks(arm) > FoundryOverseer.ARM_HOLD ? 0.45F : 0.25F;                    // つかむと閉じる
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 120));
            FxDraw.box(pose, vc, -0.07F, y - 0.25F, spread - 0.07F, 0.07F, y + 0.45F, spread + 0.07F, light, STEEL);
            pose.popPose();
        }
        pose.popPose();
    }

    private void armGlow(FoundryOverseer e, int arm, float partialTick, PoseStack pose, VertexConsumer vc, Vec3 origin) {
        Vec3 at = e.armTarget(arm).subtract(origin);
        float left = e.armTicks(arm) - partialTick;
        float y = clawY(e, arm, partialTick, (float) at.y);
        pose.pushPose();
        pose.translate(at.x, 0, at.z);
        FxDraw.box(pose, vc, -0.41F, y + 0.52F, -0.41F, 0.41F, y + 0.6F, 0.41F, FxDraw.FULL_BRIGHT, ORANGE, 255);     // 警告の帯
        if (left > FoundryOverseer.ARM_HOLD) {
            boolean locked = left - FoundryOverseer.ARM_HOLD <= FoundryOverseer.ARM_LOCK;
            float r = (float) FoundryOverseer.ARM_RADIUS;
            pose.translate(0, at.y + 0.04F, 0);
            int pulse = (int) (40 * Mth.sin((e.tickCount + partialTick) * 0.6F));
            FxDraw.ring(pose.last(), vc, r - 0.12F, r, EYE, locked ? 230 : 150 + pulse, locked ? 230 : 150 + pulse);
            FxDraw.ring(pose.last(), vc, 0, r - 0.12F, EYE, locked ? 70 : 35, locked ? 50 : 20);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ プレス

    /** 板の高さ（足から）: 少しずつ下がってきて、最後に狙った床（ground、足から）へ一気に落ちる。落ちたあと少し止まって上がる。 */
    private static float plateY(FoundryOverseer e, float partialTick, float ground) {
        float left = e.pressTicks() - partialTick;
        float low = ground + 0.05F, high = CEIL - 1.2F, hang = Math.min(ground + 4.0F, high);
        if (left > FoundryOverseer.PRESS_FLASH) {
            float k = 1 - (left - FoundryOverseer.PRESS_FLASH) / FoundryOverseer.PRESS_WINDUP;
            return k < 0.85F ? Mth.lerp(ease(k / 0.85F), high, hang) : Mth.lerp((k - 0.85F) / 0.15F, hang, low);
        }
        float k = 1 - left / FoundryOverseer.PRESS_FLASH;
        return k < 0.35F ? low : Mth.lerp(ease((k - 0.35F) / 0.65F), low, high);
    }

    private void pressSolid(FoundryOverseer e, float partialTick, PoseStack pose, VertexConsumer vc, int light, Vec3 origin) {
        Vec3 at = e.pressTarget().subtract(origin);
        float r = (float) e.pressRadius() * 0.9F;
        float y = plateY(e, partialTick, (float) at.y);
        pose.pushPose();
        pose.translate(at.x, 0, at.z);
        FxDraw.box(pose, vc, -r, y, -r, r, y + 0.55F, r, light, STEEL);
        FxDraw.box(pose, vc, -r * 0.6F, y + 0.55F, -r * 0.6F, r * 0.6F, y + 0.85F, r * 0.6F, light, DARK);
        FxDraw.box(pose, vc, -0.3F, y + 0.85F, -0.3F, 0.3F, CEIL, 0.3F, light, STEEL);
        pose.popPose();
    }

    private void pressGlow(FoundryOverseer e, float partialTick, PoseStack pose, VertexConsumer vc, Vec3 origin) {
        Vec3 at = e.pressTarget().subtract(origin);
        float r = (float) e.pressRadius();
        float left = e.pressTicks() - partialTick;
        float y = plateY(e, partialTick, (float) at.y);
        pose.pushPose();
        pose.translate(at.x, 0, at.z);
        // 板の縁の警告の帯
        FxDraw.box(pose, vc, -r * 0.9F - 0.01F, y + 0.2F, -r * 0.9F - 0.01F, r * 0.9F + 0.01F, y + 0.32F, r * 0.9F + 0.01F,
                FxDraw.FULL_BRIGHT, ORANGE, 255);
        if (left > FoundryOverseer.PRESS_FLASH) {
            float k = 1 - (left - FoundryOverseer.PRESS_FLASH) / FoundryOverseer.PRESS_WINDUP;
            pose.translate(0, at.y + 0.04F, 0);
            FxDraw.ring(pose.last(), vc, 0, r, 0x000000, (int) (60 + 120 * k), (int) (40 + 100 * k));          // 影
            FxDraw.ring(pose.last(), vc, r - 0.15F, r, EYE, 200, 200);                                           // 赤い縁
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ 溶接レーザー

    private void laser(FoundryOverseer e, float partialTick, PoseStack pose, VertexConsumer vc, Quaternionf camera, float lift, Vec3 origin) {
        float left = e.laserTicks() - partialTick;
        float from = e.laserFrom();
        float to = from + e.laserSpin() * FoundryOverseer.LASER_ARC;
        Vec3 c = e.anchor().subtract(origin);
        pose.pushPose();
        pose.translate(c.x, FLOOR + 0.05F, c.z);
        if (left > FoundryOverseer.LASER_SWEEP) {
            // 前触れ: なぞる扇の印（強まっていく）と、なぞり始めの線
            float k = 1 - (left - FoundryOverseer.LASER_SWEEP) / FoundryOverseer.LASER_AIM;
            fan(pose, vc, Math.min(from, to), Math.max(from, to), EYE, (int) (20 + 50 * k), (int) (10 + 30 * k));
            Vec3 d = dir(from);
            FxDraw.beam(pose.last(), vc, d.scale(1.0), d.scale(FoundryOverseer.laserReach(from)), 0.08F + 0.1F * k, EYE, (int) (120 + 120 * k));
            pose.popPose();
            return;
        }
        float angle = e.laserAngle(partialTick);
        float trail = 14 * e.laserSpin();
        fan(pose, vc, Math.min(angle - trail, angle), Math.max(angle - trail, angle), ORANGE, 90, 30);
        Vec3 d = dir(angle);
        Vec3 near = d.scale(1.0).add(0, 0.4, 0), far = d.scale(FoundryOverseer.laserReach(angle)).add(0, 0.4, 0);
        FxDraw.beam(pose.last(), vc, near, far, 0.5F, ORANGE, 200);
        FxDraw.beam(pose.last(), vc, near, far, 0.16F, HOT, 255);
        // 単眼から床へ落ちる光
        Vec3 eye = new Vec3(0, -FLOOR + CY + lift - 0.05, 0);
        FxDraw.beam(pose.last(), vc, eye, near, 0.3F, EYE, 200);
        FxDraw.beam(pose.last(), vc, eye, near, 0.1F, HOT, 255);
        pose.translate(far.x, far.y, far.z);
        FxDraw.billboard(pose, vc, camera, 1.2F, HOT, 220);
        pose.popPose();
    }

    /** 床の扇形（外側は向きごとに四角い部屋の壁まで）。細い扇を並べて描く。 */
    private static void fan(PoseStack pose, VertexConsumer vc, float from, float to, int rgb, int alphaIn, int alphaOut) {
        for (float a = from; a < to; a += 3) {
            float b = Math.min(to, a + 3);
            float outer = (float) FoundryOverseer.laserReach((a + b) / 2);
            FxDraw.sector(pose.last(), vc, 1.0F, outer, a, b, rgb, alphaIn, alphaOut);
        }
    }

    private static Vec3 dir(float deg) {
        return new Vec3(Math.cos(deg * Mth.DEG_TO_RAD), 0, Math.sin(deg * Mth.DEG_TO_RAD));
    }
}
