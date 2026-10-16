package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.ruin.FactoryLineBlockEntity;
import io.github.genichimaruo.singulo.ruin.FoundryOverseer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 製造ライン: 近くのファウンドリが動いている間、印刷ヘッドの下からコアへ橙の給電の光が伸びる（脈打ちながら流れる）。
 * これが伸びているラインの数だけ、コアは硬い。ファウンドリが倒されると、光はちらついて消える。
 */
public class FactoryLineRenderer implements BlockEntityRenderer<FactoryLineBlockEntity> {
    static final double RANGE = 24;

    public FactoryLineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FactoryLineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        Vec3 head = Vec3.atLowerCornerOf(be.getBlockPos()).add(0.5, 0.62, 0.5);
        FoundryOverseer target = null;
        double best = RANGE * RANGE;
        for (FoundryOverseer f : be.getLevel().getEntitiesOfClass(FoundryOverseer.class, new AABB(head, head).inflate(RANGE))) {
            double d = f.position().distanceToSqr(head);
            if (d < best) {
                best = d;
                target = f;
            }
        }
        if (target == null) {
            return;
        }
        float death = target.deathProgress(partialTick);
        if (death >= 0.5F) {
            return;
        }
        float t = be.getLevel().getGameTime() + partialTick;
        float e = target.emergeProgress(partialTick);
        float fade = 1 - death / 0.5F;
        if (death > 0 && ((int) (t * 4)) % 3 == 0) {
            fade *= 0.2F;
        }
        int color = target.awakened() ? FoundryOverseerRenderer.AWAKENED : FoundryOverseerRenderer.ORANGE;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        Vec3 from = new Vec3(0.5, 0.62, 0.5);
        Vec3 to = target.getPosition(partialTick).add(0, FoundryOverseerRenderer.CY, 0).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        Vec3 tip = from.add(to.subtract(from).scale(Math.min(1, e * 1.3)));
        int alpha = (int) ((90 + 50 * Math.sin(t * 0.25)) * fade);
        FxDraw.beam(pose.last(), vc, from, tip, 0.09F * fade, color, Math.max(10, alpha));
        FxDraw.beam(pose.last(), vc, from, tip, 0.03F, 0xFFE2B0, (int) (170 * fade));
        // 流れる光の粒（ラインからコアへ）
        for (int k = 0; k < 3; k++) {
            double p = ((t * 0.04 + k / 3.0) % 1.0) * Math.min(1, e * 1.3);
            Vec3 q = from.add(to.subtract(from).scale(p));
            pose.pushPose();
            pose.translate(q.x, q.y, q.z);
            FxDraw.box(pose, vc, -0.07F, -0.07F, -0.07F, 0.07F, 0.07F, 0.07F, FxDraw.FULL_BRIGHT, 0xFFFFFF, (int) (220 * fade));
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FactoryLineBlockEntity be) {
        return true;
    }

}
