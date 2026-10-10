package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * ホライズン・ウォーデン: 事象の地平線を守る機械の守護者（人の約1.6倍）。
 * <ul>
 *   <li>黒い躯体に、とさかのある兜と光る目の帯。肘と膝で曲がる手足、前腕の刃、背中の4枚の翼板</li>
 *   <li>胸に小さなブラックホールの炉心、頭の後ろに回る光輪。光る線は別の層（暗くても光る）で、フェーズで色が変わる（水色→赤）</li>
 *   <li>フェーズ1: 白い陶磁の胸当てと肩当て（金の縁）。フェーズ2: 外装が剥がれ、破片が体の周りを回る。翼板が開く</li>
 *   <li>フェーズ3: 宙に浮き、脚が垂れる。破片が速く回り、翼板が大きく開く。事象の地平線ビームをためて撃つ</li>
 * </ul>
 */
public class HorizonWardenRenderer extends MobRenderer<HorizonWarden, HorizonWardenRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("horizon_warden"), "main");
    static final float SCALE = 1.6F;
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/horizon_warden.png");
    private static final RenderType[] GLOW = {
            RenderType.eyes(Singulo.id("textures/entity/horizon_warden_glow_1.png")),
            RenderType.eyes(Singulo.id("textures/entity/horizon_warden_glow_2.png")),
            RenderType.eyes(Singulo.id("textures/entity/horizon_warden_glow_3.png")),
    };
    /** フェーズごとの光の色（光輪・炉心の輪・ビーム）。 */
    private static final int[] GLOW_RGB = {0x9BE6FF, 0x6FD8FF, 0xFF5050};

    public HorizonWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 1.0F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HorizonWarden warden) {
        return TEXTURE;
    }

    @Override
    protected void scale(HorizonWarden warden, PoseStack pose, float partialTick) {
        float k = assembled(warden.emergeProgress(partialTick));
        float d = warden.deathProgress(partialTick);
        if (d > DEATH_SWALLOW) {
            // 最後は胸の特異点へ吸い込まれて消える
            float b = Mth.clamp((d - DEATH_SWALLOW) / (DEATH_COLLAPSE - DEATH_SWALLOW), 0, 1);
            k *= 1 - b * b;
        }
        pose.scale(SCALE * k, SCALE * k, SCALE * k);
    }

    /** 倒されたとき: 特異点がふくらみ始める進み、体を吸い込み終える進み、一点につぶれる進み。 */
    static final float DEATH_GROW = 0.55F;
    static final float DEATH_SWALLOW = 0.75F;
    static final float DEATH_COLLAPSE = 0.9F;

    /** 倒されたときは倒れ込まず、膝をついて前へ傾き、震える。 */
    @Override
    protected void setupRotations(HorizonWarden warden, PoseStack pose, float bob, float bodyYaw, float partialTick) {
        if (!warden.isDeadOrDying()) {
            super.setupRotations(warden, pose, bob, bodyYaw, partialTick);
            return;
        }
        float d = warden.deathProgress(partialTick);
        float kneel = Mth.clamp(d / 0.2F, 0, 1);
        kneel = kneel * kneel * (3 - 2 * kneel);
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.translate(0, -0.45F * kneel, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-16 * kneel));
        if (d < DEATH_SWALLOW) {
            float t = warden.tickCount + partialTick;
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 2.7F) * (1.2F + 2.0F * d)));
        }
    }

    /** 出現の演出で、体が組み上がった割合（特異点が開くまでは 0、そのあと 1 まで）。 */
    static float assembled(float e) {
        if (e >= 0.75F) {
            return 1;
        }
        float b = Mth.clamp((e - 0.3F) / 0.45F, 0, 1);
        return 0.02F + 0.98F * b * b * (3 - 2 * b);
    }

    /** フェーズ3で浮いている高さ（ブロック）。 */
    static float hover(HorizonWarden warden, float partialTick) {
        if (warden.phase() < 3) {
            return 0;
        }
        return 0.55F + 0.12F * Mth.sin((warden.tickCount + partialTick) * 0.1F);
    }

    @Override
    public void render(HorizonWarden warden, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float e = warden.emergeProgress(partialTick);
        if (e < 1) {
            renderEmerge(warden, e, pose, buffers);
            if (e < 0.3F) {
                return;            // 特異点が開くまでは、まだ体はない
            }
        }
        float hover = hover(warden, partialTick);
        pose.pushPose();
        pose.translate(0, hover, 0);
        super.render(warden, yaw, partialTick, pose, buffers, light);
        if (!warden.isDeadOrDying()) {
            renderHaloAndCore(warden, partialTick, pose, buffers);
        }
        pose.popPose();
        if (warden.isDeadOrDying()) {
            renderDeath(warden, warden.deathProgress(partialTick), pose, buffers);
            return;
        }
        renderBeam(warden, partialTick, pose, buffers, hover);
        renderSweep(warden, partialTick, pose, buffers, hover);
        renderLaser(warden, partialTick, pose, buffers);
        renderTether(warden, partialTick, pose, buffers);
    }

    /**
     * 出現の演出（描画）。
     * <ol>
     *   <li>〜30%: 胸の高さに黒い特異点が開き、まわりを回る光の輪がすぼまっていく</li>
     *   <li>〜75%: 特異点が胸へ沈み、足元から頭へ走査の光の輪が昇りながら、体が組み上がる</li>
     *   <li>〜100%: 目覚めの閃光と、地面を走る衝撃波の輪</li>
     * </ol>
     */
    private void renderEmerge(HorizonWarden warden, float e, PoseStack pose, MultiBufferSource buffers) {
        int rgb = glowRgb(warden);
        float t = warden.tickCount;
        org.joml.Quaternionf camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        pose.pushPose();
        pose.translate(0, 2.2, 0);
        if (e < 0.75F) {
            float open = Mth.clamp(e / 0.3F, 0, 1);
            float sink = Mth.clamp((e - 0.3F) / 0.45F, 0, 1);
            float r = (0.15F + 0.55F * open) * (1 - sink);
            if (r > 0.01F) {
                FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, 0);
                // 別の描き方の入れ物を取ると前の入れ物は閉じられるので、光の入れ物は取り直す
                glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                FxDraw.billboard(pose, glow, camera, r * 5 + 0.5F, rgb, (int) (140 * open));
            }
            FxDraw.gyroRings(pose, glow, 3.0F - 2.0F * open + 0.6F * sink, 0.06F, t * (4 + 10 * open), rgb, (int) (90 + 140 * open));
        }
        pose.popPose();
        if (e >= 0.3F && e < 0.75F) {
            float b = (e - 0.3F) / 0.45F;
            for (int k = 0; k < 2; k++) {
                pose.pushPose();
                pose.translate(0, 4.4F * b - k * 0.35F, 0);
                FxDraw.ring(pose.last(), glow, 1.5F - k * 0.2F, 2.1F - k * 0.2F, k == 0 ? rgb : 0xFFFFFF, 220, 0);
                pose.popPose();
            }
        }
        if (e >= 0.75F) {
            float c = (e - 0.75F) / 0.25F;
            pose.pushPose();
            pose.translate(0, 0.05, 0);
            FxDraw.ring(pose.last(), glow, 1 + 9 * c, 1.7F + 9 * c, rgb, (int) (230 * (1 - c)), 0);
            pose.translate(0, 2.15, 0);
            FxDraw.billboard(pose, glow, camera, 7 * (1 - c) + 0.5F, 0xFFFFFF, (int) (220 * (1 - c)));
            pose.popPose();
        }
    }

    /**
     * 倒されたときの演出（描画）。
     * <ol>
     *   <li>〜55%: 膝をつき、震えながら胸の炉心が明滅する</li>
     *   <li>〜90%: 胸の特異点がふくらみ、光の輪がすぼまりながら体を吸い込んでいく</li>
     *   <li>〜100%: 一点につぶれ、閃光と地面を走る衝撃波の輪</li>
     * </ol>
     */
    private void renderDeath(HorizonWarden warden, float d, PoseStack pose, MultiBufferSource buffers) {
        int rgb = glowRgb(warden);
        float t = warden.tickCount;
        org.joml.Quaternionf camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float kneel = Mth.clamp(d / 0.2F, 0, 1);
        pose.pushPose();
        pose.translate(0, 2.2 - 0.75 * kneel, 0);
        if (d < DEATH_GROW) {
            // 明滅する炉心
            if (((int) (t * 3)) % 4 != 0) {
                VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                FxDraw.billboard(pose, glow, camera, 0.8F + 0.3F * Mth.sin(t * 1.3F), rgb, 200);
            }
        } else if (d < DEATH_COLLAPSE) {
            float g = (d - DEATH_GROW) / (DEATH_COLLAPSE - DEATH_GROW);
            float r = 0.2F + 1.4F * (g < 0.7F ? g / 0.7F : 1 - (g - 0.7F) / 0.3F * 0.85F);
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, 0);
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            FxDraw.billboard(pose, glow, camera, r * 3.2F, rgb, 170);
            FxDraw.gyroRings(pose, glow, r * 2.4F + 2.5F * (1 - g), 0.06F, t * (6 + 20 * g), rgb, 220);
            GravitationalLensing.add(warden.position().add(0, 2.2 - 0.75 * kneel, 0), r * 2.5F, 0.8F);
        } else {
            float c = (d - DEATH_COLLAPSE) / (1 - DEATH_COLLAPSE);
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            FxDraw.billboard(pose, glow, camera, 1 + 9 * c, 0xFFFFFF, (int) (255 * (1 - c)));
            FxDraw.billboard(pose, glow, camera, 2 + 13 * c, rgb, (int) (200 * (1 - c)));
            pose.translate(0, -(2.2 - 0.75 * kneel) + 0.05, 0);
            FxDraw.ring(pose.last(), glow, 1 + 12 * c, 1.8F + 12 * c, rgb, (int) (230 * (1 - c)), 0);
        }
        pose.popPose();
    }

    /** 薙ぎ払い: 振り抜く間、刃の軌跡に大きな三日月が走り、薄れていく。 */
    private void renderSweep(HorizonWarden warden, float partialTick, PoseStack pose, MultiBufferSource buffers, float hover) {
        float st = warden.sweepTicks() - partialTick;
        if (st <= 0 || st > HorizonWarden.SWEEP_STRIKE) {
            return;
        }
        float p = 1 - st / HorizonWarden.SWEEP_STRIKE;
        float bodyYaw = Mth.rotLerp(partialTick, warden.yBodyRotO, warden.yBodyRot);
        float half = (float) HorizonWarden.SWEEP_ARC / 2;
        float lead = half - (float) HorizonWarden.SWEEP_ARC * Math.min(1, p * 1.6F);
        int alpha = (int) (235 * (1 - p * p));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.translate(0, 2.0 + hover, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-12));
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        FxDraw.crescent(pose.last(), glow, half, lead, 4.3F, 2.4F, glowRgb(warden), (int) (alpha * 0.75F));
        FxDraw.crescent(pose.last(), glow, half, lead, 4.5F, 0.9F, 0xFFFFFF, alpha);
        pose.popPose();
    }

    /** 胸のレーザー: 白い芯と、フェーズの色のにじみ（ちらつく）。 */
    private void renderLaser(HorizonWarden warden, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (warden.laserTicks() <= 0) {
            return;
        }
        Vec3 base = warden.getPosition(partialTick);
        Vec3 from = warden.corePosition().subtract(warden.position());
        Vec3 to = warden.laserEnd().subtract(base);
        float t = warden.tickCount + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        FxDraw.beam(pose.last(), glow, from, to, 0.24F + 0.04F * Mth.sin(t * 3), glowRgb(warden), 140);
        FxDraw.beam(pose.last(), glow, from, to, 0.07F, 0xFFFFFF, 255);
    }

    /** 重力の手: 右手から持ち上げている相手へ、紫の光の紐。 */
    private void renderTether(HorizonWarden warden, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (!warden.gripHolding()) {
            return;
        }
        net.minecraft.world.entity.Entity target = warden.gripTarget();
        if (target == null) {
            return;
        }
        Vec3 base = warden.getPosition(partialTick);
        Vec3 from = warden.handPosition().subtract(warden.position());
        Vec3 to = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0).subtract(base);
        float t = warden.tickCount + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        FxDraw.beam(pose.last(), glow, from, to, 0.16F + 0.05F * Mth.sin(t * 1.7F), 0xB050FF, 150);
        FxDraw.beam(pose.last(), glow, from, to, 0.04F, 0xF0D8FF, 230);
    }

    private static int glowRgb(HorizonWarden warden) {
        return GLOW_RGB[Mth.clamp(warden.phase(), 1, 3) - 1];
    }

    /** 頭の後ろの光輪と、胸のブラックホールの炉心。 */
    private void renderHaloAndCore(HorizonWarden warden, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float t = warden.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, warden.yBodyRotO, warden.yBodyRot);
        int rgb = glowRgb(warden);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        // 光輪: 頭の後ろに縦に立てて、ゆっくり回す
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.translate(0, 3.35, 0.42);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(t * (warden.phase() == 3 ? 4 : 1.5F)));
        FxDraw.ring(pose.last(), glow, 0.62F, 0.7F, 0xFFFFFF, 230, 230);
        FxDraw.ring(pose.last(), glow, 0.5F, 0.86F, rgb, 150, 0);
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 90));
            pose.translate(0.66, 0, 0);
            FxDraw.sphere(pose.last(), glow, 0.05F, rgb, FxDraw.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
        // 炉心: 胸の前の黒い球と、回る輪
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.translate(0, 2.2, -0.5);
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.13F, 0x000000, 0);
        pose.mulPose(Axis.XP.rotationDegrees(70));
        pose.mulPose(Axis.YP.rotationDegrees(t * 12));
        // 別の描き方（黒い球）を挟んだので、光る描き方を取り直す（前のものは閉じられている）
        FxDraw.ring(pose.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE)), 0.17F, 0.34F, rgb, 240, 0);
        pose.popPose();
        if (warden.phase() == 3) {
            // フェーズ3: 炉心の周りの景色が歪む
            Vec3 forward = Vec3.directionFromRotation(0, bodyYaw);
            Vec3 core = warden.getPosition(partialTick).add(0, 2.2 + hover(warden, partialTick), 0).add(forward.scale(0.5));
            GravitationalLensing.addDistortion(core, 0.3F, 0.9F, 1.0F);
        }
    }

    /** 事象の地平線ビーム: ためている間は細い狙いの線（固定すると太く明るく）、撃った瞬間は太い光の帯。 */
    private void renderBeam(HorizonWarden warden, float partialTick, PoseStack pose, MultiBufferSource buffers, float hover) {
        int charge = warden.beamCharge();
        int flash = warden.beamFlash();
        if (charge <= 0 && flash <= 0) {
            return;
        }
        float bodyYaw = Mth.rotLerp(partialTick, warden.yBodyRotO, warden.yBodyRot);
        Vec3 base = warden.getPosition(partialTick);
        Vec3 from = new Vec3(0, 2.2 + hover, 0).add(Vec3.directionFromRotation(0, bodyYaw).scale(0.5));
        Vec3 to = warden.beamEnd().subtract(base);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        float t = warden.tickCount + partialTick;
        if (flash > 0) {
            float k = flash / 8.0F;
            FxDraw.beam(pose.last(), glow, from, to, 1.1F * k, 0xFF4040, (int) (160 * k));
            FxDraw.beam(pose.last(), glow, from, to, 0.45F * k + 0.05F, 0xFFFFFF, (int) (255 * k));
            return;
        }
        boolean locked = charge <= HorizonWarden.BEAM_LOCK_TICKS;
        float progress = 1 - (float) charge / HorizonWarden.BEAM_CHARGE_TICKS;
        int alpha = locked ? 230 : (int) (60 + 120 * progress * (0.7F + 0.3F * Mth.sin(t * 2.1F)));
        FxDraw.beam(pose.last(), glow, from, to, locked ? 0.14F : 0.05F, locked ? 0xFFB0B0 : 0xFF3030, alpha);
    }

    @Override
    public boolean shouldRender(HorizonWarden warden, Frustum frustum, double x, double y, double z) {
        return warden.beamCharge() > 0 || warden.beamFlash() > 0 || warden.laserTicks() > 0 || warden.gripTicks() > 0
                || warden.sweepTicks() > 0 || super.shouldRender(warden, frustum, x, y, z);
    }

    /** 光る線（目の帯・継ぎ目・刃の縁・炉心）。暗いところでも光る。 */
    static final class Glow extends EyesLayer<HorizonWarden, Model> {
        Glow(RenderLayerParent<HorizonWarden, Model> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW[0];
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, HorizonWarden warden, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (warden.isDeadOrDying() && ((int) (ageInTicks * 2)) % 3 == 0) {
                return;                                   // 倒されたあとは、光る線がちらついて消えかける
            }
            VertexConsumer vc = buffers.getBuffer(GLOW[Mth.clamp(warden.phase(), 1, 3) - 1]);
            getParentModel().renderToBuffer(pose, vc, 0xF00000, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    // ------------------------------------------------------------------ モデル（テクスチャ 128×128。配置は tools/gen_data.py の WARDEN_PARTS と同じ）

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, -2, 0));
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -11, -2, 1, 4, 6), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-6, 0, -3.5F, 12, 8, 7)
                        .texOffs(40, 16).addBox(-4.5F, 8, -2.5F, 9, 6, 5),
                PartPose.offset(0, -2, 0));
        body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(70, 16).addBox(-2, 2, -4.5F, 4, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(0, 34).addBox(-6.5F, 0.5F, -4.6F, 13, 7, 1),
                PartPose.ZERO);
        float[][] vanes = {{-2.5F, 0.35F}, {2.5F, -0.35F}, {-4.5F, 0.75F}, {4.5F, -0.75F}};
        for (int i = 0; i < vanes.length; i++) {
            body.addOrReplaceChild("vane_" + i, CubeListBuilder.create().texOffs(32, 64).addBox(-0.5F, -13, -1, 1, 14, 2),
                    PartPose.offsetAndRotation(vanes[i][0], i < 2 ? 2 : 3, 3.5F, 0.3F, 0, vanes[i][1]));
        }
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            String n = right ? "right" : "left";
            float s = right ? -1 : 1;
            CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 48);
            CubeListBuilder forearm = CubeListBuilder.create().texOffs(24, 48);
            CubeListBuilder blade = CubeListBuilder.create().texOffs(52, 48);
            CubeListBuilder pad = CubeListBuilder.create().texOffs(0, 64);
            CubeListBuilder thigh = CubeListBuilder.create().texOffs(64, 48);
            CubeListBuilder shin = CubeListBuilder.create().texOffs(88, 48);
            if (!right) {
                arm.mirror();
                forearm.mirror();
                blade.mirror();
                pad.mirror();
                thigh.mirror();
                shin.mirror();
            }
            PartDefinition a = root.addOrReplaceChild(n + "_arm",
                    arm.addBox(right ? -5 : 0, -1, -2.5F, 5, 8, 5), PartPose.offset(6 * s, -1, 0));
            a.addOrReplaceChild(n + "_pad", pad.addBox(right ? -6 : -1, -4, -3.5F, 7, 4, 7), PartPose.ZERO);
            PartDefinition f = a.addOrReplaceChild(n + "_forearm", forearm.addBox(-3, 0, -3, 6, 9, 6),
                    PartPose.offset(2.5F * s, 7, 0));
            f.addOrReplaceChild(n + "_blade", blade.addBox(right ? -4 : 3, -2, -1, 1, 12, 3), PartPose.ZERO);
            PartDefinition th = root.addOrReplaceChild(n + "_thigh", thigh.addBox(-2.5F, 0, -2.5F, 5, 6, 5),
                    PartPose.offset(3 * s, 12, 0));
            th.addOrReplaceChild(n + "_shin", shin.addBox(-3, 0, -3, 6, 6, 6), PartPose.offset(0, 6, 0));
        }
        for (int i = 0; i < HorizonWarden.SHARDS; i++) {
            root.addOrReplaceChild("shard_" + i, CubeListBuilder.create().texOffs(44, 64).addBox(-2.5F, -3, -0.5F, 5, 6, 1),
                    PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static class Model extends HierarchicalModel<HorizonWarden> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart body;
        private final ModelPart chestPlate;
        private final ModelPart[] vanes = new ModelPart[4];
        private final ModelPart[] arms = new ModelPart[2];
        private final ModelPart[] forearms = new ModelPart[2];
        private final ModelPart[] pads = new ModelPart[2];
        private final ModelPart[] thighs = new ModelPart[2];
        private final ModelPart[] shins = new ModelPart[2];
        private final ModelPart[] shards = new ModelPart[HorizonWarden.SHARDS];

        public Model(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.chestPlate = body.getChild("chest_plate");
            for (int i = 0; i < 4; i++) {
                vanes[i] = body.getChild("vane_" + i);
            }
            String[] names = {"right", "left"};
            for (int i = 0; i < 2; i++) {
                arms[i] = root.getChild(names[i] + "_arm");
                pads[i] = arms[i].getChild(names[i] + "_pad");
                forearms[i] = arms[i].getChild(names[i] + "_forearm");
                thighs[i] = root.getChild(names[i] + "_thigh");
                shins[i] = thighs[i].getChild(names[i] + "_shin");
            }
            for (int i = 0; i < shards.length; i++) {
                shards[i] = root.getChild("shard_" + i);
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(HorizonWarden warden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            int phase = warden.phase();
            float t = ageInTicks;
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            body.xRot = 0.04F * Mth.sin(t * 0.06F);

            // 脚: 歩くと振る（膝は後ろへ曲がる）。浮いている間は垂らしてゆらす
            if (phase == 3) {
                for (int i = 0; i < 2; i++) {
                    thighs[i].xRot = 0.15F + 0.08F * Mth.sin(t * 0.1F + i);
                    shins[i].xRot = 0.35F + 0.06F * Mth.sin(t * 0.1F + i + 1);
                }
            } else {
                float swing = Mth.cos(limbSwing * 0.5F) * 0.8F * limbSwingAmount;
                thighs[0].xRot = swing;
                thighs[1].xRot = -swing;
                shins[0].xRot = Math.max(0, -swing) * 0.9F;
                shins[1].xRot = Math.max(0, swing) * 0.9F;
            }

            // 腕: 歩くと振り、前腕は少し曲げて構える。攻撃で右腕を振り下ろす
            float armSwing = Mth.cos(limbSwing * 0.5F) * 0.6F * limbSwingAmount;
            arms[0].xRot = -armSwing + Mth.sin(t * 0.05F) * 0.05F;
            arms[1].xRot = armSwing - Mth.sin(t * 0.05F) * 0.05F;
            forearms[0].xRot = -0.3F;
            forearms[1].xRot = -0.3F;
            float spread = phase >= 2 ? 0.25F : 0.05F;
            arms[0].zRot = spread;
            arms[1].zRot = -spread;
            if (attackTime > 0) {
                float a = Mth.sin(attackTime * Mth.PI);
                arms[0].xRot = -2.4F * a;
                forearms[0].xRot = -0.6F * a - 0.1F;
                body.yRot = -0.3F * a;
            }
            // 薙ぎ払い: 刃の右腕を外へ大きく振りかぶり、体をひねってから、前を横切るように振り抜く
            float st = warden.sweepTicks() - (ageInTicks - Mth.floor(ageInTicks));
            if (st > 0) {
                int strike = HorizonWarden.SWEEP_STRIKE;
                if (st > strike) {
                    float w = 1 - (st - strike) / (HorizonWarden.SWEEP_TICKS - strike);
                    arms[0].xRot = Mth.lerp(w, arms[0].xRot, -1.2F);
                    arms[0].zRot = Mth.lerp(w, arms[0].zRot, 1.5F);
                    forearms[0].xRot = Mth.lerp(w, -0.3F, -0.9F);
                    body.yRot = 0.6F * w;
                    head.yRot += 0.3F * w;
                } else {
                    float p = 1 - st / strike;
                    float e = 1 - (1 - p) * (1 - p) * (1 - p);
                    arms[0].xRot = -1.45F;
                    arms[0].zRot = Mth.lerp(e, 1.5F, -0.7F);
                    forearms[0].xRot = Mth.lerp(e, -0.9F, 0);
                    body.yRot = Mth.lerp(e, 0.6F, -0.8F);
                }
                arms[1].zRot = -0.5F;
            }
            // 重力の手: 右手を前へ高く掲げる
            if (warden.gripTicks() > 0) {
                arms[0].xRot = -2.3F;
                arms[0].zRot = 0.15F;
                forearms[0].xRot = -0.2F;
                arms[1].xRot = -0.6F;
            }
            // 胸のレーザー: 両腕を後ろへ開き、胸を張る
            if (warden.laserTicks() > 0) {
                for (int i = 0; i < 2; i++) {
                    arms[i].xRot = 0.35F;
                    arms[i].zRot = i == 0 ? 0.95F : -0.95F;
                    forearms[i].xRot = -0.5F;
                }
                body.xRot = -0.12F;
            }
            if (warden.beamCharge() > 0 || warden.beamFlash() > 0) {
                // ビーム: 両腕を前へ突き出して、炉心を開く
                for (int i = 0; i < 2; i++) {
                    arms[i].xRot = -1.45F;
                    arms[i].zRot = i == 0 ? 0.35F : -0.35F;
                    forearms[i].xRot = 0;
                }
                head.xRot = Math.max(head.xRot, 0.1F);
            }

            // 背中の翼板: フェーズが進むほど開き、フェーズ3ではゆらめく
            float open = phase == 1 ? 1.0F : phase == 2 ? 1.9F : 2.4F;
            float flutter = phase == 3 ? 0.08F * Mth.sin(t * 0.3F) : 0.03F * Mth.sin(t * 0.08F);
            for (int i = 0; i < 4; i++) {
                float base = i < 2 ? 0.35F : 0.75F;
                float sign = i % 2 == 0 ? 1 : -1;
                vanes[i].zRot = sign * (base * open * 0.5F + flutter);
                vanes[i].xRot = 0.3F + (phase >= 2 ? 0.15F : 0);
            }

            // 外装: フェーズ1は胸当てと肩当て。2以降は剥がれて、破片が体の周りを回る
            boolean armored = phase == 1;
            chestPlate.visible = armored;
            pads[0].visible = armored;
            pads[1].visible = armored;
            float speed = phase == 3 ? 0.14F : 0.08F;
            for (int i = 0; i < shards.length; i++) {
                ModelPart s = shards[i];
                s.visible = !armored;
                float a = t * speed + i * Mth.TWO_PI / shards.length;
                s.x = Mth.cos(a) * 17;
                s.z = Mth.sin(a) * 17;
                s.y = 3 + Mth.sin(t * 0.1F + i * 1.7F) * 2.5F;
                s.yRot = -a + Mth.HALF_PI;
                s.xRot = 0.2F * Mth.sin(t * 0.07F + i);
            }
        }
    }
}
