package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * ホライズン・ウォーデン: 白い外装の人型の守護機（人の約1.6倍）。フェーズ1だけ胸当てと肩当てがあり、
 * 剥がれると黒い躯体と光るラインが出る（フェーズ2は水色、3は赤）。
 */
public class HorizonWardenRenderer extends MobRenderer<HorizonWarden, HorizonWardenRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("horizon_warden"), "main");
    static final float SCALE = 1.6F;
    private static final ResourceLocation[] TEXTURES = {
            Singulo.id("textures/entity/horizon_warden_1.png"),
            Singulo.id("textures/entity/horizon_warden_2.png"),
            Singulo.id("textures/entity/horizon_warden_3.png"),
    };

    public HorizonWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HorizonWarden warden) {
        return TEXTURES[Mth.clamp(warden.phase(), 1, 3) - 1];
    }

    @Override
    protected void scale(HorizonWarden warden, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, -2, 0));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-6, 0, -3.5F, 12, 14, 7), PartPose.offset(0, -2, 0));
        body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(52, 40).addBox(-2, 4, -4, 4, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(24, 40).addBox(-6.5F, 1, -4.6F, 13, 8, 1),
                PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16).addBox(-5, -1, -2.5F, 5, 16, 5), PartPose.offset(-6, -1, 0));
        rightArm.addOrReplaceChild("right_pad", CubeListBuilder.create().texOffs(24, 52).addBox(-5.5F, -3, -3, 6, 3, 6),
                PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(0, -1, -2.5F, 5, 16, 5), PartPose.offset(6, -1, 0));
        leftArm.addOrReplaceChild("left_pad", CubeListBuilder.create().texOffs(24, 52).mirror().addBox(-0.5F, -3, -3, 6, 3, 6),
                PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 40).addBox(-2.5F, 0, -2.5F, 5, 12, 5),
                PartPose.offset(-3, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 40).mirror().addBox(-2.5F, 0, -2.5F, 5, 12, 5),
                PartPose.offset(3, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static class Model extends HierarchicalModel<HorizonWarden> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;
        private final ModelPart chestPlate;
        private final ModelPart rightPad;
        private final ModelPart leftPad;

        public Model(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
            this.chestPlate = root.getChild("body").getChild("chest_plate");
            this.rightPad = rightArm.getChild("right_pad");
            this.leftPad = leftArm.getChild("left_pad");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(HorizonWarden warden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            float swing = Mth.cos(limbSwing * 0.5F) * 0.8F * limbSwingAmount;
            rightLeg.xRot = swing;
            leftLeg.xRot = -swing;
            rightArm.xRot = -swing + Mth.sin(ageInTicks * 0.05F) * 0.05F;
            leftArm.xRot = swing - Mth.sin(ageInTicks * 0.05F) * 0.05F;
            if (warden.phase() >= 2) {
                // 外装が剥がれると両腕を少し広げて構える
                rightArm.zRot = 0.25F;
                leftArm.zRot = -0.25F;
            } else {
                rightArm.zRot = 0;
                leftArm.zRot = 0;
            }
            boolean armored = warden.phase() == 1;
            chestPlate.visible = armored;
            rightPad.visible = armored;
            leftPad.visible = armored;
        }
    }
}
