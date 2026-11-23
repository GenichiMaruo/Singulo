package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.HullRepairer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
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
 * 暴走した炉殻修理機: 警備ドローンと同じ形の、{@value #SCALE} 倍の大型機（光る線は橙）。押しつぶしの前は前に傾いて輪が速く回り、
 * 床に伏せている間は輪が止まる。自己修理の間はゆっくり回る。
 */
public class HullRepairerRenderer extends MobRenderer<HullRepairer, HullRepairerRenderer.Model> {
    static final float SCALE = 2.6F;
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/security_drone.png");
    private static final RenderType GLOW = RenderType.eyes(Singulo.id("textures/entity/security_drone_glow_2.png"));

    public HullRepairerRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(SecurityDroneRenderer.LAYER)), 0.9F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HullRepairer repairer) {
        return TEXTURE;
    }

    @Override
    protected void scale(HullRepairer repairer, PoseStack pose, float partialTick) {
        float k = SCALE * (0.2F + 0.8F * repairer.emergeProgress(partialTick)) * (1 - 0.6F * repairer.deathProgress(partialTick));
        pose.scale(k, k, k);
    }

    static final class Glow extends EyesLayer<HullRepairer, Model> {
        Glow(RenderLayerParent<HullRepairer, Model> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, HullRepairer repairer, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer vc = buffers.getBuffer(GLOW);
            getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
    }

    /** 警備ドローンの型（{@link SecurityDroneRenderer#createLayer()}）を、修理機の動きで動かす。 */
    public static class Model extends HierarchicalModel<HullRepairer> {
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
        public void setupAnim(HullRepairer repairer, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            int state = repairer.state();
            boolean grounded = state == HullRepairer.STATE_GROUNDED;
            body.y = 18 + (grounded ? 0 : Mth.sin(ageInTicks * 0.1F) * 0.8F);
            body.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            body.xRot = state == HullRepairer.STATE_WINDUP ? 0.45F : state == HullRepairer.STATE_DROP ? 0.2F : 0.1F * limbSwingAmount;
            if (grounded) {
                body.zRot = 0.18F;
            }
            float spin = switch (state) {
                case HullRepairer.STATE_WINDUP -> 0.7F;
                case HullRepairer.STATE_GROUNDED -> 0;
                case HullRepairer.STATE_REPAIR -> 0.02F;
                default -> 0.08F;
            };
            ring.yRot = ageInTicks * spin;
            for (int i = 0; i < 4; i++) {
                rotors[i].yRot = grounded ? 0 : ageInTicks * (i % 2 == 0 ? 1.4F : -1.4F);
            }
        }
    }
}
