package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.block.GlassJoin;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * 水に浸かったガラスの階段の水（0.21.0）。水はふつうの階段と同じに描き（バニラの描き方のまま）、透けるガラスの段の中には
 * 水が入っていないように見せる。
 * <ul>
 * <li>バニラが描く水の面を受け取り、階段を 2×2×2 に分けた区画（{@link GlassJoin#filledOctants}）で切り分けて、
 * ガラスで埋まっている区画にかかる部分を捨てる</li>
 * <li>水とガラスの境（バニラは描かない）に水の面を足す。ガラスの側から見える向き</li>
 * <li>隣が同じ水に浸かったガラスの階段で、こちらの空いている区画の向かいがガラスなら、その境にも面を足す
 * （バニラは同じ水どうしの境を描かない）</li>
 * </ul>
 * Sodium を入れているときは Sodium が水を描くので、ここは使われない（バニラと同じ見た目）。
 */
public final class GlassStairsWater {
    private GlassStairsWater() {}

    private static final float EPS = 1e-4F;

    /** 1つの頂点（チャンクの区画の中の座標と、色・UV・光・法線）。 */
    record V(float x, float y, float z, int r, int g, int b, int a, float u, float v, int l0, int l1, float nx, float ny, float nz) {
        float axis(int i) {
            return i == 0 ? x : i == 1 ? y : z;
        }

        V lerp(V o, float t) {
            return new V(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t, li(r, o.r, t), li(g, o.g, t), li(b, o.b, t), li(a, o.a, t),
                    u + (o.u - u) * t, v + (o.v - v) * t, li(l0, o.l0, t), li(l1, o.l1, t), nx, ny, nz);
        }

        private static int li(int p, int q, float t) {
            return Math.round(p + (q - p) * t);
        }

        void emit(VertexConsumer out) {
            out.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setUv2(l0, l1).setNormal(nx, ny, nz);
        }
    }

    /** バニラの水の頂点を受け取る（4つで1面）。 */
    public static final class Capture implements VertexConsumer {
        final List<V> vertices = new ArrayList<>();
        private float x, y, z, u, v, nx, ny, nz;
        private int r = 255, g = 255, b = 255, a = 255, l0, l1;
        private boolean open;

        private void flush() {
            if (open) {
                vertices.add(new V(x, y, z, r, g, b, a, u, v, l0, l1, nx, ny, nz));
                open = false;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            this.x = x;
            this.y = y;
            this.z = z;
            open = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            l0 = u;
            l1 = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override public VertexConsumer setColor(int argb) { return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24); }
        @Override public VertexConsumer setLineWidth(float width) { return this; }

        List<V> done() {
            flush();
            return vertices;
        }
    }

    /** バニラが描いた面を、空いている区画の分だけ out に描き、ガラスとの境の面を足す。 */
    public static void finish(BlockAndTintGetter level, BlockPos pos, VertexConsumer out, BlockState state, FluidState fluid, Capture capture,
                              int light, FluidModel model) {
        int filled = GlassJoin.filledOctants(state);
        float ox = pos.getX() & 15, oy = pos.getY() & 15, oz = pos.getZ() & 15;
        List<V> all = capture.done();
        for (int q = 0; q + 3 < all.size(); q += 4) {
            List<V> quad = List.of(all.get(q), all.get(q + 1), all.get(q + 2), all.get(q + 3));
            if (filled == 0) {
                quad.forEach(v -> v.emit(out));
                continue;
            }
            for (int i = 0; i < 8; i++) {
                if ((filled >> i & 1) != 0) {
                    continue;
                }
                List<V> poly = quad;
                float[] o = {ox, oy, oz};
                int[] idx = {i & 1, i >> 1 & 1, i >> 2 & 1};
                for (int axis = 0; axis < 3 && poly.size() >= 3; axis++) {
                    float lo = o[axis] + idx[axis] * 0.5F, hi = lo + 0.5F;
                    poly = clip(poly, axis, lo - EPS, true);
                    poly = clip(poly, axis, hi + EPS, false);
                }
                emitPolygon(poly, out);
            }
        }
        if (filled != 0) {
            borders(level, pos, out, state, fluid, filled, light, ox, oy, oz, model);
        }
    }

    /** 平面 axis = at の片側（keepAbove なら at 以上）を残す（Sutherland–Hodgman）。 */
    private static List<V> clip(List<V> poly, int axis, float at, boolean keepAbove) {
        List<V> res = new ArrayList<>(poly.size() + 2);
        for (int k = 0; k < poly.size(); k++) {
            V p = poly.get(k), q = poly.get((k + 1) % poly.size());
            float dp = keepAbove ? p.axis(axis) - at : at - p.axis(axis);
            float dq = keepAbove ? q.axis(axis) - at : at - q.axis(axis);
            if (dp >= 0) {
                res.add(p);
            }
            if ((dp >= 0) != (dq >= 0)) {
                res.add(p.lerp(q, dp / (dp - dq)));
            }
        }
        return res;
    }

    /** 凸の多角形を、扇形に分けた四角（三角は1つの頂点を繰り返す）で描く。 */
    private static void emitPolygon(List<V> poly, VertexConsumer out) {
        if (poly.size() < 3) {
            return;
        }
        for (int k = 1; k + 1 < poly.size(); k += 2) {
            V a = poly.get(0), b = poly.get(k), c = poly.get(k + 1), d = k + 2 < poly.size() ? poly.get(k + 2) : c;
            a.emit(out);
            b.emit(out);
            c.emit(out);
            d.emit(out);
        }
    }

    // ------------------------------------------------------------------ 水とガラスの境

    private static void borders(BlockAndTintGetter level, BlockPos pos, VertexConsumer out, BlockState state, FluidState fluid, int filled, int light,
                                float ox, float oy, float oz, FluidModel model) {
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tint = model.fluidTintSource() == null ? -1 : model.fluidTintSource().colorInWorld(fluid, state, level, pos);
        float r = (tint >> 16 & 255) / 255F, g = (tint >> 8 & 255) / 255F, b = (tint & 255) / 255F;
        Quad q = new Quad(out, ox, oy, oz, sprite, r, g, b, light, level);
        float top = fluid.getHeight(level, pos);
        for (int i = 0; i < 8; i++) {
            if ((filled >> i & 1) != 0) {
                outsideWater(level, pos, q, fluid, i);
                continue;
            }
            int ix = i & 1, iy = i >> 1 & 1, iz = i >> 2 & 1;
            float x0 = ix * 0.5F, y0 = iy * 0.5F, z0 = iz * 0.5F, x1 = x0 + 0.5F, z1 = z0 + 0.5F;
            float y1 = iy == 1 ? top : 0.5F;
            if (y1 <= y0) {
                continue;
            }
            for (Direction d : Direction.values()) {
                int nx = ix + d.getStepX(), ny = iy + d.getStepY(), nz = iz + d.getStepZ();
                boolean inside = nx >= 0 && nx < 2 && ny >= 0 && ny < 2 && nz >= 0 && nz < 2;
                boolean glass = inside ? (filled >> (nx + 2 * ny + 4 * nz) & 1) != 0 : glassAcross(level, pos, fluid, d, ix, iy, iz);
                if (glass) {
                    q.face(x0, y0, z0, x1, y1, z1, d);
                }
            }
        }
    }

    /**
     * ガラスの区画 i の外側に同じ水（ガラスの階段でない）があれば、その境に水の面をガラスの側へ向けて描く。
     * 隣の水は、水に浸かったこの階段との境を同じ水どうしとして描かないので、描かないとガラス越しに水が途切れて見える。
     */
    private static void outsideWater(BlockAndTintGetter level, BlockPos pos, Quad q, FluidState fluid, int i) {
        int ix = i & 1, iy = i >> 1 & 1, iz = i >> 2 & 1;
        float x0 = ix * 0.5F, y0 = iy * 0.5F, z0 = iz * 0.5F, x1 = x0 + 0.5F, y1 = y0 + 0.5F, z1 = z0 + 0.5F;
        for (Direction d : Direction.values()) {
            int nx = ix + d.getStepX(), ny = iy + d.getStepY(), nz = iz + d.getStepZ();
            if (nx >= 0 && nx < 2 && ny >= 0 && ny < 2 && nz >= 0 && nz < 2) {
                continue;
            }
            BlockPos npos = pos.relative(d);
            BlockState ns = level.getBlockState(npos);
            FluidState nf = ns.getFluidState();
            if (ns.getBlock() instanceof GlassJoin.Shaped || nf.isEmpty() || !nf.getType().isSame(fluid.getType())) {
                continue;
            }
            float top = y1;
            if (d.getAxis() != Direction.Axis.Y) {
                top = Math.min(y1, nf.getHeight(level, npos));
                if (top <= y0) {
                    continue;
                }
            }
            // 境の面（ブロックの外側の面の位置）を、ガラスの区画の中へ向ける
            switch (d) {
                case UP -> q.face(x0, 1, z0, x1, 1, z1, Direction.DOWN);
                case DOWN -> q.face(x0, 0, z0, x1, 0, z1, Direction.UP);
                case NORTH -> q.face(x0, y0, 0, x1, top, 0, Direction.SOUTH);
                case SOUTH -> q.face(x0, y0, 1, x1, top, 1, Direction.NORTH);
                case WEST -> q.face(0, y0, z0, 0, top, z1, Direction.EAST);
                case EAST -> q.face(1, y0, z0, 1, top, z1, Direction.WEST);
            }
        }
    }

    /** 隣が同じ水に浸かったガラスの階段で、向かいの区画がガラスか（バニラは同じ水どうしの境を描かないので、こちらが描く）。 */
    private static boolean glassAcross(BlockAndTintGetter level, BlockPos pos, FluidState fluid, Direction d, int ix, int iy, int iz) {
        if (d == Direction.UP) {
            return false;
        }
        BlockState ns = level.getBlockState(pos.relative(d));
        FluidState nf = ns.getFluidState();
        if (!(ns.getBlock() instanceof GlassJoin.Shaped) || nf.isEmpty() || !nf.getType().isSame(fluid.getType())) {
            return false;
        }
        int jx = d.getAxis() == Direction.Axis.X ? 1 - ix : ix;
        int jy = d.getAxis() == Direction.Axis.Y ? 1 - iy : iy;
        int jz = d.getAxis() == Direction.Axis.Z ? 1 - iz : iz;
        return (GlassJoin.filledOctants(ns) >> (jx + 2 * jy + 4 * jz) & 1) != 0;
    }

    /** 箱の1つの面を、法線の側から見て反時計回りに4頂点で描く。 */
    private record Quad(VertexConsumer buffer, float ox, float oy, float oz, TextureAtlasSprite sprite, float r, float g, float b,
                        int light, BlockAndTintGetter level) {
        void face(float x0, float y0, float z0, float x1, float y1, float z1, Direction n) {
            switch (n) {
                case UP -> {
                    v(x0, y1, z0, n); v(x0, y1, z1, n); v(x1, y1, z1, n); v(x1, y1, z0, n);
                }
                case DOWN -> {
                    v(x0, y0, z0, n); v(x1, y0, z0, n); v(x1, y0, z1, n); v(x0, y0, z1, n);
                }
                case NORTH -> {
                    v(x0, y0, z0, n); v(x0, y1, z0, n); v(x1, y1, z0, n); v(x1, y0, z0, n);
                }
                case SOUTH -> {
                    v(x0, y0, z1, n); v(x1, y0, z1, n); v(x1, y1, z1, n); v(x0, y1, z1, n);
                }
                case WEST -> {
                    v(x0, y0, z0, n); v(x0, y0, z1, n); v(x0, y1, z1, n); v(x0, y1, z0, n);
                }
                case EAST -> {
                    v(x1, y0, z0, n); v(x1, y1, z0, n); v(x1, y1, z1, n); v(x1, y0, z1, n);
                }
            }
        }

        private void v(float x, float y, float z, Direction n) {
            float u = n.getAxis() == Direction.Axis.X ? z : x;
            float w = n.getAxis() == Direction.Axis.Y ? z : 1 - y;
            var lighting = level.cardinalLighting();
            float shade = switch (n) {
                case UP -> lighting.up(); case DOWN -> lighting.down();
                case NORTH, SOUTH -> lighting.north(); case EAST, WEST -> lighting.west();
            };
            buffer.addVertex(ox + x, oy + y, oz + z).setColor(r * shade, g * shade, b * shade, 1F)
                    .setUv(sprite.getU(u), sprite.getV(w)).setLight(light).setNormal(n.getStepX(), n.getStepY(), n.getStepZ());
        }
    }
}
