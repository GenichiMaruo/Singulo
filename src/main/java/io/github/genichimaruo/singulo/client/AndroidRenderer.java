package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AxeAndroid;
import io.github.genichimaruo.singulo.android.BitAndroid;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.GunnerAndroid;
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
        BLADE("blade_android", BladeAndroid.ACCENT, AndroidModel.BLADE_LAYER, false),
        WHIP("whip_android", WhipAndroid.ACCENT, AndroidModel.WHIP_LAYER, true),
        SABER("saber_android", SaberAndroid.ACCENT, AndroidModel.SABER_LAYER, false),
        AXE("axe_android", AxeAndroid.ACCENT, AndroidModel.AXE_LAYER, false),
        BIT("bit_android", BitAndroid.ACCENT, AndroidModel.BIT_LAYER, true),
        GUNNER("gunner_android", GunnerAndroid.ACCENT, AndroidModel.GUNNER_LAYER, false);

        final ResourceLocation texture;
        final ResourceLocation glow;
        final int accent;
        final ModelLayerLocation layer;
        /** 長い房とサイドテールが体の右（tools/androids.py の mirror と同じ）。 */
        final boolean mirror;

        Variant(String name, int accent, ModelLayerLocation layer, boolean mirror) {
            this.texture = Singulo.id("textures/entity/" + name + ".png");
            this.glow = Singulo.id("textures/entity/" + name + "_glow.png");
            this.accent = accent;
            this.layer = layer;
            this.mirror = mirror;
        }
    }

    private final Variant variant;
    /** 今描いている個体の、体の原点の変形（銃口の位置を、体の原点からの位置に直すため）。 */
    private final org.joml.Matrix4f base = new org.joml.Matrix4f();
    /** 銃を描いたときの、銃口の位置（体の原点から。光弾・光線をここから出す）と、それを描いた個体の番号。 */
    private Vec3 muzzle = Vec3.ZERO;
    /** そのときの銃身の向き（体の原点の座標で、長さ 1）。 */
    private Vec3 muzzleDir = Vec3.ZERO;
    private int muzzleOf = -1;

    /** 描いた銃の銃口の位置（体の原点から）。まだ描いていなければ fallback。 */
    private Vec3 muzzle(RuinAndroid android, Vec3 fallback) {
        return muzzleOf == android.getId() ? muzzle : fallback;
    }

    /**
     * 描いた銃口から、銃身の向きへ length 進んだ点（体の原点から）。予告の線を、描いた銃とぴったりそろえるため。
     * まだ描いていなければ fallback。
     */
    private Vec3 alongBarrel(RuinAndroid android, double length, Vec3 fallback) {
        return muzzleOf == android.getId() ? muzzle.add(muzzleDir.scale(length)) : fallback;
    }

    /** 今の変形での点 (x, y, z) を体の原点からの位置として、向き (dx, dy, dz) を銃身の向きとして覚える。 */
    private void rememberMuzzle(PoseStack pose, RuinAndroid android, float x, float y, float z, float dx, float dy, float dz) {
        org.joml.Matrix4f toBody = new org.joml.Matrix4f(base).invert().mul(pose.last().pose());
        org.joml.Vector3f v = toBody.transformPosition(new org.joml.Vector3f(x, y, z));
        org.joml.Vector3f d = toBody.transformDirection(new org.joml.Vector3f(dx, dy, dz)).normalize();
        muzzle = new Vec3(v.x, v.y, v.z);
        muzzleDir = new Vec3(d.x, d.y, d.z);
        muzzleOf = android.getId();
    }

    public AndroidRenderer(EntityRendererProvider.Context context, Variant variant) {
        super(context, new AndroidModel(context.bakeLayer(variant.layer)), 0.35F);
        this.variant = variant;
        addLayer(new Glow(this));
        addLayer(new Blade(this));
        addLayer(new Whip(this));
        addLayer(new Saber(this));
        addLayer(new Axe(this));
        addLayer(new Dagger(this));
        addLayer(new StowedBits(this));
        addLayer(new Gun(this));
        addLayer(new Solar(this));
        addLayer(new Pickaxe(this));
        addLayer(new Tanks(this));
        addLayer(new Thrusters(this));
        addLayer(new SwimRing(this));
        addLayer(new Accessories(this));
        addLayer(new Blink(this));
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

    /**
     * 無重力や水の中で飛んでいる間は、進む向きへ体を少し傾ける（前へ進めば前のめり、横へ進めばそちらへ）。
     * 水上であお向けに浮かんで休むときは、体を寝かせて、波でゆっくり揺らす。
     */
    @Override
    protected void setupRotations(RuinAndroid android, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(android, pose, bob, yBodyRot, partialTick, scale);
        if (android.state() == RuinAndroid.STATE_FLOAT_REST) {
            float k = Mth.clamp(android.stateTime(partialTick) / 14, 0, 1);
            k = k * k * (3 - 2 * k);
            float age = android.tickCount + partialTick;
            float mid = android.getBbHeight() * 0.5F;
            pose.translate(0, mid, 0);
            pose.mulPose(Axis.XP.rotationDegrees(84 * k + 2 * Mth.sin(age * 0.07F)));      // 上が後ろへ: あお向け
            pose.mulPose(Axis.ZP.rotationDegrees(4 * Mth.sin(age * 0.05F) * k));
            pose.translate(0, -mid, 0);
            return;
        }
        float open = android.thrusterOpen(partialTick);
        if (open <= 0) {
            return;
        }
        double vx = android.getX() - android.xo, vz = android.getZ() - android.zo;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double forward = -vx * Mth.sin(yaw) + vz * Mth.cos(yaw);
        double side = vx * Mth.cos(yaw) + vz * Mth.sin(yaw);
        float pitch = -(float) Mth.clamp(forward * 70, -10, 18) * open;    // 上を前（-Z）へ: 前のめり
        float roll = (float) Mth.clamp(side * 50, -10, 10) * open;
        float mid = android.getBbHeight() * 0.5F;
        pose.translate(0, mid, 0);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(0, -mid, 0);
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
        base.set(pose.last().pose());
        muzzleOf = -1;
        super.render(android, yaw, partialTick, pose, buffers, light);
        renderMoves(android, partialTick, pose, buffers);
        if (android instanceof BitAndroid bit) {
            renderBits(bit, partialTick, pose, buffers, light);
        }
        if (android instanceof GunnerAndroid gun && gun.shotFlash() > 0) {
            // 小型銃の光弾の跡（撃った直後だけ、だんだん細く薄く）と、銃口の光
            float k = Mth.clamp((gun.shotFlash() - partialTick) / GunnerAndroid.FLASH, 0, 1);
            Vec3 base = android.getPosition(partialTick);
            Vec3 a = muzzle(android, gun.shotFrom().subtract(base)), b = gun.shotAt().subtract(base);
            FxDraw.beam(pose.last(), buffers.getBuffer(GlowCore.TYPE), a, b, 0.02F + 0.02F * k, 0xFFFFFF, (int) (230 * k));
            FxDraw.beam(pose.last(), buffers.getBuffer(RenderType.lightning()), a, b, 0.05F + 0.06F * k, variant.accent, (int) (150 * k));
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            pose.pushPose();
            pose.translate(a.x, a.y, a.z);
            FxDraw.billboard(pose, glow, entityRenderDispatcher.cameraOrientation(), 0.12F + 0.25F * k, variant.accent, (int) (220 * k));
            pose.popPose();
        }
        renderWorkIcon(android, partialTick, pose, buffers, light);
        if (android.napping() && android.printed()) {
            renderSleep(android, partialTick, pose, buffers, light);
        }
    }

    /** 作業中の今していることの印（頭の上に、小さく回る物の絵）。測量メガネを掛けているときだけ見える。設定 androidWorkIcons で消せる。 */
    /** 昼寝: 頭の上に、小さな Z が3つ、ずれて浮かび上がっては消える（いつもこちらを向く）。 */
    private void renderSleep(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        float age = android.tickCount + partialTick;
        for (int i = 0; i < 3; i++) {
            float phase = ((age / 50F) + i / 3F) % 1F;             // 0〜1 で上へ
            int alpha = (int) (255 * Math.min(1, Math.min(phase * 4, (1 - phase) * 3)));
            if (alpha < 8) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.15F + phase * 0.35F, android.getBbHeight() + 0.05F + phase * 0.7F, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            float scale = 0.012F + phase * 0.012F;
            pose.scale(scale, -scale, scale);
            mc.font.drawInBatch("Z", -2.5F, 0, (alpha << 24) | 0xE8F0FF, false, pose.last().pose(), buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
        }
    }

    private void renderWorkIcon(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int act = android.activity();
        var viewer = net.minecraft.client.Minecraft.getInstance().player;
        boolean stalled = android.stall() != RuinAndroid.STALL_NONE;
        if (act == RuinAndroid.ACT_NONE && !stalled || viewer == null || !io.github.genichimaruo.singulo.android.work.SurveyGlassesItem.wearing(viewer)
                || io.github.genichimaruo.singulo.generated.ClientConfig.SPEC.isLoaded()
                && !io.github.genichimaruo.singulo.generated.ClientConfig.ANDROID_WORK_ICONS.get()) {
            return;
        }
        // 止まっている理由があるときは、作業の印の代わりに、立ち入り禁止の印（理由はアンドロイドの画面に出る）
        net.minecraft.world.item.ItemStack icon = new net.minecraft.world.item.ItemStack(stalled ? net.minecraft.world.item.Items.BARRIER : switch (act) {
            case RuinAndroid.ACT_FETCH -> net.minecraft.world.item.Items.CHEST;
            case RuinAndroid.ACT_CRAFT -> net.minecraft.world.item.Items.CRAFTING_TABLE;
            case RuinAndroid.ACT_PROCESS -> net.minecraft.world.item.Items.FURNACE;
            case RuinAndroid.ACT_MINE -> net.minecraft.world.item.Items.DIAMOND_PICKAXE;
            case RuinAndroid.ACT_DELIVER -> net.minecraft.world.item.Items.CHEST_MINECART;
            case RuinAndroid.ACT_RETURN -> net.minecraft.world.item.Items.BARREL;
            case RuinAndroid.ACT_WAIT -> net.minecraft.world.item.Items.CLOCK;
            case RuinAndroid.ACT_CHARGE -> net.minecraft.world.item.Items.REDSTONE;
            case RuinAndroid.ACT_FIGHT -> net.minecraft.world.item.Items.IRON_SWORD;
            case RuinAndroid.ACT_FARM -> net.minecraft.world.item.Items.WHEAT;
            case RuinAndroid.ACT_RANCH -> net.minecraft.world.item.Items.SHEARS;
            case RuinAndroid.ACT_FELL -> net.minecraft.world.item.Items.DIAMOND_AXE;
            case RuinAndroid.ACT_USE -> net.minecraft.world.item.Items.LEVER;
            default -> net.minecraft.world.item.Items.AIR;
        });
        if (icon.isEmpty()) {
            return;
        }
        float age = android.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, android.getBbHeight() + 0.45F + 0.04F * Mth.sin(age * 0.1F), 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 3));
        pose.scale(0.35F, 0.35F, 0.35F);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(icon, net.minecraft.world.item.ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, pose, buffers, android.level(), android.getId());
        pose.popPose();
    }

    /**
     * 透きとおって光る刃の描き方: entityTranslucentEmissive と同じだが、奥行きも書く。書かないと、あとから描かれるブロックの
     * 絵（チェストなど）が、手前にある刃の上に描かれてしまう。
     */
    private static final class DepthGlow extends RenderType {
        static final java.util.function.Function<ResourceLocation, RenderType> TYPE = net.minecraft.Util.memoize(texture -> create(
                "singulo_translucent_emissive_depth", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));

        private DepthGlow(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                          boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /**
     * 光の刃の芯（白い芯）の描き方: RenderType.lightning と同じだが、別の描き先にする。
     * lightning は描く前に面を遠い順に並べ替えるので、芯と外側の光を同じ描き先に入れると、芯より先に外側の面が描かれて
     * 奥行きを書き、中の芯が隠れることがある（見る角度で芯が消える）。芯をこの描き先で先に描き終えてから、外側を描く。
     */
    private static final class GlowCore extends RenderType {
        static final RenderType TYPE = create("singulo_glow_core", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setOutputState(WEATHER_TARGET)
                        .createCompositeState(false));

        private GlowCore(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                         boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /**
     * スラスターの炎の描き方: RenderType.lightning と同じく光を足して描くが、奥行きを書かない（書くと、あとから描かれる
     * 透ける物（後ろのリボンなど）が、炎の奥にあるとして消えてしまう）。円錐の内側の面も描く。
     */
    private static final class Exhaust extends RenderType {
        static final RenderType TYPE = create("singulo_exhaust", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setOutputState(WEATHER_TARGET)
                        .createCompositeState(false));

        private Exhaust(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                        boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /**
     * 光る部品の描き方: 陰をつけず、色そのままの明るさで描く（ふつうの体の描き方や entityTranslucentEmissive は、
     * 面の向きで暗くなるので、昼に光って見えない）。透けずに奥行きも書く。
     */
    private static final class Shine extends RenderType {
        static final RenderType TYPE = create("singulo_shine", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Shine(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                      boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
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
            case GunnerAndroid.STATE_RAIL_CHARGE -> {
                if (android instanceof GunnerAndroid gun) {
                    // 狙いの光線: 細く揺らぎながら、溜まるほど濃く太く。狙いが固まると白く点滅する
                    boolean locked = t >= GunnerAndroid.RAIL_CHARGE - GunnerAndroid.RAIL_LOCK;
                    float k = Mth.clamp(t / GunnerAndroid.RAIL_CHARGE, 0, 1);
                    Vec3 base = android.getPosition(partialTick);
                    int color = locked && ((int) (age * 0.8F)) % 2 == 0 ? 0xFFFFFF : accent;
                    VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
                    // 線は描いた銃身の向きへ引く（銃が狙いへ向き終わる前でも、銃と線がずれない）
                    Vec3 from = muzzle(android, gun.railFrom().subtract(base)), to = gun.railTo().subtract(base);
                    FxDraw.beam(pose.last(), fx, from, alongBarrel(android, to.distanceTo(from), to),
                            0.025F + 0.04F * k, color, (int) (70 + 120 * k));
                    glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                    pose.pushPose();
                    Vec3 at = muzzle(android, gun.railFrom().subtract(base));
                    pose.translate(at.x, at.y, at.z);
                    FxDraw.billboard(pose, glow, camera, 0.15F + 0.35F * k, accent, (int) (120 + 100 * k));
                    pose.popPose();
                }
            }
            case GunnerAndroid.STATE_RAIL_FIRE -> {
                if (android instanceof GunnerAndroid gun && t < 8) {
                    // 貫く光線: 太い水緑の外側と白い芯が、だんだん細く薄く消える
                    float k = 1 - t / 8;
                    Vec3 base = android.getPosition(partialTick);
                    Vec3 a = muzzle(android, gun.railFrom().subtract(base)), b = gun.railTo().subtract(base);
                    FxDraw.beam(pose.last(), buffers.getBuffer(GlowCore.TYPE), a, b, 0.07F + 0.12F * k, 0xFFFFFF, (int) (250 * k));
                    FxDraw.beam(pose.last(), buffers.getBuffer(RenderType.lightning()), a, b, 0.2F + 0.5F * k, accent, (int) (170 * k));
                    glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
                    for (Vec3 at : new Vec3[]{a, b}) {
                        pose.pushPose();
                        pose.translate(at.x, at.y, at.z);
                        FxDraw.billboard(pose, glow, camera, 0.4F + 0.9F * k, accent, (int) (200 * k));
                        FxDraw.billboard(pose, glow, camera, 0.2F + 0.4F * k, 0xFFFFFF, (int) (240 * k));
                        pose.popPose();
                    }
                }
            }
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
            case AxeAndroid.STATE_CLEAVE_WINDUP -> {
                // 右後ろへ引いた斧頭に光が集まる
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(-0.9, 1.15, 0.45);
                float k = Mth.clamp(t / AxeAndroid.CLEAVE_WINDUP, 0, 1);
                float pulse = 0.85F + 0.15F * Mth.sin(age * 1.8F);
                FxDraw.billboard(pose, glow, camera, (0.5F + 1.0F * k) * pulse, accent, 140);
                FxDraw.billboard(pose, glow, camera, (0.2F + 0.4F * k) * pulse, 0xFFFFFF, 230);
                pose.popPose();
            }
            case AxeAndroid.STATE_CLEAVE -> slashTrail(android, partialTick, pose, glow, t, 2.5F, (float) AxeAndroid.CLEAVE_REACH, 1.25F);
            case AxeAndroid.STATE_CHOP -> slashTrail(android, partialTick, pose, glow, t, 3, 1.5F, 0.7F);
            case AxeAndroid.STATE_SLAM_WINDUP, AxeAndroid.STATE_LEAP -> {
                // 頭上へ掲げた斧頭に光が集まる
                pose.pushPose();
                bodySpace(android, partialTick, pose);
                pose.translate(0, 2.35, 0.15);
                float k = state == AxeAndroid.STATE_LEAP ? 1 : Mth.clamp(t / AxeAndroid.SLAM_WINDUP, 0, 1);
                float pulse = 0.85F + 0.15F * Mth.sin(age * 2.0F);
                FxDraw.billboard(pose, glow, camera, (0.5F + 1.1F * k) * pulse, accent, 150);
                FxDraw.billboard(pose, glow, camera, (0.2F + 0.45F * k) * pulse, 0xFFFFFF, 235);
                pose.popPose();
                if (state == AxeAndroid.STATE_LEAP) {
                    renderAfterimages(android, partialTick, pose, buffers, 0.8F);
                }
            }
            case AxeAndroid.STATE_SLAM -> axeShockwave(android, partialTick, pose, buffers, t, age);
            case AxeAndroid.STATE_LEAP_WINDUP -> {
                // 足元に、踏み込む前の光の輪
                pose.pushPose();
                pose.translate(0, 0.03, 0);
                float k = Mth.clamp(t / AxeAndroid.LEAP_WINDUP, 0, 1);
                FxDraw.ring(pose.last(), glow, 0.5F * k, 0.8F * k, accent, (int) (170 * k), (int) (60 * k));
                pose.popPose();
            }
            case BitAndroid.STATE_STAB -> {
                if (t < 6) {
                    slashTrail(android, partialTick, pose, glow, t, 2, 1.1F, 0.45F);
                } else {
                    slashTrail(android, partialTick, pose, glow, t - 6, 2, 1.2F, 0.55F);
                }
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

    /** 斧の叩きつけの衝撃波: 体の前へまっすぐ、床を走る光の帯と、先で立ち上がる光の板、走った跡の割れ目の光。 */
    private void axeShockwave(RuinAndroid android, float partialTick, PoseStack pose, MultiBufferSource buffers, float t, float age) {
        if (t < 3) {
            return;
        }
        float front = (float) Math.min(AxeAndroid.SHOCKWAVE_LENGTH, 0.8 + (t - 3) * 2.2);
        float fade = 1 - Mth.clamp((t - 7) / 5, 0, 1);
        pose.pushPose();
        bodySpace(android, partialTick, pose);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose p = pose.last();
        Vec3 a = new Vec3(0, 0.05, -0.8);
        Vec3 b = new Vec3(0, 0.05, -front);
        FxDraw.beam(p, vc, a, b, 1.1F, variant.accent, (int) (90 * fade));
        FxDraw.beam(p, vc, a, b, 0.25F, 0xFFFFFF, (int) (200 * fade));
        // 割れ目: 帯にそってジグザグの細い光
        Vec3 prev = a;
        for (int i = 1; i <= 10; i++) {
            float s = i / 10F;
            Vec3 q = new Vec3(Mth.sin(i * 2.3F) * 0.25, 0.06, Mth.lerp(s, -0.8F, -front));
            FxDraw.beam(p, vc, prev, q, 0.08F, 0xFFE0C8, (int) (220 * fade));
            prev = q;
        }
        // 波の先: 立ち上がる光の板
        for (int k = -3; k <= 3; k++) {
            Vec3 base = new Vec3(k * 0.22, 0.05, -front);
            double h = 0.6 + 0.4 * Math.abs(Math.sin(age * 1.7 + k));
            FxDraw.beam(p, vc, base, base.add(0, h * (1 - Math.abs(k) * 0.18), 0), 0.14F, variant.accent, (int) (210 * fade));
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ ビット

    /** 背中のビットをしまう位置（体の向きの座標。背中の上、肩甲骨のあたり）。 */
    private static Vec3 stowedOffset(int i, float bodyYaw) {
        double side = (i - (BitAndroid.BITS - 1) / 2.0) * 0.12;
        double a = Math.toRadians(bodyYaw);
        Vec3 back = new Vec3(Math.sin(a), 0, -Math.cos(a));
        Vec3 right = new Vec3(Math.cos(a), 0, Math.sin(a));
        return back.scale(0.28).add(right.scale(side)).add(0, 1.2 + 0.12 * Math.abs(side), 0);
    }

    /**
     * 浮かんだビット（体の後ろ上に扇のように並ぶ）。紺の細長い菱形の本体に、菫の光の芯と、後ろの噴射の光。
     * 展開の途中は背中から飛び出していく。撃ったビットからは、相手へ光線（白い芯と水色の外側）が走る。
     */
    private void renderBits(BitAndroid bit, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int n = bit.deployed();
        int total = Math.max(n, bit.recallingFrom(partialTick));     // n より後ろは、背中へ戻っていく途中
        if (total <= 0 || !bit.printed()) {
            return;
        }
        float back = ease(bit.recallProgress(partialTick));
        float age = bit.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, bit.yBodyRotO, bit.yBodyRot);
        float out = 1;
        if (bit.state() == BitAndroid.STATE_DEPLOY) {
            out = ease(Mth.clamp((bit.stateTime(partialTick) - BitAndroid.DEPLOY / 2F) / 5, 0, 1));
        }
        Vec3[] at = new Vec3[total];
        float[] size = new float[total];
        for (int i = 0; i < total; i++) {
            Vec3 stowed = stowedOffset(i % BitAndroid.BITS, bodyYaw);
            Vec3 flying = bit.bitPosition(i, partialTick).subtract(bit.getPosition(partialTick));
            if (i < n) {
                at[i] = stowed.lerp(flying, out);
                size[i] = 1;
            } else {
                // 戻る: 少し上へ弧を描きながら背中の差し込み口へ吸い込まれ、小さくなる
                at[i] = flying.lerp(stowed, back).add(0, 0.35 * Mth.sin(Mth.PI * back), 0);
                size[i] = Mth.lerp(back, 1F, 0.8F);
            }
        }
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
        for (int i = 0; i < total; i++) {
            pose.pushPose();
            pose.translate(at[i].x, at[i].y, at[i].z);
            orientBit(pose, bit.bitDirection(i, partialTick));
            pose.scale(size[i], size[i], size[i]);
            bitBody(pose, solid, light);
            pose.popPose();
        }
        // 光の刃の白い芯を、別の描き先で先に描き終える（外側の光と同じ描き先だと、並べ替えで芯が隠れることがある）
        float blade = Mth.clamp(bit.slashTime(partialTick) / BitAndroid.SLASH_WINDUP, 0, 1);
        VertexConsumer core = buffers.getBuffer(GlowCore.TYPE);
        for (int i = 0; i < n; i++) {
            if (bit.bladeOut(i, partialTick)) {
                pose.pushPose();
                pose.translate(at[i].x, at[i].y, at[i].z);
                orientBit(pose, bit.bitDirection(i, partialTick));
                pose.scale(size[i], size[i], size[i]);
                glowBox(pose, core, -0.025F * blade, -0.01F, -0.26F - 0.62F * blade, 0.025F * blade, 0.01F, -0.23F, 0xFFFFFF, 240);
                pose.popPose();
            }
        }
        VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
        for (int i = 0; i < total; i++) {
            pose.pushPose();
            pose.translate(at[i].x, at[i].y, at[i].z);
            orientBit(pose, bit.bitDirection(i, partialTick));
            pose.scale(size[i], size[i], size[i]);
            if (i >= n) {
                // 戻る途中: 光の芯と、通り道に残る短い光の尾
                glowBox(pose, fx, -0.018F, -0.018F, -0.1F, 0.018F, 0.018F, 0.1F, variant.accent, (int) (220 * (1 - back * 0.5F)));
                pose.popPose();
                continue;
            }
            float pulse = 0.75F + 0.25F * Mth.sin(age * 0.4F + i * 1.3F);
            glowBox(pose, fx, -0.018F, -0.018F, -0.1F, 0.018F, 0.018F, 0.1F, variant.accent, (int) (200 * pulse));
            glowBox(pose, fx, -0.03F, -0.03F, 0.13F, 0.03F, 0.03F, 0.2F + 0.05F * pulse, variant.accent, (int) (120 * pulse));
            if (bit.bladeOut(i, partialTick)) {
                // 先端(-Z)に現れる光の刃。予備動作で伸び、飛び出す前に攻撃を知らせる（白い芯は上で描いた）。
                glowBox(pose, fx, -0.11F * blade, -0.022F, -0.26F - 0.65F * blade,
                        0.11F * blade, 0.022F, -0.23F, BitAndroid.ACCENT, 170);
            }
            pose.popPose();
        }
        // 光線（撃った直後だけ、だんだん細く薄く）
        int flash = bit.shotFlash();
        int shot = bit.shotBit();
        if (flash > 0 && shot < n) {
            float k = Mth.clamp((flash - partialTick) / BitAndroid.FLASH, 0, 1);
            Vec3 to = bit.shotAt().subtract(bit.getPosition(partialTick));
            PoseStack.Pose p = pose.last();
            FxDraw.beam(p, fx, bit.shotFrom().subtract(bit.getPosition(partialTick)), to, 0.16F * k + 0.04F, BitAndroid.BEAM, (int) (150 * k));
            FxDraw.beam(p, fx, bit.shotFrom().subtract(bit.getPosition(partialTick)), to, 0.05F * k + 0.015F, 0xFFFFFF, (int) (240 * k));
            pose.pushPose();
            pose.translate(at[shot].x, at[shot].y, at[shot].z);
            Quaternionf camera = entityRenderDispatcher.cameraOrientation();
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            FxDraw.billboard(pose, glow, camera, 0.35F * k + 0.1F, BitAndroid.BEAM, (int) (200 * k));
            pose.popPose();
        }
    }

    private static void orientBit(PoseStack pose, Vec3 direction) {
        if (direction.lengthSqr() < 1e-6) {
            return;
        }
        // モデルの前は-Z。ワールド方向へ合わせる（本体の向きから独立）。
        float yaw = (float) Math.atan2(-direction.x, -direction.z);
        float pitch = (float) Math.asin(Mth.clamp(direction.y, -1, 1));
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(pitch));
    }

    /** ビットの本体（向きの座標。前は -Z）: 細長い菱形を、太さの違う箱を重ねて作る。 */
    private static void bitBody(PoseStack pose, VertexConsumer vc, int light) {
        int navy = 0x2C345C, edge = 0x8C94B8;
        FxDraw.box(pose, vc, -0.035F, -0.035F, -0.12F, 0.035F, 0.035F, 0.12F, light, navy, 255);
        FxDraw.box(pose, vc, -0.022F, -0.022F, -0.2F, 0.022F, 0.022F, -0.12F, light, navy, 255);
        FxDraw.box(pose, vc, -0.01F, -0.01F, -0.26F, 0.01F, 0.01F, -0.2F, light, edge, 255);
        FxDraw.box(pose, vc, -0.07F, -0.006F, 0.02F, 0.07F, 0.006F, 0.12F, light, edge, 255);      // 横の小さな翼
        FxDraw.box(pose, vc, -0.028F, -0.028F, 0.12F, 0.028F, 0.028F, 0.15F, light, 0x1A1E34, 255);
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
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
                vc.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(r, g, b, alpha);
            }
            for (int i = 3; i >= 0; i--) {
                vc.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(r, g, b, alpha);
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
            if (state == RuinAndroid.STATE_CHARGING || state == RuinAndroid.STATE_RESTING || state == RuinAndroid.STATE_FLOAT_REST
                    || state == RuinAndroid.STATE_SEABED_REST) {
                alpha = (int) (70 + 60 * Mth.sin(ageInTicks * 0.15F));
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(variant.glow));
            getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY, argb(alpha, 0xFFFFFF));
            if (android instanceof GunnerAndroid) {
                // レールガンを溜めている間: 普段は光らない髪の筋と毛先が、溜まるほど明るく光る（撃ったあとは消えていく）
                float t = android.stateTime(partialTick);
                float k = state == GunnerAndroid.STATE_RAIL_CHARGE ? Mth.clamp(t / GunnerAndroid.RAIL_CHARGE, 0, 1)
                        : state == GunnerAndroid.STATE_RAIL_FIRE ? 1 - Mth.clamp(t / 10, 0, 1) : 0;
                if (k > 0) {
                    float pulse = 0.8F + 0.2F * Mth.sin(ageInTicks * (0.5F + 0.8F * k));
                    int level = (int) (255 * Math.min(1, (0.25F + 0.75F * k) * pulse));
                    getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GUNNER_CHARGE)), 0xF00000, OverlayTexture.NO_OVERLAY,
                            0xFF000000 | level << 16 | level << 8 | level);
                }
            }
        }
    }

    /** 射撃型がレールガンを溜めている間だけ重ねる、髪の光。 */
    private static final ResourceLocation GUNNER_CHARGE = Singulo.id("textures/entity/gunner_android_charge.png");

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

    /**
     * 採掘: 左手に持つツルハシ。プレイヤーと同じく、柄は前腕に直角（手から前へ）、頭は柄の先で腕と同じ向きに張り出す。
     * 柄は実体（濃い金属色）、頭はアンドロイドの色で光る（先が持ち手の方へ曲がった弧）。
     */
    final class Pickaxe extends RenderLayer<RuinAndroid, AndroidModel> {
        Pickaxe(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            if (android.state() != RuinAndroid.STATE_MINING) {
                return;
            }
            pose.pushPose();
            getParentModel().toLeftHand(pose);
            float k = 1 / SCALE;
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            // 柄: 手の中（少し後ろに出る）から前へ
            FxDraw.box(pose, body, -0.022F * k, -0.022F * k, -0.42F * k, 0.022F * k, 0.022F * k, 0.08F * k, light, 0x4A505C, 255);
            // 頭の付け根
            FxDraw.box(pose, body, -0.032F * k, -0.04F * k, -0.45F * k, 0.032F * k, 0.04F * k, -0.39F * k, light, 0x8C94A2, 255);
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            float pulse = 0.8F + 0.2F * Mth.sin(age * 0.3F);
            if (android.tool() == RuinAndroid.TOOL_AXE) {
                // 光る斧の刃: 柄の先から片側（上）へ、先ほど幅の広い刃
                for (int j = 0; j < 6; j++) {
                    float yy = 0.02F + j * 0.04F;
                    float half = 0.035F + j * 0.014F;
                    glowBox(pose, fx, -0.02F * k, yy * k, (-0.42F - half) * k, 0.02F * k, (yy + 0.04F) * k, (-0.42F + half) * k, variant.accent,
                            (int) (130 * pulse));
                }
                pose.popPose();
                return;
            }
            // 光る頭: 腕の向き（上下）に張り出し、先が持ち手の方（後ろ）へ曲がる
            for (int i = -4; i <= 4; i++) {
                float y = i * 0.045F;
                float z = -0.42F + i * i * 0.006F;
                float t = 1 - Math.abs(i) / 5F;
                float h = 0.022F + 0.01F * t;
                glowBox(pose, fx, -0.02F * k, (y - 0.026F) * k, (z - h) * k, 0.02F * k, (y + 0.026F) * k, (z + h) * k, variant.accent,
                        (int) (120 * pulse));
            }
            pose.popPose();
        }
    }

    /**
     * タンクアップグレード: 腰の左右に、ガラスの筒を1本ずつ。中の流体の色で、入っている高さまで満たす。上下に金属のふた。
     */
    /**
     * スラスター（無重力の空間で飛ぶとき）。バックパックの左右の側面に主ノズル2つ（下へ、外と少し後ろへ開いて噴く。腰の後ろの
     * バッテリーに炎がかからない。射撃型は背負ったレールガンを避けて、上寄りから後ろへ強めに噴く）、両かかとに小さな補助ノズル。
     * 無重力に入るとノズルが開いて出てきて、外へ出るとたたまれる。炎は芯の白い光と、そのまわりのプラズマの炎の二重の円錐で、
     * 進む速さと向きで長さと明るさが変わり（前や上へ進むほど主ノズルが、上へ上がるほどかかとが強く噴く）、少しずつゆらぐ。
     */
    final class Thrusters extends RenderLayer<RuinAndroid, AndroidModel> {
        private static final int SIDES = 10;

        Thrusters(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            float open = android.thrusterOpen(partialTick);
            if (open <= 0 || android.isInvisible()) {
                return;
            }
            float ease = open * open * (3 - 2 * open);
            double vx = android.getX() - android.xo, vy = android.getY() - android.yo, vz = android.getZ() - android.zo;
            double horizontal = Math.sqrt(vx * vx + vz * vz);
            // 噴く強さ（浮いているだけでも少し噴く）。上へ上がるほどかかと、前へ進むほど主ノズルが強い。下りるときは弱める
            float main = (float) Mth.clamp(0.35 + horizontal * 5 + Math.max(vy, 0) * 4 - Math.max(-vy, 0) * 3, 0.15, 1.2) * ease;
            float heel = (float) Mth.clamp(0.3 + Math.max(vy, 0) * 8 + horizontal * 1.5 - Math.max(-vy, 0) * 4, 0.1, 1.2) * ease;
            int plasma = FxDraw.lerpRgb(variant.accent, 0x7FE8FF, 0.45F);
            AndroidModel m = getParentModel();
            for (int side = -1; side <= 1; side += 2) {
                // 主ノズル: バックパックの左右の側面の下寄り。外へ開いて噴く（腰の後ろのバッテリーやスカートに炎がかからない）
                // 射撃型は背中の低い所にレールガンを斜めに背負うので、側面の上寄りから、後ろへ強めに噴いて銃の後ろを通す
                boolean gunner = android instanceof io.github.genichimaruo.singulo.android.GunnerAndroid;
                pose.pushPose();
                m.toBackpack(pose);
                pose.translate(side * 3.3F / 16, (gunner ? -6F : -2.5F) / 16, 1.5F / 16);
                pose.mulPose(Axis.ZP.rotationDegrees(-side * (gunner ? 15 : 32)));
                pose.mulPose(Axis.XP.rotationDegrees(gunner ? 55 : 16));
                nozzle(pose, buffers, light, ease, 0.075F, android, age, side, main, plasma, 0.62F);
                pose.popPose();
                // 補助ノズル: かかとの後ろ
                pose.pushPose();
                m.toShin(pose, side < 0);
                pose.translate(0, 5.6F / 16, 1.3F / 16);
                pose.mulPose(Axis.XP.rotationDegrees(14));
                nozzle(pose, buffers, light, ease, 0.05F, android, age, side + 3, heel, plasma, 0.4F);
                pose.popPose();
            }
        }

        /**
         * ノズル1つ（今の pose の原点が付け根、+Y が噴く向き）。size: ノズルの口の幅。ノズルは開き具合で根元から伸びて出てくる。
         * power: 噴く強さ（炎の長さと明るさ）。reach: 一番強いときの炎の長さ（ブロック）。
         */
        private void nozzle(PoseStack pose, MultiBufferSource buffers, int light, float open, float size,
                            RuinAndroid android, float age, int seed, float power, int plasma, float reach) {
            VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));   // 炎の描き先へ替えたあとなので、毎回取り直す
            float h = size * 0.9F * open;
            float r = size / 2;
            // 根元の台座と、口の広がったベル（暗い金属と明るい縁）
            FxDraw.box(pose, metal, -r * 0.8F, 0, -r * 0.8F, r * 0.8F, h * 0.45F, r * 0.8F, light, 0x3A4048);
            FxDraw.box(pose, metal, -r, h * 0.45F, -r, r, h, r, light, 0x8C949E);
            if (open < 0.6F) {
                return;
            }
            // 口の奥の光
            VertexConsumer fx = buffers.getBuffer(Exhaust.TYPE);
            glowBox(pose, fx, -r * 0.7F, h - 0.004F, -r * 0.7F, r * 0.7F, h + 0.002F, r * 0.7F, 0xE8FBFF, (int) (200 * Math.min(1, power)));
            // 炎: ゆらぎ（ノズルごとに位相をずらす）
            float phase = android.getId() * 1.7F + seed * 2.3F;
            float flicker = 0.86F + 0.09F * Mth.sin(age * 1.9F + phase) + 0.05F * Mth.sin(age * 4.7F + phase * 1.3F);
            float length = (0.08F + reach * power) * flicker;
            pose.pushPose();
            pose.translate(0, h, 0);
            cone(pose, fx, r * 1.05F, length, plasma, (int) Mth.clamp(150 * power, 40, 190));
            cone(pose, fx, r * 0.5F, length * 0.5F, 0xF2FDFF, (int) Mth.clamp(230 * power, 70, 240));
            pose.popPose();
        }

        /** 炎の円錐（原点の輪から +Y へ。3分の1ほどで少しふくらみ、先へ向かって細く薄くなる。光を足す描き方）。 */
        private void cone(PoseStack pose, VertexConsumer vc, float radius, float length, int rgb, int alpha) {
            PoseStack.Pose p = pose.last();
            int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
            float[] ys = {0, length * 0.32F, length};
            float[] rs = {radius, radius * 1.15F, 0};
            int[] as = {alpha, (int) (alpha * 0.7F), 0};
            for (int ring = 0; ring < 2; ring++) {
                for (int i = 0; i < SIDES; i++) {
                    float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES;
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    vc.addVertex(p, rs[ring] * c0, ys[ring], rs[ring] * s0).setColor(red, green, blue, as[ring]);
                    vc.addVertex(p, rs[ring] * c1, ys[ring], rs[ring] * s1).setColor(red, green, blue, as[ring]);
                    vc.addVertex(p, rs[ring + 1] * c1, ys[ring + 1], rs[ring + 1] * s1).setColor(red, green, blue, as[ring + 1]);
                    vc.addVertex(p, rs[ring + 1] * c0, ys[ring + 1], rs[ring + 1] * s0).setColor(red, green, blue, as[ring + 1]);
                }
            }
        }
    }

    /**
     * 浮き輪（水の中で充電が切れたとき、ぱっとふくらんで水面まで連れていく。水上で充電している間も付けている）。
     * マイクラらしく、四角い管を八角形につないだ輪（前後左右の4本はアクセントの色、角の4つは白のしま）。管は上下の角を1段ずつ
     * 落として、ふっくらした断面にする。ふくらむときは少し大きくなってから落ち着く。空気を入れる口が前に付いている。
     */
    final class SwimRing extends RenderLayer<RuinAndroid, AndroidModel> {

        SwimRing(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            float k = android.ringOpen(partialTick);
            if (k <= 0 || android.isInvisible()) {
                return;
            }
            // ぱっとふくらむ（少し大きくなりすぎてから戻る）
            float pop = k < 1 ? k * (1.25F - 0.25F * k) + 0.15F * Mth.sin(k * Mth.PI) : 1;
            pose.pushPose();
            getParentModel().toHips(pose);
            pose.translate(0, -1.2F / 16, 0);
            pose.scale(pop, pop, pop);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            float p = 1 / 16F;
            int accent = variant.accent, white = 0xF8FAFC;
            // 前後左右のまっすぐな管（ピクセル単位: 内側 4.5、外側 7.5、高さ ±1.5）
            for (int side = 0; side < 4; side++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(90 * side));
                tube(pose, vc, light, -3.2F * p, 3.2F * p, 4.5F * p, 7.5F * p, accent);
                pose.popPose();
            }
            // 角の管（斜め45度。白）
            for (int corner = 0; corner < 4; corner++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(45 + 90 * corner));
                tube(pose, vc, light, -2.0F * p, 2.0F * p, 5.0F * p, 8.0F * p, white);   // まっすぐな管の端どうしをつなぐ
                pose.popPose();
            }
            // 空気を入れる口（前の管の上）
            FxDraw.box(pose, vc, -0.5F * p, -2.1F * p, -6.5F * p, 0.5F * p, -1.4F * p, -5.5F * p, light, white, 255);
            pose.popPose();
        }

        /**
         * 管の1本（x0..x1 の長さ、中心から z0..z1 の前の向き（-Z）の位置）。四角い管の上下の外側の角を1段落として、ふっくらさせる。
         */
        private void tube(PoseStack pose, VertexConsumer vc, int light, float x0, float x1, float z0, float z1, int rgb) {
            float p = 1 / 16F, h = 1.5F * p, step = 0.5F * p;
            FxDraw.box(pose, vc, x0, -h + step, -z1, x1, h - step, -z0, light, rgb, 255);                    // 太い真ん中
            FxDraw.box(pose, vc, x0, -h, -z1 + step, x1, h, -z0 - step, light, rgb, 255);                    // 上下の面（角を落とした内側）
        }
    }

    final class Tanks extends RenderLayer<RuinAndroid, AndroidModel> {
        Tanks(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            boolean fluids = android.tankFill(0) >= 0, chems = android.chemFill(0) >= 0;
            if (!fluids && !chems || !android.printed()) {
                return;
            }
            for (int i = 0; i < RuinAndroid.TANKS; i++) {
                boolean left = i == 0;
                pose.pushPose();
                getParentModel().toHipSide(pose, left);
                float s = left ? 1 : -1;
                // 筒の大きさ（ピクセル）: 外側へ出して、太さ 1.8、高さ 3.6。液体と化学物質の両方なら、前に筒、後ろにボンベを細く並べる
                float x0 = s * 0.55F / 16, x1 = s * 2.35F / 16, y0 = 0.6F / 16, y1 = 4.2F / 16;
                float z0 = -0.9F / 16, z1 = fluids && chems ? -0.05F / 16 : 0.9F / 16;
                float lo = Math.min(x0, x1), hi = Math.max(x0, x1);
                if (chems) {
                    cylinder(pose, buffers, light, android, i, lo, hi, y0, y1, fluids ? 0.05F / 16 : -0.9F / 16, 0.9F / 16);
                }
                if (!fluids) {
                    pose.popPose();
                    continue;
                }
                // 蓋は筒よりほんの少し太く、ガラスの上下の面は蓋から少し離す（同じ面が重なると、境目がちらつく）
                VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
                float lip = 0.06F / 16, gap = 0.02F / 16;
                FxDraw.box(pose, metal, lo - lip, y0 - 0.35F / 16, z0 - lip, hi + lip, y0, z1 + lip, light, 0x8C94A2, 255);
                FxDraw.box(pose, metal, lo - lip, y1, z0 - lip, hi + lip, y1 + 0.35F / 16, z1 + lip, light, 0x8C94A2, 255);
                float fill = android.tankFill(i);
                net.minecraft.world.level.material.Fluid fluid = android.tankFluid(i);
                if (fluid != null && fill > 0) {
                    // 中身: 流体そのものの絵（流れるアニメーションも動く）。ガスは上から溜まる
                    VertexConsumer liquid = buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
                    float level = (y1 - y0) * Math.min(1, fill);
                    float bottom = FluidDraw.gaseous(fluid) ? y1 - level : y0, top = FluidDraw.gaseous(fluid) ? y1 : y0 + level;
                    FluidDraw.box(pose, liquid, fluid, lo + 0.15F / 16, bottom, z0 + 0.15F / 16, hi - 0.15F / 16, top, z1 - 0.15F / 16, light,
                            FluidDraw.gaseous(fluid) ? 170 : 235);
                }
                VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE));
                FxDraw.box(pose, glass, lo, y0 + gap, z0, hi, y1 - gap, z1, light, 0xDDEEF8, 70);
                pose.popPose();
            }
        }

        /**
         * ガスボンベ（化学物質のタンク）: 鋼の胴と丸い肩、上にバルブと金のつまみ、肩の下に中身の色の帯、前に中身の見える細い窓
         * （化学物質そのものの絵で、入っている高さまで）。
         */
        private void cylinder(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, int i, float lo, float hi, float y0, float y1,
                              float z0, float z1) {
            float p = 1 / 16F;
            VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            int steel = 0xB4BCC8, dark = 0x6E7684;
            FxDraw.box(pose, metal, lo, y0 - 0.3F * p, z0, hi, y1 - 0.4F * p, z1, light, steel, 255);                            // 胴
            float inset = 0.25F * p;
            FxDraw.box(pose, metal, lo + inset, y1 - 0.39F * p, z0 + inset, hi - inset, y1 + 0.2F * p, z1 - inset, light, dark, 255);  // 肩（胴の上の面と重ねない）
            float mx = (lo + hi) / 2, mz = (z0 + z1) / 2, v = 0.3F * p;
            FxDraw.box(pose, metal, mx - v, y1 + 0.2F * p, mz - v, mx + v, y1 + 0.8F * p, mz + v, light, 0x5A606C, 255);          // バルブ
            FxDraw.box(pose, metal, mx - 0.5F * p, y1 + 0.8F * p, mz - 0.15F * p, mx + 0.5F * p, y1 + 1.0F * p, mz + 0.15F * p, light, 0xE2BA5C, 255);
            String id = android.chemId(i);
            float fill = android.chemFill(i);
            int band = id.isEmpty() ? 0x9AA2AE : io.github.genichimaruo.singulo.android.work.Chemicals.tint(id);
            FxDraw.box(pose, metal, lo - 0.02F * p, y1 - 1.2F * p, z0 - 0.02F * p, hi + 0.02F * p, y1 - 0.8F * p, z1 + 0.02F * p, light, band, 255);  // 帯
            // 前の窓（暗いガラスの奥に、入っている高さまで中身）
            float wx0 = lo + 0.45F * (hi - lo), wx1 = hi - 0.2F * (hi - lo), wy0 = y0 + 0.2F * p, wy1 = y1 - 1.5F * p, wz = z0 - 0.01F * p;
            FxDraw.box(pose, metal, wx0, wy0, wz - 0.02F * p, wx1, wy1, wz, light, 0x2A3040, 255);
            if (!id.isEmpty() && fill > 0) {
                VertexConsumer chem = buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
                FluidDraw.chemicalBox(pose, chem, id, wx0, wy0, wz - 0.045F * p, wx1, wy0 + (wy1 - wy0) * Math.min(1, fill), wz - 0.025F * p, light, 235);
            }
        }
    }

    /**
     * 瞬き: 数秒ごと（アンドロイドごとに間合いを変える）に、目の上にまぶた（肌の色と、下の縁のまつげの線）を重ねる。
     * 半分閉じる → 閉じる（2 tick）→ 半分閉じる。目は顔（頭の前の面、z -3.5）の 5〜6 段目、左右 1〜2 列・5〜6 列。
     */
    final class Blink extends RenderLayer<RuinAndroid, AndroidModel> {
        private static final int SKIN = 0xFFECE4, CREASE = 0xE6CFC8, LASH = 0x4A3A44;

        Blink(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            if (!android.printed()) {
                return;
            }
            int period = 70 + Math.floorMod(android.getId() * 37, 50);          // 3.5〜6秒ごと
            int phase = android.napping() ? 1 : Math.floorMod(android.tickCount + android.getId() * 13, period);   // 昼寝中は閉じたまま
            if (phase > 3) {
                return;
            }
            boolean half = phase == 0 || phase == 3;                              // 閉じかけ・開きかけ
            float p = 1 / 16F;
            pose.pushPose();
            getParentModel().toHead(pose);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            float z0 = -3.52F * p, z1 = -3.5F * p;
            for (float x0 : new float[] {-3, 1}) {
                float x1 = x0 + 2;
                float bottom = half ? -2 : -1;                                     // 半分なら上の段だけ
                FxDraw.box(pose, vc, x0 * p, -3 * p, z0, x1 * p, (bottom - 0.25F) * p, z1, light, half ? CREASE : SKIN, 255);
                FxDraw.box(pose, vc, x0 * p, (bottom - 0.25F) * p, z0 - 0.002F, x1 * p, bottom * p, z1, light, LASH, 255);
            }
            pose.popPose();
        }
    }

    /**
     * 装飾: 頭（リボン・髪飾り・ベレー帽・ヘッドドレス）、背中/腰（マント・マフラー・腰リボン）、手（本・花束・ティーカップ・傘）。
     * 染めた色で描く。手に持つ物は、待機している間だけ。大きさは体のピクセル（1/16）で決める。
     */
    final class Accessories extends RenderLayer<RuinAndroid, AndroidModel> {
        private static final float P = 1 / 16F;

        Accessories(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            if (!android.printed()) {
                return;
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            AndroidModel m = getParentModel();
            for (int part = 0; part < 3; part++) {
                net.minecraft.world.item.ItemStack stack = android.decor(part);
                if (!(stack.getItem() instanceof io.github.genichimaruo.singulo.android.AndroidAccessoryItem acc)) {
                    continue;
                }
                if (part == io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND && !android.showsHeldDecor()) {
                    continue;
                }
                int c = acc.color(stack);
                pose.pushPose();
                switch (part) {
                    case 0 -> m.toHead(pose);
                    case 1 -> {
                        if (acc.style().equals("waist_ribbon")) {
                            m.toHips(pose);
                        } else {
                            m.toTorso(pose);
                        }
                    }
                    default -> m.toLeftHand(pose);
                }
                if (acc.style().equals("bouquet")) {
                    pose.mulPose(Axis.XP.rotationDegrees(25));      // 花を前へ傾けて、前に立った人に見せる
                }
                if (acc.style().equals("umbrella")) {
                    pose.mulPose(Axis.XP.rotationDegrees(UMBRELLA_TILT));   // 傘を後ろへ傾け、肩にあずけるように持つ
                }
                draw(acc.style(), pose, vc, light, c, Math.min(1, limbSwingAmount), age);
                if (acc.style().equals("bouquet")) {
                    // 透ける包装は、形を描き終えてから別の描き方で（描き方を混ぜないよう、形の受け口は取り直す）
                    bouquetWrap(pose, buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE)), light);
                    vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
                }
                if (acc.style().equals("glasses")) {
                    glassesLenses(pose, buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE)), light);
                    vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
                }
                pose.popPose();
            }
        }

        /** アンドロイドが傘を持つ傾き（度。手の向きに対して後ろへ）。 */
        static final float UMBRELLA_TILT = -30;

        /** 花束の透明な包装（下ほど細い3段と、開いた口の縁。うっすら水色に透ける）。 */
        private void bouquetWrap(PoseStack pose, VertexConsumer vc, int light) {
            int film = 0xE6F4FF, edge = 0xF4FAFF;
            float[][] boxes = {
                    {-0.55F, -0.05F, -0.9F, 0.55F, 1.05F, 0.6F}, {-0.95F, -0.45F, -1.7F, 0.95F, 1.45F, -0.8F},
                    {-1.35F, -0.85F, -2.6F, 1.35F, 1.85F, -1.6F}};
            for (float[] q : boxes) {
                FxDraw.box(pose, vc, q[0] * P, q[1] * P, q[2] * P, q[3] * P, q[4] * P, q[5] * P, light, film, 80);
            }
            float[][] rim = {
                    {-1.55F, 1.8F, -2.95F, 1.55F, 2.02F, -2.5F}, {-1.55F, -1.02F, -2.95F, 1.55F, -0.8F, -2.5F},
                    {-1.57F, -0.85F, -2.9F, -1.3F, 1.85F, -2.5F}, {1.3F, -0.85F, -2.9F, 1.57F, 1.85F, -2.5F}};
            for (float[] q : rim) {
                FxDraw.box(pose, vc, q[0] * P, q[1] * P, q[2] * P, q[3] * P, q[4] * P, q[5] * P, light, edge, 130);
            }
        }

        /** メガネのレンズ（縁の内側に、うっすら水色に透ける板）。縁は draw の "glasses"。 */
        private void glassesLenses(PoseStack pose, VertexConsumer vc, int light) {
            for (float x0 : new float[] {-3.25F, 0.85F}) {
                FxDraw.box(pose, vc, x0 * P, -3.15F * P, -3.8F * P, (x0 + 2.4F) * P, -1.05F * P, -3.74F * P, light, 0xD2ECFF, 70);
            }
        }

        /** 輪（外の四角 x0..x1・y0..y1 から、内の四角 i… を抜いた4枚の板。高さ z0..z1）。 */
        private void ring(PoseStack pose, VertexConsumer vc, int light, float x0, float y0, float x1, float y1,
                          float ix0, float iy0, float ix1, float iy1, float z0, float z1, int rgb) {
            b(pose, vc, light, x0, y0, z0, x1, iy0, z1, rgb);
            b(pose, vc, light, x0, iy1, z0, x1, y1, z1, rgb);
            b(pose, vc, light, x0, iy0, z0, ix0, iy1, z1, rgb);
            b(pose, vc, light, ix1, iy0, z0, x1, iy1, z1, rgb);
        }

        /** 箱（ピクセル単位）。 */
        private void b(PoseStack pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
            FxDraw.box(pose, vc, x0 * P, y0 * P, z0 * P, x1 * P, y1 * P, z1 * P, light, rgb, 255);
        }

        private void draw(String style, PoseStack pose, VertexConsumer vc, int light, int c, float walk, float age) {
            int dark = mix(c, 0x000000, 0.25F);
            switch (style) {
                // ---- 頭（頭の箱: x -4〜4、y -8〜0、z -3.5〜3.5。前は -z）
                case "ribbon" -> {
                    // 後頭部の上に、左右の輪と結び目、短い垂れ
                    b(pose, vc, light, -0.7F, -7.9F, 4.1F, 0.7F, -6.7F, 4.9F, dark);
                    b(pose, vc, light, -3.0F, -8.8F, 4.2F, -0.7F, -6.2F, 4.8F, c);
                    b(pose, vc, light, 0.7F, -8.8F, 4.2F, 3.0F, -6.2F, 4.8F, c);
                    // 垂れ: 後ろ髪の外側に沿わせる（髪の角度・揺れに合わせる）
                    pose.popPose();
                    pose.pushPose();
                    getParentModel().toBackHair(pose);
                    b(pose, vc, light, -1.4F, -0.2F, 0.75F, -0.5F, 3.6F, 1.1F, c);
                    b(pose, vc, light, 0.5F, -0.2F, 0.75F, 1.4F, 3.2F, 1.1F, c);
                }
                case "hair_flower" -> {
                    // 右の側頭部に、花びら4枚と黄色い芯
                    float x = -4.25F;
                    b(pose, vc, light, x - 0.5F, -7.4F, -1.9F, x, -6.4F, -0.9F, 0xF7E07A);
                    b(pose, vc, light, x - 0.4F, -8.6F, -1.9F, x + 0.1F, -7.4F, -0.9F, c);
                    b(pose, vc, light, x - 0.4F, -6.4F, -1.9F, x + 0.1F, -5.2F, -0.9F, c);
                    b(pose, vc, light, x - 0.4F, -7.4F, -3.1F, x + 0.1F, -6.4F, -1.9F, c);
                    b(pose, vc, light, x - 0.4F, -7.4F, -0.9F, x + 0.1F, -6.4F, 0.3F, c);
                }
                case "beret" -> {
                    // 頭の上に少し傾けた円盤と、てっぺんの小さな柄。頭より少し小さく、少し右（-X）へ寄せて載せる。
                    // 傾けて低くなる側でも髪の殻（頭のてっぺん -8.45）がつばを突き抜けないよう少し上げ、つばを下へ厚くする
                    // （高い側は、つばの下の端が髪のてっぺんに届くので、すき間もできない）。
                    // 髪の殻の面（x ±4.45・z ±3.95）とつばの面は重ねない（重なるとちらつく）
                    pose.pushPose();
                    pose.translate(-0.3F * P, -8.4F * P, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(-10));
                    b(pose, vc, light, -4.5F, -1.1F, -4.0F, 4.5F, 0.75F, 4.0F, c);
                    b(pose, vc, light, -3.8F, -1.6F, -3.3F, 3.8F, -1.0F, 3.3F, c);
                    b(pose, vc, light, -0.3F, -2.3F, -0.3F, 0.3F, -1.5F, 0.3F, dark);
                    pose.popPose();
                }
                case "headdress" -> {
                    // 頭の上の前寄りに帯、前の縁にひだ（交互に）、両わきに小さなリボン
                    b(pose, vc, light, -4.3F, -8.7F, -2.2F, 4.3F, -8.0F, -0.8F, c);
                    for (int i = 0; i < 8; i++) {
                        float x0 = -4.3F + i * 1.075F;
                        b(pose, vc, light, x0, -9.3F - (i % 2) * 0.4F, -2.3F, x0 + 1.0F, -8.7F, -1.7F, c);
                    }
                    b(pose, vc, light, -4.6F, -8.4F, -1.8F, -4.2F, -6.8F, -1.0F, dark);
                    b(pose, vc, light, 4.2F, -8.4F, -1.8F, 4.6F, -6.8F, -1.0F, dark);
                }
                case "glasses" -> {
                    // 目（x -3〜-1・1〜3、y -3〜-1）を囲む四角い縁2つ、ブリッジ、髪の中へ消えるつる。顔の前の面（z -3.5）より少し前
                    float z0 = -3.9F, z1 = -3.62F;
                    ring(pose, vc, light, -3.75F, -3.6F, -0.35F, -0.6F, -3.25F, -3.15F, -0.85F, -1.05F, z0, z1, c);
                    ring(pose, vc, light, 0.35F, -3.6F, 3.75F, -0.6F, 0.85F, -3.15F, 3.25F, -1.05F, z0, z1, c);
                    b(pose, vc, light, -0.35F, -2.75F, z0 + 0.05F, 0.35F, -2.35F, z1 - 0.05F, c);
                    b(pose, vc, light, -4.1F, -2.75F, z0, -3.75F, -2.35F, 0.5F, dark);
                    b(pose, vc, light, 3.75F, -2.75F, z0, 4.1F, -2.35F, 0.5F, dark);
                }
                // ---- 背中（胴の箱: x -3〜3、y -8〜0、z -1.5〜1.5。後ろは +z）
                case "cape" -> {
                    // 肩から背中へ垂れるマント。歩くと後ろへなびく
                    b(pose, vc, light, -3.4F, -8.3F, -1.8F, 3.4F, -7.5F, 1.9F, dark);           // 襟
                    pose.pushPose();
                    pose.translate(0, -7.8F * P, 1.7F * P);
                    pose.mulPose(Axis.XP.rotationDegrees(6 + 22 * walk + 3 * Mth.sin(age * 0.08F)));
                    b(pose, vc, light, -3.8F, 0, 0, 3.8F, 15.5F, 0.4F, c);
                    pose.popPose();
                }
                case "scarf" -> {
                    // 首に巻いて、前の左に垂れ（箱どうしを重ねない: 同じ面が重なるとちらつく）
                    b(pose, vc, light, -3.4F, -8.2F, -1.9F, 3.4F, -6.8F, -1.5F, c);
                    b(pose, vc, light, -3.4F, -8.2F, 1.5F, 3.4F, -6.8F, 1.9F, c);
                    b(pose, vc, light, -3.4F, -8.2F, -1.5F, -3.0F, -6.8F, 1.5F, c);
                    b(pose, vc, light, 3.0F, -8.2F, -1.5F, 3.4F, -6.8F, 1.5F, c);
                    pose.pushPose();
                    pose.translate(1.6F * P, -6.8F * P, -1.9F * P);
                    pose.mulPose(Axis.XP.rotationDegrees(-8 - 15 * walk));
                    b(pose, vc, light, 0.05F, 0, -0.35F, 1.55F, 4.4F, -0.05F, c);
                    b(pose, vc, light, 0, 4.4F, -0.4F, 1.6F, 5.0F, 0, dark);
                    pose.popPose();
                }
                case "waist_ribbon" -> {
                    // 腰の後ろに大きな蝶結び（腰の箱: x -3.5〜3.5、y -3〜0、z -2〜2）
                    b(pose, vc, light, -0.9F, -2.4F, 2.0F, 0.9F, -0.8F, 2.9F, dark);
                    b(pose, vc, light, -4.0F, -3.4F, 2.1F, -0.9F, -0.2F, 2.8F, c);
                    b(pose, vc, light, 0.9F, -3.4F, 2.1F, 4.0F, -0.2F, 2.8F, c);
                    // 垂れ: スカートの後ろの面の外側に沿わせる（スカートの角度・揺れに合わせる。バッテリーの内側）
                    pose.popPose();
                    pose.pushPose();
                    getParentModel().toSkirtBack(pose);
                    b(pose, vc, light, -1.25F, 0.2F, 0.35F, -0.3F, 5.6F, 0.7F, c);
                    b(pose, vc, light, 0.3F, 0.2F, 0.35F, 1.25F, 5.0F, 0.7F, c);
                }
                // ---- 手（左手の先。前腕を前へ水平に出すので、上は -z、前は +y）
                case "book" -> {
                    // 立てて開いた本（表紙は染めた色、中は白いページ）
                    b(pose, vc, light, -1.5F, 0.6F, -3.6F, 1.5F, 1.1F, -0.2F, c);
                    b(pose, vc, light, -1.3F, 0.3F, -3.4F, 1.3F, 0.6F, -0.4F, 0xF4F1E6);
                    b(pose, vc, light, -0.1F, 0.2F, -3.6F, 0.1F, 1.1F, -0.2F, dark);
                }
                case "bouquet" -> {
                    // 透明な包装（下ほど細い。透けて茎が見える。包装は透けるので、最後に別の描き方で描く: bouquetWrap）と、
                    // 正面に染めた色のリボンの蝶結び。
                    // 口の上に花をこんもり盛る: 中央に高く赤いバラ、まわりに6輪（白・桃・黄・薄桃のデイジーなど、芯つき）、
                    // 紫のラベンダーの穂、かすみ草の白い粒、外へ広がる葉。
                    // 面がぴったり重なるとちらつくので、箱どうしは少しずつ食い込ませ、高さもずらす
                    int leaf = 0x5E9E48, deep = 0x3F7A34, stem = 0x4E8A3A;
                    b(pose, vc, light, -0.4F, 0.15F, -2.6F, -0.1F, 0.45F, 0.5F, stem);         // 茎（包装ごしに見える）
                    b(pose, vc, light, 0.1F, 0.25F, -2.6F, 0.4F, 0.55F, 0.3F, stem);
                    b(pose, vc, light, -0.15F, 0.6F, -2.6F, 0.15F, 0.9F, 0.4F, 0x5A9A44);
                    b(pose, vc, light, -1.25F, -0.75F, -2.8F, 1.25F, 1.75F, -2.45F, deep);     // 口の中の葉
                    b(pose, vc, light, -1.0F, -0.5F, -1.45F, 1.0F, 1.5F, -1.1F, c);            // リボン（染めた色）
                    b(pose, vc, light, -1.3F, 1.45F, -1.75F, -0.2F, 1.8F, -0.85F, c);
                    b(pose, vc, light, 0.2F, 1.45F, -1.75F, 1.3F, 1.8F, -0.85F, c);
                    b(pose, vc, light, -0.25F, 1.45F, -1.5F, 0.25F, 1.9F, -1.05F, dark);
                    b(pose, vc, light, -0.6F, 1.52F, -1.1F, -0.3F, 1.7F, 0.05F, c);
                    b(pose, vc, light, 0.3F, 1.52F, -1.1F, 0.6F, 1.7F, -0.15F, c);
                    // 外へ広がる葉
                    b(pose, vc, light, -2.25F, 0.2F, -3.6F, -1.45F, 0.8F, -2.6F, leaf);
                    b(pose, vc, light, 1.45F, 0.15F, -3.5F, 2.25F, 0.75F, -2.6F, leaf);
                    b(pose, vc, light, -0.3F, -1.45F, -3.55F, 0.3F, -0.7F, -2.65F, leaf);
                    b(pose, vc, light, -0.25F, 1.75F, -3.45F, 0.35F, 2.4F, -2.7F, leaf);
                    // 中央のバラ（高く、濃い芯）
                    b(pose, vc, light, -0.65F, -0.15F, -4.6F, 0.65F, 1.15F, -3.4F, 0xD8334A);
                    b(pose, vc, light, -0.25F, 0.25F, -4.7F, 0.25F, 0.75F, -4.5F, 0x9E1E34);
                    // まわりの6輪: x, y, てっぺん, 花びら, 芯
                    float[][] ring = {
                            {1.1F, 0.5F, -3.9F}, {-1.1F, 0.5F, -3.8F}, {0.55F, 1.45F, -3.95F},
                            {-0.55F, 1.45F, -3.85F}, {0.55F, -0.45F, -3.75F}, {-0.55F, -0.45F, -3.9F}};
                    int[][] colors = {
                            {0xFAFAFA, 0xF6C83A}, {0xF29AB8, 0xF6D24A}, {0xF6D24A, 0xE88A2A},
                            {0xFAFAFA, 0xF6C83A}, {0xF8C4D6, 0xF6D24A}, {0xF6D24A, 0xE88A2A}};
                    for (int i = 0; i < ring.length; i++) {
                        float x = ring[i][0], y = ring[i][1], top = ring[i][2];
                        b(pose, vc, light, x - 0.55F, y - 0.55F, top, x + 0.55F, y + 0.55F, top + 1.0F, colors[i][0]);
                        b(pose, vc, light, x - 0.2F, y - 0.2F, top - 0.1F, x + 0.2F, y + 0.2F, top + 0.05F, colors[i][1]);
                    }
                    // ラベンダーの穂
                    b(pose, vc, light, -1.65F, -0.45F, -4.4F, -1.15F, 0.05F, -3.0F, 0xA68AD8);
                    b(pose, vc, light, 1.23F, 1.18F, -4.2F, 1.67F, 1.62F, -3.0F, 0xB89CE6);
                    // かすみ草
                    float[][] dots = {{0.0F, 2.05F, -3.5F}, {-1.5F, 1.3F, -3.4F}, {1.55F, -0.3F, -3.45F}, {-1.0F, -1.0F, -3.4F}, {1.1F, 1.95F, -3.3F}};
                    for (float[] d : dots) {
                        b(pose, vc, light, d[0] - 0.17F, d[1] - 0.17F, d[2], d[0] + 0.17F, d[1] + 0.17F, d[2] + 0.34F, 0xFFFFFF);
                    }
                }
                case "teacup" -> {
                    // 手のひらの上に、受け皿とカップ。カップは底と4枚の壁で中が凹み、紅茶は縁より低い所に張る。
                    // 縁は金、横に輪の取っ手（z は負の向きが上）
                    b(pose, vc, light, -1.5F, -0.1F, -0.4F, 1.5F, 2.7F, -0.15F, c);                 // 受け皿
                    b(pose, vc, light, -1.25F, 0.15F, -0.5F, 1.25F, 2.45F, -0.4F, c);               // 受け皿の縁の盛り上がり
                    b(pose, vc, light, -0.9F, 0.4F, -0.7F, 0.9F, 2.2F, -0.4F, c);                   // 底
                    b(pose, vc, light, -0.9F, 0.4F, -2.0F, 0.9F, 0.65F, -0.7F, c);                  // 手前の壁
                    b(pose, vc, light, -0.9F, 1.95F, -2.0F, 0.9F, 2.2F, -0.7F, c);                  // 奥の壁
                    b(pose, vc, light, -0.9F, 0.65F, -2.0F, -0.65F, 1.95F, -0.7F, c);               // 左の壁
                    b(pose, vc, light, 0.65F, 0.65F, -2.0F, 0.9F, 1.95F, -0.7F, c);                 // 右の壁
                    b(pose, vc, light, -0.95F, 0.35F, -2.15F, 0.95F, 0.65F, -2.0F, 0xE0B850);        // 金の縁（手前）
                    b(pose, vc, light, -0.95F, 1.95F, -2.15F, 0.95F, 2.25F, -2.0F, 0xE0B850);        // 金の縁（奥）
                    b(pose, vc, light, -0.95F, 0.65F, -2.15F, -0.65F, 1.95F, -2.0F, 0xE0B850);       // 金の縁（左）
                    b(pose, vc, light, 0.65F, 0.65F, -2.15F, 0.95F, 1.95F, -2.0F, 0xE0B850);         // 金の縁（右）
                    b(pose, vc, light, -0.65F, 0.65F, -1.55F, 0.65F, 1.95F, -1.45F, 0x8A5A3A);       // 紅茶（縁より低い）
                    b(pose, vc, light, 0.9F, 1.05F, -1.75F, 1.55F, 1.55F, -1.55F, c);               // 取っ手（上）
                    b(pose, vc, light, 0.9F, 1.05F, -0.95F, 1.55F, 1.55F, -0.75F, c);               // 取っ手（下）
                    b(pose, vc, light, 1.35F, 1.05F, -1.55F, 1.55F, 1.55F, -0.95F, c);              // 取っ手（外側）
                }
                case "umbrella" -> {
                    // 柄を上へ。頭の上に、染めた色の傘（下ほど広い4段のなだらかな屋根）、縁の8か所に骨の先、
                    // てっぺんの石突き。手元は曲がった持ち手（z は負の向きが上）
                    b(pose, vc, light, -0.25F, 0.2F, -22F, 0.25F, 0.7F, 0.6F, 0x5A4A3A);              // 柄
                    b(pose, vc, light, -0.25F, 0.2F, 0.6F, 1.2F, 0.7F, 1.1F, 0x4A3A2C);              // 持ち手（下へ曲がる）
                    b(pose, vc, light, 0.8F, 0.2F, -0.4F, 1.3F, 0.7F, 0.6F, 0x4A3A2C);
                    // 布: 下ほど広い4段の輪（内側を抜く）。下から見ると、内側が段々に凹んで見える
                    ring(pose, vc, light, -8.5F, -8.0F, 8.5F, 8.9F, -6.8F, -6.3F, 6.8F, 7.2F, -21.3F, -20.8F, c);
                    ring(pose, vc, light, -6.8F, -6.3F, 6.8F, 7.2F, -4.5F, -4.0F, 4.5F, 4.9F, -22.0F, -21.3F, c);
                    ring(pose, vc, light, -4.5F, -4.0F, 4.5F, 4.9F, -2.2F, -1.7F, 2.2F, 2.6F, -22.6F, -22.0F, dark);
                    b(pose, vc, light, -2.2F, -1.7F, -23.1F, 2.2F, 2.6F, -22.6F, c);                // てっぺん
                    b(pose, vc, light, -0.3F, 0.15F, -24.0F, 0.3F, 0.75F, -23.1F, 0xC8C8C8);         // 石突き
                    float[][] tips = {{-8.5F, -8.0F}, {-0.3F, -8.0F}, {8.0F, -8.0F}, {-8.5F, 0.15F}, {8.0F, 0.15F},
                            {-8.5F, 8.4F}, {-0.3F, 8.4F}, {8.0F, 8.4F}};
                    for (float[] t : tips) {                                                            // 骨の先
                        b(pose, vc, light, t[0], t[1], -20.8F, t[0] + 0.5F, t[1] + 0.5F, -20.4F, 0xC8C8C8);
                    }
                }
                default -> {
                }
            }
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
            if (m.whipHandle == null || !(android instanceof WhipAndroid whip) || android.weaponStowed()) {
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
            if (m.hilt == null || android.weaponStowed()) {
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
            int a = (int) Math.min(255, 140 * glow);
            int c = (int) (230 * Math.min(1, glow));
            // 芯を別の描き先で先に描いてから、外側を描く（同じ描き先だと、並べ替えで芯が隠れることがある）
            glowBox(pose, buffers.getBuffer(GlowCore.TYPE), -core, 0, -core, core, l + 0.08F / SCALE, core, 0xFFFFFF, c);
            glowBox(pose, buffers.getBuffer(RenderType.lightning()), -outer, 0, -outer, outer, l, outer, variant.accent, a);
            pose.popPose();
        }
    }

    /**
     * 斧型の両手斧: 長い柄（濃い金属、握りに革巻き、端に石突き）の先に、両刃の斧頭。斧頭の芯は金属で、刃は光る
     * （真ん中が広い三日月の形を、薄い光の箱を重ねて作る）。前触れのあいだは強く光る。
     */
    final class Axe extends RenderLayer<RuinAndroid, AndroidModel> {
        Axe(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.axeGrip == null || !android.printed() || android.weaponStowed()) {
                return;
            }
            int state = android.state();
            float glow = state == RuinAndroid.STATE_LOW_POWER ? 0.3F : RuinAndroid.charging(state) ? 0.5F
                    : state == AxeAndroid.STATE_CLEAVE_WINDUP || state == AxeAndroid.STATE_SLAM_WINDUP || state == AxeAndroid.STATE_LEAP
                    ? 1.3F + 0.1F * Mth.sin(age * 0.4F) : 0.85F + 0.08F * Mth.sin(age * 0.2F);
            pose.pushPose();
            m.toBlade(pose);
            m.axeGrip.translateAndRotate(pose);
            float k = 1 / SCALE;
            pose.scale(k, k, k);                       // ここからはブロックの長さで描く
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            float r = 0.028F;
            FxDraw.box(pose, body, -r, -0.42F, -r, r, 1.02F, r, light, 0x3C424E, 255);                       // 柄
            FxDraw.box(pose, body, -r - 0.006F, -0.12F, -r - 0.006F, r + 0.006F, 0.16F, r + 0.006F, light, 0x5A3E30, 255);  // 革巻き
            FxDraw.box(pose, body, -0.04F, -0.5F, -0.04F, 0.04F, -0.42F, 0.04F, light, 0x8C94A2, 255);    // 石突き
            FxDraw.box(pose, body, -0.045F, 0.78F, -0.07F, 0.045F, 1.06F, 0.07F, light, 0x6E7684, 255);   // 斧頭の芯
            FxDraw.box(pose, body, -0.035F, 1.06F, -0.035F, 0.035F, 1.14F, 0.035F, light, 0x8C94A2, 255); // 先の飾り
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            // 刃: 三日月の輪郭を細かく区切り、両面の板と刃先の白い帯・縁を、互いに重ならない面だけで描く
            // （光は奥行きも書くので、同じ平面に面が重なるとちらつく）
            PoseStack.Pose p = pose.last();
            int a = (int) Math.min(255, 150 * glow);
            int edge = (int) Math.min(255, 210 * glow);
            int n = 12;
            float h = 0.5F, y0 = 0.68F, inner = 0.05F, w = 0.018F, band = 0.025F;
            for (int side = -1; side <= 1; side += 2) {
                for (int j = 0; j < n; j++) {
                    float ya = y0 + h * j / n, yb = y0 + h * (j + 1) / n;
                    float ra = side * (inner + bladeReach(j / (float) n)), rb = side * (inner + bladeReach((j + 1) / (float) n));
                    float ea = ra + side * band, eb = rb + side * band;
                    for (float x = -w; x <= w; x += 2 * w) {
                        glowQuad(p, fx, x, ya, side * inner, x, yb, side * inner, x, yb, rb, x, ya, ra, variant.accent, a);
                        glowQuad(p, fx, x, ya, ra, x, yb, rb, x, yb, eb, x, ya, ea, 0xFFFFFF, edge);
                    }
                    glowQuad(p, fx, -w, ya, ea, w, ya, ea, w, yb, eb, -w, yb, eb, 0xFFFFFF, edge);   // 刃先の縁
                }
            }
            pose.popPose();
        }
    }

    /** 斧の刃の張り出し（柄の向きの位置 0〜1。真ん中が広く、上下の端が少し後ろへ反る三日月）。 */
    private static float bladeReach(float s) {
        return 0.06F + 0.27F * Mth.sin(Mth.PI * s);
    }

    /** 足し合わせの光の四角形（表裏の両方の向きで描く）。 */
    private static void glowQuad(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        vc.addVertex(p, x0, y0, z0).setColor(r, g, b, alpha);
        vc.addVertex(p, x1, y1, z1).setColor(r, g, b, alpha);
        vc.addVertex(p, x2, y2, z2).setColor(r, g, b, alpha);
        vc.addVertex(p, x3, y3, z3).setColor(r, g, b, alpha);
        vc.addVertex(p, x3, y3, z3).setColor(r, g, b, alpha);
        vc.addVertex(p, x2, y2, z2).setColor(r, g, b, alpha);
        vc.addVertex(p, x1, y1, z1).setColor(r, g, b, alpha);
        vc.addVertex(p, x0, y0, z0).setColor(r, g, b, alpha);
    }

    /** ビット型の短剣: 短い柄と鍔、光の刃（白い芯と菫の外側）。 */
    final class Dagger extends RenderLayer<RuinAndroid, AndroidModel> {
        Dagger(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.daggerGrip == null || !android.printed() || android.weaponStowed()) {
                return;
            }
            int state = android.state();
            float glow = state == RuinAndroid.STATE_LOW_POWER ? 0.3F : RuinAndroid.charging(state) ? 0.5F
                    : state == BitAndroid.STATE_STAB ? 1.3F : 0.9F;
            float len = state == RuinAndroid.STATE_LOW_POWER ? 0.12F : 0.42F;
            pose.pushPose();
            m.toBlade(pose);
            m.daggerGrip.translateAndRotate(pose);
            float k = 1 / SCALE;
            pose.scale(k, k, k);
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            FxDraw.box(pose, body, -0.022F, -0.12F, -0.022F, 0.022F, 0.06F, 0.022F, light, 0x2C345C, 255);
            FxDraw.box(pose, body, -0.07F, 0.06F, -0.018F, 0.07F, 0.09F, 0.018F, light, 0x8C94B8, 255);
            float outer = 0.03F * glow;
            // 白い芯を別の描き先で先に描き終える（外側と同じ描き先だと、並べ替えで外側が先に奥行きを書き、芯が隠れる）
            glowBox(pose, buffers.getBuffer(GlowCore.TYPE), -0.01F, 0.095F, -0.005F, 0.01F, 0.11F + len, 0.005F, 0xFFFFFF,
                    (int) (230 * Math.min(1, glow)));
            glowBox(pose, buffers.getBuffer(RenderType.lightning()), -outer, 0.09F, -0.008F - outer * 0.4F, outer, 0.09F + len,
                    0.008F + outer * 0.4F, variant.accent, (int) Math.min(255, 150 * glow));
            pose.popPose();
        }
    }

    /**
     * 射撃型の銃: ふだんは右手の片手持ちの小型銃（白い外装に水緑の光の線）と、腰の後ろに斜めに掛けたたたんだレールガン。
     * レールガンを出すと、背中のレールガンの部品（機関部・握り・銃床・2本のレール・光る芯・3つのコイル・照準・銃口）が
     * 順に弧を描いて手元へ飛び、そのまま組み上がる（飛んでいる間は光る）。溜めの間はレールの間とコイルが光り、しまうときは逆に背中へ戻る。
     */
    final class Gun extends RenderLayer<RuinAndroid, AndroidModel> {
        /**
         * レールガンの部品: 箱（画素の単位。+X が銃口、+Y が下（握りの側）、+Z が背負ったときの外側）・色・塗り方・飛び始める tick。
         * 塗り方は 0 がふつうの色、1 が光る水緑、2 が白っぽく光る水緑、3〜5 が順に脈打つコイルの帯。
         */
        private record Part(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int tone, int delay) {
        }

        /** 部品にくっついたぼんやりした光（部品といっしょに動く）。coil はどのコイルの脈に合わせるか（-1 は脈打たない）。 */
        private record Halo(int anchor, float x0, float y0, float z0, float x1, float y1, float z1, int alpha, int coil) {
        }

        private static final int WHITE = 0xE6EAF0, DARK = 0x363C4C, LIGHT = 0xCDD3DE;
        private static final int SOLID = 0, ACCENT = 1, HOT = 2, BAND = 3;
        /** 小型銃の大きさ（握りを中心に）。 */
        private static final float PISTOL_SCALE = 1.3F;
        /** 小型銃の銃身の軸の高さ（画素。上が負）。 */
        private static final float PISTOL_AXIS = 0.42F;
        /** 背中の留め具の位置（銃の長さの向き）。留め具は背中に残る。 */
        private static final float[] CLAMPS = {-2.6F, 4.5F};
        /** 部品が背中から手元へ飛ぶのにかかる tick。 */
        private static final float FLIGHT = 6;
        /** 銃の画素の座標から握りの座標へ: 銃口（+X）が握りの +Y、下（+Y）が握りの +Z、外側（+Z）が握りの +X。 */
        private static final Quaternionf TO_HAND = new Quaternionf().rotationAxis(Mth.TWO_PI / 3, 0.57735F, 0.57735F, 0.57735F);
        private static final Part[] PARTS;
        private static final Halo[] HALOS;
        private static final int CORE, LENS;
        private static final int[] COILS = new int[3];

        static {
            java.util.List<Part> parts = new java.util.ArrayList<>();
            // 面がぴったり重なるとちらつくので、つながる部品は少しずつ食い込ませる
            // 機関部: 黒い芯に白い装甲板を重ね、継ぎ目に光る線と放熱の溝
            parts.add(new Part(-4.6F, -1.05F, -0.95F, 1.4F, 1.05F, 0.95F, DARK, SOLID, 0));
            parts.add(new Part(-4.3F, -1.4F, -1.3F, 1.0F, 0.45F, 1.3F, WHITE, SOLID, 0));
            parts.add(new Part(-3.9F, 0.4F, -1.1F, 0.6F, 1.25F, 1.1F, LIGHT, SOLID, 0));
            parts.add(new Part(-3.6F, 0.5F, 1.05F, 0.3F, 0.7F, 1.2F, 0, ACCENT, 0));
            parts.add(new Part(-3.6F, 0.5F, -1.2F, 0.3F, 0.7F, -1.05F, 0, ACCENT, 0));
            for (int i = 0; i < 3; i++) {
                float x = -3.4F + i * 0.9F;
                parts.add(new Part(x, -0.95F, 1.25F, x + 0.45F, 0.05F, 1.38F, DARK, SOLID, 0));
                parts.add(new Part(x, -0.95F, -1.38F, x + 0.45F, 0.05F, -1.25F, DARK, SOLID, 0));
            }
            parts.add(new Part(-3.4F, 1.0F, -0.4F, -2.5F, 2.4F, 0.4F, DARK, SOLID, 0));             // 握り
            // 銃床（上下の棒と床尾板。間を抜いて奥行きを見せる）
            parts.add(new Part(-7.9F, -1.0F, -0.5F, -4.4F, -0.45F, 0.5F, LIGHT, SOLID, 2));
            parts.add(new Part(-7.8F, 0.45F, -0.4F, -4.4F, 0.95F, 0.4F, DARK, SOLID, 2));
            parts.add(new Part(-8.5F, -1.3F, -0.7F, -7.7F, 1.3F, 0.7F, DARK, SOLID, 2));
            parts.add(new Part(-8.6F, -0.9F, -0.3F, -8.4F, 0.9F, 0.3F, 0, ACCENT, 2));
            // 上下のレールと、その間の光る芯
            parts.add(new Part(1.2F, -1.0F, -0.6F, 8.2F, -0.5F, 0.6F, DARK, SOLID, 3));
            parts.add(new Part(1.2F, 0.5F, -0.6F, 8.2F, 1.0F, 0.6F, DARK, SOLID, 3));
            CORE = parts.size();
            parts.add(new Part(1.5F, -0.3F, -0.3F, 8.1F, 0.3F, 0.3F, 0, HOT, 4));
            // 照準器
            parts.add(new Part(-2.9F, -2.0F, -0.45F, -0.3F, -1.35F, 0.45F, DARK, SOLID, 5));
            LENS = parts.size();
            parts.add(new Part(-0.35F, -1.9F, -0.3F, -0.2F, -1.5F, 0.3F, 0, HOT, 5));
            // 角形コイル（銃身を四方から囲む）
            for (int i = 0; i < 3; i++) {
                float x0 = 2.3F + i * 1.85F, x1 = x0 + 0.85F;
                int d = 6 + i;
                COILS[i] = parts.size();
                parts.add(new Part(x0, -1.5F, -1.05F, x1, -0.95F, 1.05F, WHITE, SOLID, d));
                parts.add(new Part(x0, 0.95F, -1.05F, x1, 1.5F, 1.05F, WHITE, SOLID, d));
                parts.add(new Part(x0, -1.0F, 0.55F, x1, 1.0F, 1.0F, WHITE, SOLID, d));
                parts.add(new Part(x0, -1.0F, -1.0F, x1, 1.0F, -0.55F, WHITE, SOLID, d));
                parts.add(new Part(x0 + 0.2F, -0.75F, 0.95F, x1 - 0.2F, 0.75F, 1.1F, 0, BAND + i, d));
                parts.add(new Part(x0 + 0.2F, -0.75F, -1.1F, x1 - 0.2F, 0.75F, -0.95F, 0, BAND + i, d));
            }
            // 銃口（溝を切った制退器と、光る口）
            parts.add(new Part(8.0F, -1.3F, -0.9F, 9.0F, 1.3F, 0.9F, DARK, SOLID, 9));
            parts.add(new Part(8.3F, -1.45F, -1.0F, 8.6F, 1.45F, 1.0F, LIGHT, SOLID, 9));
            int tip = parts.size();
            parts.add(new Part(8.9F, -0.45F, -0.45F, 9.1F, 0.45F, 0.45F, 0, HOT, 9));
            PARTS = parts.toArray(Part[]::new);
            HALOS = new Halo[]{
                    new Halo(CORE, 1.55F, -0.42F, -0.5F, 7.95F, 0.42F, 0.5F, 130, -1),
                    new Halo(COILS[0], 2.25F, -1.6F, -1.15F, 3.2F, 1.6F, 1.15F, 90, 0),
                    new Halo(COILS[1], 4.1F, -1.6F, -1.15F, 5.05F, 1.6F, 1.15F, 90, 1),
                    new Halo(COILS[2], 5.95F, -1.6F, -1.15F, 6.9F, 1.6F, 1.15F, 90, 2),
                    new Halo(tip, 8.95F, -0.7F, -0.7F, 9.4F, 0.7F, 0.7F, 150, -1),
            };
        }

        Gun(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            AndroidModel m = getParentModel();
            if (m.gunGrip == null || !android.printed()) {
                return;
            }
            int state = android.state();
            float t = android.stateTime(partialTick);
            boolean rail = GunnerAndroid.railOut(state);
            float glow = state == RuinAndroid.STATE_LOW_POWER ? 0.3F : RuinAndroid.charging(state) ? 0.5F : 0.85F + 0.1F * Mth.sin(age * 0.2F);
            org.joml.Matrix4f back = stowedFrame(pose, m), hand = null;
            if (!rail && android.weaponStowed()) {
                // 物を作っている間: 小型銃は右の腰のホルスターに差す（銃口を下へ、握りを後ろへ）
                pose.pushPose();
                m.toHips(pose);
                pose.translate(-4.6F / 16, -1.2F / 16, 0.2F / 16);
                pose.mulPose(new Quaternionf().setFromNormalized(new org.joml.Matrix3f(0, 1, 0, 0, 0, 1, 1, 0, 0)));
                float s = PISTOL_SCALE / SCALE / 18;
                pose.scale(s, s, s);
                pose.translate(-0.6F, 0, 0);
                pistol(pose, buffers, light, glow, age, 0, -1);
                pose.popPose();
                railgun(pose, buffers, light, android, state, t, age, glow, back, null);
                return;
            }
            pose.pushPose();
            m.toBlade(pose);
            m.gunGrip.translateAndRotate(pose);
            float k = 1 / SCALE;
            pose.scale(k, k, k);
            GunnerAndroid gun = android instanceof GunnerAndroid g ? g : null;
            Vec3 origin = android.getPosition(partialTick);
            // 射撃型の仕草（リロード・一回し）の効き（0〜1。構えていない間だけ。始めと終わりはなめらかに）
            int kind = android.gesture();
            boolean reloading = kind == GunnerAndroid.GESTURE_RELOAD;
            float gestureTime = android.gestureTime(partialTick), gesture = 0;
            if (state == RuinAndroid.STATE_IDLE && (reloading || kind == GunnerAndroid.GESTURE_TWIRL)) {
                gesture = ease(Math.min(gestureTime / 5, (RuinAndroid.GESTURE_TICKS - gestureTime) / 5));
            }
            if (rail) {
                if (gun != null && state != GunnerAndroid.STATE_RAIL_ASSEMBLE) {
                    // しまう間も、部品が背中へ戻り終わるまでは狙いの向きのまま（撃った向きから急に向きが変わらない）
                    aimToward(pose, gun.railTo().subtract(origin), -0.045F, 60, switch (state) {
                        case GunnerAndroid.STATE_RAIL_CHARGE -> ease(t / 6);
                        case GunnerAndroid.STATE_RAIL_STOW -> 1 - ease((t - 7) / 3);
                        default -> 1;
                    });
                }
                // 銃の画素の座標へ（銃身の軸を z = -0.045、握りを手の中に）
                float p = 1 / 16F;
                pose.translate(0, 0, -0.045F);
                pose.mulPose(TO_HAND);
                pose.scale(p, p, p);
                pose.translate(2.95F, 0, 0);
                hand = new org.joml.Matrix4f(pose.last().pose());
                rememberMuzzle(pose, android, 9.1F, 0, 0, 1, 0, 0);
            } else {
                pose.scale(PISTOL_SCALE, PISTOL_SCALE, PISTOL_SCALE);
                if (gun != null && state == GunnerAndroid.STATE_SHOT) {
                    // 撃った光が見えている間は、必ず銃身を狙いへぴったり向ける（構えの途中で撃っても射線とそろう）
                    boolean firing = gun.shotFlash() > 0;
                    aimToward(pose, gun.aim().subtract(origin), -0.025F, firing ? 70 : 35, firing ? 1 : ease(t / 4));
                } else {
                    // 構えていないとき: 腕の動き（作業・歩き）にかかわらず、銃口を足元の少し前の床へ向けて下げておく
                    float yaw = Mth.rotLerp(partialTick, android.yBodyRotO, android.yBodyRot) * Mth.DEG_TO_RAD;
                    Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                    Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
                    Vec3 rel = fwd.scale(0.45).add(right.scale(0.25));
                    float lower = ease(t / 5);
                    if (gesture > 0) {
                        // 仕草の間: リロードは胸の前で少し上向きに、一回しは前へ向けて回したあと、銃口を真上へ立てる
                        Vec3 at = reloading ? new Vec3(0, 1.1, 0).add(fwd.scale(1.2)).subtract(right.scale(0.4))
                                : new Vec3(0, 1.1, 0).add(fwd.scale(1.5)).add(right.scale(0.4)).lerp(
                                new Vec3(0, 3.2, 0).add(fwd.scale(0.3)).add(right.scale(1.0)), ease((gestureTime - 13) / 5));
                        rel = rel.lerp(at, gesture);
                        lower = Math.max(lower, gesture);
                    }
                    aimToward(pose, rel, -0.025F, 90, lower);
                }
                // 銃の画素の座標へ（銃身の軸を z = -0.025、握りを手の中に）
                float p = 1 / 18F;
                pose.translate(0, 0, -0.025F);
                pose.mulPose(TO_HAND);
                pose.scale(p, p, p);
                pose.translate(0, PISTOL_AXIS, 0);
                if (gesture > 0 && !reloading) {
                    // 一回し: 用心金に掛けた指を軸に、銃を2回転させる
                    float spin = ease((gestureTime - 1) / 12);
                    pose.translate(1.2F, 1.0F, 0);
                    pose.mulPose(Axis.ZP.rotation(-Mth.TWO_PI * 2 * spin));
                    pose.translate(-1.2F, -1.0F, 0);
                }
                float flash = gun == null ? 0 : Mth.clamp((gun.shotFlash() - partialTick) / GunnerAndroid.FLASH, 0, 1);
                pistol(pose, buffers, light, glow, age, flash, reloading && gesture > 0 ? gestureTime : -1);
                rememberMuzzle(pose, android, 4.7F, -PISTOL_AXIS, 0, 1, 0, 0);
            }
            pose.popPose();
            railgun(pose, buffers, light, android, state, t, age, glow, back, hand);
        }

        /**
         * 銃身（+Y、銃身の軸は z = axisZ）を、狙いの点 rel（体の原点から）へぴったり向ける（手首で合わせる）。
         * 腕の向きで大まかに狙い、残りの差を手首で埋めるので、射線と銃の向きがそろう。曲げは maxDeg 度まで、k の割合で。
         */
        private void aimToward(PoseStack pose, Vec3 rel, float axisZ, float maxDeg, float k) {
            org.joml.Vector3f v = base.transformPosition(new org.joml.Vector3f((float) rel.x, (float) rel.y, (float) rel.z), new org.joml.Vector3f());
            new org.joml.Matrix4f(pose.last().pose()).invert().transformPosition(v);
            v.sub(0, 0, axisZ);
            if (v.lengthSquared() < 1e-6F || k <= 0) {
                return;
            }
            v.normalize();
            Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, v.x, v.y, v.z);
            float angle = (float) Math.acos(Mth.clamp(v.y, -1, 1));
            float limit = maxDeg * Mth.DEG_TO_RAD;
            float amount = k * (angle > limit ? limit / angle : 1);
            if (amount < 1) {
                q = new Quaternionf().slerp(q, amount);
            }
            pose.translate(0, 0, axisZ);
            pose.mulPose(q);
            pose.translate(0, 0, -axisZ);
        }

        /** 腰の後ろ（後ろ髪の下）に、銃口側（体の左）を少し上げて斜めに掛けたレールガンの、画素の座標の変形。 */
        private org.joml.Matrix4f stowedFrame(PoseStack pose, AndroidModel m) {
            pose.pushPose();
            m.toTorso(pose);
            float p = 1 / 16F;
            pose.translate(0, -1.9F * p, 2.85F * p);
            pose.mulPose(Axis.ZP.rotationDegrees(-14));
            pose.scale(p, p, p);
            org.joml.Matrix4f frame = new org.joml.Matrix4f(pose.last().pose());
            pose.popPose();
            return frame;
        }

        /** 部品の、背中から手元への進み（0 は背中、1 は手元）。 */
        private static float progress(int state, float t, int delay) {
            return switch (state) {
                case GunnerAndroid.STATE_RAIL_ASSEMBLE -> Mth.clamp((t - delay) / FLIGHT, 0, 1);
                case GunnerAndroid.STATE_RAIL_CHARGE, GunnerAndroid.STATE_RAIL_FIRE -> 1;
                case GunnerAndroid.STATE_RAIL_STOW -> 1 - Mth.clamp((t - (9 - delay) * 0.35F) / 4F, 0, 1);   // 銃口側から順に、7 tick ほどで戻り終わる
                default -> 0;
            };
        }

        /** 飛んでいる部品が回り込む、体の軸からの距離（ブロック）。胴と腕を避けて、体の外側を通る。 */
        private static final float CLEARANCE = 0.55F;

        /**
         * 飛んでいる部品の変形: 部品の中心が、体の縦の軸のまわりを背中の側から回り込んで、背中と手元の間を進む
         * （まっすぐ進むと胴を突き抜ける）。途中は軸から CLEARANCE 以上離れ、少し上へ弧を描く。
         * 向きは背中の向きと手元の向きの間を回り、途中で少し小さくなる。
         */
        private org.joml.Matrix4f flight(org.joml.Matrix4f from, org.joml.Matrix4f to, Part p, float d) {
            float e = ease(d), arc = Mth.sin(e * Mth.PI);
            org.joml.Vector3f c = new org.joml.Vector3f((p.x0 + p.x1) / 2, (p.y0 + p.y1) / 2, (p.z0 + p.z1) / 2);
            // 体の原点の座標（縦の軸が体の中心を通る）で、軸のまわりの角度・軸からの距離・高さを、それぞれつなぐ
            org.joml.Matrix4f toBody = new org.joml.Matrix4f(base).invert();
            org.joml.Vector3f a = toBody.transformPosition(from.transformPosition(c, new org.joml.Vector3f()));
            org.joml.Vector3f b = toBody.transformPosition(to.transformPosition(c, new org.joml.Vector3f()));
            org.joml.Vector3f behind = toBody.transformDirection(from.transformDirection(0, 0, 1, new org.joml.Vector3f()));
            float angleA = (float) Math.atan2(a.x, a.z), angleB = (float) Math.atan2(b.x, b.z);
            float back = (float) Math.atan2(behind.x, behind.z);
            // 回る向きは2通り: 途中の向きが背中に近いほうを通る（体の前を横切らない）
            float turn = Mth.wrapDegrees((angleB - angleA) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            float other = turn - Math.signum(turn) * Mth.TWO_PI;
            float offTurn = Math.abs(Mth.wrapDegrees((angleA + turn / 2 - back) * Mth.RAD_TO_DEG));
            float offOther = Math.abs(Mth.wrapDegrees((angleA + other / 2 - back) * Mth.RAD_TO_DEG));
            float angle = angleA + (offOther < offTurn ? other : turn) * e;
            float radius = Mth.lerp(e, (float) Mth.length(a.x, a.z), (float) Mth.length(b.x, b.z));
            radius += Math.max(0, CLEARANCE - radius) * arc;
            org.joml.Vector3f at = new org.joml.Vector3f(Mth.sin(angle) * radius, Mth.lerp(e, a.y, b.y) + 0.3F * arc, Mth.cos(angle) * radius);
            base.transformPosition(at);
            Quaternionf q = from.getNormalizedRotation(new Quaternionf()).slerp(to.getNormalizedRotation(new Quaternionf()), e);
            float s = Mth.lerp(e, from.getScale(new org.joml.Vector3f()).x, to.getScale(new org.joml.Vector3f()).x) * (1 - 0.3F * arc);
            return new org.joml.Matrix4f().translation(at).rotate(q).scale(s).translate(-c.x, -c.y, -c.z);
        }

        /** 変形 frame で描き始める（終わったら popPose）。 */
        private static void push(PoseStack pose, org.joml.Matrix4f frame) {
            pose.pushPose();
            pose.last().pose().set(frame);
            pose.last().normal().set(frame.getNormalizedRotation(new Quaternionf()));
        }

        /**
         * レールガン: 部品ごとに、背中・飛んでいる途中・手元のどこかに描く。背負い物があるときは、背中に残っている部品と留め具は描かない。
         * 光る部品は昼でも光って見えるように明るさを最大で描き、まわりにぼんやりした光を重ねる。充電切れ・充電中は光が弱まる。
         */
        private void railgun(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, int state, float t, float age,
                             float glow, org.joml.Matrix4f back, org.joml.Matrix4f hand) {
            boolean onBack = !android.hasBackpack();
            float[] done = new float[PARTS.length];
            org.joml.Matrix4f[] at = new org.joml.Matrix4f[PARTS.length];
            for (int i = 0; i < PARTS.length; i++) {
                done[i] = hand == null ? 0 : progress(state, t, PARTS[i].delay);
                at[i] = done[i] <= 0 ? (onBack ? back : null) : done[i] >= 1 ? hand : flight(back, hand, PARTS[i], done[i]);
            }
            int accent = mix(mix(DARK, variant.accent, 0.35F + 0.65F * glow), 0xFFFFFF, 0.12F * glow);
            int hot = mix(accent, 0xFFFFFF, 0.35F * glow);
            // 溜め: コイルが銃床側から1つずつ灯り（stage）、撃つと消えていく。ふだんは銃床側から銃口へ、順に光が走る（wave）
            float charge = state == GunnerAndroid.STATE_RAIL_CHARGE ? Mth.clamp(t / GunnerAndroid.RAIL_CHARGE, 0, 1)
                    : state == GunnerAndroid.STATE_RAIL_FIRE ? 1 - Mth.clamp(t / GunnerAndroid.RAIL_FIRE, 0, 1) : 0;
            boolean charging = hand != null && (state == GunnerAndroid.STATE_RAIL_CHARGE || state == GunnerAndroid.STATE_RAIL_FIRE);
            float[] wave = new float[3], stage = new float[3];
            for (int i = 0; i < 3; i++) {
                wave[i] = 0.5F + 0.5F * Mth.sin(age * 0.2F - i * 1.3F);
                stage[i] = state == GunnerAndroid.STATE_RAIL_CHARGE ? Mth.clamp((t - GunnerAndroid.RAIL_STAGES[i]) / 2, 0, 1) : charge;
            }
            // 狙いを固めたら、照準器のレンズが白く点滅する
            boolean locked = state == GunnerAndroid.STATE_RAIL_CHARGE && t >= GunnerAndroid.RAIL_CHARGE - GunnerAndroid.RAIL_LOCK;
            boolean blink = locked && ((int) (age * 0.8F)) % 2 == 0;
            // 先に形（同じ描き方をまとめて描く。描き方を混ぜると、前の描き方の受け口が閉じられる）
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            if (onBack) {
                push(pose, back);
                for (float x : CLAMPS) {
                    FxDraw.box(pose, body, x - 0.6F, -0.8F, -1.6F, x + 0.6F, 0.8F, -0.9F, light, DARK, 255);   // 留め具
                }
                pose.popPose();
            }
            for (int i = 0; i < PARTS.length; i++) {
                Part p = PARTS[i];
                if (at[i] != null && p.tone == SOLID) {
                    push(pose, at[i]);
                    FxDraw.box(pose, body, p.x0, p.y0, p.z0, p.x1, p.y1, p.z1, light, p.rgb, 255);
                    pose.popPose();
                }
            }
            // 光る部品は、陰のつかない描き方で
            VertexConsumer shine = buffers.getBuffer(Shine.TYPE);
            if (onBack) {
                push(pose, back);
                for (float x : CLAMPS) {
                    glowBox(pose, shine, x - 0.65F, -0.2F, -1.5F, x + 0.65F, 0.2F, -1.0F, accent, 255);
                }
                pose.popPose();
            }
            for (int i = 0; i < PARTS.length; i++) {
                Part p = PARTS[i];
                if (at[i] == null || p.tone == SOLID) {
                    continue;
                }
                int rgb = switch (p.tone) {
                    case ACCENT -> accent;
                    case HOT -> i == LENS && blink ? 0xFFFFFF : hot;
                    default -> charging ? mix(DARK, mix(hot, 0xFFFFFF, 0.5F), 0.15F + 0.85F * stage[p.tone - BAND])
                            : mix(DARK, hot, 0.3F + 0.7F * wave[p.tone - BAND] * glow);
                };
                push(pose, at[i]);
                glowBox(pose, shine, p.x0, p.y0, p.z0, p.x1, p.y1, p.z1, rgb, 255);
                pose.popPose();
            }
            // 次にぼんやりした光
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            for (Halo h : HALOS) {
                if (at[h.anchor] != null) {
                    push(pose, at[h.anchor]);
                    float k = h.coil < 0 ? 1 : charging ? stage[h.coil] : wave[h.coil];
                    glowBox(pose, fx, h.x0, h.y0, h.z0, h.x1, h.y1, h.z1, variant.accent, (int) (h.alpha * glow * k));
                    pose.popPose();
                }
            }
            // 飛んでいる部品は光に包まれている
            for (int i = 0; i < PARTS.length; i++) {
                if (done[i] > 0 && done[i] < 1) {
                    Part p = PARTS[i];
                    float g = 0.35F;
                    push(pose, at[i]);
                    glowBox(pose, fx, p.x0 - g, p.y0 - g, p.z0 - g, p.x1 + g, p.y1 + g, p.z1 + g, variant.accent, (int) (170 * (1 - done[i] * 0.6F)));
                    pose.popPose();
                }
            }
            // 溜めと発射: 灯ったコイルが光に包まれ（灯った瞬間は大きく広がる）、レールの間を光が満たしていく
            if (charge > 0 && hand != null) {
                float pulse = 0.85F + 0.15F * Mth.sin(age * (1.2F + 2F * charge));
                push(pose, hand);
                for (int i = 0; i < 3; i++) {
                    if (stage[i] <= 0) {
                        continue;
                    }
                    float x0 = 2.3F + i * 1.85F;
                    float pop = state == GunnerAndroid.STATE_RAIL_CHARGE ? Math.max(0, 1 - (t - GunnerAndroid.RAIL_STAGES[i]) / 5) : 0;
                    float g = 0.25F + 0.2F * charge + 0.6F * pop;
                    glowBox(pose, fx, x0 - g, -1.5F - g, -1.05F - g, x0 + 0.85F + g, 1.5F + g, 1.05F + g, variant.accent,
                            (int) Math.min(255, 160 * stage[i] * pulse + 90 * pop));
                }
                glowBox(pose, fx, 1.55F, -0.46F, -0.52F, 7.95F, 0.46F, 0.52F, variant.accent, (int) (200 * charge * pulse));
                if (locked) {
                    glowBox(pose, fx, -0.6F, -2.2F, -0.7F, 0.1F, -1.2F, 0.7F, blink ? 0xFFFFFF : variant.accent, blink ? 220 : 120);
                }
                pose.popPose();
            }
            // 最後に白い芯
            VertexConsumer core = buffers.getBuffer(GlowCore.TYPE);
            if (at[CORE] != null) {
                push(pose, at[CORE]);
                glowBox(pose, core, 1.6F, -0.12F, -0.12F, 8.0F, 0.12F, 0.12F, 0xFFFFFF, (int) (160 * glow));
                pose.popPose();
            }
            if (charge > 0 && hand != null) {
                push(pose, hand);
                glowBox(pose, core, 1.6F, -0.2F, -0.2F, 9.2F, 0.2F, 0.2F, 0xFFFFFF, (int) (240 * charge));
                pose.popPose();
            }
        }

        /**
         * 片手持ちの小型銃（画素の単位。+X が銃口、+Y が下（握りの側）、+Z が横）。白い遊底に光る線、銃口の手前に脈打つコイル、
         * 握りの背に光るエネルギーの窓、握りの底にエネルギーカートリッジ。撃った直後は銃口が強く光る。
         * reload: リロード（カートリッジの交換）の仕草の tick（-1 はしていない）。空のカートリッジが抜け落ち、新しいカートリッジが
         * 飛んできて押し込まれ、握りの窓が下から満ちて、満ちた瞬間に強く光る。
         * 面がぴったり重なるとちらつくので、つながる部品は少し食い込ませる。
         */
        private void pistol(PoseStack pose, MultiBufferSource buffers, int light, float glow, float age, float flash, float reload) {
            boolean reloading = reload >= 0;
            float fill = !reloading ? 1 : reload < GunnerAndroid.RELOAD_EJECT + 2 ? 1 - ease((reload - GunnerAndroid.RELOAD_EJECT) / 2)
                    : ease((reload - GunnerAndroid.RELOAD_INSERT - 1) / (GunnerAndroid.RELOAD_DONE - GunnerAndroid.RELOAD_INSERT - 1));
            float filled = reloading ? Math.max(0, 1 - Math.abs(reload - GunnerAndroid.RELOAD_DONE) / 4) : 0;
            int accent = mix(mix(DARK, variant.accent, 0.35F + 0.65F * glow), 0xFFFFFF, 0.12F * glow);
            int hot = mix(accent, 0xFFFFFF, Math.min(1, 0.35F * glow + 0.6F * flash));
            float wave = 0.5F + 0.5F * Mth.sin(age * 0.25F);
            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            FxDraw.box(pose, body, -1.0F, -0.1F, -0.45F, 3.6F, 0.7F, 0.45F, light, DARK, 255);          // 骨組み
            FxDraw.box(pose, body, -1.3F, -0.95F, -0.55F, 3.4F, 0.0F, 0.55F, light, WHITE, 255);        // 遊底
            FxDraw.box(pose, body, -0.8F, -1.15F, -0.3F, 2.8F, -0.9F, 0.3F, light, LIGHT, 255);
            FxDraw.box(pose, body, -1.3F, -1.25F, -0.35F, -0.7F, -0.9F, 0.35F, light, DARK, 255);       // 照門
            FxDraw.box(pose, body, 3.3F, -0.75F, -0.4F, 4.6F, -0.1F, 0.4F, light, DARK, 255);           // 銃身の先
            FxDraw.box(pose, body, 3.7F, -0.95F, -0.55F, 4.1F, 0.1F, 0.55F, light, WHITE, 255);         // コイル
            FxDraw.box(pose, body, -0.7F, 0.5F, -0.4F, 0.5F, 2.8F, 0.4F, light, DARK, 255);             // 握り
            FxDraw.box(pose, body, -0.8F, 2.7F, -0.45F, 0.6F, 2.95F, 0.45F, light, LIGHT, 255);
            FxDraw.box(pose, body, 0.4F, 1.25F, -0.15F, 2.0F, 1.45F, 0.15F, light, DARK, 255);          // 用心金
            FxDraw.box(pose, body, 1.85F, 0.6F, -0.15F, 2.05F, 1.35F, 0.15F, light, DARK, 255);
            FxDraw.box(pose, body, 0.9F, 0.6F, -0.08F, 1.1F, 1.1F, 0.08F, light, LIGHT, 255);           // 引き金
            for (boolean spent : new boolean[]{false, true}) {
                if (cartridge(pose, reload, spent)) {
                    FxDraw.box(pose, body, -0.45F, 0, -0.28F, 0.25F, 1.9F, 0.28F, light, DARK, 255);         // カートリッジ
                    FxDraw.box(pose, body, -0.55F, 1.9F, -0.35F, 0.35F, 2.15F, 0.35F, light, LIGHT, 255);
                    pose.popPose();
                }
            }
            // 光る部品は、陰のつかない描き方で
            VertexConsumer shine = buffers.getBuffer(Shine.TYPE);
            int line = mix(DARK, mix(accent, 0xFFFFFF, 0.6F * filled), 0.25F + 0.75F * fill);     // リロード中は暗く、満ちると光る
            glowBox(pose, shine, -0.6F, -0.55F, 0.5F, 2.9F, -0.35F, 0.6F, line, 255);            // 光る線
            glowBox(pose, shine, -0.6F, -0.55F, -0.6F, 2.9F, -0.35F, -0.5F, line, 255);
            int band = mix(DARK, hot, 0.3F + 0.7F * wave * glow);
            glowBox(pose, shine, 3.75F, -0.7F, 0.5F, 4.05F, -0.15F, 0.6F, band, 255);
            glowBox(pose, shine, 3.75F, -0.7F, -0.6F, 4.05F, -0.15F, -0.5F, band, 255);
            glowBox(pose, shine, 4.5F, -0.6F, -0.2F, 4.7F, -0.25F, 0.2F, hot, 255);              // 銃口
            for (boolean spent : new boolean[]{false, true}) {
                if (cartridge(pose, reload, spent)) {
                    // カートリッジの光る芯（空のカートリッジは消えている）
                    int core = spent ? mix(DARK, accent, 0.15F) : accent;
                    glowBox(pose, shine, -0.3F, 0.3F, 0.26F, 0.1F, 1.6F, 0.32F, core, 255);
                    glowBox(pose, shine, -0.3F, 0.3F, -0.32F, 0.1F, 1.6F, -0.26F, core, 255);
                    pose.popPose();
                }
            }
            if (fill > 0) {
                // エネルギーの窓（下から満ちる）
                glowBox(pose, shine, -0.75F, 2.4F - 1.5F * fill, -0.2F, -0.55F, 2.4F, 0.2F, mix(accent, 0xFFFFFF, 0.6F * filled), 255);
            }
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            glowBox(pose, fx, 3.65F, -1.05F, -0.65F, 4.15F, 0.2F, 0.65F, variant.accent, (int) (90 * wave * glow));
            glowBox(pose, fx, 4.55F, -0.75F - 0.3F * flash, -0.35F - 0.3F * flash, 4.95F + 0.6F * flash, -0.1F + 0.3F * flash,
                    0.35F + 0.3F * flash, variant.accent, (int) Math.min(255, 140 * glow + 110 * flash));
            glowBox(pose, fx, -0.85F, 2.5F - 1.7F * fill, -0.3F, -0.6F, 2.5F, 0.3F, variant.accent, (int) (80 * glow * fill + 150 * filled));
            if (reloading && reload < GunnerAndroid.RELOAD_INSERT + 1 && cartridge(pose, reload, false)) {
                glowBox(pose, fx, -0.6F, -0.15F, -0.45F, 0.4F, 2.3F, 0.45F, variant.accent, (int) (130 * glow));   // 運ばれてくる新しいカートリッジ
                pose.popPose();
            }
        }

        /** カートリッジを握りに押し込んだときの、カートリッジの上端の高さ（画素）。 */
        private static final float CARTRIDGE_IN = 0.95F;

        /**
         * カートリッジ（上端の中央が原点）を描く位置へ変形を積む（描かないときは積まずに false）。積んだら popPose すること。
         * spent: 抜け落ちる空のカートリッジ（リロードの始めだけ見える）。そうでなければ、握りに入っているカートリッジ
         * （リロード中は、左手の側から飛んできて、握りの下で向きをそろえ、下から押し込まれる）。
         */
        private static boolean cartridge(PoseStack pose, float reload, boolean spent) {
            float x = 0, y = CARTRIDGE_IN, z = 0, turn = 0, tilt = 0, size = 1;
            if (spent) {
                float d = reload - GunnerAndroid.RELOAD_EJECT;
                if (reload < 0 || d < 0 || d > 7) {
                    return false;
                }
                // 握りから抜けて、くるりと回りながら落ちていく
                y += 2.2F * ease(d / 2) + (d > 2 ? 0.4F * (d - 2) * (d - 2) : 0);
                turn = d > 2 ? -0.45F * (d - 2) : 0;
                size = 1 - 0.5F * Mth.clamp((d - 4) / 3, 0, 1);
            } else if (reload >= 0 && reload < GunnerAndroid.RELOAD_INSERT + 1) {
                float start = 5, under = GunnerAndroid.RELOAD_INSERT - 1;
                if (reload < start) {
                    return false;               // まだ左手の中: 空のカートリッジが抜けたところ
                }
                if (reload < under) {
                    // 左手の側の下から、握りの真下へ。向きもそろえていく
                    float u = ease((reload - start) / (under - start));
                    x = Mth.lerp(u, -1.0F, 0);
                    y = Mth.lerp(u, CARTRIDGE_IN + 5.5F, CARTRIDGE_IN + 2.4F);
                    z = Mth.lerp(u, 4.0F, 0);
                    tilt = (1 - u) * 1.1F;
                } else {
                    // 下から、ぐっと押し込む
                    y = Mth.lerp(ease((reload - under) / 2), CARTRIDGE_IN + 2.4F, CARTRIDGE_IN);
                }
            }
            pose.pushPose();
            pose.translate(x - 0.1F, y + 1, z);
            pose.mulPose(Axis.ZP.rotation(turn));
            pose.mulPose(Axis.XP.rotation(tilt));
            pose.scale(size, size, size);
            pose.translate(0.1F, -1, 0);
            return true;
        }
    }

    /** ビット型の、背中にしまったビット（浮かべていない間）。背中の上に、翼のように並ぶ。 */
    final class StowedBits extends RenderLayer<RuinAndroid, AndroidModel> {
        StowedBits(RenderLayerParent<RuinAndroid, AndroidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RuinAndroid android, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float netHeadYaw, float headPitch) {
            if (!(android instanceof BitAndroid bit) || !android.printed()) {
                return;
            }
            int out = bit.recallingFrom(partialTick);          // 戻っていく途中のビットは、着くまで背中に描かない
            if (bit.state() == BitAndroid.STATE_DEPLOY && bit.stateTime(partialTick) < BitAndroid.DEPLOY / 2F) {
                out = 0;                                  // 送り出す前は、まだ背中にある
            }
            if (out >= BitAndroid.BITS) {
                return;
            }
            pose.pushPose();
            getParentModel().toTorso(pose);
            float k = 1 / SCALE;
            // 別の描画先を取り出すと前の描画先は閉じられるので、本体を全部描いてから光を描く
            VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
            for (int i = out; i < BitAndroid.BITS; i++) {
                stowed(pose, i, k);
                bitBody(pose, solid, light);
                pose.popPose();
            }
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            for (int i = out; i < BitAndroid.BITS; i++) {
                stowed(pose, i, k);
                glowBox(pose, fx, -0.018F, -0.018F, -0.1F, 0.018F, 0.018F, 0.1F, variant.accent,
                        (int) (110 + 50 * Mth.sin(age * 0.15F + i)));
                pose.popPose();
            }
            pose.popPose();
        }

        /** しまったビット i の位置へ（pushPose する）。左右3基ずつ、背中の上から外へ斜めに開く（先が上）。 */
        private void stowed(PoseStack pose, int i, float k) {
            float p = 1 / 16F;
            int side = i % 2 == 0 ? 1 : -1;
            int row = i / 2;
            pose.pushPose();
            pose.translate(side * (1.2F + row * 0.9F) * p, (-6.6F + row * 1.1F) * p, 2.1F * p);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (25 + row * 18)));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(k * 0.8F, k * 0.8F, k * 0.8F);
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
            if (m.blade == null || android.weaponStowed()) {
                return;
            }
            int state = android.state();
            float k = state == RuinAndroid.STATE_LOW_POWER ? 0.35F : state == BladeAndroid.STATE_DASH_WINDUP
                    || state == BladeAndroid.STATE_DASH ? 1F : 0.8F + 0.08F * Mth.sin(ageInTicks * 0.3F);
            pose.pushPose();
            m.toBlade(pose);
            m.blade.visible = true;
            m.blade.render(pose, buffers.getBuffer(DepthGlow.TYPE.apply(variant.texture)), FxDraw.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, argb((int) (255 * k), 0xFFFFFF));
            m.blade.visible = false;
            // 刃のまわりの、ほのかな光（刃より少しだけ大きい、薄い光の箱を2重に。刃の明るさに合わせて息づく）
            m.blade.translateAndRotate(pose);
            float p = 1 / 16F;
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            glowBox(pose, fx, -0.9F * p, -0.4F * p, -1.4F * p, 0.9F * p, 14.9F * p, 1.4F * p, variant.accent, (int) (38 * k));
            glowBox(pose, fx, -1.5F * p, -0.8F * p, -2.0F * p, 1.5F * p, 15.3F * p, 2.0F * p, variant.accent, (int) (16 * k));
            pose.popPose();
        }
    }
}
