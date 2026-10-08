package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.WardenSingularity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** ウォーデンの小型の特異点: 黒い球と赤い降着の輪、周りの景色を歪める重力レンズ。出てくるときに育ち、消える前にしぼむ。 */
public class WardenSingularityRenderer extends EntityRenderer<WardenSingularity> {
    static final float HORIZON = 0.5F;

    public WardenSingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WardenSingularity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = e.size(partialTick);
        if (size <= 0.01F) {
            return;
        }
        float t = e.tickCount + partialTick;
        float r = HORIZON * size;
        pose.pushPose();
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, 0);
        pose.mulPose(Axis.XP.rotationDegrees(18));
        pose.mulPose(Axis.YP.rotationDegrees(t * 9));
        var glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        FxDraw.ring(pose.last(), glow, r * 1.5F, r * 3.2F, 0xFF5A5A, 220, 0);
        FxDraw.ring(pose.last(), glow, r * 1.5F, r * 2.0F, 0xFFD0B0, 200, 40);
        pose.popPose();
        Vec3 c = e.getPosition(partialTick);
        GravitationalLensing.add(c, r * GravitationalLensing.EINSTEIN_PER_HORIZON, 1.0F, (float) WardenSingularity.RADIUS * size);
    }

    @Override
    public boolean shouldRender(WardenSingularity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(WardenSingularity e) {
        return FxDraw.WHITE;
    }
}
