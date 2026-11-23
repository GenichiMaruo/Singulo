package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.ContainmentHauler;
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
 * 封じ込め搬送機: 4本の弓なりの脚に吊られた低い車体、背に檻で囲った封じ込め容器（光る芯）、前に操縦室と2本の挟み具。
 * 突進の溜めと突進のあいだは光が赤くなり、壁にぶつかって止まっている間は容器が浮き上がって傾き、光がまたたく。
 * 踏み鳴らしの溜めでは前脚を上げて車体を反らす。
 */
public class ContainmentHaulerRenderer extends MobRenderer<ContainmentHauler, ContainmentHaulerRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("containment_hauler"), "main");
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/containment_hauler.png");
    private static final RenderType GLOW = RenderType.eyes(Singulo.id("textures/entity/containment_hauler_glow.png"));
    static final int CYAN = 0x78DCFF, RED = 0xFF5040;

    public ContainmentHaulerRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 1.2F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ContainmentHauler hauler) {
        return TEXTURE;
    }

    @Override
    protected void scale(ContainmentHauler hauler, PoseStack pose, float partialTick) {
        float k = (0.3F + 0.7F * hauler.emergeProgress(partialTick)) * (1 - 0.4F * hauler.deathProgress(partialTick));
        pose.scale(k, k, k);
    }

    static final class Glow extends EyesLayer<ContainmentHauler, Model> {
        Glow(RenderLayerParent<ContainmentHauler, Model> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, ContainmentHauler hauler, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int state = hauler.state();
            int color = switch (state) {
                case ContainmentHauler.STATE_RAM_WINDUP, ContainmentHauler.STATE_RAM -> RED;
                case ContainmentHauler.STATE_STUNNED -> ((int) ageInTicks / 3) % 2 == 0 ? CYAN : 0x203840;   // 固定が外れてまたたく
                default -> CYAN;
            };
            VertexConsumer vc = buffers.getBuffer(GLOW);
            getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY, 0xFF000000 | color);
        }
    }

    // ------------------------------------------------------------------ モデル（テクスチャ 128×64。配置は tools/gen_data.py の HAULER_PARTS と同じ）

    /** 脚の付け根の向き（x の符号, z の符号）。0, 1 が前（-Z）。 */
    static final int[][] LEGS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    static final float LEG_RISE = 0.45F;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10, -4, -13, 20, 8, 26),
                PartPose.offset(0, 9, 0));
        PartDefinition cab = body.addOrReplaceChild("cab", CubeListBuilder.create()
                        .texOffs(0, 34).addBox(-6, -3, -6, 12, 6, 6)
                        .texOffs(40, 34).addBox(-4, -1.5F, -6.5F, 8, 2, 1),
                PartPose.offset(0, 0, -13));
        cab.addOrReplaceChild("jaw_l", CubeListBuilder.create().texOffs(60, 34).addBox(-1.5F, -1.5F, -10, 3, 3, 10),
                PartPose.offset(-4, 2, -5));
        cab.addOrReplaceChild("jaw_r", CubeListBuilder.create().texOffs(60, 34).mirror().addBox(-1.5F, -1.5F, -10, 3, 3, 10),
                PartPose.offset(4, 2, -5));
        CubeListBuilder vessel = CubeListBuilder.create()
                .texOffs(0, 46).addBox(-4, -10, -4, 8, 10, 8)
                .texOffs(44, 48).addBox(-6, -14, -6, 12, 2, 12);
        for (int[] c : new int[][] {{-6, -6}, {4, -6}, {-6, 4}, {4, 4}}) {
            vessel.texOffs(34, 48).addBox(c[0], -12, c[1], 2, 12, 2);
        }
        body.addOrReplaceChild("vessel", vessel, PartPose.offset(0, -4, 3));
        for (int i = 0; i < 4; i++) {
            int sx = LEGS[i][0], sz = LEGS[i][1];
            PartDefinition leg = root.addOrReplaceChild("leg_" + i, CubeListBuilder.create()
                            .texOffs(94, 30).addBox(-2, -2, -2, 4, 4, 4),
                    PartPose.offset(sx * 10, 10, sz * 9));
            CubeListBuilder upperBox = CubeListBuilder.create().texOffs(94, 0);
            if (sx < 0) {
                upperBox.mirror().addBox(-9, -1.5F, -1.5F, 9, 3, 3);
            } else {
                upperBox.addBox(0, -1.5F, -1.5F, 9, 3, 3);
            }
            PartDefinition upper = leg.addOrReplaceChild("upper", upperBox, PartPose.rotation(0, 0, -sx * LEG_RISE));
            upper.addOrReplaceChild("lower", CubeListBuilder.create()
                            .texOffs(94, 8).addBox(-1.5F, 0, -1.5F, 3, 16, 3)
                            .texOffs(108, 8).addBox(-2.5F, 16, -2.5F, 5, 2, 5),
                    PartPose.offsetAndRotation(sx * 9, 0, 0, 0, 0, sx * LEG_RISE));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static class Model extends HierarchicalModel<ContainmentHauler> {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart cab;
        private final ModelPart jawL;
        private final ModelPart jawR;
        private final ModelPart vessel;
        private final ModelPart[] legs = new ModelPart[4];
        private final ModelPart[] uppers = new ModelPart[4];
        private final ModelPart[] lowers = new ModelPart[4];

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            this.cab = body.getChild("cab");
            this.jawL = cab.getChild("jaw_l");
            this.jawR = cab.getChild("jaw_r");
            this.vessel = body.getChild("vessel");
            for (int i = 0; i < 4; i++) {
                legs[i] = root.getChild("leg_" + i);
                uppers[i] = legs[i].getChild("upper");
                lowers[i] = uppers[i].getChild("lower");
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(ContainmentHauler hauler, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            int state = hauler.state();
            float t = hauler.stateTime(ageInTicks - hauler.tickCount);
            boolean ramming = state == ContainmentHauler.STATE_RAM;
            float swing = ramming ? ageInTicks * 1.1F : limbSwing * 0.7F;
            float amount = ramming ? 1 : Math.min(1, limbSwingAmount * 1.6F);
            body.y += Mth.sin(ageInTicks * 0.08F) * 0.3F;
            cab.yRot = Mth.clamp(netHeadYaw, -30, 30) * Mth.DEG_TO_RAD;
            float jaw = 0.12F + Mth.sin(ageInTicks * 0.05F) * 0.04F;
            for (int i = 0; i < 4; i++) {
                int sx = LEGS[i][0];
                // 対角の脚が組になって運ぶ（0と3、1と2）
                float phase = (i == 0 || i == 3) ? 0 : Mth.PI;
                legs[i].yRot = Mth.sin(swing + phase) * 0.35F * amount * -sx;
                float lift = Math.max(0, Mth.cos(swing + phase)) * 0.25F * amount;
                uppers[i].zRot -= sx * lift;
                lowers[i].zRot += sx * lift;
            }
            switch (state) {
                case ContainmentHauler.STATE_RAM_WINDUP -> {
                    float k = Math.min(1, t / 10F);
                    body.xRot = 0.14F * k;                          // 頭を下げて構える
                    body.y += 1.2F * k;
                    jaw = 0;
                    vessel.y += Mth.sin(ageInTicks * 2.5F) * 0.25F;
                }
                case ContainmentHauler.STATE_RAM -> {
                    body.xRot = 0.18F;
                    body.y += 1.2F;
                    jaw = 0;
                }
                case ContainmentHauler.STATE_STUNNED -> {
                    body.xRot = -0.06F;
                    body.zRot = 0.1F;
                    body.y += 2.5F;                                 // 脚が沈み込む
                    vessel.y -= 3.5F;                               // 固定が外れて浮く
                    vessel.zRot = -0.25F + Mth.sin(ageInTicks * 0.6F) * 0.05F;
                    vessel.xRot = 0.15F;
                    jaw = 0.5F;
                    for (int i = 0; i < 4; i++) {
                        legs[i].yRot = 0;
                        uppers[i].zRot += LEGS[i][0] * 0.2F;
                        lowers[i].zRot -= LEGS[i][0] * 0.2F;
                    }
                }
                case ContainmentHauler.STATE_STOMP_WINDUP -> {
                    float k = Math.min(1, t / (float) ContainmentHauler.STOMP_WINDUP);
                    body.xRot = -0.35F * k;                         // 車体を反らして前脚を上げる
                    for (int i = 0; i < 2; i++) {
                        legs[i].yRot = 0;
                        uppers[i].zRot -= LEGS[i][0] * 0.6F * k;
                    }
                }
                case ContainmentHauler.STATE_CLAMP -> {
                    float k = t / (float) ContainmentHauler.CLAMP_WINDUP;
                    jaw = k < 1 ? 0.6F * k : 0;                     // 開いてから閉じる
                    cab.z -= 1.5F * Math.min(1, k);
                }
                default -> {
                }
            }
            jawL.yRot = jaw;
            jawR.yRot = -jaw;
        }
    }
}
