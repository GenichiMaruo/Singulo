package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.reactor.InfallingBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 炉心へ吸い込まれていくブロック: 回りながら、中心へ近づくほど中心の向きへ引き伸ばされて細くなる（潮汐で引き裂かれる）。
 * 後ろに淡い光の尾を引く。
 */
public class InfallingBlockRenderer extends EntityRenderer<InfallingBlock> {
    public InfallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(InfallingBlock entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(InfallingBlock e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        Vec3 at = e.getPosition(partialTick);
        float k = e.fall(at);
        Vec3 to = e.target().subtract(at);
        pose.pushPose();
        // 中心の向きを Z にそろえ、Z へ伸ばして X・Y を細くする
        pose.mulPose(FxDraw.orient(to));
        float thin = 0.8F * (1 - 0.75F * k * k);
        float stretch = 0.8F * (1 + 2.2F * k * k);
        pose.mulPose(Axis.ZP.rotationDegrees(t * 14));
        pose.scale(thin, thin, stretch);
        pose.mulPose(Axis.XP.rotationDegrees(t * 9 * (1 - k)));
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.block(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // 中心へ向かう淡い光の尾（後ろ側へ）
        if (to.lengthSqr() > 1e-4) {
            Vec3 tail = to.normalize().scale(-(0.6 + 1.4 * k));
            FxDraw.beam(pose.last(), FxDraw.glow(buffers), Vec3.ZERO, tail,
                    0.25F * (1 - 0.5F * k), 0x9C7BFF, (int) (40 + 90 * k));
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }
}
