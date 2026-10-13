package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
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
        BLADE("blade_android", BladeAndroid.ACCENT, AndroidModel.BLADE_LAYER);

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
        model.renderToBuffer(pose, vc, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb(flicker, mix(variant.accent, 0xFFFFFF, 0.3F)));
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
            case BladeAndroid.STATE_SPIN -> {
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0, 0.95, 0);
                float u = Mth.clamp(t / 7, 0, 1);
                float fade = 1 - Mth.clamp((t - 6) / 4, 0, 1);
                float head = 360 * (1 - (1 - u) * (1 - u));
                float from = Math.max(0, head - 200);
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
    private static void slashTrail(RuinAndroid android, float partialTick, PoseStack pose, VertexConsumer glow, float t, float fast,
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
        FxDraw.crescent(pose.last(), glow, tail, head, radius, 0.6F * amount, ((AndroidRenderer.Variant.BLADE.accent)), (int) (180 * fade));
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
            model.renderToBuffer(pose, vc, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb(alpha, mix(variant.accent, 0xFFFFFF, 0.25F)));
            pose.popPose();
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
            if (state == RuinAndroid.STATE_CHARGING) {
                alpha = (int) (70 + 60 * Mth.sin(ageInTicks * 0.15F));
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(variant.glow));
            getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY, argb(alpha, 0xFFFFFF));
        }
    }

    /** 透きとおって光る刃。前触れのあいだは強く光る。 */
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
            m.blade.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(variant.texture)), FxDraw.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, argb((int) (255 * k), 0xFFFFFF));
            m.blade.visible = false;
            pose.popPose();
        }
    }
}
