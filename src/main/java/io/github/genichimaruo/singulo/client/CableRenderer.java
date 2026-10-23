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
 *
 * <p>最上位のホライズン・バスだけ、ほかのケーブルでは運べない量が流れているとき、さらに光が増える。
 * <ul>
 *   <li>{@link #SHEATH_FLOW} 以上: ケーブルを一回り大きい半透明の被膜が包み、流れの向きへ明るさの波が走る。</li>
 *   <li>{@link #FRAME_FLOW} 以上: 被膜の外に、半透明の四角い箱の枠が流れに乗って次々と通り過ぎる。</li>
 * </ul>
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
    /** 被膜が出る量（FE/t）: トポロジカル導線の上限（100 MFE/t）を超える量。 */
    public static final long SHEATH_FLOW = 100_000_000L;
    /** 箱の枠が流れる量（FE/t）: 1 GFE/t（Pリアクターの出力や点火の送電の桁）。 */
    public static final long FRAME_FLOW = 1_000_000_000L;
    /** 被膜の断面の半分（ブロック。ケーブルの芯は 4/16）と、脈打つ幅。 */
    private static final float SHEATH_HALF = 5.5F / 16, SHEATH_PULSE = 0.35F / 16;
    /** 箱の枠の断面の半分・奥行きの半分・枠の太さ（ブロック）、間隔（ブロック）と速さ（ブロック/秒）。 */
    private static final float FRAME_HALF = 7.5F / 16, FRAME_DEPTH = 1.75F / 16, FRAME_BAR = 1F / 16;
    private static final float FRAME_SPACING = 1.25F, FRAME_SPEED = 6.0F;

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
        if (cable.profile() == io.github.genichimaruo.singulo.cable.CableProfile.HORIZON && flow >= SHEATH_FLOW) {
            // 加算で重ねる（下の芯や周りが透けたまま明るくなり、光の膜に見える）
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            sheath(be, last, fx, flow, time, phase);
            if (flow >= FRAME_FLOW) {
                frames(be, last, fx, flow, time);
            }
        }
    }

    // ------------------------------------------------------------------ ホライズン・バスの大電力の光

    /**
     * 半透明の被膜: 電力の通る腕と中心を、芯より一回り大きい箱で包む。腕は切れごとに明るさの波を乗せ、全体はゆっくり脈打つ。
     * 量が FRAME_FLOW に近づくほど白く濃くなる。
     */
    private static void sheath(CableBlockEntity be, PoseStack.Pose p, VertexConsumer vc, long flow, float time, float phase) {
        float k = (float) Mth.clamp(Math.log10(flow / (double) SHEATH_FLOW) / Math.log10(FRAME_FLOW / (double) SHEATH_FLOW), 0, 1);
        float h = SHEATH_HALF + SHEATH_PULSE * Mth.sin(time * 3.1F);
        float lo = 0.5F - h, hi = 0.5F + h;
        int base = FxDraw.lerpRgb(0x3F7CFF, 0x6FA4FF, k);
        int alpha = (int) (70 + 30 * k);
        boolean[] arm = new boolean[6];
        for (Direction d : Direction.values()) {
            arm[d.ordinal()] = be.flowsIn(d) || be.flowsOut(d);
        }
        // 中心: 腕のない向きの面だけ閉じる
        for (Direction d : Direction.values()) {
            if (!arm[d.ordinal()]) {
                face(p, vc, d, lo, hi, d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi : lo, base, alpha);
            }
        }
        for (Direction d : Direction.values()) {
            if (!arm[d.ordinal()]) {
                continue;
            }
            boolean out = be.flowsOut(d) && !be.flowsIn(d);
            for (int i = 0; i < SLICES; i++) {
                float r0 = h + (0.5F - h) * i / SLICES;
                float r1 = h + (0.5F - h) * (i + 1) / SLICES;
                float s = (r0 + r1) / 2;
                float along = out ? 0.5F + s : 0.5F - s;
                // 芯の波より速く短い波（大電力が勢いよく流れて見える）
                float wave = 0.55F + 0.45F * Mth.sin((float) (2 * Math.PI) * (along + phase * 1.6F) / 1.5F);
                armSides(p, vc, d, r0, r1, lo, hi, scale(base, 0.75F + 0.25F * wave), (int) (alpha * (0.7F + 0.6F * wave)));
            }
        }
    }

    /**
     * 箱の枠: 流れの道（{@link CableBlockEntity#distance()} の座標）に FRAME_SPACING ごとに並んだ枠が、FRAME_SPEED で出口へ進む。
     * 枠は薄い半透明の箱と、明るい4本の縁。通り過ぎる間に少し膨らんでからしぼむ。
     */
    private static void frames(CableBlockEntity be, PoseStack.Pose p, VertexConsumer vc, long flow, float time) {
        float k = (float) Mth.clamp(Math.log10(flow / (double) FRAME_FLOW) / 2, 0, 1);     // 1 GFE/t で 0、100 GFE/t で 1
        int edge = FxDraw.lerpRgb(0x8FC0FF, 0xE8F4FF, k);
        int fill = FxDraw.lerpRgb(0x3F6CFF, 0x88B0FF, k);
        float start = ((time * FRAME_SPEED - be.distance()) % FRAME_SPACING + FRAME_SPACING) % FRAME_SPACING;
        for (float along = start; along < 1; along += FRAME_SPACING) {
            boolean inArm = along < 0.5F;
            float r = inArm ? 0.5F - along : along - 0.5F;
            // ブロックの真ん中でいちばん大きく濃い
            float swell = Mth.cos(Mth.PI * r);
            float half = FRAME_HALF * (0.86F + 0.14F * swell);
            int a = (int) ((150 + 90 * k) * (0.45F + 0.55F * swell));
            for (Direction d : Direction.values()) {
                if (inArm ? !be.flowsIn(d) : !be.flowsOut(d)) {
                    continue;
                }
                float sign = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
                float c = 0.5F + sign * r;
                frame(p, vc, d.getAxis(), c, half, edge, fill, a);
            }
        }
    }

    /** 軸 axis の位置 c に、断面の半分 half の枠（薄い箱＋4本の縁）を描く。 */
    private static void frame(PoseStack.Pose p, VertexConsumer vc, Direction.Axis axis, float c, float half, int edge, int fill,
                              int alpha) {
        float lo = 0.5F - half, hi = 0.5F + half;
        float a0 = c - FRAME_DEPTH, a1 = c + FRAME_DEPTH;
        // 薄い箱（面はごく淡く）
        cuboid(p, vc, axis, a0, a1, lo, hi, lo, hi, fill, alpha / 3);
        // 4本の縁
        float b = FRAME_BAR;
        cuboid(p, vc, axis, a0, a1, lo, hi, hi - b, hi, edge, alpha);
        cuboid(p, vc, axis, a0, a1, lo, hi, lo, lo + b, edge, alpha);
        cuboid(p, vc, axis, a0, a1, lo, lo + b, lo + b, hi - b, edge, alpha);
        cuboid(p, vc, axis, a0, a1, hi - b, hi, lo + b, hi - b, edge, alpha);
    }

    /**
     * 軸の向きに a0〜a1、残りの2つの軸に u0〜u1・v0〜v1 の箱。u・v は軸 X なら (Y, Z)、Y なら (X, Z)、Z なら (X, Y)。
     */
    private static void cuboid(PoseStack.Pose p, VertexConsumer vc, Direction.Axis axis, float a0, float a1, float u0, float u1,
                               float v0, float v1, int rgb, int alpha) {
        float x0, x1, y0, y1, z0, z1;
        switch (axis) {
            case X -> { x0 = a0; x1 = a1; y0 = u0; y1 = u1; z0 = v0; z1 = v1; }
            case Y -> { y0 = a0; y1 = a1; x0 = u0; x1 = u1; z0 = v0; z1 = v1; }
            default -> { z0 = a0; z1 = a1; x0 = u0; x1 = u1; y0 = v0; y1 = v1; }
        }
        quad(vc, p, rgb, alpha, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vc, p, rgb, alpha, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(vc, p, rgb, alpha, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        quad(vc, p, rgb, alpha, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, p, rgb, alpha, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vc, p, rgb, alpha, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
    }

    /** 中心の箱（lo〜hi の立方体）の、d の向きの面。at はその面の位置。 */
    private static void face(PoseStack.Pose p, VertexConsumer vc, Direction d, float lo, float hi, float at, int rgb, int alpha) {
        switch (d.getAxis()) {
            case X -> quad(vc, p, rgb, alpha, d.getStepX(), 0, 0, at, lo, lo, at, hi, lo, at, hi, hi, at, lo, hi);
            case Y -> quad(vc, p, rgb, alpha, 0, d.getStepY(), 0, lo, at, lo, hi, at, lo, hi, at, hi, lo, at, hi);
            case Z -> quad(vc, p, rgb, alpha, 0, 0, d.getStepZ(), lo, lo, at, hi, lo, at, hi, hi, at, lo, hi, at);
        }
    }

    /** 中心から d の向きへ r0〜r1 の区間の、断面 lo〜hi の腕の4つの側面。 */
    private static void armSides(PoseStack.Pose p, VertexConsumer vc, Direction d, float r0, float r1, float lo, float hi,
                                 int rgb, int alpha) {
        float sign = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        float a = 0.5F + sign * r0;
        float b = 0.5F + sign * r1;
        float a0 = Math.min(a, b), a1 = Math.max(a, b);
        // 側面だけ（両端の面は描かない）: 上下と前後（軸ごとに残りの2軸の4面）
        switch (d.getAxis()) {
            case X -> {
                quad(vc, p, rgb, alpha, 0, 1, 0, a0, hi, lo, a0, hi, hi, a1, hi, hi, a1, hi, lo);
                quad(vc, p, rgb, alpha, 0, -1, 0, a0, lo, lo, a1, lo, lo, a1, lo, hi, a0, lo, hi);
                quad(vc, p, rgb, alpha, 0, 0, 1, a0, lo, hi, a1, lo, hi, a1, hi, hi, a0, hi, hi);
                quad(vc, p, rgb, alpha, 0, 0, -1, a0, lo, lo, a0, hi, lo, a1, hi, lo, a1, lo, lo);
            }
            case Y -> {
                quad(vc, p, rgb, alpha, 1, 0, 0, hi, a0, lo, hi, a1, lo, hi, a1, hi, hi, a0, hi);
                quad(vc, p, rgb, alpha, -1, 0, 0, lo, a0, lo, lo, a0, hi, lo, a1, hi, lo, a1, lo);
                quad(vc, p, rgb, alpha, 0, 0, 1, lo, a0, hi, hi, a0, hi, hi, a1, hi, lo, a1, hi);
                quad(vc, p, rgb, alpha, 0, 0, -1, lo, a0, lo, lo, a1, lo, hi, a1, lo, hi, a0, lo);
            }
            case Z -> {
                quad(vc, p, rgb, alpha, 0, 1, 0, lo, hi, a0, lo, hi, a1, hi, hi, a1, hi, hi, a0);
                quad(vc, p, rgb, alpha, 0, -1, 0, lo, lo, a0, hi, lo, a0, hi, lo, a1, lo, lo, a1);
                quad(vc, p, rgb, alpha, 1, 0, 0, hi, lo, a0, hi, hi, a0, hi, hi, a1, hi, lo, a1);
                quad(vc, p, rgb, alpha, -1, 0, 0, lo, lo, a0, lo, lo, a1, lo, hi, a1, lo, hi, a0);
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
        vc.vertex(p.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FxDraw.FULL_BRIGHT).normal(p.normal(), nx, ny, nz).endVertex();
    }
}
