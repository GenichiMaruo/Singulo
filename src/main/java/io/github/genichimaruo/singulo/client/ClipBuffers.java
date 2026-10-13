package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 頂点の高さを、ある面（カメラからの相対の y）で切りそろえる描画先。面より上（または下）の頂点は面の上に押しつぶされるので、
 * 箱でできた体が「そこまで積み上がった」ように見える（3D 印刷の途中の見た目）。
 */
final class ClipBuffers {
    private ClipBuffers() {}

    /** 面より下だけを描く（上の頂点は面に押しつぶす）。 */
    static MultiBufferSource below(MultiBufferSource buffers, float plane) {
        return type -> new Clip(buffers.getBuffer(type), plane, true);
    }

    /** 面より上だけを描く。 */
    static VertexConsumer above(VertexConsumer vc, float plane) {
        return new Clip(vc, plane, false);
    }

    private static final class Clip implements VertexConsumer {
        private final VertexConsumer to;
        private final float plane;
        private final boolean below;

        Clip(VertexConsumer to, float plane, boolean below) {
            this.to = to;
            this.plane = plane;
            this.below = below;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, below ? Math.min(y, plane) : Math.max(y, plane), z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            to.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            to.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }
        @Override public void endVertex() { to.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { to.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { to.unsetDefaultColor(); }
    }
}
