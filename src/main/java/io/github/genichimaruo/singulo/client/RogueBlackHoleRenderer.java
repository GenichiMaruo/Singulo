package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** 野良ブラックホール: 炉心と同じ黒い地平線と降着円盤。景色の歪む範囲は、質量とともに少しずつ広がる。 */
public class RogueBlackHoleRenderer implements BlockEntityRenderer<RogueBlackHoleBlockEntity> {
    public RogueBlackHoleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RogueBlackHoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        float horizon = (float) be.horizonRadius();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        float region = (io.github.genichimaruo.singulo.multiblock.Structures.REACTOR_RADIUS - 0.5F) * (float) Math.sqrt(be.pullScale());
        PenroseReactorRenderer.renderHole(pose, buffers, horizon, (float) be.spin(), time, be.center(), region);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RogueBlackHoleBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    public AABB getRenderBoundingBox(RogueBlackHoleBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6);
    }
}
