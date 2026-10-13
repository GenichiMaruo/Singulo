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
        public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x, below ? Math.min(y, plane) : Math.max(y, plane), z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            to.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            to.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }
}
