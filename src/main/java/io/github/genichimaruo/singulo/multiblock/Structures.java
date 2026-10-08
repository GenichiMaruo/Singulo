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

/** マルチブロックの形の判定。コントローラの位置から探す。 */
public final class Structures {
    public static final int TOWER_MIN_HEIGHT = 5;
    public static final int TOWER_MAX_HEIGHT = 15;
    public static final int RING_MIN_SIDE = 8;
    public static final int RING_MAX_SIDE = 32;

    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private Structures() {}

    /** 形成できた冷却塔。height は高さ、ports は搬入出口の位置。 */
    public record Tower(int height, List<BlockPos> ports) {}

    /**
     * 極低温冷却塔: 3×3 の筒（高さ5〜15）。各段の外周8マスは外壁・ガラス・搬入出口・コントローラで、
     * 中心の列は熱交換コアか空気（煙突）。熱交換コアは「高さ−2」個以上要る。コントローラは外周のどこか1つ。
     */
    @Nullable
    public static Tower findTower(Level level, BlockPos controller, Block controllerBlock) {
        for (int[] off : RING) {
            BlockPos center = controller.offset(-off[0], 0, -off[1]);
            Tower tower = towerAround(level, center, controller, controllerBlock);
            if (tower != null) {
                return tower;
            }
        }
        return null;
    }

    @Nullable
    private static Tower towerAround(Level level, BlockPos center, BlockPos controller, Block controllerBlock) {
        if (!towerLayer(level, center, controller.getY(), controllerBlock)) {
            return null;
        }
        int bottom = controller.getY();
        while (controller.getY() - bottom < TOWER_MAX_HEIGHT - 1 && towerLayer(level, center, bottom - 1, controllerBlock)) {
            bottom--;
        }
        int top = controller.getY();
        while (top - bottom < TOWER_MAX_HEIGHT - 1 && towerLayer(level, center, top + 1, controllerBlock)) {
            top++;
        }
        int height = top - bottom + 1;
        if (height < TOWER_MIN_HEIGHT) {
            return null;
        }
        int cores = 0;
        int controllers = 0;
        List<BlockPos> ports = new ArrayList<>();
        for (int y = bottom; y <= top; y++) {
            BlockPos c = new BlockPos(center.getX(), y, center.getZ());
            BlockState inner = level.getBlockState(c);
            if (role(inner) == MultiblockPart.Role.HEAT_EXCHANGE_CORE) {
                cores++;
            } else if (!inner.isAir()) {
                return null;
            }
            for (int[] off : RING) {
                BlockPos p = c.offset(off[0], 0, off[1]);
                BlockState s = level.getBlockState(p);
                if (s.is(controllerBlock)) {
                    controllers++;
                } else if (role(s) == MultiblockPart.Role.TOWER_PORT) {
                    ports.add(p);
                }
            }
        }
        if (controllers != 1 || cores < height - 2) {
            return null;
        }
        return new Tower(height, ports);
    }

    private static boolean towerLayer(Level level, BlockPos center, int y, Block controllerBlock) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        for (int[] off : RING) {
            BlockState s = level.getBlockState(new BlockPos(center.getX() + off[0], y, center.getZ() + off[1]));
            MultiblockPart.Role r = role(s);
            boolean shell = s.is(controllerBlock) || r == MultiblockPart.Role.TOWER_CASING
                    || r == MultiblockPart.Role.TOWER_GLASS || r == MultiblockPart.Role.TOWER_PORT;
            if (!shell) {
                return false;
            }
        }
        return true;
    }

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

    private static int ringFrom(Level level, BlockPos start) {
        int limit = 4 * (RING_MAX_SIDE - 1);
        Set<BlockPos> ring = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ring.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
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

    // ------------------------------------------------------------------ 段階4

    /**
     * 縮退圧縮炉: 縮退炉外殻で作る 3×3×3 の箱（中心は空気）。コントローラは外殻のどこか1つ。
     *
     * @return 形成できたら 3、できなければ 0
     */
    public static int findCompactor(Level level, BlockPos controller, Block controllerBlock) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos center = controller.offset(-dx, -dy, -dz);
                    if (compactorAround(level, center, controllerBlock)) {
                        return 3;
                    }
                }
            }
        }
        return 0;
    }

    private static boolean compactorAround(Level level, BlockPos center, Block controllerBlock) {
        if (!level.getBlockState(center).isAir()) {
            return false;
        }
        int controllers = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (p.equals(center)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(controllerBlock)) {
                controllers++;
            } else if (role(s) != MultiblockPart.Role.DEGENERATE_CASING) {
                return false;
            }
        }
        return controllers == 1;
    }

    /**
     * カシミール空洞: 5×5×5。下の面と上の面が鏡面プレート（向かい合う2枚）で、間の3段は四隅の柱が縮退炉外殻。
     * 鏡面の間（中の 3×3×3）は真空（空気）。側面の残りは空気か外殻。コントローラは間の3段の側面のどこか1つ。
     *
     * @return 形成できたら 5、できなければ 0
     */
    public static int findCavity(Level level, BlockPos controller, Block controllerBlock) {
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean side = x == 0 || x == 4 || z == 0 || z == 4;
                    if (side && cavityAt(level, controller.offset(-x, -y, -z), controllerBlock)) {
                        return 5;
                    }
                }
            }
        }
        return 0;
    }

    private static boolean cavityAt(Level level, BlockPos origin, Block controllerBlock) {
        int controllers = 0;
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockState s = level.getBlockState(origin.offset(x, y, z));
                    MultiblockPart.Role r = role(s);
                    boolean side = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                    if (y == 0 || y == 4) {
                        if (r != MultiblockPart.Role.MIRROR_PLATE) {
                            return false;
                        }
                    } else if (!side) {
                        if (!s.isAir()) {
                            return false;
                        }
                    } else if (s.is(controllerBlock)) {
                        controllers++;
                    } else if (corner ? r != MultiblockPart.Role.DEGENERATE_CASING
                            : !(s.isAir() || r == MultiblockPart.Role.DEGENERATE_CASING)) {
                        return false;
                    }
                }
            }
        }
        return controllers == 1;
    }

    /**
     * 縮退熱炉: 7×7×9（高さ9）。上下の面は縮退炉外殻で、中心の十字の4か所が圧縮ピストン部。
     * 間の7段は四隅の柱が外殻（コントローラは柱のどこか1つ）、中は空気（ピストンが押し込む空間）。
     *
     * @return 形成できたら 9（高さ）、できなければ 0
     */
    public static int findFurnace(Level level, BlockPos controller, Block controllerBlock) {
        for (int cx : new int[]{0, 6}) {
            for (int cz : new int[]{0, 6}) {
                for (int y = 1; y <= 7; y++) {
                    if (furnaceAt(level, controller.offset(-cx, -y, -cz), controllerBlock)) {
                        return 9;
                    }
                }
            }
        }
        return 0;
    }

    private static boolean furnaceAt(Level level, BlockPos origin, Block controllerBlock) {
        int controllers = 0;
        for (int y = 0; y <= 8; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean face = y == 0 || y == 8;
                    boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    boolean inside = x >= 1 && x <= 5 && z >= 1 && z <= 5;
                    if (!face && !corner && !inside) {
                        continue;                                  // 側面は開いていてよい（中が見える）
                    }
                    BlockState s = level.getBlockState(origin.offset(x, y, z));
                    MultiblockPart.Role r = role(s);
                    if (face) {
                        boolean piston = (x == 3 && (z == 2 || z == 4)) || (z == 3 && (x == 2 || x == 4));
                        if (r != (piston ? MultiblockPart.Role.FURNACE_PISTON : MultiblockPart.Role.DEGENERATE_CASING)) {
                            return false;
                        }
                    } else if (corner) {
                        if (s.is(controllerBlock)) {
                            controllers++;
                        } else if (r != MultiblockPart.Role.DEGENERATE_CASING) {
                            return false;
                        }
                    } else if (!s.isAir()) {
                        return false;
                    }
                }
            }
        }
        return controllers == 1;
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

    /** リングの点の役割（設計図の配置）: 軸上はジャイロ駆動部、斜め45°は抽出ポート、ほかは炉殻。抽出ポートは炉殻のどこに置いてもよい。 */
    static MultiblockPart.Role ringRole(int u, int v) {
        if (u == 0 || v == 0) {
            return MultiblockPart.Role.GYRO_DRIVE;
        }
        return Math.abs(u) == Math.abs(v) ? MultiblockPart.Role.EXTRACTION_PORT : MultiblockPart.Role.REACTOR_SHELL;
    }

    /** 形成できたリアクター。center は炉心の中心、ports は抽出ポート。 */
    public record Reactor(BlockPos center, List<BlockPos> ports) {}

    /**
     * Pリアクター: 13×13×13 のジャイロスコープ型。炉心の中心を通る直交3平面（XY・YZ・XZ）に、半径6の円環を
     * 隙間なくつなげて作る（各48ブロック）。3本が交わる軸の6点はジャイロ駆動部、各円環の斜め45°の点（計12）は抽出ポート、
     * ほかは炉殻ブロック。中心の 3×3×3 は真空（空気）の炉心。炉心制御装置は炉心の真下（中心から5ブロック下）に置く。
     */
    @Nullable
    public static Reactor findReactor(Level level, BlockPos controller) {
        BlockPos c = controller.above(CONTROLLER_BELOW_CENTER);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
            if (!level.getBlockState(p).isAir()) {
                return null;
            }
        }
        java.util.Map<BlockPos, MultiblockPart.Role> layout = reactorLayout(c);
        List<BlockPos> ports = new ArrayList<>();
        for (java.util.Map.Entry<BlockPos, MultiblockPart.Role> e : layout.entrySet()) {
            MultiblockPart.Role have = role(level.getBlockState(e.getKey()));
            boolean ring = e.getValue() == MultiblockPart.Role.REACTOR_SHELL || e.getValue() == MultiblockPart.Role.EXTRACTION_PORT;
            if (ring ? have != MultiblockPart.Role.REACTOR_SHELL && have != MultiblockPart.Role.EXTRACTION_PORT
                    : have != e.getValue()) {
                return null;
            }
            if (have == MultiblockPart.Role.EXTRACTION_PORT) {
                ports.add(e.getKey());
            }
        }
        return new Reactor(c.immutable(), ports);
    }

    /** リアクターの部品（押し出し先から外す）。 */
    public static boolean isReactorPart(BlockState state) {
        MultiblockPart.Role r = role(state);
        return r == MultiblockPart.Role.REACTOR_SHELL || r == MultiblockPart.Role.GYRO_DRIVE
                || r == MultiblockPart.Role.EXTRACTION_PORT;
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

    // ------------------------------------------------------------------ 段階5: 特異点技術（コントローラは底の中央）

    /**
     * イベントホライズン・シールド発生塔（3×3×9、縮退炉外殻20個）。底の層はコアの周りの8個、その上に中央の柱8個、
     * 柱の根元（高さ1）の四隅に支え4個。
     */
    public static List<BlockPos> shieldTowerLayout(BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    out.add(core.offset(x, 0, z));
                }
                if (x != 0 && z != 0) {
                    out.add(core.offset(x, 1, z));
                }
            }
        }
        for (int y = 1; y <= 8; y++) {
            out.add(core.above(y));
        }
        return out;
    }

    /**
     * ティプラー・シリンダー（3×3×7、縮退炉外殻36個）。底の層はコアの周りの8個、上の層（高さ6）は中央を空けた8個、
     * その間の四隅に柱（高さ1〜5）。中央の高さ1〜6は空気（回る円柱が入る）。
     */
    public static List<BlockPos> tiplerLayout(BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                out.add(core.offset(x, 0, z));
                out.add(core.offset(x, 6, z));
                if (x != 0 && z != 0) {
                    for (int y = 1; y <= 5; y++) {
                        out.add(core.offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * ワームホール生成器（3×3×3、縮退炉外殻24個）。コアは底の中央。箱の中心と天井の中央は空気（口が生まれる穴）。
     */
    public static List<BlockPos> wormholeGeneratorLayout(BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) {
                        out.add(core.offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * 縮退炉外殻で決まった形ができているか。ただし外殻の代わりに portRole の入出力口を置いてもよい（置いた場所を ports に入れる）。
     */
    public static boolean casingShapeWithPorts(Level level, BlockPos core, List<BlockPos> layout, int airAbove,
                                               MultiblockPart.Role portRole, List<BlockPos> ports) {
        ports.clear();
        for (BlockPos p : layout) {
            MultiblockPart.Role r = role(level.getBlockState(p));
            if (r == portRole) {
                ports.add(p.immutable());
            } else if (r != MultiblockPart.Role.DEGENERATE_CASING) {
                ports.clear();
                return false;
            }
        }
        for (int y = 1; y <= airAbove; y++) {
            if (!level.getBlockState(core.above(y)).isAir()) {
                ports.clear();
                return false;
            }
        }
        return true;
    }

    /** 縮退炉外殻で決まった形ができているか。airAbove は中央で空気でなければならない高さの数。 */
    public static boolean casingShape(Level level, BlockPos core, List<BlockPos> layout, int airAbove) {
        for (BlockPos p : layout) {
            if (role(level.getBlockState(p)) != MultiblockPart.Role.DEGENERATE_CASING) {
                return false;
            }
        }
        for (int y = 1; y <= airAbove; y++) {
            if (!level.getBlockState(core.above(y)).isAir()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    static MultiblockPart.Role role(BlockState state) {
        return state.getBlock() instanceof MultiblockPart part ? part.role() : null;
    }
}
