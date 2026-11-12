package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.CargoDrone;
import io.github.genichimaruo.singulo.drone.DroneKind;
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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 輸送ドローン。型（{@link DroneKind}）ごとにモデル・絵・光・効果が違う。どれも梱包箱を足もとに吊るす（箱の底がドローンの足もと）。
 * <ul>
 *   <li>ローター型: 平たい白い胴に橙の帯と前の単眼、斜めの4本の腕の先で回るローター、腕から下がる4本の爪。進む向きへ前に傾く。</li>
 *   <li>ジェット型: 黒鉛色の細い胴に青い線、後ろへ流れた主翼、外へ開いた2枚の尾翼、主翼の上の2つのエンジン。噴射口から青白い炎を
 *       出し（速いほど長い）、曲がる向きへ機体を傾け、上るときは機首を上げる。腹の2本の横棒の先の爪で箱をはさむ。</li>
 *   <li>反重力型: 頂点を上にした暗い結晶の核がゆっくり回り、そのまわりを八角形の光輪と、4つの浮かぶビットが回る。箱は爪で持たず、
 *       ビットが箱の四隅の上に並んで牽引の光を当て、箱を青緑の場で包む。空のときは足もとに浮かせる場の輪が広がって消える。</li>
 * </ul>
 * 印刷中は下から積み上がる。
 */
public class CargoDroneRenderer extends MobRenderer<CargoDrone, CargoDroneRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("cargo_drone"), "main");
    public static final ModelLayerLocation JET_LAYER = new ModelLayerLocation(Singulo.id("cargo_drone_jet"), "main");
    public static final ModelLayerLocation GRAVITY_LAYER = new ModelLayerLocation(Singulo.id("cargo_drone_gravity"), "main");
    /** 型ごとの絵の名前（textures/entity/ の下。光る線は _glow）。 */
    private static final String[] SKINS = {"cargo_drone", "cargo_drone_jet", "cargo_drone_gravity"};
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[SKINS.length];
    private static final RenderType[] GLOWS = new RenderType[SKINS.length];
    private static final RenderType[] SHELLS = new RenderType[SKINS.length];
    /** 箱の番号（DroneCrateItem.displayTier）ごとのアイテム: 梱包箱3段階と、液体カプセル3段階。 */
    private static final String[] CRATES = {"", "packing_crate", "battery_packing_crate", "large_battery_packing_crate",
            "fluid_capsule", "battery_fluid_capsule", "large_battery_fluid_capsule"};
    /** 箱の半分の高さ（箱の絵は 10/16 の立方体）。 */
    static final float CRATE_HALF = 5 / 16F;

    static {
        for (int i = 0; i < SKINS.length; i++) {
            TEXTURES[i] = Singulo.id("textures/entity/" + SKINS[i] + ".png");
            GLOWS[i] = RenderType.eyes(Singulo.id("textures/entity/" + SKINS[i] + "_glow.png"));
            SHELLS[i] = PhaseShellType.of(SKINS[i], TEXTURES[i]);
        }
    }

    private final ItemRenderer items;
    private final Model[] models;

    public CargoDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new RotorModel(context.bakeLayer(LAYER)), 0.35F);
        models = new Model[]{model, new JetModel(context.bakeLayer(JET_LAYER)), new GravityModel(context.bakeLayer(GRAVITY_LAYER))};
        items = context.getItemRenderer();
        addLayer(new Glow(this));
        addLayer(new Rotors(this));
        addLayer(new JetFlames(this));
        addLayer(new GravityField(this));
        addLayer(new PhaseShell(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CargoDrone drone) {
        return TEXTURES[drone.kind().ordinal()];
    }

    static ItemStack crate(int tier) {
        return tier <= 0 || tier >= CRATES.length ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(CRATES[tier])));
    }

    @Override
    public void render(CargoDrone drone, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = models[drone.kind().ordinal()];
        float print = drone.printProgress(partialTick);
        renderArmAtPad(drone, print, partialTick, pose, buffers, light);
        if (print < 1) {
            float plane = pose.last().pose().m31() + print * (drone.getBbHeight() + 0.1F);
            super.render(drone, yaw, partialTick, pose, ClipBuffers.below(buffers, plane), light);
            renderScan(drone, print, pose, buffers);
            return;
        }
        super.render(drone, yaw, partialTick, pose, buffers, light);
        ItemStack crate = drone.cargo().isEmpty() ? crate(drone.crateTier()) : drone.cargo();
        if (!crate.isEmpty()) {
            pose.pushPose();
            float body = Mth.rotLerp(partialTick, drone.yBodyRotO, drone.yBodyRot);
            pose.translate(0, CRATE_HALF, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - body));
            // 機体と同じ傾き。箱は吊るしている高さを軸に揺れる
            pose.translate(0, CRATE_HALF, 0);
            attitude(drone, partialTick, pose);
            pose.translate(0, -CRATE_HALF, 0);
            items.renderStatic(crate, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, drone.level(), drone.getId());
            pose.popPose();
        }
    }

    /**
     * 飛ぶ姿勢（モデルと箱で同じ）。ローター型は進む向きへ前に傾く。ジェット型は曲がる向きへ横に傾き、上るときは機首を上げる。
     * 反重力型は浮いているので、少しだけ傾く。
     */
    static void attitude(CargoDrone drone, float partialTick, PoseStack pose) {
        double dx = drone.getX() - drone.xo;
        double dz = drone.getZ() - drone.zo;
        double dy = drone.getY() - drone.yo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        // この座標では前が -Z（X 軸まわりの負の向きで、機首が下がる）
        switch (drone.kind()) {
            case JET -> {
                pose.mulPose(Axis.ZP.rotationDegrees(-drone.bank(partialTick)));
                pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp((float) dy * 70, -22, 22) - Math.min(5, speed * 6)));
            }
            case GRAVITY -> pose.mulPose(Axis.XP.rotationDegrees(-Math.min(7, speed * 12)));
            default -> pose.mulPose(Axis.XP.rotationDegrees(-Math.min(18, speed * 40)));
        }
    }

    /**
     * 印刷の腕は、印刷した場所（ステーションの上面）に置いたまま描く。印刷中は印刷している高さ、印刷し終えたら RETRACT_TICKS で
     * ステーションの中へ沈む（ドローンが飛び立っても腕は残る）。解体のときは逆に、まず中から伸びてきて、それから印刷の高さを下りる。
     */
    private void renderArmAtPad(CargoDrone drone, float print, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int ticks = drone.printTicks();
        int end = CargoDrone.PRINT_TICKS + CargoDrone.RETRACT_TICKS;
        if (ticks >= end) {
            return;
        }
        double x = Mth.lerp(partialTick, drone.xo, drone.getX());
        double y = Mth.lerp(partialTick, drone.yo, drone.getY());
        double z = Mth.lerp(partialTick, drone.zo, drone.getZ());
        if (ticks < CargoDrone.PRINT_TICKS || drone.dismantling()) {
            drone.armOrigin = new Vec3(x, y, z);                     // 上面にいる間の場所を覚える
        }
        if (drone.armOrigin == null) {
            return;                                                   // 腕をしまう途中から見え始めた
        }
        float full = drone.getBbHeight() + 0.2F;
        float top;
        if (ticks < CargoDrone.PRINT_TICKS) {
            top = print * (drone.getBbHeight() + 0.1F) + 0.1F;
        } else {
            float k = (ticks - CargoDrone.PRINT_TICKS + (drone.dismantling() ? -partialTick : partialTick)) / CargoDrone.RETRACT_TICKS;
            top = Mth.lerp(Mth.clamp(k, 0, 1), full, -0.35F);
        }
        pose.pushPose();
        pose.translate(drone.armOrigin.x - x, drone.armOrigin.y - y, drone.armOrigin.z - z);
        renderPrintArm(drone, top, partialTick, pose, buffers, light);
        pose.popPose();
    }

    /**
     * 印刷の腕: ステーションの上面の四隅の1つ（北西）から鋼の棒が伸びる。先端には寝かせた小さな L 字の印刷ヘッドがあり、
     * ドローンの上を左右に振れながら、先のノズルから下の層へ橙の光を当てる。
     */
    private void renderPrintArm(CargoDrone drone, float top, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = drone.tickCount + partialTick;
        float cx = -0.44F, cz = -0.44F, rod = 0.03F;
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
        // 上面の隅の受け口と、伸びる棒
        FxDraw.box(pose, solid, cx - 0.06F, 0, cz - 0.06F, cx + 0.06F, 0.03F, cz + 0.06F, light, 0x4E5560);
        if (top + 0.02F > 0) {
            FxDraw.box(pose, solid, cx - rod, 0, cz - rod, cx + rod, top + 0.02F, cz + rod, light, 0xAEB6C0);
        }
        // 寝かせた L 字のヘッド（棒を軸に、ドローンの真上を中心に振れる）
        pose.pushPose();
        pose.translate(cx, top, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-45 + 35 * Mth.sin(t * 0.3F)));
        FxDraw.box(pose, solid, -0.04F, -0.02F, -0.03F, 0.46F, 0.03F, 0.03F, light, 0x58606C);
        FxDraw.box(pose, solid, 0.40F, -0.02F, -0.03F, 0.46F, 0.03F, 0.18F, light, 0x58606C);
        FxDraw.box(pose, solid, 0.39F, -0.06F, 0.12F, 0.47F, -0.02F, 0.20F, light, 0x2E333A);
        VertexConsumer glow = GlowDepth.of(buffers, FxDraw.WHITE);
        int pulse = 180 + (int) (60 * Mth.sin(t * 1.3F));
        FxDraw.box(pose, glow, 0.41F, -0.07F, 0.14F, 0.45F, -0.06F, 0.18F, FxDraw.FULL_BRIGHT, DroneStationScreen.ACCENT, pulse);
        FxDraw.box(pose, glow, 0.425F, -0.1F, 0.155F, 0.435F, -0.07F, 0.165F, FxDraw.FULL_BRIGHT, 0xFFE2B0, 200);
        pose.popPose();
    }

    /** 印刷の走査面: 橙の光る薄い板が下から上がる。 */
    private void renderScan(CargoDrone drone, float print, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = GlowDepth.of(buffers, FxDraw.WHITE);
        float y = print * (drone.getBbHeight() + 0.1F);
        float r = 0.42F;
        int alpha = 120 + (int) (60 * Mth.sin((drone.tickCount) * 0.8F));
        FxDraw.box(pose, vc, -r, y, -r, r, y + 0.02F, r, FxDraw.FULL_BRIGHT, DroneStationScreen.ACCENT, alpha);
    }

    @Override
    protected void setupRotations(CargoDrone drone, PoseStack pose, float bob, float yaw, float partialTick) {
        super.setupRotations(drone, pose, bob, yaw, partialTick);
        if (drone.printProgress(partialTick) >= 1) {
            pose.translate(0, 0.85F, 0);
            attitude(drone, partialTick, pose);
            pose.translate(0, -0.85F, 0);
        }
    }

    /** 飛んでいるか（印刷し終えて、解体していない）。羽根・炎・場はこの間だけ。 */
    private static boolean flying(CargoDrone drone) {
        return drone.printed() && !drone.dismantling();
    }

    /** 光る線（型ごとの _glow の絵）。 */
    static final class Glow extends RenderLayer<CargoDrone, Model> {
        Glow(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(GLOWS[drone.kind().ordinal()]), FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    /**
     * ローター: 2枚の羽根（薄い箱。裏の面を描かないので、上下の面が重なってちらつかない。2枚目は少し上にずらす）と、
     * 回っている羽根の残像のうすい円盤。モデルの羽根（厚さ0の板）は、両面が同じ位置で描かれてちらつくので使わない。
     */
    static final class Rotors extends RenderLayer<CargoDrone, Model> {
        private static final int BLADE = 0x4E525C, TIP = 0xF0A040;

        Rotors(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!flying(drone) || !(getParentModel() instanceof RotorModel rotor)) {
                return;
            }
            VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
            float u = 1 / 16F;
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                rotor.rotorSpace(i, pose);
                for (int b = 0; b < 2; b++) {
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotation(b * Mth.HALF_PI));
                    float y = b * 0.2F * u;
                    FxDraw.box(pose, solid, -3.5F * u, y - 0.08F * u, -0.45F * u, 3.5F * u, y + 0.08F * u, 0.45F * u, light, BLADE);
                    FxDraw.box(pose, solid, 3.0F * u, y - 0.1F * u, -0.5F * u, 3.6F * u, y + 0.1F * u, 0.5F * u, light, TIP);
                    FxDraw.box(pose, solid, -3.6F * u, y - 0.1F * u, -0.5F * u, -3.0F * u, y + 0.1F * u, 0.5F * u, light, TIP);
                    pose.popPose();
                }
                pose.popPose();
            }
            // 残像の円盤は、羽根を描き終えてから（別の描画先を取ると、羽根の描画先が閉じられる）
            VertexConsumer blur = buffers.getBuffer(RotorBlur.TYPE);
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                rotor.rotorSpace(i, pose);
                pose.translate(0, 0.45F * u, 0);
                FxDraw.ring(pose.last(), blur, 0.6F * u, 3.7F * u, 0xC8CCD4, 40, 18);
                pose.popPose();
            }
        }
    }

    /**
     * ジェットの炎: 2つの噴射口から後ろへ、青い外炎と白い芯の円錐（速いほど長く、細かくゆらぐ）。噴射口の奥も光る。
     * 光を足して描き、奥行きを書かない（あとから描かれる透ける物を消さない）。
     */
    static final class JetFlames extends RenderLayer<CargoDrone, Model> {
        private static final int OUTER = 0x3F8CFF, CORE = 0xE6F6FF;

        JetFlames(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!flying(drone) || !(getParentModel() instanceof JetModel jet)) {
                return;
            }
            double dx = drone.getX() - drone.xo, dy = drone.getY() - drone.yo, dz = drone.getZ() - drone.zo;
            float power = 0.3F + 0.7F * Mth.clamp((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.9F, 0, 1);
            VertexConsumer fx = buffers.getBuffer(Glare.TYPE);
            float u = 1 / 16F;
            for (int i = 0; i < 2; i++) {
                float phase = drone.getId() * 1.3F + i * 2.1F;
                float flicker = 0.85F + 0.1F * Mth.sin(ageInTicks * 2.3F + phase) + 0.05F * Mth.sin(ageInTicks * 5.1F + phase);
                float length = (0.12F + 0.55F * power) * flicker;
                pose.pushPose();
                jet.nozzleSpace(i, pose);
                // 噴射口の奥の光る円盤
                FxDraw.box(pose, fx, -1.3F * u, -1.3F * u, -0.05F * u, 1.3F * u, 1.3F * u, 0.02F * u, FxDraw.FULL_BRIGHT, CORE, (int) (140 + 90 * power));
                pose.mulPose(Axis.XP.rotationDegrees(90));                // 円錐の +Y を機体の後ろ（+Z）へ
                cone(pose, fx, 1.4F * u, length, OUTER, (int) (90 + 90 * power));
                cone(pose, fx, 0.7F * u, length * 0.55F, CORE, (int) (140 + 100 * power));
                pose.popPose();
            }
        }
    }

    /**
     * 反重力の場: 箱を持っている間は、4つのビットの先から箱の上の四隅へ牽引の光を当て、箱を青緑の場で包む（ゆっくり明滅）。
     * 空のときは、足もとに浮かせる場のうすい輪が、広がりながら下りて消える。核・ビットの先・放射板のまわりは、外へぼんやりと光る。
     */
    static final class GravityField extends RenderLayer<CargoDrone, Model> {
        static final int TEAL = 0x5CFFE0, TEAL_DEEP = 0x1FB5A8, TEAL_HI = 0xD2FFF6;

        GravityField(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!flying(drone) || !(getParentModel() instanceof GravityModel g)) {
                return;
            }
            VertexConsumer fx = buffers.getBuffer(Glare.TYPE);
            float u = 1 / 16F;
            float pulse = 0.8F + 0.2F * Mth.sin(ageInTicks * 0.25F);
            if (drone.crateTier() > 0) {
                for (int i = 0; i < 4; i++) {
                    PoseStack local = new PoseStack();
                    g.tipSpace(i, local);
                    Vector3f tip = local.last().pose().transformPosition(new Vector3f(0, 4.6F * u, 0));
                    float a = Mth.PI / 4 + i * Mth.HALF_PI;
                    Vec3 corner = new Vec3(Mth.sin(a) * 5.2F * u, 14.2F * u, Mth.cos(a) * 5.2F * u);
                    Vec3 from = new Vec3(tip.x(), tip.y(), tip.z());
                    FxDraw.beam(pose.last(), fx, from, corner, 0.35F * u, TEAL, (int) (110 * pulse));
                }
                // 箱を包む場（箱より少し大きい）
                float r = 5.5F * u;
                FxDraw.box(pose, fx, -r, 13.6F * u, -r, r, 24.3F * u, r, FxDraw.FULL_BRIGHT, TEAL_DEEP, (int) (28 * pulse));
            } else {
                pose.pushPose();
                pose.translate(0, 14.5F * u, 0);
                float k = (ageInTicks / 32) % 1;
                pose.translate(0, k * 7 * u, 0);                            // モデルの座標は下が +Y
                float inner = (2.5F + k * 5) * u;
                FxDraw.ring(pose.last(), fx, inner, inner + (1.2F - k * 0.6F) * u, TEAL, (int) (90 * (1 - k)), (int) (20 * (1 - k)));
                pose.popPose();
            }
            // 外側へぼんやり広がる光（カメラを向いた、中心から外へ薄くなる円）: 核は大きく、ビットの先と放射板は小さく
            org.joml.Matrix4f m = pose.last().pose();
            float breathe = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.18F);
            PoseStack local = new PoseStack();
            g.coreSpace(local);
            halo(fx, m.transformPosition(local.last().pose().transformPosition(new Vector3f())), 0.62F * breathe, TEAL, (int) (75 * pulse));
            halo(fx, m.transformPosition(local.last().pose().transformPosition(new Vector3f())), 0.3F, TEAL_HI, (int) (60 * pulse));
            halo(fx, m.transformPosition(new Vector3f(0, (g.bodyY() + 5.8F) * u, 0)), 0.22F, TEAL, (int) (55 * pulse));
            for (int i = 0; i < 4; i++) {
                PoseStack bit = new PoseStack();
                g.tipSpace(i, bit);
                Vector3f tip = bit.last().pose().transformPosition(new Vector3f(0, 4.6F * u, 0));
                halo(fx, m.transformPosition(tip), 0.16F, TEAL, (int) (70 * pulse));
            }
        }

        /**
         * カメラを向いた、ぼんやりした光の円（中心が濃く、外へ向かって消える）。at はカメラから見た位置（ポーズを掛けたあと）。
         * 光を足す描き方なので、重なるほど明るくなる。
         */
        private static void halo(VertexConsumer vc, Vector3f at, float radius, int rgb, int alpha) {
            org.joml.Quaternionf facing = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            org.joml.Matrix4f id = new org.joml.Matrix4f();
            int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
            float[] rs = {0.02F, radius * 0.35F, radius};
            int[] as = {alpha, (int) (alpha * 0.55F), 0};
            int sides = 16;
            for (int ring = 0; ring < 2; ring++) {
                for (int i = 0; i < sides; i++) {
                    float a0 = Mth.TWO_PI * i / sides, a1 = Mth.TWO_PI * (i + 1) / sides;
                    Vector3f p0 = point(facing, at, rs[ring], a0), p1 = point(facing, at, rs[ring], a1);
                    Vector3f q1 = point(facing, at, rs[ring + 1], a1), q0 = point(facing, at, rs[ring + 1], a0);
                    vc.vertex(id, p0.x, p0.y, p0.z).color(red, green, blue, as[ring]).endVertex();
                    vc.vertex(id, p1.x, p1.y, p1.z).color(red, green, blue, as[ring]).endVertex();
                    vc.vertex(id, q1.x, q1.y, q1.z).color(red, green, blue, as[ring + 1]).endVertex();
                    vc.vertex(id, q0.x, q0.y, q0.z).color(red, green, blue, as[ring + 1]).endVertex();
                }
            }
        }

        private static Vector3f point(org.joml.Quaternionf facing, Vector3f at, float r, float a) {
            return facing.transform(new Vector3f(Mth.cos(a) * r, Mth.sin(a) * r, 0)).add(at);
        }
    }

    /** 円錐（原点の輪から +Y へ。3分の1ほどで少しふくらみ、先へ向かって細く薄くなる）。 */
    static void cone(PoseStack pose, VertexConsumer vc, float radius, float length, int rgb, int alpha) {
        PoseStack.Pose p = pose.last();
        int sides = 10;
        int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
        float[] ys = {0, length * 0.3F, length};
        float[] rs = {radius, radius * 1.15F, 0};
        int[] as = {alpha, (int) (alpha * 0.7F), 0};
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < sides; i++) {
                float a0 = Mth.TWO_PI * i / sides, a1 = Mth.TWO_PI * (i + 1) / sides;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                vc.vertex(p.pose(), rs[ring] * c0, ys[ring], rs[ring] * s0).color(red, green, blue, as[ring]).endVertex();
                vc.vertex(p.pose(), rs[ring] * c1, ys[ring], rs[ring] * s1).color(red, green, blue, as[ring]).endVertex();
                vc.vertex(p.pose(), rs[ring + 1] * c1, ys[ring + 1], rs[ring + 1] * s1).color(red, green, blue, as[ring + 1]).endVertex();
                vc.vertex(p.pose(), rs[ring + 1] * c0, ys[ring + 1], rs[ring + 1] * s0).color(red, green, blue, as[ring + 1]).endVertex();
            }
        }
    }

    /**
     * 位相透過: 壁を抜ける少し前から抜けたあとまで、紫に光る半透明のドローンの姿を重ねる。奥行きを比べずに描くので、
     * 壁の中にいる間も、壁越しに光る姿が見える（ゆっくり明滅する）。
     */
    static final class PhaseShell extends RenderLayer<CargoDrone, Model> {
        static final int COLOR = 0x9B7BFF;

        PhaseShell(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float glow = drone.phaseGlow(partialTick);
            if (glow < 0.02F) {
                return;
            }
            int alpha = (int) (150 * glow * (0.8F + 0.2F * Mth.sin(ageInTicks * 0.6F)));
            pose.pushPose();
            pose.scale(1.05F, 1.05F, 1.05F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(SHELLS[drone.kind().ordinal()]), FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ((COLOR >> 16) & 255) / 255f, ((COLOR >> 8) & 255) / 255f, (COLOR & 255) / 255f, alpha / 255f);
            pose.popPose();
        }
    }

    /** 位相透過の姿の描き方: ドローンの絵を光らせて半透明で重ね、奥行きを比べない・書かない（壁越しに見える）。 */
    private static final class PhaseShellType extends RenderType {
        static RenderType of(String name, ResourceLocation texture) {
            return create("singulo_" + name + "_phase", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false));
        }

        private PhaseShellType(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode,
                               int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /**
     * 回転の残像の描き方: うすく透けて重ねるが、奥行きを書かない（書くと、あとから描かれる奥のエンティティが、
     * 残像の円盤の向こうにあるとして消えてしまう。下から見上げたときに目立つ）。両面を描く。
     */
    private static final class RotorBlur extends RenderType {
        static final RenderType TYPE = create("singulo_rotor_blur", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .createCompositeState(false));

        private RotorBlur(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                          boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /** 炎・牽引の光・場の描き方: 光を足して描き、奥行きを書かない（アンドロイドのスラスターの炎と同じ）。両面を描く。 */
    private static final class Glare extends RenderType {
        static final RenderType TYPE = create("singulo_drone_glare", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setOutputState(WEATHER_TARGET)
                        .createCompositeState(false));

        private Glare(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int size,
                      boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    // ------------------------------------------------------------------ モデル（テクスチャ 64×64。配置は tools/drones.py の PARTS・JET_PARTS・GRAVITY_PARTS と同じ）

    /** 型ごとのモデルの共通部分。 */
    public abstract static class Model extends HierarchicalModel<CargoDrone> {
        protected final ModelPart root;
        protected final ModelPart body;

        Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        /** 待機中だけ少し上下に揺れる（運搬中・受け渡し中は箱とずれないよう止める）。 */
        protected void hover(CargoDrone drone, float ageInTicks, float y) {
            if (drone.printed() && drone.crateTier() == 0 && !drone.docked()) {
                body.y = y + Mth.sin(ageInTicks * 0.12F) * 0.6F;
            }
        }

        /** 爪の開き: 箱を持っていれば閉じ（先が内へ）、空なら開く。受け渡しを待つ間は大きく開く。 */
        static float clawOpen(CargoDrone drone) {
            return drone.crateTier() > 0 ? -0.22F : drone.docked() ? 0.65F : 0.35F;
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -1.5F, -4, 8, 3, 8)
                        .texOffs(32, 0).addBox(-2, -2.5F, -2, 4, 1, 4)
                        .texOffs(48, 0).addBox(-1, -1, -4.6F, 2, 2, 1)
                        .texOffs(0, 12).addBox(-2, 1.5F, -2, 4, 1, 4),
                PartPose.offset(0, 10, 0));
        for (int i = 0; i < 4; i++) {
            PartDefinition arm = body.addOrReplaceChild("arm_" + i, CubeListBuilder.create()
                            .texOffs(0, 18).addBox(-0.5F, -0.5F, 3, 1, 1, 5)
                            .texOffs(16, 18).addBox(-1, -1, 7.5F, 2, 2, 2),
                    PartPose.rotation(0, Mth.PI / 4 + i * Mth.HALF_PI, 0));
            // ローターの軸（羽根は Rotors が描く）
            arm.addOrReplaceChild("rotor_" + i, CubeListBuilder.create(), PartPose.offset(0, -1.3F, 8.5F));
            arm.addOrReplaceChild("claw_" + i, CubeListBuilder.create().texOffs(48, 18).addBox(-0.5F, 0, -0.5F, 1, 5, 1),
                    PartPose.offset(0, 0.5F, 7));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** ローター型。 */
    public static class RotorModel extends Model {
        private final ModelPart[] arms = new ModelPart[4];
        private final ModelPart[] rotors = new ModelPart[4];
        private final ModelPart[] claws = new ModelPart[4];

        public RotorModel(ModelPart root) {
            super(root);
            for (int i = 0; i < 4; i++) {
                ModelPart arm = body.getChild("arm_" + i);
                arms[i] = arm;
                rotors[i] = arm.getChild("rotor_" + i);
                claws[i] = arm.getChild("claw_" + i);
            }
        }

        /** ローター i の軸の座標へ（回る角度まで）。 */
        void rotorSpace(int i, PoseStack pose) {
            root.translateAndRotate(pose);
            body.translateAndRotate(pose);
            arms[i].translateAndRotate(pose);
            rotors[i].translateAndRotate(pose);
        }

        @Override
        public void setupAnim(CargoDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            hover(drone, ageInTicks, 10);
            float spin = drone.printed() ? ageInTicks * 1.6F : 0;
            float open = clawOpen(drone);
            for (int i = 0; i < 4; i++) {
                rotors[i].yRot = i % 2 == 0 ? spin : -spin;
                claws[i].xRot = open;
            }
        }
    }

    /**
     * ジェット型（前が -Z）: 細い胴（4×3×12）と機首・センサー・操縦席の覆い、後ろへ流れて少し上へ反った主翼、主翼の付け根の上の
     * 2つのエンジン（後ろに噴射口）、外へ開いて後ろへ寝た2枚の尾翼。腹に前後2本の横棒があり、その両端の爪で箱をはさむ。
     */
    public static LayerDefinition createJetLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2, -1.5F, -6, 4, 3, 12)
                        .texOffs(32, 0).addBox(-1.5F, -1, -8, 3, 2, 2)
                        .texOffs(44, 0).addBox(-0.5F, -0.5F, -9, 1, 1, 1)
                        .texOffs(48, 0).addBox(-1, -2.5F, -4.5F, 2, 1, 4),
                PartPose.offset(0, 10, 0));
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1 : 1;
            body.addOrReplaceChild("wing_" + s, CubeListBuilder.create().texOffs(0, 16).mirror(s == 1)
                            .addBox(s == 0 ? -7 : 0, -0.5F, -1.5F, 7, 1, 3),
                    PartPose.offsetAndRotation(side * 2, 0.3F, 0.5F, 0, -side * 0.5F, -side * 0.12F));
            PartDefinition nacelle = body.addOrReplaceChild("nacelle_" + s, CubeListBuilder.create()
                            .texOffs(20, 16).addBox(-1.5F, -1.5F, -3, 3, 3, 6)
                            .texOffs(40, 16).addBox(-2, -2, 3, 4, 4, 1),
                    PartPose.offset(side * 3.6F, -0.6F, 2));
            // 炎の出る所（噴射口の後ろの面）
            nacelle.addOrReplaceChild("nozzle_" + s, CubeListBuilder.create(), PartPose.offset(0, 0, 4));
            body.addOrReplaceChild("fin_" + s, CubeListBuilder.create().texOffs(0, 26).addBox(-0.5F, -4, -1.5F, 1, 4, 3),
                    PartPose.offsetAndRotation(side * 1.6F, -1.3F, 4.5F, -0.45F, 0, side * 0.38F));
        }
        PartDefinition clamp = body.addOrReplaceChild("clamp", CubeListBuilder.create()
                        .texOffs(0, 34).addBox(-6, 0, -3.5F, 12, 1, 1)
                        .texOffs(0, 34).addBox(-6, 0, 2.5F, 12, 1, 1),
                PartPose.offset(0, 1.5F, 0));
        for (int i = 0; i < 4; i++) {
            clamp.addOrReplaceChild("claw_" + i, CubeListBuilder.create().texOffs(28, 34).addBox(-0.5F, 0, -0.5F, 1, 4, 1),
                    PartPose.offset(i % 2 == 0 ? -5.5F : 5.5F, 1, i < 2 ? -3 : 3));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** ジェット型。 */
    public static class JetModel extends Model {
        private final ModelPart[] nacelles = new ModelPart[2];
        private final ModelPart[] nozzles = new ModelPart[2];
        private final ModelPart clamp;
        private final ModelPart[] claws = new ModelPart[4];

        public JetModel(ModelPart root) {
            super(root);
            for (int s = 0; s < 2; s++) {
                nacelles[s] = body.getChild("nacelle_" + s);
                nozzles[s] = nacelles[s].getChild("nozzle_" + s);
            }
            clamp = body.getChild("clamp");
            for (int i = 0; i < 4; i++) {
                claws[i] = clamp.getChild("claw_" + i);
            }
        }

        /** 噴射口 i の後ろの面の座標へ（+Z が後ろ）。 */
        void nozzleSpace(int i, PoseStack pose) {
            root.translateAndRotate(pose);
            body.translateAndRotate(pose);
            nacelles[i].translateAndRotate(pose);
            nozzles[i].translateAndRotate(pose);
        }

        @Override
        public void setupAnim(CargoDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            hover(drone, ageInTicks, 10);
            float open = clawOpen(drone);
            for (int i = 0; i < 4; i++) {
                claws[i].zRot = (i % 2 == 0 ? 1 : -1) * open;       // 外へ開く・内へ閉じる
            }
        }
    }

    /**
     * 反重力型: 頂点を上にした結晶の核（5×5×5 を傾けて立てる）、核のまわりの八角形の光輪（少し傾けて、核と逆に回る）、
     * 核の上の短いアンテナ、下の放射板。4つのビット（白い殻の細い胴、下の光る先、外側の黒い板）が浮きながら核のまわりを回る。
     * 箱を持つと、ビットは箱の四隅の上で止まり、先を箱へ向ける。
     */
    public static LayerDefinition createGravityLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-2, 4.8F, -2, 4, 1, 4)
                        .texOffs(54, 0).addBox(-0.5F, -6.6F, -0.5F, 1, 2, 1),
                PartPose.offset(0, 8, 0));
        PartDefinition core = body.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.ZERO);
        core.addOrReplaceChild("gem", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -2.5F, 5, 5, 5),
                PartPose.rotation(Mth.PI / 4, 0, 0.6155F));
        PartDefinition halo = body.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.rotation(0.22F, 0, 0.1F));
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.PI / 4;
            halo.addOrReplaceChild("seg_" + i, CubeListBuilder.create().texOffs(24, 0).addBox(-2, -0.5F, -0.5F, 4, 1, 1),
                    PartPose.offsetAndRotation(Mth.sin(a) * 5.2F, 0, Mth.cos(a) * 5.2F, 0, a, 0));
        }
        for (int i = 0; i < 4; i++) {
            PartDefinition bit = body.addOrReplaceChild("bit_" + i, CubeListBuilder.create(), PartPose.ZERO);
            bit.addOrReplaceChild("pod_" + i, CubeListBuilder.create()
                            .texOffs(0, 12).addBox(-1, -2.5F, -1, 2, 5, 2)
                            .texOffs(10, 12).addBox(-0.5F, 2.5F, -0.5F, 1, 2, 1)
                            .texOffs(16, 12).addBox(-1.5F, -2, 1, 3, 3, 1),
                    PartPose.offset(0, 1, 7.5F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 反重力型。 */
    public static class GravityModel extends Model {
        private final ModelPart core;
        private final ModelPart halo;
        private final ModelPart[] bits = new ModelPart[4];
        private final ModelPart[] pods = new ModelPart[4];

        public GravityModel(ModelPart root) {
            super(root);
            core = body.getChild("core");
            halo = body.getChild("halo");
            for (int i = 0; i < 4; i++) {
                bits[i] = body.getChild("bit_" + i);
                pods[i] = bits[i].getChild("pod_" + i);
            }
        }

        /** 核の中心の座標へ。 */
        void coreSpace(PoseStack pose) {
            root.translateAndRotate(pose);
            body.translateAndRotate(pose);
            core.translateAndRotate(pose);
        }

        /** 胴の高さ（モデルの座標。待機中は揺れる）。 */
        float bodyY() {
            return body.y;
        }

        /** ビット i の座標へ（先は (0, 4.5, 0) の向き）。 */
        void tipSpace(int i, PoseStack pose) {
            root.translateAndRotate(pose);
            body.translateAndRotate(pose);
            bits[i].translateAndRotate(pose);
            pods[i].translateAndRotate(pose);
        }

        @Override
        public void setupAnim(CargoDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            hover(drone, ageInTicks, 8);
            boolean flying = drone.printed() && !drone.dismantling();
            float t = flying ? ageInTicks : 0;
            core.yRot = t * 0.07F;
            halo.yRot = -t * 0.045F;
            boolean hold = drone.crateTier() > 0 || drone.docked();
            for (int i = 0; i < 4; i++) {
                // 持っている間は箱の四隅の上（45°ずつ）で止まり、空のときは核のまわりを回る
                bits[i].yRot = (hold ? Mth.PI / 4 + Mth.sin(t * 0.1F + i) * 0.04F : t * 0.05F) + i * Mth.HALF_PI;
                pods[i].y = 1 + Mth.sin(t * 0.15F + i * 1.7F) * (hold ? 0.25F : 0.8F);
                pods[i].xRot = hold ? -0.38F : -0.12F;                // 先（下）を内へ向ける
            }
        }
    }
}
