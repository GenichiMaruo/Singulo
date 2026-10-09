package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ワームホール生成器の球の中: 電力を受けている間、中心に渦が生まれて育つ。渦の腕は速く回りながら広がり、
 * 上下の収束器から光が中心へ注ぎ、まわりの景色がだんだん歪む。口ができると、渦は開いたまま静かに回って待つ。
 */
public class WormholeGeneratorRenderer implements BlockEntityRenderer<WormholeGeneratorBlockEntity> {
    public WormholeGeneratorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WormholeGeneratorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        boolean ready = be.shownReady();
        float p = ready ? 1F : be.shownProgress();
        if (p <= 0) {
            return;
        }
        BlockPos core = be.core();
        Vec3 offset = Vec3.atLowerCornerOf(core.subtract(be.getBlockPos())).add(0.5, 0.5, 0.5);
        float time = level.getGameTime() + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float size = 0.25F + 1.05F * p;
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        // 上下の収束器から中心へ注ぐ光（育っている間だけ）
        if (!ready) {
            for (int s = -1; s <= 1; s += 2) {
                FxDraw.beam(pose.last(), glow, new Vec3(0, s * 1.5, 0), Vec3.ZERO, 0.04F + 0.1F * p, 0xE0B0FF, (int) (80 + 140 * p));
                float f = (time * 0.08F) % 1F;
                pose.pushPose();
                pose.translate(0, s * 1.5F * (1 - f), 0);
                FxDraw.billboard(pose, glow, camera, 0.18F, 0xFFFFFF, (int) (200 * (1 - f)));
                pose.popPose();
            }
        }
        // 渦の腕（傾いた円盤の上を回る三日月）
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(18));
        float spin = time * (ready ? 4F : 6F + 14F * p);
        for (int arm = 0; arm < 3; arm++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(spin + arm * 120));
            FxDraw.crescent(pose.last(), glow, 0, 150, size * 0.95F, 0.12F + 0.22F * p, 0xC070FF, (int) (110 + 120 * p));
            FxDraw.crescent(pose.last(), glow, 20, 110, size * 0.6F, 0.08F + 0.12F * p, 0xF0D8FF, (int) (90 + 150 * p));
            pose.popPose();
        }
        FxDraw.ring(pose.last(), glow, size * 0.9F, size * 1.05F, 0xB060FF, (int) (60 + 100 * p), 0);
        pose.popPose();
        // 中心: 向こう側へ抜ける暗い喉と、その縁の光
        FxDraw.billboard(pose, glow, camera, size * 1.5F, 0x9040E0, (int) (40 + 80 * p));
        pose.pushPose();
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.08F + 0.32F * p, 0x07020F,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
        GravitationalLensing.addDistortion(Vec3.atCenterOf(core), 0.08F + 0.45F * p, 0.25F + 0.55F * p, 1.9F);
    }

    @Override
    public boolean shouldRenderOffScreen(WormholeGeneratorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(WormholeGeneratorBlockEntity be) {
        return new AABB(be.core()).inflate(3);
    }
}
