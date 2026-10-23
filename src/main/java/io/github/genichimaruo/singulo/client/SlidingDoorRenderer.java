package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.lab.SlidingDoorBlock;
import io.github.genichimaruo.singulo.ruin.lab.SlidingDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * スライドドアの2枚の扉。閉じていると真ん中で合わさり、開くと左右の壁の中へ7ピクセルずつ滑り込む（先の1ピクセルは枠の陰に残る）。
 * 絵は {ブロックID}_leaf_lower / _leaf_upper（左半分が左の扉、右半分が右の扉）。扉の小口は、絵のいちばん外の列を引き伸ばす。
 */
public class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity> {
    /** 扉の厚さ（z 6.5〜9.5）と、開いたときに滑る分（ピクセル）。 */
    private static final float Z0 = 6.5F / 16, Z1 = 9.5F / 16, TRAVEL = 7F / 16;
    /**
     * 扉の外の端・上の端・下の端。枠（左右 x 0〜1.5、上の鴨居 y 14〜16）の面と同じ平面に重ねるとちらつくので、外と上の端は枠の中に
     * 少し入れ、下の端はわずかに浮かせる（壁に埋めて置くと隣のブロックで枠の面が消えるが、単独で置くと両方が描かれる）。
     */
    private static final float OUTER = 0.25F / 16, TOP = 15F / 16, BOTTOM = 0.1F / 16;

    public SlidingDoorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SlidingDoorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SlidingDoorBlock)) {
            return;
        }
        boolean lower = state.getValue(SlidingDoorBlock.HALF) == DoubleBlockHalf.LOWER;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation tex = id.withPath(p -> "textures/block/" + p + "_leaf_" + (lower ? "lower" : "upper") + ".png");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(tex));
        float s = be.slide(partialTick) * TRAVEL;
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(-state.getValue(SlidingDoorBlock.FACING).toYRot() + 180), 0.5F, 0, 0.5F);
        leaf(pose.last(), vc, OUTER - s, 0.5F - s, OUTER, 0.5F, lower, light, overlay);
        leaf(pose.last(), vc, 0.5F + s, 1 - OUTER + s, 0.5F, 1 - OUTER, lower, light, overlay);
        pose.popPose();
    }

    /** 扉1枚（x0〜x1 に、絵の u0〜u1 の部分を貼る。u は 0〜1）。上半分は上の端を、下半分は下の端を枠の面から外す。 */
    private static void leaf(PoseStack.Pose p, VertexConsumer vc, float x0, float x1, float u0, float u1, boolean lower, int light, int overlay) {
        float edge = u0 < 0.5F ? 0 : 1 - 1F / 16;        // 小口の色に使う列（外側の端）
        float e1 = edge + 1F / 16;
        float y0 = lower ? BOTTOM : 0, y1 = lower ? 1 : TOP;
        // 前（north、-z）と後ろ（south、+z。絵を左右反転）
        face(vc, p, x1, y0, Z0, x0, y0, Z0, x0, y1, Z0, x1, y1, Z0, u1, u0, 0, 0, -1, light, overlay);
        face(vc, p, x0, y0, Z1, x1, y0, Z1, x1, y1, Z1, x0, y1, Z1, u0, u1, 0, 0, 1, light, overlay);
        // 左右の小口
        face(vc, p, x0, y0, Z0, x0, y0, Z1, x0, y1, Z1, x0, y1, Z0, edge, e1, -1, 0, 0, light, overlay);
        face(vc, p, x1, y0, Z1, x1, y0, Z0, x1, y1, Z0, x1, y1, Z1, edge, e1, 1, 0, 0, light, overlay);
        // 上の小口（上半分）と下の小口（下半分）。上下の継ぎ目の面は描かない
        if (!lower) {
            flat(vc, p, x0, x1, y1, u0, u1, 1, light, overlay);
        } else {
            flat(vc, p, x0, x1, y0, u0, u1, -1, light, overlay);
        }
    }

    /** 縦の面（4つの角を下から反時計回りに。u は左右の端、v は高さから: 上の端 1 が v 0、下の端 0 が v 1）。 */
    private static void face(VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float ua, float ub, float nx, float ny, float nz,
                             int light, int overlay) {
        vertex(vc, p, ax, ay, az, ua, 1 - ay, nx, ny, nz, light, overlay);
        vertex(vc, p, bx, by, bz, ub, 1 - by, nx, ny, nz, light, overlay);
        vertex(vc, p, cx, cy, cz, ub, 1 - cy, nx, ny, nz, light, overlay);
        vertex(vc, p, dx, dy, dz, ua, 1 - dy, nx, ny, nz, light, overlay);
    }

    /** 水平の面（y の高さ。上向きか下向き）。色は絵の上の1行。 */
    private static void flat(VertexConsumer vc, PoseStack.Pose p, float x0, float x1, float y, float u0, float u1, int ny, int light,
                             int overlay) {
        float v0 = 0, v1 = 1F / 16;
        if (ny > 0) {
            vertex(vc, p, x0, y, Z0, u0, v0, 0, 1, 0, light, overlay);
            vertex(vc, p, x0, y, Z1, u0, v1, 0, 1, 0, light, overlay);
            vertex(vc, p, x1, y, Z1, u1, v1, 0, 1, 0, light, overlay);
            vertex(vc, p, x1, y, Z0, u1, v0, 0, 1, 0, light, overlay);
        } else {
            vertex(vc, p, x0, y, Z1, u0, v1, 0, -1, 0, light, overlay);
            vertex(vc, p, x0, y, Z0, u0, v0, 0, -1, 0, light, overlay);
            vertex(vc, p, x1, y, Z0, u1, v0, 0, -1, 0, light, overlay);
            vertex(vc, p, x1, y, Z1, u1, v1, 0, -1, 0, light, overlay);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float nx, float ny, float nz,
                               int light, int overlay) {
        vc.vertex(p.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(overlay).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }

}
