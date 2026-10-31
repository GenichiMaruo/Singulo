package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.CargoDrone;
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
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 輸送ドローン: 平たい白い胴に橙の帯と前の単眼、斜めの4本の腕の先で回るローター、腕から下がる4本の爪。
 * 爪のあいだに梱包箱を吊るす（箱の底がドローンの足もと）。進む向きへ少し傾き、印刷中は下から積み上がる。
 */
public class CargoDroneRenderer extends MobRenderer<CargoDrone, CargoDroneRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("cargo_drone"), "main");
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/cargo_drone.png");
    private static final RenderType GLOW = RenderType.eyes(Singulo.id("textures/entity/cargo_drone_glow.png"));
    private static final String[] CRATES = {"", "packing_crate", "battery_packing_crate", "large_battery_packing_crate"};
    /** 箱の半分の高さ（箱の絵は 10/16 の立方体）。 */
    static final float CRATE_HALF = 5 / 16F;

    private final ItemRenderer items;

    public CargoDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.35F);
        items = context.getItemRenderer();
        addLayer(new Glow(this));
        addLayer(new Rotors(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CargoDrone drone) {
        return TEXTURE;
    }

    static ItemStack crate(int tier) {
        return tier <= 0 || tier >= CRATES.length ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(CRATES[tier])));
    }

    @Override
    public void render(CargoDrone drone, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float print = drone.printProgress(partialTick);
        if (print < 1) {
            float plane = pose.last().pose().m31() + print * (drone.getBbHeight() + 0.1F);
            super.render(drone, yaw, partialTick, pose, ClipBuffers.below(buffers, plane), light);
            renderScan(drone, print, pose, buffers);
            return;
        }
        super.render(drone, yaw, partialTick, pose, buffers, light);
        ItemStack crate = crate(drone.crateTier());
        if (!crate.isEmpty()) {
            pose.pushPose();
            float body = Mth.rotLerp(partialTick, drone.yBodyRotO, drone.yBodyRot);
            pose.translate(0, CRATE_HALF, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - body));
            // 進む向きへの傾き（胴と同じ）。箱は爪の高さを軸に揺れる
            pose.translate(0, CRATE_HALF, 0);
            tilt(drone, partialTick, pose);
            pose.translate(0, -CRATE_HALF, 0);
            items.renderStatic(crate, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, drone.level(), drone.getId());
            pose.popPose();
        }
    }

    /** 進む速さに応じた前への傾き（モデルと箱で同じ角度）。 */
    static void tilt(CargoDrone drone, float partialTick, PoseStack pose) {
        double dx = drone.getX() - drone.xo;
        double dz = drone.getZ() - drone.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        pose.mulPose(Axis.XP.rotationDegrees(Math.min(18, speed * 40)));
    }

    /** 印刷の走査面: 橙の光る薄い板が下から上がる。 */
    private void renderScan(CargoDrone drone, float print, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        float y = print * (drone.getBbHeight() + 0.1F);
        float r = 0.42F;
        int alpha = 120 + (int) (60 * Mth.sin((drone.tickCount) * 0.8F));
        FxDraw.box(pose, vc, -r, y, -r, r, y + 0.02F, r, FxDraw.FULL_BRIGHT, DroneStationScreen.ACCENT, alpha);
    }

    @Override
    protected void setupRotations(CargoDrone drone, PoseStack pose, float bob, float yaw, float partialTick, float scale) {
        super.setupRotations(drone, pose, bob, yaw, partialTick, scale);
        if (drone.printProgress(partialTick) >= 1) {
            pose.translate(0, 0.85F, 0);
            tilt(drone, partialTick, pose);
            pose.translate(0, -0.85F, 0);
        }
    }

    /** 光る線（単眼・帯・ローターの軸）。 */
    static final class Glow extends EyesLayer<CargoDrone, Model> {
        Glow(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW;
        }
    }

    /**
     * ローター: 2枚の羽根（薄い箱。裏の面を描かないので、上下の面が重なってちらつかない。2枚目は少し上にずらす）と、
     * 回っている羽根の残像のうすい円盤。モデルの羽根（厚さ0の板）は、両面が同じ位置で描かれてちらつくので使わない。
     */
    static final class Rotors extends net.minecraft.client.renderer.entity.layers.RenderLayer<CargoDrone, Model> {
        private static final int BLADE = 0x4E525C, TIP = 0xF0A040;

        Rotors(RenderLayerParent<CargoDrone, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CargoDrone drone, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!drone.printed() || drone.dismantling()) {
                return;
            }
            VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
            float u = 1 / 16F;
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                getParentModel().rotorSpace(i, pose);
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
            VertexConsumer blur = buffers.getBuffer(RenderType.entityTranslucent(FxDraw.WHITE));
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                getParentModel().rotorSpace(i, pose);
                pose.translate(0, 0.45F * u, 0);
                FxDraw.ring(pose.last(), blur, 0.6F * u, 3.7F * u, 0xC8CCD4, 40, 18);
                pose.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ モデル（テクスチャ 64×64。配置は tools/gen_data.py の CARGO_DRONE_PARTS と同じ）

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

    public static class Model extends HierarchicalModel<CargoDrone> {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart[] arms = new ModelPart[4];
        private final ModelPart[] rotors = new ModelPart[4];
        private final ModelPart[] claws = new ModelPart[4];

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            for (int i = 0; i < 4; i++) {
                ModelPart arm = body.getChild("arm_" + i);
                arms[i] = arm;
                rotors[i] = arm.getChild("rotor_" + i);
                claws[i] = arm.getChild("claw_" + i);
            }
        }

        @Override
        public ModelPart root() {
            return root;
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
            boolean printed = drone.printed();
            // 待機中だけ少し上下に揺れる（運搬中・受け渡し中は箱とずれないよう止める）
            if (printed && drone.crateTier() == 0 && !drone.docked()) {
                body.y = 10 + Mth.sin(ageInTicks * 0.12F) * 0.6F;
            }
            float spin = printed ? ageInTicks * 1.6F : 0;
            for (int i = 0; i < 4; i++) {
                rotors[i].yRot = i % 2 == 0 ? spin : -spin;
                // 爪: 箱を持っていれば閉じ（先が内へ）、空なら開く。受け渡しを待つ間は大きく開く
                float open = drone.crateTier() > 0 ? -0.22F : drone.docked() ? 0.65F : 0.35F;
                claws[i].xRot = open;
            }
        }
    }
}
