package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

/**
 * 電力が実際に通っているケーブルの芯を光らせる。光るのは電力が入ってきた面と出ていった面の腕だけ（通っていない枝は暗いまま）。
 * 明るさは通っている量で決まり、入口から出口へ向かって、ゆるやかな明るさの波が流れる（強弱の差は小さく、ずっと光っている）。
 */
public class CableRenderer implements BlockEntityRenderer<CableBlockEntity> {
    /** 波の長さ（ブロック）と速さ（ブロック/秒）。 */
    private static final float WAVELENGTH = 3.0F;
    private static final float SPEED = 4.0F;
    /** 波の明るさの幅（1 - DEPTH 〜 1）。 */
    private static final float DEPTH = 0.12F;
    /** 腕1本を何切れに分けて波を描くか。 */
    private static final int SLICES = 4;
    private static final float LIFT = 0.0015F;

    public CableRenderer(BlockEntityRendererProvider.Context context) {}

    /** 段階ごとの光の色（弱いとき・強いとき）と、いちばん明るくなる量（FE/t）。 */
    private record Glow(int dim, int bright, double full) {}

    private static Glow glowOf(Block block) {
        if (block == SinguloBlocks.COPPER_WIRE.get()) {
            return new Glow(0xD8783A, 0xFFD69A, 2_000);
        }
        if (block == SinguloBlocks.SUPERCONDUCTING_CABLE.get()) {
            return new Glow(0x4FB8F0, 0xDDF8FF, 1_000_000);
        }
        if (block == SinguloBlocks.TOPOLOGICAL_WIRE.get()) {
            return new Glow(0x9C7CF0, 0xF0E2FF, 100_000_000);
        }
        return new Glow(0x9AA4FF, 0xFFFFFF, 1e10);
    }

    @Override
    public void render(CableBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long flow = be.flow();
        if (flow <= 0 || be.getLevel() == null || !(be.getBlockState().getBlock() instanceof CableBlock cable)) {
            return;
        }
        Glow glow = glowOf(cable);
        // 量の桁で明るさを決める（1 FE/t で 0、その段階の上限で 1）
        float k = (float) Mth.clamp(Math.log10(flow) / Math.log10(glow.full()), 0, 1);
        int base = FxDraw.lerpRgb(glow.dim(), glow.bright(), 0.2F + 0.8F * k);
        int alpha = (int) (170 + 85 * k);
        float time = (be.getLevel().getGameTime() % 24000 + partialTick) / 20F;
        float phase = be.distance() - time * SPEED;

        int w = switch (cable.profile()) {
            case COPPER -> 4;
            case SUPERCONDUCTING -> 4;
            case TOPOLOGICAL -> 6;
            case HORIZON -> 8;
        };
        float lo = (8 - w / 2F) / 16F - LIFT;
        float hi = (8 + w / 2F) / 16F + LIFT;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        PoseStack.Pose last = pose.last();
        for (Direction d : Direction.values()) {
            boolean in = be.flowsIn(d);
            boolean out = be.flowsOut(d);
            if (!in && !out) {
                continue;
            }
            for (int i = 0; i < SLICES; i++) {
                // 中心からの距離 r0〜r1（0〜0.5）
                float r0 = 0.5F * i / SLICES;
                float r1 = 0.5F * (i + 1) / SLICES;
                float s = (r0 + r1) / 2;
                // 道の上の位置: 入ってきた腕は面→中心（0→0.5）、出ていく腕は中心→面（0.5→1）
                float along = out && !in ? 0.5F + s : 0.5F - s;
                float wave = 1 - DEPTH * (0.5F + 0.5F * Mth.sin((float) (2 * Math.PI) * (along + phase) / WAVELENGTH));
                int rgb = scale(base, wave);
                stripes(last, vc, d, r0, r1, lo, hi, rgb, alpha);
            }
        }
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** 中心から d の向きへ r0〜r1 の区間で、腕の4つの側面に芯の帯（幅2ドット）を描く。 */
    private static void stripes(PoseStack.Pose p, VertexConsumer vc, Direction d, float r0, float r1, float lo, float hi,
                                int rgb, int alpha) {
        float sign = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        float a = 0.5F + sign * r0;
        float b = 0.5F + sign * r1;
        float t0 = 7 / 16F;
        float t1 = 9 / 16F;
        switch (d.getAxis()) {
            case X -> {
                quad(vc, p, rgb, alpha, 0, 1, 0, a, hi, t0, a, hi, t1, b, hi, t1, b, hi, t0);
                quad(vc, p, rgb, alpha, 0, -1, 0, a, lo, t0, b, lo, t0, b, lo, t1, a, lo, t1);
                quad(vc, p, rgb, alpha, 0, 0, 1, a, t0, hi, b, t0, hi, b, t1, hi, a, t1, hi);
                quad(vc, p, rgb, alpha, 0, 0, -1, a, t0, lo, a, t1, lo, b, t1, lo, b, t0, lo);
            }
            case Y -> {
                quad(vc, p, rgb, alpha, 1, 0, 0, hi, a, t0, hi, b, t0, hi, b, t1, hi, a, t1);
                quad(vc, p, rgb, alpha, -1, 0, 0, lo, a, t0, lo, a, t1, lo, b, t1, lo, b, t0);
                quad(vc, p, rgb, alpha, 0, 0, 1, t0, a, hi, t1, a, hi, t1, b, hi, t0, b, hi);
                quad(vc, p, rgb, alpha, 0, 0, -1, t0, a, lo, t0, b, lo, t1, b, lo, t1, a, lo);
            }
            case Z -> {
                quad(vc, p, rgb, alpha, 0, 1, 0, t0, hi, a, t0, hi, b, t1, hi, b, t1, hi, a);
                quad(vc, p, rgb, alpha, 0, -1, 0, t0, lo, a, t1, lo, a, t1, lo, b, t0, lo, b);
                quad(vc, p, rgb, alpha, 1, 0, 0, hi, t0, a, hi, t1, a, hi, t1, b, hi, t0, b);
                quad(vc, p, rgb, alpha, -1, 0, 0, lo, t0, a, lo, t0, b, lo, t1, b, lo, t1, a);
            }
        }
    }

    /** 両面から見えるように、表と裏の2枚を描く。 */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, int rgb, int alpha, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(vc, p, ax, ay, az, rgb, alpha, nx, ny, nz);
        vertex(vc, p, bx, by, bz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, bx, by, bz, rgb, alpha, nx, ny, nz);
        vertex(vc, p, ax, ay, az, rgb, alpha, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, int alpha,
                               float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FxDraw.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }
}
