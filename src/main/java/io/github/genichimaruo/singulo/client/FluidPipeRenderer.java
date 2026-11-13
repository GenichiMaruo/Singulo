package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlock;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 液体パイプの中を流れる液体。流れているパイプだけを描く（流れていなければ何もしない）。
 * <ul>
 *   <li>液体はその液体の絵と色で、管の内側（ガラスの窓・透明な管から見える）に柱として描く。液体は量に応じた深さで底にたまり、
 *   気体はうすく管いっぱいに広がる。上下の管はいつもいっぱい。</li>
 *   <li>流れの向きへ、少し明るい帯が流れていく（入ってきた腕は中心へ、出ていく腕は外へ）。帯の位置は世界の座標で決めるので、
 *   まっすぐ続くパイプでは途切れずにつながる。</li>
 * </ul>
 * どの面から入って出たか・量・液体は、ネットワークが20 tick ごとに決めて送ってくる（{@link FluidPipeBlockEntity}）。
 */
public class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity> {
    /** 帯の長さ・間隔（ブロック）と、流れの速さ（ブロック/秒）。 */
    private static final float STREAK = 0.18F, SPACING = 0.75F, SPEED = 1.6F;
    /** 管の内側から少し離す（ガラスの面と重ならないように）。 */
    private static final float GAP = 0.06F / 16;

    public FluidPipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FluidPipeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.visualState() != FluidPipeBlockEntity.FLOWING || be.visualFluid().isEmpty() || be.getLevel() == null
                || !(be.getBlockState().getBlock() instanceof FluidPipeBlock pipe)
                || io.github.genichimaruo.singulo.cable.ConduitCover.of(be.getBlockState()) == io.github.genichimaruo.singulo.cable.ConduitCover.State.BLOCK) {
            return;     // ブロックをはめ込んだカバーの中は見えない
        }
        FluidLook.Look look = FluidLook.look(be.visualFluid());
        boolean gas = be.visualFluid().getFluid().getFluidType().isLighterThanAir();
        float r = pipe.profile().innerHalf() - GAP;
        float lo = 0.5F - r, hi = 0.5F + r;
        // 液体は量の桁で深さが決まる（細い流れは底を這い、その段階の上限近くでいっぱい）
        float level = gas ? 1 : depth(be.visualAmount(), pipe.capacity());
        float top = lo + (hi - lo) * level;
        // 色は深度を書かずに遠い順に重ね、そのあと深度だけ書く（重なり合う箱と帯で、面が抜けて見えないように）
        VertexConsumer vc = GlowDepth.fluid(buffers);
        int rgb = look.rgb();
        int alpha = look.alpha();
        FluidLook.box(pose, vc, look, rgb, alpha, light, 0x3F, lo, lo, lo, hi, top, hi);
        float time = (be.getLevel().getGameTime() % 24000 + partialTick) / 20F;
        int bright = FxDraw.lerpRgb(rgb, 0xFFFFFF, 0.45F);
        for (Direction d : Direction.values()) {
            boolean in = be.flowsIn(d), out = be.flowsOut(d);
            if (!in && !out) {
                continue;
            }
            float armTop = d.getAxis() == Direction.Axis.Y ? hi : top;
            float[] a = arm(d, lo, hi, armTop);
            // 腕の両端の面（中心側と、隣のパイプとの継ぎ目）は描かない
            int mask = 0x3F & ~(1 << d.get3DDataValue()) & ~(1 << d.getOpposite().get3DDataValue());
            FluidLook.box(pose, vc, look, rgb, alpha, light, mask, a[0], a[1], a[2], a[3], a[4], a[5]);
            streaks(be.getBlockPos(), d, out, time, pose, vc, look, bright, alpha, light, lo, hi, armTop);
        }
    }

    /** 量の桁で決める深さ（0.3〜1）。上限なしのパイプは 1 MB/t でいっぱい。 */
    private static float depth(int amount, long capacity) {
        double full = Math.min(capacity, 1_000_000L);
        return (float) Mth.clamp(0.3 + 0.7 * Math.log10(Math.max(1, amount)) / Math.log10(Math.max(10, full)), 0.3, 1);
    }

    /** 中心の箱の縁から、面 d まで伸びる腕の箱（x0, y0, z0, x1, y1, z1）。 */
    private static float[] arm(Direction d, float lo, float hi, float top) {
        return switch (d) {
            case DOWN -> new float[]{lo, 0, lo, hi, lo, hi};
            case UP -> new float[]{lo, hi, lo, hi, 1, hi};
            case NORTH -> new float[]{lo, lo, 0, hi, top, lo};
            case SOUTH -> new float[]{lo, lo, hi, hi, top, 1};
            case WEST -> new float[]{0, lo, lo, lo, top, hi};
            case EAST -> new float[]{hi, lo, lo, 1, top, hi};
        };
    }

    /**
     * 流れの向きへ動く明るい帯。腕の上での位置 s（中心 0 〜 面 0.5）は、世界の座標を流れの向きに測った値と時刻で決める。
     * 出ていく腕は中心から外へ、入ってくる腕は外から中心へ動く。
     */
    private static void streaks(BlockPos pos, Direction d, boolean out, float time, PoseStack pose, VertexConsumer vc,
                                FluidLook.Look look, int rgb, int alpha, int light, float lo, float hi, float top) {
        // 流れの向き（出ていく腕なら d、入ってくる腕なら d の逆）に沿った世界の位置
        Direction flow = out ? d : d.getOpposite();
        float along = pos.getX() * flow.getStepX() + pos.getY() * flow.getStepY() + pos.getZ() * flow.getStepZ();
        float lift = 0.15F / 16;
        for (int k = -1; k <= 1; k++) {
            // 帯の中心の、流れの向きに測った位置（ブロックの中心から）
            float c = Mth.positiveModulo(time * SPEED - along, SPACING) + k * SPACING;
            // 腕の上の位置 s（中心から面へ 0〜0.5）に直す
            float s = out ? c : -c;
            float edge = hi - 0.5F;
            float s0 = Mth.clamp(s - STREAK / 2, edge, 0.5F), s1 = Mth.clamp(s + STREAK / 2, edge, 0.5F);
            if (s1 - s0 <= 0.01F) {
                continue;
            }
            float a0 = 0.5F + s0, a1 = 0.5F + s1;
            float b0 = 0.5F - s1, b1 = 0.5F - s0;
            float l = lo - lift, h = hi + lift, t = Math.min(top + lift, h);
            switch (d) {
                case UP -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x3C, l, a0, l, h, a1, h);
                case DOWN -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x3C, l, b0, l, h, b1, h);
                case SOUTH -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x33, l, l, a0, h, t, a1);
                case NORTH -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x33, l, l, b0, h, t, b1);
                case EAST -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x0F, a0, l, l, a1, t, h);
                case WEST -> FluidLook.box(pose, vc, look, rgb, alpha, light, 0x0F, b0, l, l, b1, t, h);
            }
        }
    }

    public AABB getRenderBoundingBox(FluidPipeBlockEntity be) {
        return new AABB(be.getBlockPos());
    }
}
