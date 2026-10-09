package io.github.genichimaruo.singulo.multiblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 粒子加速器とペンローズ・リアクターの形の判定（ほかの決まった形は Shapes）。コントローラの位置から探す。 */
public final class Structures {
    public static final int RING_MIN_SIDE = 8;
    public static final int RING_MAX_SIDE = 32;

    private Structures() {}

    /**
     * 粒子加速器: 加速管と収束磁石で作る、水平な正方形のリング（一辺8〜32）。
     * 曲がり角の4つは収束磁石で、収束磁石はリング全体の1/4以上。コントローラはリングに接して置く。
     *
     * @return リングの一辺。形成できなければ 0
     */
    public static int findRing(Level level, BlockPos controller) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos start = controller.relative(dir);
            if (isRingBlock(level.getBlockState(start))) {
                int side = ringFrom(level, start);
                if (side > 0) {
                    return side;
                }
            }
        }
        return 0;
    }

    /** コントローラからリングへの向き（形成できていなければ null）。搬入出ポートはコントローラの左右に置く。 */
    @javax.annotation.Nullable
    public static Direction ringDirection(Level level, BlockPos controller) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos start = controller.relative(dir);
            if (isRingBlock(level.getBlockState(start)) && ringFrom(level, start) > 0) {
                return dir;
            }
        }
        return null;
    }

    /** 加速器のコントローラの左右にある搬入出ポート。 */
    public static List<BlockPos> acceleratorPorts(Level level, BlockPos controller, Direction toRing) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction side : new Direction[]{toRing.getClockWise(), toRing.getCounterClockWise()}) {
            BlockPos p = controller.relative(side);
            if (role(level.getBlockState(p)) == MultiblockPart.Role.MULTIBLOCK_PORT) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    static int ringFrom(Level level, BlockPos start) {
        int limit = 4 * (RING_MAX_SIDE - 1);
        Set<BlockPos> ring = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ring.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                if (!level.hasChunkAt(n)) {
                    return 0;
                }
                if (!ring.contains(n) && isRingBlock(level.getBlockState(n))) {
                    if (ring.size() >= limit) {
                        return 0;
                    }
                    ring.add(n);
                    queue.add(n);
                }
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : ring) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int side = maxX - minX + 1;
        if (side != maxZ - minZ + 1 || side < RING_MIN_SIDE || side > RING_MAX_SIDE || ring.size() != 4 * (side - 1)) {
            return 0;
        }
        int magnets = 0;
        for (BlockPos p : ring) {
            boolean edgeX = p.getX() == minX || p.getX() == maxX;
            boolean edgeZ = p.getZ() == minZ || p.getZ() == maxZ;
            if (!edgeX && !edgeZ) {
                return 0;
            }
            boolean magnet = role(level.getBlockState(p)) == MultiblockPart.Role.FOCUSING_MAGNET;
            if (edgeX && edgeZ && !magnet) {
                return 0;
            }
            if (magnet) {
                magnets++;
            }
        }
        return magnets >= side - 1 ? side : 0;
    }

    private static boolean isRingBlock(BlockState s) {
        MultiblockPart.Role r = role(s);
        return r == MultiblockPart.Role.ACCELERATOR_TUBE || r == MultiblockPart.Role.FOCUSING_MAGNET;
    }

    // ------------------------------------------------------------------ 段階5: ペンローズ・リアクター

    public static final int REACTOR_RADIUS = 6;
    /** 炉心制御装置から炉心の中心までの高さ（炉心の真下、下の軸のジャイロ駆動部の上）。 */
    public static final int CONTROLLER_BELOW_CENTER = 5;

    /**
     * 半径6の円を、隙間なく（辺でつながるように）並べた48点。軸上の4点（0°・90°・180°・270°）と、
     * 斜め45°の4点（±4, ±4）を含む。
     */
    static final int[][] RING_POINTS;

    static {
        List<int[]> pts = new ArrayList<>();
        for (int k = 0; k < 3600; k++) {
            double a = 2 * Math.PI * k / 3600;
            int u = (int) Math.round(REACTOR_RADIUS * Math.cos(a));
            int v = (int) Math.round(REACTOR_RADIUS * Math.sin(a));
            if (pts.isEmpty() || pts.get(pts.size() - 1)[0] != u || pts.get(pts.size() - 1)[1] != v) {
                pts.add(new int[]{u, v});
            }
        }
        if (pts.get(0)[0] == pts.get(pts.size() - 1)[0] && pts.get(0)[1] == pts.get(pts.size() - 1)[1]) {
            pts.remove(pts.size() - 1);
        }
        // 斜めにしかつながっていない所に、円に近い方の1点を足して辺でつなぐ
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            int[] q = pts.get((i + 1) % pts.size());
            out.add(p);
            if (Math.abs(p[0] - q[0]) == 1 && Math.abs(p[1] - q[1]) == 1) {
                int[] c1 = {q[0], p[1]};
                int[] c2 = {p[0], q[1]};
                double e1 = Math.abs(Math.hypot(c1[0], c1[1]) - REACTOR_RADIUS);
                double e2 = Math.abs(Math.hypot(c2[0], c2[1]) - REACTOR_RADIUS);
                out.add(e1 < e2 ? c1 : c2);
            }
        }
        RING_POINTS = out.toArray(new int[0][]);
    }

    /**
     * リングの点の役割（設計図の配置）: 軸上はジャイロ駆動部、斜め45°は炉心安定化コイル、ほかは炉殻。
     * 抽出ポートと炉心質量警報器は、炉殻の位置ならどこに置いてもよい。
     */
    static MultiblockPart.Role ringRole(int u, int v) {
        if (u == 0 || v == 0) {
            return MultiblockPart.Role.GYRO_DRIVE;
        }
        return Math.abs(u) == Math.abs(v) ? MultiblockPart.Role.REACTOR_STABILIZER : MultiblockPart.Role.REACTOR_SHELL;
    }

    /** 形成できたリアクター。center は炉心の中心、ports は抽出ポート、alarms は炉心質量警報器。 */
    public record Reactor(BlockPos center, List<BlockPos> ports, List<BlockPos> alarms) {}

    /**
     * Pリアクター: 13×13×13 のジャイロスコープ型。炉心の中心を通る直交3平面（XY・YZ・XZ）に、半径6の円環を
     * 隙間なくつなげて作る（各48ブロック）。3本が交わる軸の6点はジャイロ駆動部、各円環の斜め45°の点（計12）は抽出ポート、
     * ほかは炉殻ブロック。中心の 3×3×3 は真空（空気）の炉心。炉心制御装置は炉心の真下（中心から5ブロック下）に置く。
     */
    @Nullable
    public static Reactor findReactor(Level level, BlockPos controller) {
        BlockPos c = controller.above(CONTROLLER_BELOW_CENTER);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            // 炉心の中心だけは、野良ブラックホールがあってもよい（組み直して取り込むため）
            if (!s.isAir() && !(p.equals(c) && s.is(io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get()))) {
                return null;
            }
        }
        java.util.Map<BlockPos, MultiblockPart.Role> layout = reactorLayout(c);
        List<BlockPos> ports = new ArrayList<>();
        List<BlockPos> alarms = new ArrayList<>();
        for (java.util.Map.Entry<BlockPos, MultiblockPart.Role> e : layout.entrySet()) {
            MultiblockPart.Role have = role(level.getBlockState(e.getKey()));
            boolean shell = e.getValue() == MultiblockPart.Role.REACTOR_SHELL;
            boolean ok = shell ? have == MultiblockPart.Role.REACTOR_SHELL || have == MultiblockPart.Role.EXTRACTION_PORT
                    || have == MultiblockPart.Role.MASS_ALARM : have == e.getValue();
            if (!ok) {
                return null;
            }
            if (have == MultiblockPart.Role.EXTRACTION_PORT) {
                ports.add(e.getKey());
            } else if (have == MultiblockPart.Role.MASS_ALARM) {
                alarms.add(e.getKey());
            }
        }
        return new Reactor(c.immutable(), ports, alarms);
    }

    /** リアクターの部品（押し出し先から外す）。 */
    public static boolean isReactorPart(BlockState state) {
        MultiblockPart.Role r = role(state);
        return r == MultiblockPart.Role.REACTOR_SHELL || r == MultiblockPart.Role.GYRO_DRIVE
                || r == MultiblockPart.Role.EXTRACTION_PORT || r == MultiblockPart.Role.REACTOR_STABILIZER
                || r == MultiblockPart.Role.MASS_ALARM;
    }

    /** 炉心の中心 c に対する部品の位置と役割（形の判定・ホロ設計図・テストが使う）。 */
    public static java.util.Map<BlockPos, MultiblockPart.Role> reactorLayout(BlockPos c) {
        java.util.Map<BlockPos, MultiblockPart.Role> out = new java.util.LinkedHashMap<>();
        for (int[] pt : RING_POINTS) {
            MultiblockPart.Role r = ringRole(pt[0], pt[1]);
            out.put(c.offset(pt[0], pt[1], 0), r);
            out.put(c.offset(0, pt[0], pt[1]), r);
            out.put(c.offset(pt[0], 0, pt[1]), r);
        }
        return out;
    }

    @Nullable
    static MultiblockPart.Role role(BlockState state) {
        return state.getBlock() instanceof MultiblockPart part ? part.role() : null;
    }
}
