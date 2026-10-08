package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
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
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 警備ドローン: 白いセンサーの胴（暗い帯・上のふた・アンテナ）、光るレンズと下の砲身。周りを回る四角い輪と、
 * 4本の腕の先で回るローター。光る線は強さの色（水色・橙・赤）。突進の前はレンズが強く光って輪が速く回り、突進中は前に傾く。
 */
public class SecurityDroneRenderer extends MobRenderer<SecurityDrone, SecurityDroneRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("security_drone"), "main");
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/security_drone.png");
    private static final RenderType[] GLOW = {
            RenderType.eyes(Singulo.id("textures/entity/security_drone_glow_1.png")),
            RenderType.eyes(Singulo.id("textures/entity/security_drone_glow_2.png")),
            RenderType.eyes(Singulo.id("textures/entity/security_drone_glow_3.png")),
    };

    public SecurityDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.3F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SecurityDrone drone) {
        return TEXTURE;
    }

    @Override
    public void render(SecurityDrone drone, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(drone, yaw, partialTick, pose, buffers, light);
        int state = drone.state();
        if (state == SecurityDrone.STATE_RAM_WINDUP || state == SecurityDrone.STATE_PULSE) {
            // 前触れ: レンズ（または胴）がまぶしく光る
            float t = drone.tickCount + partialTick;
            float pulse = 0.8F + 0.3F * Mth.sin(t * 1.5F);
            int rgb = SecurityDrone.COLORS[Mth.clamp(drone.tier(), 1, 3) - 1];
            pose.pushPose();
            pose.translate(0, drone.getBbHeight() * 0.45, 0);
            if (state == SecurityDrone.STATE_RAM_WINDUP) {
                float bodyYaw = Mth.rotLerp(partialTick, drone.yBodyRotO, drone.yBodyRot);
                var f = net.minecraft.world.phys.Vec3.directionFromRotation(0, bodyYaw).scale(0.3);
                pose.translate(f.x, 0, f.z);
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            var camera = entityRenderDispatcher.cameraOrientation();
            float size = state == SecurityDrone.STATE_PULSE ? 1.6F : 0.9F;
            FxDraw.billboard(pose, vc, camera, size * pulse, rgb, 110);
            FxDraw.billboard(pose, vc, camera, size * 0.35F * pulse, 0xFFFFFF, 230);
            pose.popPose();
        }
    }

    /** 光る線（レンズ・帯・輪の縁・砲口）。暗くても光る。 */
    static final class Glow extends EyesLayer<SecurityDrone, Model> {
        Glow(RenderLayerParent<SecurityDrone, Model> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW[0];
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, SecurityDrone drone, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer vc = buffers.getBuffer(GLOW[Mth.clamp(drone.tier(), 1, 3) - 1]);
            getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY);
        }
    }

    // ------------------------------------------------------------------ モデル（テクスチャ 64×64。配置は tools/gen_data.py の DRONE_PARTS と同じ）

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -3, -3, 6, 5, 6)
                        .texOffs(0, 12).addBox(-3.5F, -1, -3.5F, 7, 1, 7)
                        .texOffs(28, 0).addBox(-2, -4, -2, 4, 1, 4)
                        .texOffs(44, 0).addBox(-0.5F, -7, -0.5F, 1, 3, 1)
                        .texOffs(48, 0).addBox(-1.5F, -2.5F, -3.6F, 3, 3, 1)
                        .texOffs(56, 0).addBox(-0.5F, 1, -5, 1, 1, 3),
                PartPose.offset(0, 18, 0));
        PartDefinition ring = body.addOrReplaceChild("ring", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-6, -0.5F, -6, 12, 1, 1)
                        .texOffs(0, 22).addBox(-6, -0.5F, 5, 12, 1, 1)
                        .texOffs(0, 26).addBox(-6, -0.5F, -5, 1, 1, 10)
                        .texOffs(0, 26).addBox(5, -0.5F, -5, 1, 1, 10),
                PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            PartDefinition arm = body.addOrReplaceChild("arm_" + i, CubeListBuilder.create()
                            .texOffs(28, 22).addBox(-0.5F, -0.5F, 2, 1, 1, 4)
                            .texOffs(40, 22).addBox(-1.5F, -1, 5.5F, 3, 2, 3),
                    PartPose.rotation(0, Mth.PI / 4 + i * Mth.HALF_PI, 0));
            PartDefinition rotor = arm.addOrReplaceChild("rotor_" + i, CubeListBuilder.create(),
                    PartPose.offset(0, -1.2F, 7));
            rotor.addOrReplaceChild("blade_a", CubeListBuilder.create().texOffs(28, 30).addBox(-3, 0, -0.5F, 6, 0, 1),
                    PartPose.ZERO);
            rotor.addOrReplaceChild("blade_b", CubeListBuilder.create().texOffs(28, 30).addBox(-3, 0, -0.5F, 6, 0, 1),
                    PartPose.rotation(0, Mth.HALF_PI, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static class Model extends HierarchicalModel<SecurityDrone> {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart ring;
        private final ModelPart[] rotors = new ModelPart[4];

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            this.ring = body.getChild("ring");
            for (int i = 0; i < 4; i++) {
                rotors[i] = body.getChild("arm_" + i).getChild("rotor_" + i);
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(SecurityDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            int state = drone.state();
            boolean alert = state == SecurityDrone.STATE_RAM_WINDUP || state == SecurityDrone.STATE_PULSE;
            body.y = 18 + Mth.sin(ageInTicks * 0.15F) * 1.0F;
            body.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            body.xRot = headPitch * Mth.DEG_TO_RAD + (state == SecurityDrone.STATE_RAM ? 0.5F : 0.15F * limbSwingAmount);
            if (alert) {
                body.zRot = 0.06F * Mth.sin(ageInTicks * 3.0F);
            }
            ring.yRot = ageInTicks * (alert ? 0.6F : 0.05F);
            ring.xRot = 0.1F * Mth.sin(ageInTicks * 0.07F);
            for (int i = 0; i < 4; i++) {
                rotors[i].yRot = ageInTicks * (i % 2 == 0 ? 1.8F : -1.8F);
            }
        }
    }
}
