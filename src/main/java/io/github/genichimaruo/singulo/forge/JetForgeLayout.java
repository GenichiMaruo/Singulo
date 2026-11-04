package io.github.genichimaruo.singulo.forge;

import io.github.genichimaruo.singulo.multiblock.ShapeSpec;
import io.github.genichimaruo.singulo.multiblock.ShapeSpec.Slot;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 降着ジェット鍛造炉の形（44×16×17 の箱で、部品があるのは奥行き1〜15。tools/jet_forge.py と同じ定義）。x はジェットの軸（0 が排熱側、増えるほど加工側）、
 * y は高さ、z は奥行き（0 が手前）。軸は y = 8・z = 8 を通る。
 * <ul>
 *   <li>土台（y = 0）: 軸の下を通る幅3の基礎と、炉心の下の角を落とした台座（9×11）。制御核は台座の手前の縁の中央に埋め込む。
 *       基礎はマルチブロック搬入出ポートと入れ替えられる</li>
 *   <li>排熱側（x = 0〜2）: 奥の吸熱板と、反対側のジェットを受ける杯の壁。下に3×3の骨組みの脚</li>
 *   <li>炉心（x = 11〜17）: 半径7の拘束コイル2つと、間を結ぶ4本の梁。コイルの斜め45°の点（計8）は炉心安定化コイル
 *       （Pリアクターと同じ部品。点火のとき、ここから種へ光が流れ込む）。中の球（ブラックホールと縦向きの降着円盤）は空気。
 *       ブラックホールの中心だけは、制御核を壊して残った野良ブラックホールがあってもよい（組み直すと取り込む）</li>
 *   <li>収束リング（x = 22・27・32）: 半径5・4・3とだんだん細くなる輪。下に支柱</li>
 *   <li>照射室（x = 35〜41）: 両端の四角い枠と四隅の桁。中央の上に浮揚器、下に回収口。素材は中央（x = 38）に浮かぶ</li>
 *   <li>後ろのビーム止め（x = 43）と、軸に沿ったビームの通り道（空気）</li>
 * </ul>
 */
public final class JetForgeLayout {
    public static final int W = 44, H = 16, D = 17;
    public static final int CX = 14, CY = 0, CZ = 3;
    /** 炉心の下の台座（x と z の範囲。四隅は1マス落とす）。 */
    public static final int PLINTH_X0 = 10, PLINTH_X1 = 18, PLINTH_Z0 = 3, PLINTH_Z1 = 13;
    public static final int AXIS_Y = 8, AXIS_Z = 8;
    public static final int CORE_X = 14;
    public static final int[] COIL_X = {11, 17};
    public static final int COIL_R = 7;
    public static final int[] LENS_X = {22, 27, 32};
    public static final int[] LENS_R = {5, 4, 3};
    public static final int CAGE_X0 = 35, CAGE_X1 = 41, TARGET_X = 38, CAGE_HALF = 3;
    public static final int STOP_X = 43;
    public static final int SINK_X1 = 2, SINK_HALF = 4;

    private static final Set<Long> COIL_RING = ring(COIL_R);
    private static final List<Set<Long>> LENS_RINGS = List.of(ring(LENS_R[0]), ring(LENS_R[1]), ring(LENS_R[2]));

    public static final ShapeSpec SPEC = new ShapeSpec(W, H, D, CX, CY, CZ, JetForgeLayout::slot);

    private JetForgeLayout() {}

    private static long key(int u, int v) {
        return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
    }

    /** 半径 r の円を、辺でつながるように並べた点（Pリアクターのリングと同じ作り方）。 */
    static Set<Long> ring(int r) {
        List<int[]> pts = new ArrayList<>();
        for (int k = 0; k < 3600; k++) {
            double a = 2 * Math.PI * k / 3600;
            int u = (int) Math.round(r * Math.cos(a));
            int v = (int) Math.round(r * Math.sin(a));
            if (pts.isEmpty() || pts.get(pts.size() - 1)[0] != u || pts.get(pts.size() - 1)[1] != v) {
                pts.add(new int[]{u, v});
            }
        }
        if (pts.get(0)[0] == pts.get(pts.size() - 1)[0] && pts.get(0)[1] == pts.get(pts.size() - 1)[1]) {
            pts.remove(pts.size() - 1);
        }
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            int[] q = pts.get((i + 1) % pts.size());
            out.add(key(p[0], p[1]));
            if (Math.abs(p[0] - q[0]) == 1 && Math.abs(p[1] - q[1]) == 1) {
                double e1 = Math.abs(Math.hypot(q[0], p[1]) - r);
                double e2 = Math.abs(Math.hypot(p[0], q[1]) - r);
                out.add(e1 < e2 ? key(q[0], p[1]) : key(p[0], q[1]));
            }
        }
        return out;
    }

    static Slot slot(int x, int y, int z) {
        int dy = y - AXIS_Y, dz = z - AXIS_Z;
        int ady = Math.abs(dy), adz = Math.abs(dz);
        int m = Math.max(ady, adz);
        int rr = dy * dy + dz * dz;
        if (x == CX && y == CY && z == CZ) {
            return Slot.CONTROLLER;
        }
        if (y == 0) {
            boolean spine = z >= 7 && z <= 9;
            boolean plinth = x >= PLINTH_X0 && x <= PLINTH_X1 && z >= PLINTH_Z0 && z <= PLINTH_Z1
                    && !((x == PLINTH_X0 || x == PLINTH_X1) && (z == PLINTH_Z0 || z == PLINTH_Z1));
            return spine || plinth ? Slot.panel(SinguloBlocks.JET_FORGE_FOUNDATION) : Slot.IGNORE;
        }
        // ブラックホールの中心（空気か野良ブラックホール。制御核が確かめる）
        if (x == CORE_X && y == AXIS_Y && z == AXIS_Z) {
            return Slot.IGNORE;
        }
        // 排熱側: 奥の吸熱板と、反対側のジェットを受ける杯の壁
        if (x <= SINK_X1) {
            if (m > SINK_HALF || ady == SINK_HALF && adz == SINK_HALF) {
                // 杯を支える3×3の脚
                return adz <= 1 && dy < -SINK_HALF ? Slot.part(SinguloBlocks.JET_FORGE_TRUSS) : Slot.IGNORE;
            }
            return x == 0 || m == SINK_HALF ? Slot.part(SinguloBlocks.JET_FORGE_HEAT_SINK) : Slot.AIR;
        }
        // 炉心: 2つの拘束コイルと、その間を結ぶ4本の梁
        if ((x == COIL_X[0] || x == COIL_X[1]) && COIL_RING.contains(key(dy, dz))) {
            return Slot.part(ady == adz ? SinguloBlocks.REACTOR_STABILIZER : SinguloBlocks.JET_FORGE_CONFINEMENT_COIL);
        }
        if (x > COIL_X[0] && x < COIL_X[1] && (dz == 0 && ady == COIL_R || dy == 0 && adz == COIL_R)) {
            return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
        }
        if (x >= COIL_X[0] && x <= COIL_X[1] && rr <= 25 || (x - CORE_X) * (x - CORE_X) + rr <= 30) {
            return Slot.AIR;
        }
        // 収束リング（だんだん細く）と、それを支える柱
        for (int i = 0; i < LENS_X.length; i++) {
            if (x == LENS_X[i]) {
                if (LENS_RINGS.get(i).contains(key(dy, dz))) {
                    return Slot.part(SinguloBlocks.JET_FORGE_COLLIMATOR);
                }
                if (dz == 0 && dy < -LENS_R[i]) {
                    return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
                }
            }
        }
        // 照射室: 両端の四角い枠・四隅の桁・上の浮揚器と下の回収口
        if (x >= CAGE_X0 && x <= CAGE_X1) {
            if (x == CAGE_X0 || x == CAGE_X1) {
                if (m == CAGE_HALF) {
                    return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
                }
                if (m < CAGE_HALF) {
                    return Slot.AIR;
                }
                return dz == 0 && dy < -CAGE_HALF ? Slot.part(SinguloBlocks.JET_FORGE_TRUSS) : Slot.IGNORE;
            }
            if (ady == CAGE_HALF && adz == CAGE_HALF) {
                return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
            }
            if (x == TARGET_X && ady == CAGE_HALF && adz < CAGE_HALF) {
                if (dz == 0) {
                    return Slot.part(dy > 0 ? SinguloBlocks.JET_FORGE_LEVITATOR : SinguloBlocks.JET_FORGE_COLLECTOR);
                }
                return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
            }
            if (x == TARGET_X && dz == 0 && dy < -CAGE_HALF) {
                return Slot.part(SinguloBlocks.JET_FORGE_TRUSS);
            }
            return m < CAGE_HALF ? Slot.AIR : Slot.IGNORE;
        }
        // 後ろのビーム止め
        if (x == STOP_X) {
            if (ady <= 2 && adz <= 2) {
                return Slot.part(SinguloBlocks.JET_FORGE_HEAT_SINK);
            }
            return adz <= 1 && dy < -2 ? Slot.part(SinguloBlocks.JET_FORGE_TRUSS) : Slot.IGNORE;
        }
        // ビームの通り道
        if (x >= 3 && x <= 42 && ady <= 1 && adz <= 1) {
            return Slot.AIR;
        }
        return Slot.IGNORE;
    }

    // ------------------------------------------------------------------ ワールドの位置（描画・演出にも使う）

    /** 形の座標 (x, y, z) のブロックの中心。back は奥の向き。 */
    public static Vec3 at(BlockPos controller, Direction back, double x, double y, double z) {
        Direction right = back.getClockWise();
        return Vec3.atCenterOf(controller)
                .add(right.getStepX() * (x - CX) + back.getStepX() * (z - CZ), y - CY, right.getStepZ() * (x - CX) + back.getStepZ() * (z - CZ));
    }

    /** 炉心安定化コイルの位置（形の座標 x, dy, dz。2つのコイルの斜め45°）。 */
    public static int[][] stabilizers() {
        int d = (int) Math.round(COIL_R * Math.sqrt(0.5));
        int[][] out = new int[8][];
        int i = 0;
        for (int cx : COIL_X) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    out[i++] = new int[]{cx, sy * d, sz * d};
                }
            }
        }
        return out;
    }

    /** ジェットの軸の向き（x が増える向き。加工側）。 */
    public static Vec3 axis(Direction back) {
        Direction right = back.getClockWise();
        return new Vec3(right.getStepX(), 0, right.getStepZ());
    }

    /** ブラックホールの中心。 */
    public static Vec3 core(BlockPos controller, Direction back) {
        return at(controller, back, CORE_X, AXIS_Y, AXIS_Z);
    }

    /** 素材が浮かぶ所（照射室の中央）。 */
    public static Vec3 target(BlockPos controller, Direction back) {
        return at(controller, back, TARGET_X, AXIS_Y, AXIS_Z);
    }

    /** 回収口の上の面の中心（冷えたインゴットが降りる所）。 */
    public static Vec3 collector(BlockPos controller, Direction back) {
        return at(controller, back, TARGET_X, AXIS_Y - CAGE_HALF + 0.5, AXIS_Z);
    }
}
