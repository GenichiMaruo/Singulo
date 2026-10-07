package io.github.genichimaruo.singulo.client;

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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 警備ドローン: 白い立方体の胴に、強さの色に光るレンズ。ふわふわ上下に揺れる。 */
public class SecurityDroneRenderer extends MobRenderer<SecurityDrone, SecurityDroneRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("security_drone"), "main");
    private static final ResourceLocation[] TEXTURES = {
            Singulo.id("textures/entity/security_drone_1.png"),
            Singulo.id("textures/entity/security_drone_2.png"),
            Singulo.id("textures/entity/security_drone_3.png"),
    };

    public SecurityDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(SecurityDrone drone) {
        return TEXTURES[Mth.clamp(drone.tier(), 1, 3) - 1];
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8), PartPose.offset(0, 18, 0));
        body.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(0, 16).addBox(-2, -2, -5, 4, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(12, 16).addBox(4, -1, -3, 3, 1, 6), PartPose.ZERO);
        body.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(12, 16).addBox(-7, -1, -3, 3, 1, 6), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static class Model extends HierarchicalModel<SecurityDrone> {
        private final ModelPart root;
        private final ModelPart body;

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(SecurityDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            body.y = 18 + Mth.sin(ageInTicks * 0.15F) * 1.0F;
            body.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            body.xRot = headPitch * Mth.DEG_TO_RAD;
        }
    }
}
