package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.cable.CableProfile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 液体パイプの断面（段階ごと）。電線（四角い帯の被覆）と見分けられるよう、パイプは管の太さ {@code tube} に加えて、
 * つないだ面ごとに一回り大きい継ぎ手のつば（{@code flange}、厚さ1ドット）を持つ。隣のパイプのつばと合わさって2ドットの継ぎ目になる。
 * <p>
 * 見た目（tools/pipes.py の PIPES）と同じ値にしておくこと。当たり判定も同じ形で作る。
 */
public enum PipeProfile {
    /** 液体パイプ: 銅の管に、ガラスの窓。 */
    COPPER(6, 8),
    /** 真空断熱パイプ: 白い断熱の外筒と霜の青の継ぎ手。 */
    VACUUM(8, 10),
    /** 超流動導管: 透明な管を金の輪が締める。 */
    SUPERFLUID(8, 10),
    /** ホライズン・ダクト: 黒い太管、金のつばと事象の地平線の光の輪。 */
    HORIZON(10, 12);

    public final int tube;
    public final int flange;
    private final VoxelShape[] arms = new VoxelShape[6];
    private final VoxelShape[] cores = new VoxelShape[4];

    PipeProfile(int tube, int flange) {
        this.tube = tube;
        this.flange = flange;
        double lo = 8 - tube / 2.0, hi = 8 + tube / 2.0;
        double flo = 8 - flange / 2.0, fhi = 8 + flange / 2.0;
        for (Direction d : Direction.values()) {
            boolean neg = d.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
            VoxelShape pipe = box(d.getAxis(), neg ? 0 : hi, neg ? lo : 16, lo, hi);
            VoxelShape rim = box(d.getAxis(), neg ? 0 : 15, neg ? 1 : 16, flo, fhi);
            arms[d.ordinal()] = Shapes.or(pipe, rim);
        }
        // 分かれ目は、管より1ドットずつ大きい継ぎ手の箱
        cores[CableProfile.Straight.NONE.ordinal()] = Block.box(lo - 1, lo - 1, lo - 1, hi + 1, hi + 1, hi + 1);
        cores[CableProfile.Straight.X.ordinal()] = box(Direction.Axis.X, lo, hi, lo, hi);
        cores[CableProfile.Straight.Y.ordinal()] = box(Direction.Axis.Y, lo, hi, lo, hi);
        cores[CableProfile.Straight.Z.ordinal()] = box(Direction.Axis.Z, lo, hi, lo, hi);
    }

    /** 断面 lo〜hi の正方形を axis 方向に from〜to まで伸ばした箱。 */
    private static VoxelShape box(Direction.Axis axis, double from, double to, double lo, double hi) {
        return switch (axis) {
            case X -> Block.box(from, lo, lo, to, hi, hi);
            case Y -> Block.box(lo, from, lo, hi, to, hi);
            case Z -> Block.box(lo, lo, from, hi, hi, to);
        };
    }

    public VoxelShape arm(Direction d) {
        return arms[d.ordinal()];
    }

    public VoxelShape core(CableProfile.Straight straight) {
        return cores[straight.ordinal()];
    }

    /** 管の内側の半分の太さ（ブロック。液体の柱を描く太さ）。 */
    public float innerHalf() {
        return (tube / 2F - 1) / 16F;
    }
}
