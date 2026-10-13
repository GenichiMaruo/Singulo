package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.android.AndroidCrescent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 光剣型の斬撃波: 進む向きに開いた三日月（色の外側と白い芯）を、少し傾けて描く。足し合わせで描く。 */
public class AndroidCrescentRenderer extends EntityRenderer<AndroidCrescent> {
    public AndroidCrescentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AndroidCrescent crescent) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(AndroidCrescent crescent, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = crescent.tickCount + partialTick;
        float grow = Mth.clamp(t / 3, 0, 1);
        int rgb = crescent.color();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-crescent.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(crescent.getXRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(-25));
        // ここでは +Z が進む向き。弧の頂点が前（+Z）に来るよう、円の中心を後ろへずらして、+Z 側の弧を描く
        float r = 1.1F * grow;
        pose.translate(0, 0, -r * 0.8);
        FxDraw.crescent(pose.last(), vc, 105, 255, r, 0.55F, rgb, 200);
        FxDraw.crescent(pose.last(), vc, 110, 250, r, 0.16F, 0xFFFFFF, 240);
        pose.popPose();
    }
}
