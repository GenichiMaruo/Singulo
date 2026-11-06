package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.multiblock.ShapeSpec;
import io.github.genichimaruo.singulo.multiblock.ShapeSpec.Slot;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;

/**
 * 宇宙現象を封じ込める3つの設備の形。どれも形を見れば働きが分かるように作る。制御卓はいちばん下の層の手前の中央で、
 * その左右は搬入出ポートの決まった位置（入力・出力・両方を画面で切り替える）。
 * <ul>
 *   <li>重力懸垂タンク（7×9×7）: 八角の土台から細い4本の支柱が立ち、八角の冠を持ち上げる。液体は冠から涙の形にぶら下がる</li>
 *   <li>核パスタ積層蓄電庫（7×9×7）: 上下の積層板と四隅の導体でできた圧縮機。中の空間で核パスタが形を変える</li>
 *   <li>潮汐紡糸機（7×8×15）: 手前からブラックホールを囲む輪、細くなる2つの絞りリング、奥のスプール受けが1本の床に並ぶ。
 *       軸は高さ 4・左右の中央を通る</li>
 * </ul>
 */
public final class CosmicLayouts {
    private CosmicLayouts() {}

    public static final ShapeSpec RESERVOIR = new ShapeSpec(7, 9, 7, 3, 0, 0, CosmicLayouts::reservoir);
    public static final ShapeSpec ACCUMULATOR = new ShapeSpec(7, 9, 7, 3, 0, 0, CosmicLayouts::accumulator);
    public static final ShapeSpec SPINDLE = new ShapeSpec(7, 8, 15, 3, 0, 0, CosmicLayouts::spindle);

    /** 制御卓と、その左右の決まった位置の搬入出ポート。どの設備も同じ。 */
    private static Slot controller(int x, int y, int z) {
        if (y != 0 || z != 0) {
            return null;
        }
        return x == 3 ? Slot.CONTROLLER : x == 2 || x == 4 ? Slot.part(SinguloBlocks.MULTIBLOCK_PORT) : null;
    }

    private static Slot frame() {
        return Slot.part(SinguloBlocks.COSMIC_FRAME);
    }

    // ------------------------------------------------------------------ 重力懸垂タンク

    private static Slot reservoir(int x, int y, int z) {
        Slot front = controller(x, y, z);
        if (front != null) {
            return front;
        }
        int dx = Math.abs(x - 3);
        int dz = Math.abs(z - 3);
        int reach = Math.max(dx, dz);
        boolean octagon = reach <= 3 && dx + dz <= 4;
        boolean rim = octagon && (reach == 3 || dx + dz == 4);
        return switch (y) {
            case 0 -> rim ? frame() : x == 3 && z == 3 ? Slot.part(SinguloBlocks.SUSPENSION_CROWN) : Slot.IGNORE;
            case 7 -> rim ? Slot.part(SinguloBlocks.SUSPENSION_CROWN) : octagon ? Slot.AIR : Slot.IGNORE;
            case 8 -> reach <= 2 && dx + dz <= 3 ? Slot.part(SinguloBlocks.SUSPENSION_CROWN) : Slot.IGNORE;
            default -> dx == 2 && dz == 2 ? Slot.part(SinguloBlocks.SUSPENSION_STRUT)
                    : octagon && !rim ? Slot.AIR : Slot.IGNORE;
        };
    }

    // ------------------------------------------------------------------ 核パスタ積層蓄電庫

    private static Slot accumulator(int x, int y, int z) {
        Slot front = controller(x, y, z);
        if (front != null) {
            return front;
        }
        int dx = Math.abs(x - 3);
        int dz = Math.abs(z - 3);
        int reach = Math.max(dx, dz);
        boolean corner = dx == 3 && dz == 3;
        if (y == 0 || y == 8) {
            return reach == 3 ? frame() : Slot.part(SinguloBlocks.PASTA_LAMINATE);
        }
        if (corner) {
            return Slot.part(SinguloBlocks.PASTA_CONDUCTOR);
        }
        if (reach <= 2) {
            return Slot.AIR;
        }
        return y == 1 || y == 7 ? frame() : Slot.IGNORE;
    }

    // ------------------------------------------------------------------ 潮汐紡糸機

    /** 軸（x = 3, y = 4）のまわりの半径 r の輪か。 */
    private static boolean ring(int u, int v, int r) {
        return Math.abs(Math.hypot(u, v) - r) < 0.5;
    }

    /** 半径 r の輪の内側か（空気でなければならない所）。 */
    private static boolean inside(int u, int v, int r) {
        return Math.hypot(u, v) < r - 0.5;
    }

    private static Slot drawRing() {
        return Slot.part(SinguloBlocks.TIDAL_DRAW_RING);
    }

    private static Slot spindle(int x, int y, int z) {
        Slot front = controller(x, y, z);
        if (front != null) {
            return front;
        }
        int u = x - 3;
        int v = y - 4;
        if (y == 0) {
            boolean rail = x == 1 || x == 5;
            boolean tie = x >= 2 && x <= 4 && (z == 3 || z == 7 || z == 9 || z == 12 || z == 14);
            return rail || tie ? frame() : Slot.IGNORE;
        }
        return switch (z) {
            // ブラックホールを囲む輪。前後は空けて、横からも円盤が見えるようにする
            case 3 -> ring(u, v, 3) ? drawRing() : inside(u, v, 3) ? Slot.AIR : Slot.IGNORE;
            case 2, 4 -> inside(u, v, 3) ? Slot.AIR : Slot.IGNORE;
            case 7 -> ring(u, v, 2) ? drawRing() : inside(u, v, 2) ? Slot.AIR : x == 3 && y == 1 ? frame() : Slot.IGNORE;
            case 9 -> ring(u, v, 1) ? drawRing() : u == 0 && v == 0 ? Slot.AIR : x == 3 && y <= 2 ? frame() : Slot.IGNORE;
            case 12 -> (x == 1 || x == 5) && y <= 4 ? Slot.part(SinguloBlocks.SPINDLE_CRADLE) : spoolRoom(x, y);
            case 11, 13 -> spoolRoom(x, y);
            // 糸の通り道
            case 5, 6, 8, 10 -> u == 0 && v == 0 ? Slot.AIR : Slot.IGNORE;
            default -> Slot.IGNORE;
        };
    }

    /** スプールが回る所。 */
    private static Slot spoolRoom(int x, int y) {
        return x >= 2 && x <= 4 && y <= 5 ? Slot.AIR : Slot.IGNORE;
    }

    public static ShapeSpec of(int kind) {
        return kind == 0 ? RESERVOIR : kind == 1 ? ACCUMULATOR : SPINDLE;
    }
}
