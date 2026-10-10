package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.EchoProjectorBlockEntity;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 残響投影器: 台座の上で回るレンズの輪と、近くの残響の番人（本体だけ。分身には伸びない）へ伸びる投影の光。
 * 光の行き先を追えば、分身にまぎれた本体が分かる。
 * 投影槍の前触れでは、レンズから狙いの場所へ細い金色の線が伸びて強まり、撃つと太い光の槍になる。
 * 番人が倒されると、投影の光はちらつきながら消えていく。
 */
public class EchoProjectorRenderer implements BlockEntityRenderer<EchoProjectorBlockEntity> {
    static final double RANGE = 24;
    static final int LANCE = 0xFFE9A0;

    public EchoProjectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EchoProjectorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float t = be.getLevel().getGameTime() + partialTick;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        Vec3 lens = Vec3.atLowerCornerOf(be.getBlockPos()).add(0.5, 0.95, 0.5);
        EchoSentinel target = null;
        double best = RANGE * RANGE;
        for (EchoSentinel s : be.getLevel().getEntitiesOfClass(EchoSentinel.class, new AABB(lens, lens).inflate(RANGE),
                s -> !s.isMinion())) {
            double d = s.position().distanceToSqr(lens);
            if (d < best) {
                best = d;
                target = s;
            }
        }
        float death = target == null ? 0 : target.deathProgress(partialTick);
        boolean live = target != null && death < 0.6F;
        pose.pushPose();
        pose.translate(0.5, 0.95, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(t * (live ? 9 : 2)));
        pose.mulPose(Axis.XP.rotationDegrees(70));
        FxDraw.ring(pose.last(), vc, 0.18F, 0.22F, EchoSentinelRenderer.RGB, live ? 230 : 120, live ? 230 : 120);
        pose.popPose();
        if (target == null) {
            return;
        }
        Vec3 from = new Vec3(0.5, 0.95, 0.5);
        Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos());
        // 番人へ伸びる投影の光（倒されると、ちらつきながら細くなって消える）
        if (live) {
            float e = target.emergeProgress(partialTick);
            Vec3 to = target.getPosition(partialTick).add(0, 1.4, 0).subtract(base);
            float fade = 1 - death / 0.6F;
            if (death > 0 && ((int) (t * 4)) % 3 == 0) {
                fade *= 0.2F;
            }
            int alpha = (int) ((80 + 60 * Math.sin(t * 0.3) * Math.sin(t * 0.11)) * fade);
            Vec3 tip = from.add(to.subtract(from).scale(Math.min(1, e * 1.4)));
            FxDraw.beam(pose.last(), vc, from, tip, 0.06F * fade, EchoSentinelRenderer.RGB, Math.max(10, alpha));
            FxDraw.beam(pose.last(), vc, from, tip, 0.02F, 0xFFFFFF, (int) (160 * fade));
        }
        // 投影槍: 前触れの細い線と、撃った光の槍
        int lance = target.lanceTicks();
        int flash = target.lanceFlash();
        if (lance > 0 || flash > 0) {
            Vec3 aim = target.lanceTarget().subtract(base);
            Vec3 dir = aim.subtract(from).normalize();
            if (lance > 0) {
                float k = 1 - (lance - partialTick) / EchoSentinel.LANCE_WINDUP;     // 0 → 1
                FxDraw.beam(pose.last(), vc, from, aim.add(dir.scale(3)), 0.015F + 0.03F * k, LANCE, (int) (60 + 180 * k));
            } else {
                float k = (flash - partialTick) / 8F;
                Vec3 end = aim.add(dir.scale(6));
                FxDraw.beam(pose.last(), vc, from, end, 0.28F * k + 0.05F, LANCE, (int) (220 * k));
                FxDraw.beam(pose.last(), vc, from, end, 0.08F, 0xFFFFFF, (int) (255 * k));
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(EchoProjectorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(EchoProjectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(RANGE);
    }
}
