package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.SaberAndroid;
import io.github.genichimaruo.singulo.android.WhipAndroid;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 遺構のアンドロイドの描画。体（{@link AndroidModel}）に、光る線と目（暗くても光る）、半透明の光の刃を重ねる。
 * 技の見た目: 刃の軌跡（三日月）、回転斬りの輪、突進の残像、前触れの光、充電切れの点滅、充電の輪。
 * 印刷中は、光る走査面より下だけが実体で、上はうすい光の像（下から積み上がるように見える）。
 */
public class AndroidRenderer extends MobRenderer<RuinAndroid, AndroidModel> {
    /** 体の大きさ（モデルはプレイヤーの大きさで作り、少し縮める）。 */
    static final float SCALE = 0.85F;

    public enum Variant {
        BLADE("blade_android", BladeAndroid.ACCENT, AndroidModel.BLADE_LAYER),
        WHIP("whip_android", WhipAndroid.ACCENT, AndroidModel.WHIP_LAYER),
        SABER("saber_android", SaberAndroid.ACCENT, AndroidModel.SABER_LAYER);

        final ResourceLocation texture;
        final ResourceLocation glow;
        final int accent;
        final ModelLayerLocation layer;

        Variant(String name, int accent, ModelLayerLocation layer) {
            this.texture = Singulo.id("textures/entity/" + name + ".png");
            this.glow = Singulo.id("textures/entity/" + name + "_glow.png");
            this.accent = accent;
            this.layer = layer;
        }
    }

    private final Variant variant;

    public AndroidRenderer(EntityRendererProvider.Context context, Variant variant) {
        super(context, new AndroidModel(context.bakeLayer(variant.layer)), 0.35F);
        this.variant = variant;
        addLayer(new Glow(this));
        addLayer(new Blade(this));
        addLayer(new Whip(this));
        addLayer(new Saber(this));
        addLayer(new Solar(this));
        addLayer(new ArmorGlint(this));
    }

    @Override
    public ResourceLocation getTextureLocation(RuinAndroid android) {
        return variant.texture;
    }

    @Override
    protected void scale(RuinAndroid android, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(RuinAndroid android, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (model.blade != null) {
            model.blade.visible = false;
        }
        float print = android.printProgress(partialTick);
        if (print < 1) {
            float plane = pose.last().pose().m31() + print * (android.getBbHeight() + 0.1F);
            super.render(android, yaw, partialTick, pose, ClipBuffers.below(buffers, plane), light);
            renderHologram(android, partialTick, pose, buffers, plane);
            renderPrintHead(android, print, partialTick, pose, buffers);
            return;
        }
        super.render(android, yaw, partialTick, pose, buffers, light);
        renderMoves(android, partialTick, pose, buffers);
    }

    /** 体を描くときと同じ変形（向き・裏返し・大きさ）を pose に積む。 */
    private void modelSpace(RuinAndroid android, float partialTick, PoseStack pose) {
        float bodyYaw = Mth.rotLerp(partialTick, android.yBodyRotO, android.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.scale(-1, -1, 1);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(0, -1.501F, 0);
    }

    /** 体の向きの座標（正面が -Z、右が +X）へ。 */
    private static void bodySpace(RuinAndroid android, float partialTick, PoseStack pose) {
        float bodyYaw = Mth.rotLerp(partialTick, android.yBodyRotO, android.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
    }

    private static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ 印刷

    /** まだ印刷されていない上の部分の、うすい光の像。 */
    private void renderHologram(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers, float plane) {
        pose.pushPose();
        modelSpace(android, partialTick, pose);
        VertexConsumer vc = ClipBuffers.above(buffers.getBuffer(RenderType.entityTranslucentEmissive(variant.texture)), plane);
        int flicker = 45 + (int) (20 * Mth.sin((android.tickCount + partialTick) * 0.9F));
        LegacyRender.model(model, pose, vc, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb(flicker, mix(variant.accent, 0xFFFFFF, 0.3F)));
        pose.popPose();
    }

    /** 走査面（光る四角い断面）と、その上を往復する印刷ヘッドの光。足元の台の四隅から光の糸。 */
    private void renderPrintHead(RuinAndroid android, float print, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float t = android.tickCount + partialTick;
        float y = print * (android.getBbHeight() + 0.1F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        bodySpace(android, partialTick, pose);
        pose.translate(0, y, 0);
        float half = 0.42F;
        FxDraw.box(pose, vc, -half, -0.01F, -half, half, 0.01F, half, FxDraw.FULL_BRIGHT, variant.accent, 70);
        float sweep = Mth.sin(t * 0.7F) * half;
        FxDraw.beam(pose.last(), vc, new Vec3(-half - 0.08, 0, sweep), new Vec3(half + 0.08, 0, sweep), 0.06F, 0xFFFFFF, 220);
        FxDraw.beam(pose.last(), vc, new Vec3(-half - 0.08, 0, sweep), new Vec3(half + 0.08, 0, sweep), 0.16F, variant.accent, 120);
        for (int i = 0; i < 4; i++) {
            float cx = (i & 1) == 0 ? -0.5F : 0.5F;
            float cz = (i & 2) == 0 ? -0.5F : 0.5F;
            FxDraw.beam(pose.last(), vc, new Vec3(cx, -y, cz), new Vec3(cx * 0.8, 0, cz * 0.8), 0.03F, variant.accent, 150);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ 技の見た目

    private void renderMoves(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        int state = android.state();
        float t = android.stateTime(partialTick);
        float age = android.tickCount + partialTick;
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        int accent = variant.accent;
        switch (state) {
            case BladeAndroid.STATE_DASH_WINDUP -> {
                // 刃に光が集まる
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0.45, 0.95, 0.35);
                float k = Mth.clamp(t / BladeAndroid.DASH_WINDUP, 0, 1);
                float pulse = 0.85F + 0.15F * Mth.sin(age * 2.0F);
                FxDraw.billboard(pose, glow, camera, (0.5F + 0.9F * k) * pulse, accent, 140);
                FxDraw.billboard(pose, glow, camera, (0.2F + 0.35F * k) * pulse, 0xFFFFFF, 230);
                pose.popPose();
            }
            case BladeAndroid.STATE_DASH -> renderAfterimages(android, partialTick, pose, buffers, 1F);
            case BladeAndroid.STATE_SLASH -> {
                if (t < 3) {
                    renderAfterimages(android, partialTick, pose, buffers, 1 - t / 3);
                    // 別の描画先を取り出すと、前に取り出した描画先は閉じられるので、取り直す
                    glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                }
                slashTrail(android, partialTick, pose, glow, t, 4, 1.45F, 1F);
            }
            case BladeAndroid.STATE_SWING -> slashTrail(android, partialTick, pose, glow, t, 3, 1.25F, 0.6F);
            case SaberAndroid.STATE_SWEEP -> slashTrail(android, partialTick, pose, glow, t, 2, (float) SaberAndroid.SWEEP_REACH, 1.1F);
            case SaberAndroid.STATE_RIPOSTE -> slashTrail(android, partialTick, pose, glow, t, 1.5F, 1.7F, 1F);
            case SaberAndroid.STATE_CUT -> slashTrail(android, partialTick, pose, glow, t, 3, 1.4F, 0.6F);
            case SaberAndroid.STATE_THRUST_WINDUP -> {
                // 腰だめの剣に光が集まる
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0.35, 0.75, -0.3);
                float k = Mth.clamp(t / SaberAndroid.THRUST_WINDUP, 0, 1);
                float pulse = 0.85F + 0.15F * Mth.sin(age * 2.2F);
                FxDraw.billboard(pose, glow, camera, (0.4F + 0.9F * k) * pulse, accent, 130);
                FxDraw.billboard(pose, glow, camera, (0.15F + 0.35F * k) * pulse, 0xFFFFFF, 230);
                pose.popPose();
            }
            case SaberAndroid.STATE_GUARD -> {
                // 正面の光の盾（六角の縁と、薄い面）
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0, 1.0, -0.75);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                float k = Mth.clamp(t / 4, 0, 1) * (1 - Mth.clamp((t - SaberAndroid.GUARD + 4) / 4, 0, 1));
                float shimmer = 0.9F + 0.1F * Mth.sin(age * 0.2F);
                FxDraw.ring(pose.last(), glow, 0, 0.75F * k, accent, (int) (45 * shimmer), (int) (70 * shimmer));
                FxDraw.ring(pose.last(), glow, 0.7F * k, 0.8F * k, 0xFFFFFF, (int) (200 * k), (int) (120 * k));
                pose.mulPose(Axis.YP.rotationDegrees(age * 3));
                for (int i = 0; i < 6; i++) {
                    float a0 = Mth.TWO_PI * i / 6;
                    float a1 = Mth.TWO_PI * (i + 1) / 6;
                    FxDraw.beam(pose.last(), glow, new Vec3(Mth.cos(a0) * 0.55 * k, 0, Mth.sin(a0) * 0.55 * k),
                            new Vec3(Mth.cos(a1) * 0.55 * k, 0, Mth.sin(a1) * 0.55 * k), 0.04F, accent, (int) (200 * k));
                }
                pose.popPose();
            }
            case SaberAndroid.STATE_WAVE -> {
                if (t >= 6) {
                    pose.pushPose();
                    bodySpace(android, partialTick, pose);
                    pose.translate(0, 1.2, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(-60));
                    float u = Mth.clamp((t - 6) / 2, 0, 1);
                    float fade = 1 - Mth.clamp((t - 8) / 4, 0, 1);
                    FxDraw.crescent(pose.last(), glow, Mth.lerp(u, 90, -80), 90, 1.5F, 0.55F, accent, (int) (180 * fade));
                    FxDraw.crescent(pose.last(), glow, Mth.lerp(u, 90, -80), 90, 1.5F, 0.16F, 0xFFFFFF, (int) (235 * fade));
                    pose.popPose();
                }
            }
            case BladeAndroid.STATE_SPIN -> {
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0, 0.95, 0);
                float u = Mth.clamp(t / 7, 0, 1);
                float fade = 1 - Mth.clamp((t - 6) / 4, 0, 1);
                // 体の回転（AndroidModel.spin）と同じ向きに、刃先の軌跡が伸びる（角度は負の向きへ進む）
                float head = -360 * (1 - (1 - u) * (1 - u));
                float from = Math.min(0, head + 200);
                FxDraw.crescent(pose.last(), glow, from, head, 1.55F, 0.55F, accent, (int) (170 * fade));
                FxDraw.crescent(pose.last(), glow, from, head, 1.55F, 0.16F, 0xFFFFFF, (int) (230 * fade));
                pose.popPose();
            }
            case BladeAndroid.STATE_CAST -> {
                // 床に当てた手の下の魔法陣
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(-0.25, 0.03, -0.45);
                float k = Mth.clamp(t / 6, 0, 1) * (1 - Mth.clamp((t - 14) / 4, 0, 1));
                pose.mulPose(Axis.YP.rotationDegrees(age * 6));
                FxDraw.ring(pose.last(), glow, 0.55F * k, 0.65F * k, accent, (int) (200 * k), (int) (120 * k));
                FxDraw.ring(pose.last(), glow, 0.25F * k, 0.3F * k, 0xFFFFFF, (int) (220 * k), (int) (150 * k));
                for (int i = 0; i < 6; i++) {
                    float a = Mth.TWO_PI * i / 6;
                    FxDraw.beam(pose.last(), glow, new Vec3(Mth.cos(a) * 0.3 * k, 0, Mth.sin(a) * 0.3 * k),
                            new Vec3(Mth.cos(a) * 0.55 * k, 0, Mth.sin(a) * 0.55 * k), 0.03F, accent, (int) (200 * k));
                }
                pose.popPose();
            }
            case RuinAndroid.STATE_LOW_POWER -> {
                // 頭の上で、橙の小さな光が点滅する（充電切れ）
                if ((int) (age / 6) % 2 == 0) {
                    pose.pushPose();
                    pose.translate(0, android.getBbHeight() + 0.3, 0);
                    FxDraw.billboard(pose, glow, camera, 0.28F, 0xFFA040, 220);
                    pose.popPose();
                }
            }
            case RuinAndroid.STATE_CHARGING_FOLLOW -> {
                // 持ち主の無線充電器から胸へ流れる光の線（ゆるく揺れる）と、足元の充電の目盛り
                Entity link = android.chargerLink();
                if (link != null) {
                    Vec3 camera3 = entityRenderDispatcher.camera.getPosition();
                    Vec3 from = link.getPosition(partialTick).add(0, link.getBbHeight() * 0.45, 0).subtract(camera3);
                    Vec3 to = android.getPosition(partialTick).add(0, android.getBbHeight() * 0.6, 0).subtract(camera3);
                    if (from.distanceToSqr(to) < 6 * 6) {
                        VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
                        PoseStack.Pose id = new PoseStack().last();
                        Vec3 prev = from;
                        for (int i = 1; i <= 10; i++) {
                            float s = i / 10F;
                            Vec3 p = from.lerp(to, s).add(0, Mth.sin(s * Mth.PI) * 0.25 + Mth.sin(age * 0.4F + s * 9) * 0.03, 0);
                            FxDraw.beam(id, fx, prev, p, 0.05F, accent, 150);
                            prev = p;
                        }
                    }
                    glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                }
                pose.pushPose();
                pose.translate(0, 0.02, 0);
                LastArchivistRenderer.arc(pose, glow, 0.62F, 0.73F, -90, -90 + 360 * android.chargeFraction(), 0xFFFFFF, 190);
                pose.popPose();
            }
            case RuinAndroid.STATE_SOLAR, RuinAndroid.STATE_RESTING -> {
                // 足元の充電の目盛り（満ちるほど白い弧がのびる）
                pose.pushPose();
                pose.translate(0, 0.02, 0);
                FxDraw.ring(pose.last(), glow, 0.6F, 0.75F, accent, 70, 15);
                LastArchivistRenderer.arc(pose, glow, 0.62F, 0.73F, -90, -90 + 360 * android.chargeFraction(), 0xFFFFFF, 190);
                pose.popPose();
            }
            case RuinAndroid.STATE_CHARGING -> {
                // 体を下から上へなぞる充電の輪（満ちるほど明るい）
                float charge = android.chargeFraction();
                pose.pushPose();
                for (int i = 0; i < 2; i++) {
                    float phase = ((age * 0.04F) + i * 0.5F) % 1F;
                    pose.pushPose();
                    pose.translate(0, phase * 1.1F, 0);
                    int alpha = (int) ((80 + 140 * charge) * Mth.sin(Mth.PI * phase));
                    FxDraw.ring(pose.last(), glow, 0.42F, 0.55F, accent, alpha, alpha / 3);
                    pose.popPose();
                }
                pose.translate(0, 0.02, 0);
                FxDraw.ring(pose.last(), glow, 0.6F, 0.75F, accent, 90, 20);
                LastArchivistRenderer.arc(pose, glow, 0.62F, 0.73F, -90, -90 + 360 * charge, 0xFFFFFF, 200);
                pose.popPose();
            }
            default -> {
            }
        }
    }

    /** 刃の軌跡（右後ろから左前への三日月。少し斜め）。 */
    private void slashTrail(RuinAndroid android, float partialTick, PoseStack pose, VertexConsumer glow, float t, float fast,
                            float radius, float amount) {
        float u = 1 - (1 - Mth.clamp(t / fast, 0, 1)) * (1 - Mth.clamp(t / fast, 0, 1));
        float fade = 1 - Mth.clamp((t - fast) / 4, 0, 1);
        if (fade <= 0) {
            return;
        }
        float head = Mth.lerp(u, 120 * amount, -55 * amount);
        float tail = Math.min(120 * amount, head + 150 * amount);
        pose.pushPose();
        bodySpace(android, partialTick, pose);
        pose.translate(0, 1.0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-14));
        FxDraw.crescent(pose.last(), glow, tail, head, radius, 0.6F * amount, variant.accent, (int) (180 * fade));
        FxDraw.crescent(pose.last(), glow, tail, head, radius, 0.18F * amount, 0xFFFFFF, (int) (235 * fade));
        pose.popPose();
    }

    /** 突進の残像（後ろへ3つ、だんだん薄く）。 */
    private void renderAfterimages(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers, float strength) {
        Vec3 v = android.getDeltaMovement().multiply(1, 0, 1);
        Vec3 dir = v.lengthSqr() > 1e-4 ? v.normalize() : Vec3.directionFromRotation(0, android.yBodyRot);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(variant.texture));
        for (int i = 1; i <= 3; i++) {
            pose.pushPose();
            pose.translate(-dir.x * 0.55 * i, 0, -dir.z * 0.55 * i);
            modelSpace(android, partialTick, pose);
            int alpha = (int) (strength * (110 - 30 * i));
            LegacyRender.model(model, pose, vc, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb(alpha, mix(variant.accent, 0xFFFFFF, 0.25F)));
            pose.popPose();
        }
    }

    /**
     * 足し合わせの光の箱（RenderType.lightning 用）。各面を表裏の両方の向きで描くので、どの向きから見ても、
     * 面の向き（裏向きの面を描かない設定）で消えることがない。
     */
    static void glowBox(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int alpha) {
        PoseStack.Pose p = pose.last();
        float[][][] faces = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
        };
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(p.pose(), f[i][0], f[i][1], f[i][2]).color(r, g, b, alpha).endVertex();
            }
            for (int i = 3; i >= 0; i--) {
                vc.vertex(p.pose(), f[i][0], f[i][1], f[i][2]).color(r, g, b, alpha).endVertex();
            }
        }
    }

    static int mix(int a, int b, float k) {
        return FxDraw.lerpRgb(a, b, k);
    }

    // ------------------------------------------------------------------ 重ねる層

    /** 光る線と目（暗くても光る）。充電切れ・充電中・印刷中は消える。 */
    final class Glow extends RenderLayer<RuinAndroid, AndroidModel> {
        Glow(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int state = android.state();
            if (state == RuinAndroid.STATE_LOW_POWER || android.printProgress(partialTick) < 0.95F) {
                return;
            }
            int alpha = 255;
            if (state == RuinAndroid.STATE_CHARGING || state == RuinAndroid.STATE_RESTING) {
                alpha = (int) (70 + 60 * Mth.sin(ageInTicks * 0.15F));
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(variant.glow));
            LegacyRender.model(getParentModel(), pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY, argb(alpha, 0xFFFFFF));
        }
    }

    /**
     * ソーラー充電: 空へ掲げた左手の上に、3枚の羽根のソーラーパネルが広がる（両脇の2枚が、たたまれた状態から開く）。
     * 濃い紺のセルに銀の枠と格子。パネルから腕へ、光が流れ落ちる。
     */
    final class Solar extends RenderLayer<RuinAndroid, AndroidModel> {
        Solar(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            int state = android.state();
            if (state != RuinAndroid.STATE_SOLAR && state != RuinAndroid.STATE_SOLAR_FOLD) {
                return;
            }
            AndroidModel m = getParentModel();
            float t = android.stateTime(partialTick);
            float open;
            float rise;
            if (state == RuinAndroid.STATE_SOLAR) {
                open = Mth.clamp((t - 6) / 14, 0, 1);
                rise = Mth.clamp(t / 8, 0, 1);
            } else {
                // たたむ: 羽根を閉じてから、手の中へ縮めて消す
                open = 1 - Mth.clamp(t / 8, 0, 1);
                rise = 1 - Mth.clamp((t - 7) / 6, 0, 1);
                if (rise <= 0) {
                    return;
                }
            }
            open = open * open * (3 - 2 * open);
            pose.pushPose();
            m.toLeftHand(pose);
            // 手の先（体の大きさで縮んでいるので、長さは SCALE で割る）
            pose.translate(0, (0.12F + 0.1F * rise) / SCALE, 0);
            float s = (0.35F + 0.65F * rise) / SCALE;
            pose.scale(s, s, s);
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            leaf(pose, body, light);
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 0.25F, 0, 0);
                // たたまれた羽根（下に180度折り返し）から、水平に開く
                pose.mulPose(Axis.ZP.rotationDegrees(side * 180 * (1 - open)));
                pose.translate(side * 0.25F, 0, 0);
                leaf(pose, body, light);
                pose.popPose();
            }
            // 支柱
            FxDraw.box(pose, body, -0.03F, -0.2F, -0.03F, 0.03F, 0, 0.03F, light, 0xB8BEC8, 255);
            // パネルから腕へ流れ落ちる光
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            float flow = (age * 0.05F) % 1F;
            glowBox(pose, fx, -0.02F, -0.2F - flow * 0.6F, -0.02F, 0.02F, -0.1F - flow * 0.6F, 0.02F, variant.accent, (int) (180 * open));
            glowBox(pose, fx, -0.76F * open, 0.026F, -0.19F, 0.76F * open, 0.032F, 0.19F, variant.accent,
                    (int) (35 + 25 * Mth.sin(age * 0.1F)));
            pose.popPose();
        }

        /** 羽根1枚（中心が原点、幅 0.5・奥行き 0.36、上が空を向く）。 */
        private void leaf(PoseStack pose, VertexConsumer vc, int light) {
            float w = 0.25F;
            float d = 0.18F;
            FxDraw.box(pose, vc, -w, 0, -d, w, 0.025F, d, light, 0x9AA2B0, 255);
            FxDraw.box(pose, vc, -w + 0.02F, 0.024F, -d + 0.02F, w - 0.02F, 0.03F, d - 0.02F, light, 0x1E2C55, 255);
            for (int i = 1; i < 4; i++) {
                float x = -w + 2 * w * i / 4;
                FxDraw.box(pose, vc, x - 0.004F, 0.03F, -d + 0.02F, x + 0.004F, 0.033F, d - 0.02F, light, 0x6C86B8, 255);
            }
            FxDraw.box(pose, vc, -w + 0.02F, 0.03F, -0.004F, w - 0.02F, 0.033F, 0.004F, light, 0x6C86B8, 255);
        }
    }

    /** エンチャントした装甲板の光沢（防具と同じ光沢を、装甲の部品だけに重ねる）。 */
    final class ArmorGlint extends RenderLayer<RuinAndroid, AndroidModel> {
        ArmorGlint(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            if (android.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).hasFoil()) {
                getParentModel().renderArmor(pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /** 透きとおって光る刃。前触れのあいだは強く光る。 */
    /**
     * 光の鞭。柄の先から、動きに合わせた曲線で描く（待機: 床へ垂れる、振りかぶり: 頭の後ろ、打ち込み: 前へ伸びて波打つ、
     * 絡め取り: 相手まで張る、叩きつけ: 頭上で回してから床へ）。叩きつけの衝撃波も描く。
     */
    final class Whip extends RenderLayer<RuinAndroid, AndroidModel> {
        Whip(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.whipHandle == null || !(android instanceof WhipAndroid whip)) {
                return;
            }
            Vec3 camera = entityRenderDispatcher.camera.getPosition();
            pose.pushPose();
            m.toWhip(pose);
            org.joml.Vector3f h = pose.last().pose().transformPosition(new org.joml.Vector3f());
            pose.popPose();
            Vec3 hand = new Vec3(h.x, h.y, h.z);
            Vec3 origin = android.getPosition(partialTick).subtract(camera);
            float bodyYaw = Mth.rotLerp(partialTick, android.yBodyRotO, android.yBodyRot);
            Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw);
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 up = new Vec3(0, 1, 0);
            int state = android.state();
            float t = android.stateTime(partialTick);
            Vec3 end;
            Vec3 ctrl;
            float wave = 0;
            float tension = 0;
            switch (state) {
                case WhipAndroid.STATE_LASH_WINDUP -> {
                    float k = Mth.clamp(t / 6, 0, 1);
                    end = origin.add(up.scale(2.2 + 0.4 * k)).subtract(fwd.scale(1.2 + 0.6 * k));
                    ctrl = hand.add(up.scale(0.7)).subtract(fwd.scale(0.4));
                    wave = 0.12F;
                }
                case WhipAndroid.STATE_LASH, WhipAndroid.STATE_BIND -> {
                    Entity linked = whip.linked();
                    if (state == WhipAndroid.STATE_BIND && linked != null && t >= WhipAndroid.BIND_THROW) {
                        end = linked.getPosition(partialTick).add(0, linked.getBbHeight() * 0.55, 0).subtract(camera);
                        ctrl = hand.lerp(end, 0.5).add(0, -0.15, 0);
                        tension = 1;
                        wave = 0.05F;
                    } else {
                        float reach = t < 2 ? Mth.lerp(t / 2, 0.6F, (float) WhipAndroid.REACH)
                                : t < 4 ? (float) WhipAndroid.REACH : Mth.lerp(Mth.clamp((t - 4) / 5, 0, 1), (float) WhipAndroid.REACH, 1.2F);
                        end = origin.add(fwd.scale(reach)).add(up.scale(1.0));
                        ctrl = hand.lerp(end, 0.4).add(up.scale(0.6 * (1 - Mth.clamp(t / 3, 0, 1))));
                        wave = 0.35F * (1 - Mth.clamp(t / 8, 0, 1));
                    }
                }
                case WhipAndroid.STATE_CRACK -> {
                    float u = 1 - (1 - Mth.clamp(t / 2, 0, 1)) * (1 - Mth.clamp(t / 2, 0, 1));
                    double a = Math.toRadians(Mth.lerp(u, 70, -70));
                    Vec3 dir = fwd.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
                    end = origin.add(dir.scale(2.8)).add(up.scale(0.9));
                    ctrl = hand.lerp(end, 0.5).add(side.scale(0.4 * (1 - u)));
                    wave = 0.15F;
                }
                case WhipAndroid.STATE_SLAM_WINDUP -> {
                    double a = age * 1.4;
                    end = origin.add(up.scale(2.9)).add(fwd.scale(Math.cos(a) * 1.5)).add(side.scale(Math.sin(a) * 1.5));
                    ctrl = hand.add(up.scale(0.8));
                    wave = 0.08F;
                }
                case WhipAndroid.STATE_SLAM -> {
                    float u = 1 - (1 - Mth.clamp(t / 2, 0, 1)) * (1 - Mth.clamp(t / 2, 0, 1));
                    float back = Mth.clamp((t - 9) / 5, 0, 1);
                    end = origin.add(fwd.scale(Mth.lerp(u, -0.5F, 3.3F) * (1 - back) + 0.4 * back))
                            .add(up.scale(Mth.lerp(u, 2.9F, 0.05F)));
                    ctrl = hand.lerp(end, 0.5).add(up.scale(0.5 * (1 - u)));
                    wave = 0.1F;
                    shockwave(origin, fwd, side, t, age, buffers);
                }
                default -> {
                    // 床へ垂れて、少し後ろへ引きずる
                    Vec3 ground = new Vec3(hand.x, origin.y + 0.03, hand.z).subtract(fwd.scale(0.25));
                    float sway = Mth.sin(age * 0.08F) * 0.06F;
                    end = ground.subtract(fwd.scale(0.9)).add(side.scale(0.25 + sway));
                    ctrl = new Vec3(hand.x, origin.y + 0.15, hand.z).add(side.scale(sway));
                    wave = 0.03F;
                }
            }
            // 光は足し合わせで描く（深度を書かないので、交差した板どうしが隠し合ってちらつかない）
            drawWhip(hand, ctrl, end, side, wave, tension, age, buffers.getBuffer(RenderType.lightning()),
                    android.active() || RuinAndroid.charging(state) ? 1F : 0.4F);
        }

        /** 柄の先 from から end まで、二次曲線に波を足した鞭。 */
        private void drawWhip(Vec3 from, Vec3 ctrl, Vec3 end, Vec3 side, float wave, float tension, float age, VertexConsumer vc,
                              float brightness) {
            PoseStack.Pose id = new PoseStack().last();
            int n = 20;
            Vec3 prev = from;
            for (int i = 1; i <= n; i++) {
                float s = (float) i / n;
                Vec3 p = from.scale((1 - s) * (1 - s)).add(ctrl.scale(2 * (1 - s) * s)).add(end.scale(s * s));
                double w = wave * Math.sin(s * Math.PI * 3 - age * 0.9) * 4 * s * (1 - s) * (1 - tension * 0.7);
                p = p.add(side.scale(w)).add(0, w * 0.5, 0);
                float width = Mth.lerp(s, 0.075F, 0.035F);
                FxDraw.beam(id, vc, prev, p, width * 1.8F, variant.accent, (int) (150 * brightness));
                FxDraw.beam(id, vc, prev, p, width * 0.55F, 0xFFFFFF, (int) (235 * brightness));
                prev = p;
            }
        }

        /** 叩きつけの衝撃波: 床を前へ走る光の帯と、先端の光。 */
        private void shockwave(Vec3 origin, Vec3 fwd, Vec3 side, float t, float age, MultiBufferSource buffers) {
            if (t < 1) {
                return;
            }
            float front = (float) Math.min(WhipAndroid.WAVE_RANGE, 1.2 + (t - 1) * WhipAndroid.WAVE_SPEED);
            float fade = 1 - Mth.clamp((t - 10) / 4, 0, 1);
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            PoseStack.Pose id = new PoseStack().last();
            Vec3 a = origin.add(fwd.scale(1.0)).add(0, 0.05, 0);
            Vec3 b = origin.add(fwd.scale(front)).add(0, 0.05, 0);
            FxDraw.beam(id, vc, a, b, 0.55F, variant.accent, (int) (90 * fade));
            FxDraw.beam(id, vc, a, b, 0.14F, 0xFFFFFF, (int) (200 * fade));
            // 波の先: 立ち上がる光の板
            for (int k = -2; k <= 2; k++) {
                Vec3 base = b.add(side.scale(k * 0.25));
                double h = 0.5 + 0.35 * Math.abs(Math.sin(age * 1.7 + k));
                FxDraw.beam(id, vc, base, base.add(0, h * (1 - Math.abs(k) * 0.2), 0), 0.12F, variant.accent, (int) (200 * fade));
            }
        }
    }

    /** 光の刀身。柄の先から伸びる（横薙ぎで伸び、溜め突きと構えで強く光る）。足し合わせで描く。 */
    final class Saber extends RenderLayer<RuinAndroid, AndroidModel> {
        Saber(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.hilt == null) {
                return;
            }
            int state = android.state();
            float t = android.stateTime(partialTick);
            float len = 1.15F;
            float glow = 1F;
            if (state == SaberAndroid.STATE_SWEEP) {
                float out = t < 2 ? t / 2 : t < 5 ? 1 : 1 - Mth.clamp((t - 5) / 3, 0, 1);
                len = Mth.lerp(out, 1.15F, (float) SaberAndroid.SWEEP_REACH);
            } else if (state == SaberAndroid.STATE_THRUST_WINDUP || state == SaberAndroid.STATE_GUARD) {
                glow = 1.25F + 0.1F * Mth.sin(age * 0.25F);
            } else if (state == SaberAndroid.STATE_THRUST && t < 5) {
                len = 1.5F;
                glow = 1.3F;
            } else if (state == RuinAndroid.STATE_LOW_POWER) {
                len = 0.35F;
                glow = 0.35F;
            } else if (RuinAndroid.charging(state)) {
                len = 0.6F;
                glow = 0.5F;
            }
            // 柄の座標（+Y が刀身の向き。体の大きさ SCALE で縮んでいるので、ブロックの長さは SCALE で割る）で、
            // 四角い光の棒を2重（色の外側と白い芯）に描く。細い板と違って、どの向きから見ても途切れない
            pose.pushPose();
            m.toBlade(pose);
            m.hilt.translateAndRotate(pose);
            pose.translate(0, 3.5F / 16, 0);
            float l = len / SCALE;
            float outer = 0.045F * glow / SCALE;
            float core = 0.017F / SCALE;
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            int a = (int) Math.min(255, 140 * glow);
            int c = (int) (230 * Math.min(1, glow));
            glowBox(pose, vc, -outer, 0, -outer, outer, l, outer, variant.accent, a);
            glowBox(pose, vc, -core, 0, -core, core, l + 0.08F / SCALE, core, 0xFFFFFF, c);
            pose.popPose();
        }
    }

    final class Blade extends RenderLayer<RuinAndroid, AndroidModel> {
        Blade(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.blade == null) {
                return;
            }
            int state = android.state();
            float k = state == RuinAndroid.STATE_LOW_POWER ? 0.35F : state == BladeAndroid.STATE_DASH_WINDUP
                    || state == BladeAndroid.STATE_DASH ? 1F : 0.8F + 0.08F * Mth.sin(ageInTicks * 0.3F);
            pose.pushPose();
            m.toBlade(pose);
            m.blade.visible = true;
            LegacyRender.part(m.blade, pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(variant.texture)), FxDraw.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, argb((int) (255 * k), 0xFFFFFF));
            m.blade.visible = false;
            pose.popPose();
        }
    }
}
