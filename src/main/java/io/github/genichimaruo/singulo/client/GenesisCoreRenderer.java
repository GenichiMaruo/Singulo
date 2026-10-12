package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 創世炉の光。
 * <ul>
 *   <li>揺籃: 柱の先から核の上へ、かすかな光の糸。小さな火花と輪</li>
 *   <li>種火: 柱の光が太くなり、粒が流れ込む。注いだぶんだけ種が膨らみ、輪が速く回る</li>
 *   <li>安定化: 光の殻の中で、生まれかけの宇宙が渦を巻く。不安定になるほど震え、赤いひびが走る。
 *       暴走の兆しには赤い警報の輪、投入を止めている間は青く静まる</li>
 *   <li>誕生: 光が一点に崩れ落ち、静寂のあと閃光。銀河が膨らみ、渦を巻いて空へ昇る</li>
 *   <li>完成: 小さな銀河が核の上で回り、その上に作る物の像と進み具合の輪</li>
 * </ul>
 */
public class GenesisCoreRenderer implements BlockEntityRenderer<GenesisCoreBlockEntity> {
    static final int PALE = 0xFFF2D8, GOLD = 0xFFD27A, BLUE = 0x8FD0FF, VIOLET = 0xB48CFF, RED = 0xFF3A2A, AQUA = 0x7FFFF0;
    private static final float Y = (float) GenesisCoreBlockEntity.LENS_Y;
    /** 柱の先（核の中心から）。 */
    private static final Vec3[] TOPS = {new Vec3(2, 4.05, 2), new Vec3(-2, 4.05, 2), new Vec3(2, 4.05, -2), new Vec3(-2, 4.05, -2)};

    private final ItemRenderer items;

    public GenesisCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    private static VertexConsumer glow(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
    }

    @Override
    public void render(GenesisCoreBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || be.stage() == GenesisCoreBlockEntity.UNFORMED) {
            return;
        }
        float time = be.clientTicks() + pt;
        Quaternionf camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        switch (be.stage()) {
            case GenesisCoreBlockEntity.CRADLE -> cradle(be, pose, buffers, camera, time);
            case GenesisCoreBlockEntity.KINDLING -> kindling(be, pose, buffers, camera, time);
            case GenesisCoreBlockEntity.STABILIZING -> stabilizing(be, pose, buffers, camera, time);
            case GenesisCoreBlockEntity.BIRTH -> birth(be, pose, buffers, camera, time, pt);
            default -> complete(be, pose, buffers, camera, time);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ 段階ごと

    private void cradle(GenesisCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float time) {
        if (!be.formed()) {
            return;
        }
        VertexConsumer vc = glow(buffers);
        float pulse = 0.7F + 0.3F * Mth.sin(time * 0.08F);
        beams(pose, vc, camera, time, PALE, (int) (45 * pulse), 0.04F, 0);
        at(pose, Y, () -> {
            FxDraw.billboard(pose, vc, camera, 0.45F, PALE, (int) (150 * pulse));
            FxDraw.gyroRings(pose, vc, 0.7F, 0.035F, time, GOLD, (int) (70 * pulse));
        });
    }

    private void kindling(GenesisCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float time) {
        VertexConsumer vc = glow(buffers);
        float c = be.chargeFraction();
        float feed = be.feeding() && be.intake() > 1e6 ? 1F : 0.25F;
        float flick = 0.85F + 0.15F * Mth.sin(time * 0.9F);
        beams(pose, vc, camera, time, GOLD, (int) ((70 + 150 * feed) * flick), 0.05F + 0.07F * feed, feed > 0.5F ? 0.035F : 0);
        at(pose, Y, () -> {
            int core = FxDraw.lerpRgb(GOLD, PALE, c);
            FxDraw.billboard(pose, vc, camera, 1.2F + 3.2F * c, core, (int) (70 * flick));
            FxDraw.billboard(pose, vc, camera, 0.35F + 1.5F * c, core, 235);
            FxDraw.billboard(pose, vc, camera, 0.18F + 0.6F * c, 0xFFFFFF, 255);
            for (int k = 0; k < 4; k++) {
                FxDraw.flare(pose, vc, camera, 1.6F + 4.5F * c, 0.06F + 0.05F * c, time * 0.6F + k * 45, PALE, (int) (120 * flick));
            }
            FxDraw.gyroRings(pose, vc, 0.8F + 1.4F * c, 0.04F + 0.03F * c, time * (1 + 3 * c), GOLD, 110 + (int) (90 * c));
        });
        GravitationalLensing.add(be.lens(), 0.6F + 1.2F * c, 0.12F + 0.25F * c);
    }

    private void stabilizing(GenesisCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float time) {
        VertexConsumer vc = glow(buffers);
        float p = be.progressFraction();
        float ins = be.instability() / 100F;
        boolean feeding = be.feeding();
        boolean surge = be.surgeTicks() > 0;
        boolean raging = be.surging() && feeding;
        boolean warn = surge && !be.surging();
        int base = FxDraw.lerpRgb(BLUE, VIOLET, p);
        int tone = raging ? FxDraw.lerpRgb(base, RED, 0.65F) : feeding ? base : FxDraw.lerpRgb(base, AQUA, 0.6F);
        float calm = feeding ? 1F : 0.6F;
        // 震え: 不安定なほど、暴走しているほど大きい
        float amp = 0.05F * ins * ins + (raging ? 0.1F : 0);
        float jx = amp * (Mth.sin(time * 37.1F) + Mth.sin(time * 23.7F) * 0.5F);
        float jy = amp * Mth.sin(time * 41.3F) * 0.6F;
        float jz = amp * (Mth.cos(time * 31.9F) + Mth.cos(time * 19.3F) * 0.5F);
        beams(pose, vc, camera, time, tone, feeding ? 190 : 30, feeding ? 0.09F : 0.03F, feeding ? 0.03F : 0);
        long seed = be.getBlockPos().asLong() * 31 + 7;
        pose.pushPose();
        pose.translate(jx, Y + jy, jz);
        // 外の光の殻と、中で渦を巻く生まれかけの宇宙
        FxDraw.billboard(pose, vc, camera, 5.0F + 1.5F * p, tone, (int) (60 * calm));
        Quaternionf tilt = new Quaternionf().rotationX(0.35F).rotateY(time * 0.002F);
        GalaxyDraw.glow(pose, vc, camera, tilt, seed, 1.0F + 1.3F * p, 0.6F * calm, 0.5F - 0.4F * p, false);
        Vector3f right = camera.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.transform(new Vector3f(0, 1, 0));
        GalaxyDraw.draw(pose.last(), vc, right, up, tilt, seed, 1.0F + 1.3F * p, time * (raging ? 0.09F : 0.03F),
                0.55F + 0.45F * p * calm, time, 0.5F - 0.4F * p, false);
        FxDraw.billboard(pose, vc, camera, 0.8F, 0xFFFFFF, 220);
        FxDraw.gyroRings(pose, vc, 2.7F, 0.05F, time * (raging ? 4 : 1), tone, (int) (120 * calm));
        // ひび: 不安定なほど多く、長く、赤く
        int cracks = (int) (ins * 9) + (raging ? 3 : 0);
        for (int k = 0; k < cracks; k++) {
            float seedAngle = k * 137.5F + (int) (time / 6) * 53;
            float flicker = 0.5F + 0.5F * Mth.sin(time * 1.7F + k * 2.1F);
            FxDraw.flare(pose, vc, camera, 2.4F + 3F * ins, 0.05F + 0.04F * ins, seedAngle, RED, (int) (200 * flicker));
        }
        pose.popPose();
        // 暴走の兆し: 地面から赤い警報の輪が広がる。止めている間は青い輪が宇宙を包んで縮める
        if (warn || raging) {
            float blink = (time % 10) < 5 ? 1F : 0.35F;
            for (int k = 0; k < 3; k++) {
                float f = (time * 0.05F + k / 3F) % 1F;
                at(pose, 0.6F, () -> FxDraw.ring(pose.last(), vc, 1 + f * 4.5F, 1.3F + f * 4.5F, RED, (int) (210 * (1 - f) * blink), 0));
            }
            at(pose, Y, () -> FxDraw.billboard(pose, vc, camera, 3.2F, RED, (int) (90 * blink)));
        } else if (surge) {
            for (int k = 0; k < 3; k++) {
                float f = (time * 0.03F + k / 3F) % 1F;
                float tiltDeg = 70 * (k - 1);
                at(pose, Y, () -> {
                    pose.mulPose(Axis.XP.rotationDegrees(tiltDeg));
                    FxDraw.ring(pose.last(), vc, 3.6F - f * 1.2F, 3.75F - f * 1.2F, AQUA, (int) (170 * Mth.sin(f * Mth.PI)), 40);
                });
            }
        }
        GravitationalLensing.add(be.lens(), 2.0F + 1.0F * p, 0.3F + 0.2F * p + 0.3F * ins);
    }

    /** 誕生の演出（サーバーの GenesisCoreBlockEntity#tickBirth と同じ時刻で）。 */
    private void birth(GenesisCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float time, float pt) {
        VertexConsumer vc = glow(buffers);
        float t = Math.min(GenesisCoreBlockEntity.BIRTH_TICKS, be.birthTicks() + pt);
        long seed = be.galaxySeed();
        Vector3f right = camera.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.transform(new Vector3f(0, 1, 0));
        Vec3 lens = be.lens();
        if (t < GenesisCoreBlockEntity.BIRTH_COLLAPSE) {
            // 崩落: 柱の光が白く燃え、まわりの光が一点へ吸い込まれ、宇宙が縮んでいく
            float k = t / GenesisCoreBlockEntity.BIRTH_COLLAPSE;
            beams(pose, vc, camera, time, 0xFFFFFF, 230, 0.14F, 0.06F + 0.05F * k);
            long protoSeed = be.getBlockPos().asLong() * 31 + 7;
            float shrink = (float) Math.pow(1 - k, 1.5);
            pose.pushPose();
            pose.translate(0, Y, 0);
            Quaternionf tilt = new Quaternionf().rotationX(0.35F);
            GalaxyDraw.glow(pose, vc, camera, tilt, protoSeed, 2.3F * shrink, 0.8F, k, false);
            GalaxyDraw.draw(pose.last(), vc, right, up, tilt, protoSeed, 2.3F * shrink, time * 0.03F + k * k * k * 30, 1F, time, k, false);
            FxDraw.billboard(pose, vc, camera, 1.0F + 2.0F * k, 0xFFFFFF, 255);
            for (int i = 0; i < 28; i++) {
                float f = (time * 0.025F + i * 0.618F) % 1F;
                float d = 16 * (1 - f) * (1 - f);
                float a = i * 2.39996F;
                float b = (float) Math.asin(((i * 0.377F) % 2F) - 1F);
                Vec3 dir = new Vec3(Mth.cos(a) * Mth.cos(b), Mth.sin(b), Mth.sin(a) * Mth.cos(b));
                pose.pushPose();
                pose.translate(dir.x * d, dir.y * d, dir.z * d);
                FxDraw.billboard(pose, vc, camera, 0.25F + 0.2F * f, PALE, (int) (255 * Math.min(1, f * 3)));
                pose.popPose();
            }
            pose.popPose();
            GravitationalLensing.add(lens, 1 + 3 * (1 - k), 0.5F + 0.5F * k);
            return;
        }
        if (t < GenesisCoreBlockEntity.BIRTH_BANG) {
            // 静寂: 何も見えない一点（空間だけがゆがむ）
            at(pose, Y, () -> FxDraw.billboard(pose, vc, camera, 0.12F, 0xFFFFFF, 255));
            GravitationalLensing.add(lens, 1.4F, 1.0F);
            return;
        }
        float b = t - GenesisCoreBlockEntity.BIRTH_BANG;
        // 閃光と衝撃波
        if (b < 50) {
            float f = b / 50F;
            at(pose, Y, () -> {
                FxDraw.billboard(pose, vc, camera, 3 + b * 1.6F, 0xFFFFFF, (int) (255 * (1 - f)));
                FxDraw.ring(pose.last(), vc, b * 0.7F, b * 0.7F + 0.8F + b * 0.05F, PALE, (int) (230 * (1 - f)), 0);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                FxDraw.ring(pose.last(), vc, b * 0.5F, b * 0.5F + 0.6F, GOLD, (int) (160 * (1 - f)), 0);
            });
        }
        // 膨張: 熱い白から、星の色へ。渦がほどけて広がる
        float span = GenesisCoreBlockEntity.BIRTH_EXPAND - GenesisCoreBlockEntity.BIRTH_BANG;
        float g = Mth.clamp(b / span, 0, 1);
        float ease = 1 - (1 - g) * (1 - g) * (1 - g);
        float radius = 7.5F * ease;
        float heat = (1 - g) * (1 - g);
        float bright = Math.min(1, b / 15F);
        float rise = 0;
        if (t > GenesisCoreBlockEntity.BIRTH_EXPAND) {
            // 夜空へ: 昇りながら薄れていく
            float r = Mth.clamp((t - GenesisCoreBlockEntity.BIRTH_EXPAND) / 80F, 0, 1);
            rise = r * r * 40;
            radius *= 1 + r * 0.8F;
            bright *= 1 - r;
        }
        float spin = time * 0.012F + (1 - g) * (1 - g) * 6;
        Quaternionf tilt = new Quaternionf().rotationX(0.25F);
        float finalRise = rise;
        float finalRadius = radius;
        float finalBright = bright;
        at(pose, Y + finalRise, () -> {
            GalaxyDraw.glow(pose, vc, camera, tilt, seed, finalRadius, finalBright, heat, false);
            GalaxyDraw.draw(pose.last(), vc, right, up, tilt, seed, finalRadius, spin, finalBright, time, heat, false);
        });
        if (b < span) {
            GravitationalLensing.add(lens, 1 + radius * 0.4F, 0.4F * (1 - g));
        }
        // 空へ送り出したあと、核の上に小さな銀河が灯る
        if (t > 480) {
            float k = Mth.clamp((t - 480) / 120F, 0, 1);
            smallGalaxy(be, pose, vc, camera, right, up, time, k);
        }
        beams(pose, vc, camera, time, PALE, (int) (200 * Math.max(0, 1 - b / 80F)), 0.1F, 0);
    }

    private void complete(GenesisCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float time) {
        VertexConsumer vc = glow(buffers);
        Vector3f right = camera.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.transform(new Vector3f(0, 1, 0));
        boolean working = be.formed() && be.feeding() && be.intake() > 1e5 && !be.template().isEmpty();
        smallGalaxy(be, pose, vc, camera, right, up, time, 1);
        beams(pose, vc, camera, time, GOLD, working ? 170 : 35, working ? 0.07F : 0.03F, working ? 0.04F : 0);
        ItemStack template = be.template();
        if (template.isEmpty()) {
            return;
        }
        float frac = be.synthFraction();
        float itemY = Y + 2.3F;
        // 進み具合の輪（作る物の下で、注いだぶんだけ伸びる）
        at(pose, itemY - 0.7F, () -> {
            FxDraw.ring(pose.last(), vc, 0.62F, 0.7F, PALE, 50, 50);
            arc(pose.last(), vc, 0.6F, 0.72F, frac, GOLD, 230);
        });
        at(pose, itemY, () -> FxDraw.billboard(pose, vc, camera, 1.3F + 0.25F * Mth.sin(time * 0.15F),
                working ? GOLD : PALE, working ? 120 : 60));
        // 出来上がった物の数だけ、小さな光が像のまわりを巡る
        int ready = Math.min(16, be.output().getCount());
        for (int i = 0; i < ready; i++) {
            float a = time * 0.05F + Mth.TWO_PI * i / Math.max(1, ready);
            float bob = 0.25F * Mth.sin(time * 0.07F + i);
            at(pose, itemY + bob, () -> {
                pose.translate(Mth.cos(a) * 0.95F, 0, Mth.sin(a) * 0.95F);
                FxDraw.billboard(pose, vc, camera, 0.14F, PALE, 230);
            });
        }
        // 作る物の像（ゆっくり回り、生まれるたびに少し脈打つ）
        pose.pushPose();
        pose.translate(0, itemY + 0.08F * Mth.sin(time * 0.06F), 0);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2));
        float s = 0.9F + 0.25F * (float) Math.pow(frac, 6);
        pose.scale(s, s, s);
        items.renderStatic(template, ItemDisplayContext.GROUND, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    // ------------------------------------------------------------------ 部品

    /** 完成した宇宙: 核の上で回る小さな銀河（k は現れ具合）。 */
    private static void smallGalaxy(GenesisCoreBlockEntity be, PoseStack pose, VertexConsumer vc, Quaternionf camera, Vector3f right,
                                    Vector3f up, float time, float k) {
        long seed = be.galaxySeed();
        Quaternionf tilt = new Quaternionf().rotationX(0.3F);
        float radius = 2.4F * (0.6F + 0.4F * k);
        at(pose, Y, () -> {
            GalaxyDraw.glow(pose, vc, camera, tilt, seed, radius, 0.8F * k, 0, false);
            GalaxyDraw.draw(pose.last(), vc, right, up, tilt, seed, radius, time * 0.004F, 0.95F * k, time, 0, false);
        });
        GravitationalLensing.add(be.lens(), 1.2F, 0.18F * k);
    }

    /** 柱の先から宇宙へ注ぐ光。flow が 0 でなければ、光の粒がその速さで流れ込む。 */
    private static void beams(PoseStack pose, VertexConsumer vc, Quaternionf camera, float time, int rgb, int alpha, float width,
                              float flow) {
        if (alpha <= 0) {
            return;
        }
        Vec3 lens = new Vec3(0, Y, 0);
        for (int i = 0; i < TOPS.length; i++) {
            Vec3 top = TOPS[i];
            FxDraw.beam(pose.last(), vc, top, lens, width, rgb, alpha);
            FxDraw.beam(pose.last(), vc, top, lens, width * 3, rgb, alpha / 5);
            pose.pushPose();
            pose.translate(top.x, top.y, top.z);
            FxDraw.billboard(pose, vc, camera, 0.5F + width * 3, rgb, Math.min(255, alpha + 30));
            pose.popPose();
            if (flow > 0) {
                for (int m = 0; m < 5; m++) {
                    float f = (time * flow + m / 5F + i * 0.13F) % 1F;
                    Vec3 at = top.lerp(lens, f);
                    pose.pushPose();
                    pose.translate(at.x, at.y, at.z);
                    FxDraw.billboard(pose, vc, camera, 0.22F, 0xFFFFFF, (int) (alpha * Mth.sin(f * Mth.PI)));
                    pose.popPose();
                }
            }
        }
    }

    /** XZ 平面の輪の一部（0〜frac 周、真上から見て時計回り）。 */
    private static void arc(PoseStack.Pose last, VertexConsumer vc, float inner, float outer, float frac, int rgb, int alpha) {
        int seg = Math.max(1, (int) (48 * frac));
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * frac * s / seg - Mth.HALF_PI;
            float a1 = Mth.TWO_PI * frac * (s + 1) / seg - Mth.HALF_PI;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int side = 0; side < 2; side++) {
                float ny = side == 0 ? 1 : -1;
                if (side == 0) {
                    vtx(vc, last, c0 * inner, s0 * inner, rgb, alpha, ny);
                    vtx(vc, last, c0 * outer, s0 * outer, rgb, alpha, ny);
                    vtx(vc, last, c1 * outer, s1 * outer, rgb, alpha, ny);
                    vtx(vc, last, c1 * inner, s1 * inner, rgb, alpha, ny);
                } else {
                    vtx(vc, last, c1 * inner, s1 * inner, rgb, alpha, ny);
                    vtx(vc, last, c1 * outer, s1 * outer, rgb, alpha, ny);
                    vtx(vc, last, c0 * outer, s0 * outer, rgb, alpha, ny);
                    vtx(vc, last, c0 * inner, s0 * inner, rgb, alpha, ny);
                }
            }
        }
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose last, float x, float z, int rgb, int alpha, float ny) {
        vc.addVertex(last, x, 0, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FxDraw.FULL_BRIGHT).setNormal(last, 0, ny, 0);
    }

    /** 高さ y に移して描く。 */
    private static void at(PoseStack pose, float y, Runnable draw) {
        pose.pushPose();
        pose.translate(0, y, 0);
        draw.run();
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GenesisCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(GenesisCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(9).expandTowards(0, 48, 0);
    }
}
