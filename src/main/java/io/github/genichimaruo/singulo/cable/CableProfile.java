package io.github.genichimaruo.singulo.cable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ケーブルの断面。中心に置いた正方形（一辺は偶数のドット数、8まで）。段階の違いはテクスチャの帯で描き分ける。
 * 見た目（tools/art16.py の CABLE_WIDTHS）と同じ値にしておくこと。当たり判定も同じ形で作る。
 */
public enum CableProfile {
    COPPER(new double[][]{{7, 7, 9, 9}}),
    SUPERCONDUCTING(new double[][]{{6, 6, 10, 10}}),
    TOPOLOGICAL(new double[][]{{5, 5, 11, 11}}),
    HORIZON(new double[][]{{4, 4, 12, 12}});

    /** 腕がまっすぐ1本だけ通っているときの向き（中心の形を変えるのに使う）。 */
    public enum Straight implements StringRepresentable {
        NONE, X, Y, Z;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    private final double[][] rects;
    /** 断面全体を囲む範囲（中心の箱と腕の境目）。 */
    final double lo;
    final double hi;
    private final VoxelShape[] arms = new VoxelShape[6];
    private final VoxelShape[] cores = new VoxelShape[4];

    CableProfile(double[][] rects) {
        this.rects = rects;
        double l = 16;
        double h = 0;
        for (double[] r : rects) {
            l = Math.min(l, Math.min(r[0], r[1]));
            h = Math.max(h, Math.max(r[2], r[3]));
        }
        this.lo = l;
        this.hi = h;
        for (Direction d : Direction.values()) {
            arms[d.ordinal()] = extrude(d.getAxis(), d.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : hi,
                    d.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? lo : 16);
        }
        cores[Straight.NONE.ordinal()] = Block.box(lo, lo, lo, hi, hi, hi);
        cores[Straight.X.ordinal()] = extrude(Direction.Axis.X, lo, hi);
        cores[Straight.Y.ordinal()] = extrude(Direction.Axis.Y, lo, hi);
        cores[Straight.Z.ordinal()] = extrude(Direction.Axis.Z, lo, hi);
    }

    /** 断面を axis 方向に from〜to まで伸ばした形。断面の (u, v) は X 軸なら (z, y)、Y 軸なら (x, z)、Z 軸なら (x, y)。 */
    private VoxelShape extrude(Direction.Axis axis, double from, double to) {
        List<VoxelShape> parts = new ArrayList<>();
        for (double[] r : rects) {
            parts.add(switch (axis) {
                case X -> Block.box(from, r[1], r[0], to, r[3], r[2]);
                case Y -> Block.box(r[0], from, r[1], r[2], to, r[3]);
                case Z -> Block.box(r[0], r[1], from, r[2], r[3], to);
            });
        }
        VoxelShape s = Shapes.empty();
        for (VoxelShape p : parts) {
            s = Shapes.or(s, p);
        }
        return s;
    }

    public VoxelShape arm(Direction d) {
        return arms[d.ordinal()];
    }

    public VoxelShape core(Straight straight) {
        return cores[straight.ordinal()];
    }
}
