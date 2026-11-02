package io.github.genichimaruo.singulo.drone;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 輸送ドローンの通り道。壁や天井を抜けずに飛ぶ。
 * <ul>
 *   <li>近い（{@value #NEAR} ブロック以内）なら、出発点から目的地まで、空いているマスをたどる道を探す（同じ建物の中どうしでも届く）。</li>
 *   <li>遠ければ、出発点から空の見える所（上に何もない列）まで出る道、目的地から空の見える所まで出る道を探し、その間は
 *       地形に沿って、地面から CLEARANCE ブロック上（木の上は葉から LEAF_GAP ブロック上）を巡航する（高い所の手前から上がり始める）。</li>
 * </ul>
 * 道を探すときは、下に {@value #FLOOR_GAP} マスの空きがあるマスを好む（地面すれすれを飛ぶのは、戸口など、そこしか道がないときだけ）。
 * 道はマスの中心を結び、見通しのきく所は飛ばしてなめらかにする。探すのは読み込まれている所だけで、手間に上限がある
 * （見つからなければ null。ドローンは止まって待ち、しばらくして探し直す）。
 * <p>
 * 位相透過アップグレードのドローンは壁を抜けて飛べるので、{@link #straight} で目的地の上へまっすぐ近道する。
 */
final class DroneRoute {
    /** 近い行き先として、出発点から目的地まで直接道を探す距離（水平）。 */
    static final int NEAR = 32;
    /** 道を探す範囲（出発点からの各方向のマス数）と、調べるマスの上限。 */
    static final int SEARCH_RADIUS = 40, MAX_NODES = 16_000;
    /** 巡航の高さ（地面から）と、高い所の手前で上がり始める距離（ブロック）。 */
    static final int CLEARANCE = 3, RISE_AHEAD = 3;
    /** 木の上を巡航するときの、葉からの高さ（葉の上まで CLEARANCE を足すと、森の上で高く飛びすぎる）。 */
    static final int LEAF_GAP = 1;
    /** 道を探すとき、下にこれだけ空きのあるマスを好む（少ないほど1歩ごとに FLOOR_COST ずつ遠回りに数える）。 */
    static final int FLOOR_GAP = 3;
    static final double FLOOR_COST = 0.35;
    /** 道の見通しを調べる箱（ドローンより少しだけ細く低い。1マスの穴を通れるように）。 */
    private static final double HALF_WIDTH = 0.34, HEIGHT = 0.94;

    /** 通る点（最後が目的地）と、その点までがせまい所の道か（ゆっくり正確に飛ぶ）。 */
    final List<Vec3> points;
    final List<Boolean> tight;
    private int next;

    private DroneRoute(List<Vec3> points, List<Boolean> tight) {
        this.points = points;
        this.tight = tight;
    }

    Vec3 current() {
        return points.get(next);
    }

    boolean currentTight() {
        return tight.get(next);
    }

    boolean last() {
        return next == points.size() - 1;
    }

    /**
     * p から、ゆるい点（巡航）が続く所の終わりまでの道のり。巡航の点は数ブロックおきなので、次の点までの距離で速さを絞ると
     * 速い型も速く飛べない。続きの長さで絞り、終わり（せまい所や目的地）の手前で落とす。
     */
    double looseAhead(Vec3 p) {
        double d = p.distanceTo(points.get(next));
        for (int i = next + 1; i < points.size() && !tight.get(i); i++) {
            d += points.get(i - 1).distanceTo(points.get(i));
        }
        return d;
    }

    void advance() {
        if (next < points.size() - 1) {
            next++;
        }
    }

    // ------------------------------------------------------------------ 道を決める

    /** from から goal までの、壁を抜けない道（見つからなければ null）。 */
    @Nullable
    static DroneRoute plan(ServerLevel level, Vec3 from, Vec3 goal) {
        BlockPos start = startCell(level, from);
        BlockPos end = BlockPos.containing(goal.x, goal.y + 0.05, goal.z);
        if (start == null || !free(level, end)) {
            return null;
        }
        List<Vec3> points = new ArrayList<>();
        List<Boolean> tight = new ArrayList<>();
        double hx = goal.x - from.x, hz = goal.z - from.z;
        if (hx * hx + hz * hz <= (double) NEAR * NEAR) {
            List<BlockPos> direct = search(level, start, end);
            if (direct != null) {
                addSmoothed(level, from, direct, points, tight, true);
                points.add(goal);
                tight.add(true);
                return new DroneRoute(points, tight);
            }
        }
        List<BlockPos> exit = sky(level, start) ? List.of(start) : search(level, start, null);
        List<BlockPos> entry = sky(level, end) ? List.of(end) : search(level, end, null);
        if (exit == null || entry == null) {
            return null;
        }
        entry = new ArrayList<>(entry);
        Collections.reverse(entry);                       // 空の見える所から目的地へ
        BlockPos out = exit.get(exit.size() - 1), in = entry.get(0);
        addSmoothed(level, from, exit, points, tight, true);
        Vec3 top = Vec3.atBottomCenterOf(out), down = Vec3.atBottomCenterOf(in);
        for (Vec3 p : terrain(level, top, down)) {
            points.add(p);
            tight.add(false);
        }
        addSmoothed(level, points.get(points.size() - 1), entry, points, tight, true);
        points.add(goal);
        tight.add(true);
        return new DroneRoute(points, tight);
    }

    /** 壁を抜けて飛べるドローンの道: 目的地の少し上へまっすぐ近道し（途中の壁や地面は抜ける）、そこから降りる。 */
    static DroneRoute straight(ServerLevel level, Vec3 from, Vec3 goal) {
        List<Vec3> points = new ArrayList<>();
        List<Boolean> tight = new ArrayList<>();
        Vec3 above = goal.add(0, 1.5, 0);
        if (from.distanceToSqr(above) > 4) {
            points.add(above);
            tight.add(false);
        }
        points.add(goal);
        tight.add(true);
        return new DroneRoute(points, tight);
    }

    /**
     * 地形に沿った巡航の点（from と to はどちらも空の見える所）。1ブロックごとに地面（葉を除く）の高さ＋CLEARANCE と、葉の上＋LEAF_GAP の高いほうを調べ、
     * 前後 RISE_AHEAD ブロックのいちばん高い所に合わせて（高い所の手前から上がり、過ぎてから下がる）、4ブロックごとに点を置く。
     * 点の間は、両端のどちらの高さも、その間の地形より上になる。
     */
    static List<Vec3> terrain(ServerLevel level, Vec3 from, Vec3 to) {
        double len = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        int n = Math.max(1, (int) Math.ceil(len));
        double[] want = new double[n + 1];
        double known = Math.max(from.y, to.y);
        for (int i = 0; i <= n; i++) {
            double k = i / (double) n;
            int x = Mth.floor(Mth.lerp(k, from.x, to.x));
            int z = Mth.floor(Mth.lerp(k, from.z, to.z));
            // 読み込まれていない所は、わかっている高さで（実体はそこへ入ると見えない飛行になる）
            want[i] = level.hasChunk(x >> 4, z >> 4)
                    ? Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + CLEARANCE,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + LEAF_GAP)
                    : known;
        }
        double ceiling = level.getMaxBuildHeight() - 2;
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i <= n; i = i == n ? n + 1 : Math.min(n, i + 4)) {
            double y = 0;
            for (int j = Math.max(0, i - RISE_AHEAD); j <= Math.min(n, i + RISE_AHEAD); j++) {
                y = Math.max(y, want[j]);
            }
            if (i == 0) {
                y = Math.max(y, from.y);
            }
            if (i == n) {
                y = Math.max(y, to.y);
            }
            double k = i / (double) n;
            out.add(new Vec3(Mth.lerp(k, from.x, to.x), Math.min(ceiling, y), Mth.lerp(k, from.z, to.z)));
        }
        return out;
    }

    // ------------------------------------------------------------------ マス

    /** ドローンがいられるマスか（読み込まれていて、ぶつかる形がなく、溶岩でない）。 */
    static boolean free(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p) || p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && !s.getFluidState().is(FluidTags.LAVA);
    }

    /** 空の見える所か（その列でいちばん高い、動きをさえぎるブロックより上）。 */
    static boolean sky(ServerLevel level, BlockPos p) {
        return level.hasChunk(p.getX() >> 4, p.getZ() >> 4) && p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
    }

    /** 出発のマス（ドローンの足もと。少しめり込んでいれば、すぐ上の空いたマス）。 */
    @Nullable
    private static BlockPos startCell(ServerLevel level, Vec3 from) {
        BlockPos p = BlockPos.containing(from.x, from.y + 0.05, from.z);
        for (int i = 0; i < 3; i++, p = p.above()) {
            if (free(level, p)) {
                return p;
            }
        }
        return null;
    }

    /** ドローンが pos（足もと）にいて、ぶつからないか。 */
    private static boolean clearAt(ServerLevel level, Vec3 pos) {
        AABB box = new AABB(pos.x - HALF_WIDTH, pos.y + 0.03, pos.z - HALF_WIDTH, pos.x + HALF_WIDTH, pos.y + HEIGHT, pos.z + HALF_WIDTH);
        return level.noCollision(box);
    }

    /** a から b へまっすぐ飛んで、ぶつからないか。 */
    private static boolean clearLine(ServerLevel level, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b);
        int steps = Math.max(1, (int) Math.ceil(len / 0.2));
        for (int i = 0; i <= steps; i++) {
            if (!clearAt(level, a.lerp(b, i / (double) steps))) {
                return false;
            }
        }
        return true;
    }

    /** マスの並びを、見通しのきく所を飛ばした点の並びにして足す（from は今いる所）。 */
    private static void addSmoothed(ServerLevel level, Vec3 from, List<BlockPos> cells, List<Vec3> points, List<Boolean> tight, boolean isTight) {
        Vec3 anchor = from;
        int i = 0;
        while (i < cells.size()) {
            int j = i;
            while (j + 1 < cells.size() && clearLine(level, anchor, Vec3.atBottomCenterOf(cells.get(j + 1)))) {
                j++;
            }
            Vec3 p = Vec3.atBottomCenterOf(cells.get(j));
            points.add(p);
            tight.add(isTight);
            anchor = p;
            i = j + 1;
        }
    }

    // ------------------------------------------------------------------ 探す

    private static final int[][] STEPS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
    };

    /**
     * start から、target（null なら空の見える所のどれか）までのマスの並び（start を含む。見つからなければ null）。
     * A*（target がないときは上へ向かうほど良いとする）。斜めは、両わきのマスも空いているときだけ。
     */
    @Nullable
    static List<BlockPos> search(ServerLevel level, BlockPos start, @Nullable BlockPos target) {
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        best.defaultReturnValue(Double.MAX_VALUE);
        // マスの番号（long）は double に入れると桁が落ちるので、そのまま持つ
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        long s = start.asLong();
        best.put(s, 0);
        open.add(new Node(heuristic(level, start, target), 0, s));
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        int visited = 0;
        while (!open.isEmpty() && visited < MAX_NODES) {
            Node top = open.poll();
            long key = top.key;
            double g = top.g;
            if (g > best.get(key)) {
                continue;
            }
            visited++;
            BlockPos p = BlockPos.of(key);
            if (target != null ? p.equals(target) : sky(level, p)) {
                List<BlockPos> path = new ArrayList<>();
                for (long k = key; ; k = parent.get(k)) {
                    path.add(BlockPos.of(k));
                    if (k == s) {
                        break;
                    }
                }
                Collections.reverse(path);
                return path;
            }
            for (int[] d : STEPS) {
                n.set(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]);
                if (Math.abs(n.getX() - start.getX()) > SEARCH_RADIUS || Math.abs(n.getY() - start.getY()) > SEARCH_RADIUS
                        || Math.abs(n.getZ() - start.getZ()) > SEARCH_RADIUS || !free(level, n)) {
                    continue;
                }
                if (d[0] != 0 && d[2] != 0 && (!free(level, side.set(p.getX() + d[0], p.getY(), p.getZ()))
                        || !free(level, side.set(p.getX(), p.getY(), p.getZ() + d[2])))) {
                    continue;
                }
                double ng = g + (d[0] != 0 && d[2] != 0 ? 1.4142 : 1.0) + floorCost(level, n, target);
                long nk = n.asLong();
                if (ng < best.get(nk)) {
                    best.put(nk, ng);
                    parent.put(nk, key);
                    open.add(new Node(ng + heuristic(level, n, target), ng, nk));
                }
            }
        }
        return null;
    }

    /** 下の空きが FLOOR_GAP マスより少ないマスへの1歩に足す遠回り（目的地のマスそのものには足さない）。 */
    private static double floorCost(ServerLevel level, BlockPos p, @Nullable BlockPos target) {
        if (p.equals(target)) {
            return 0;
        }
        BlockPos.MutableBlockPos below = p.mutable();
        int gap = 0;
        while (gap < FLOOR_GAP && free(level, below.move(0, -1, 0))) {
            gap++;
        }
        return (FLOOR_GAP - gap) * FLOOR_COST;
    }

    /** 探す途中のマス（見積もりの合計・ここまでの長さ・マスの番号）。 */
    private record Node(double f, double g, long key) {}

    /** 残りの見積もり: target までのまっすぐな距離。空を探すときは、その列の空まで上がる高さ（読み込まれていなければ 0）。 */
    private static double heuristic(ServerLevel level, BlockPos p, @Nullable BlockPos target) {
        if (target != null) {
            return Math.sqrt(p.distSqr(target));
        }
        if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
            return 0;
        }
        return Math.max(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) - p.getY()) * 0.5;
    }
}
