package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * マルチブロックの設計図。コントローラの位置と「奥」の向き（コントローラの正面の反対、置いた人が見ていた向き）から、
 * 正しい形の各位置に置くブロックを返す。空気でなければいけない位置は AIR で入る。
 * クリエイティブの組み立てコマンド（/singulo build）、ホロ投影機、テストが同じ設計図を使う。
 * 形の判定（Structures）が受け付ける形の1つを作るので、判定を変えたらここも合わせる。
 */
public final class Blueprints {
    private Blueprints() {}

    public enum Kind {
        COOLING_TOWER("cooling_tower", new int[]{5, 10, 15}),
        PARTICLE_ACCELERATOR("particle_accelerator", new int[]{8, 16, 32}),
        DEGENERATE_COMPACTOR("degenerate_compactor", new int[]{3}),
        CASIMIR_CAVITY("casimir_cavity", new int[]{5}),
        DEGENERATE_FURNACE("degenerate_furnace", new int[]{9}),
        PENROSE_REACTOR("penrose_reactor", new int[]{13}),
        SHIELD_TOWER("event_horizon_shield", new int[]{9}),
        TIPLER_CYLINDER("tipler_cylinder", new int[]{7}),
        WORMHOLE_GENERATOR("wormhole_generator", new int[]{3});

        private final String id;
        private final int[] sizes;

        Kind(String id, int[] sizes) {
            this.id = id;
            this.sizes = sizes;
        }

        public String id() {
            return id;
        }

        /** 選べる大きさ（冷却塔は高さ、加速器は一辺。固定の形は1つだけ）。 */
        public int[] sizes() {
            return sizes;
        }

        public int defaultSize() {
            return sizes[0];
        }

        public boolean validSize(int size) {
            return switch (this) {
                case COOLING_TOWER -> size >= Structures.TOWER_MIN_HEIGHT && size <= Structures.TOWER_MAX_HEIGHT;
                case PARTICLE_ACCELERATOR -> size >= Structures.RING_MIN_SIDE && size <= Structures.RING_MAX_SIDE;
                default -> size == sizes[0];
            };
        }

        @Nullable
        public static Kind byId(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return null;
        }
    }

    /** コントローラのブロックからマルチブロックの種類を引く。 */
    @Nullable
    public static Kind kindOf(Block controller) {
        for (Kind k : Kind.values()) {
            if (controllerBlock(k) == controller) {
                return k;
            }
        }
        return null;
    }

    public static Block controllerBlock(Kind kind) {
        return switch (kind) {
            case COOLING_TOWER -> SinguloBlocks.CONTROLLERS.get(MachineType.CRYOGENIC_COOLING_TOWER).get();
            case PARTICLE_ACCELERATOR -> SinguloBlocks.CONTROLLERS.get(MachineType.PARTICLE_ACCELERATOR).get();
            case DEGENERATE_COMPACTOR -> SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get();
            case CASIMIR_CAVITY -> SinguloBlocks.CONTROLLERS.get(MachineType.CASIMIR_CAVITY).get();
            case DEGENERATE_FURNACE -> SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER.get();
            case PENROSE_REACTOR -> SinguloBlocks.CORE_CONTROLLER.get();
            case SHIELD_TOWER -> SinguloBlocks.SHIELD_TOWER_CORE.get();
            case TIPLER_CYLINDER -> SinguloBlocks.TIPLER_CORE.get();
            case WORMHOLE_GENERATOR -> SinguloBlocks.WORMHOLE_GENERATOR_CORE.get();
        };
    }

    /**
     * 設計図。controller はコントローラの位置、back は奥の向き（水平）。コントローラ自身も含む。
     * size が無効なら既定の大きさにする。
     */
    public static Map<BlockPos, BlockState> layout(Kind kind, BlockPos controller, Direction back, int size) {
        if (!kind.validSize(size)) {
            size = kind.defaultSize();
        }
        Builder b = new Builder(controller, back);
        b.put(0, 0, 0, controllerBlock(kind).defaultBlockState().setValue(AbstractMachineBlock.FACING, back.getOpposite()));
        switch (kind) {
            case COOLING_TOWER -> coolingTower(b, size);
            case PARTICLE_ACCELERATOR -> accelerator(b, size);
            case DEGENERATE_COMPACTOR -> compactor(b);
            case CASIMIR_CAVITY -> cavity(b);
            case DEGENERATE_FURNACE -> furnace(b);
            case PENROSE_REACTOR -> reactor(b, controller);
            case SHIELD_TOWER -> {
                for (BlockPos p : Structures.shieldTowerLayout(controller)) {
                    b.world(p, SinguloBlocks.DEGENERATE_CASING);
                }
            }
            case TIPLER_CYLINDER -> {
                for (BlockPos p : Structures.tiplerLayout(controller)) {
                    b.world(p, SinguloBlocks.DEGENERATE_CASING);
                }
                for (int y = 1; y <= 6; y++) {
                    b.air(0, y, 0);
                }
            }
            case WORMHOLE_GENERATOR -> {
                for (BlockPos p : Structures.wormholeGeneratorLayout(controller)) {
                    b.world(p, SinguloBlocks.DEGENERATE_CASING);
                }
                b.air(0, 1, 0);
                b.air(0, 2, 0);
            }
        }
        return b.out;
    }

    /** 冷却塔: コントローラは最下段の手前の面の中央。中心の列は熱交換コア（最下段と最上段は空気）、奥の面の最下段に搬入出口。 */
    private static void coolingTower(Builder b, int height) {
        for (int y = 0; y < height; y++) {
            for (int f = 0; f <= 2; f++) {
                for (int r = -1; r <= 1; r++) {
                    if (f == 1 && r == 0) {
                        boolean core = y >= 1 && y <= height - 2;
                        if (core) {
                            b.put(f, y, r, SinguloBlocks.HEAT_EXCHANGE_CORE);
                        } else {
                            b.air(f, y, r);
                        }
                        continue;
                    }
                    if (y == 0 && f == 0 && r == 0) {
                        continue;                                   // コントローラ
                    }
                    if (y == 0 && f == 2 && r == 0) {
                        b.put(f, y, r, SinguloBlocks.COOLING_TOWER_PORT);
                    } else if (y % 2 == 1 && (f == 1 || r == 0)) {
                        b.put(f, y, r, SinguloBlocks.COOLING_TOWER_GLASS);   // 側面の中央は1段おきにガラス（中が見える）
                    } else {
                        b.put(f, y, r, SinguloBlocks.COOLING_TOWER_CASING);
                    }
                }
            }
        }
    }

    /** 加速器: コントローラの奥に一辺 side の正方形のリング。周に沿って4つおきと四隅が収束磁石。 */
    private static void accelerator(Builder b, int side) {
        int left = -(side / 2);
        int k = 0;
        // 周を順にたどる（手前の辺 → 右の辺 → 奥の辺 → 左の辺）
        java.util.List<int[]> perimeter = new java.util.ArrayList<>();
        for (int r = 0; r < side; r++) {
            perimeter.add(new int[]{1, left + r});
        }
        for (int f = 2; f <= side; f++) {
            perimeter.add(new int[]{f, left + side - 1});
        }
        for (int r = side - 2; r >= 0; r--) {
            perimeter.add(new int[]{side, left + r});
        }
        for (int f = side - 1; f >= 2; f--) {
            perimeter.add(new int[]{f, left});
        }
        for (int[] p : perimeter) {
            boolean corner = (p[0] == 1 || p[0] == side) && (p[1] == left || p[1] == left + side - 1);
            b.put(p[0], 0, p[1], corner || k % 4 == 0 ? SinguloBlocks.FOCUSING_MAGNET : SinguloBlocks.ACCELERATOR_TUBE);
            k++;
        }
    }

    /** 縮退圧縮炉: コントローラは 3×3×3 の手前の面の中央。中心は空気。 */
    private static void compactor(Builder b) {
        for (int f = 0; f <= 2; f++) {
            for (int y = -1; y <= 1; y++) {
                for (int r = -1; r <= 1; r++) {
                    if (f == 0 && y == 0 && r == 0) {
                        continue;
                    }
                    if (f == 1 && y == 0 && r == 0) {
                        b.air(f, y, r);
                    } else {
                        b.put(f, y, r, SinguloBlocks.DEGENERATE_CASING);
                    }
                }
            }
        }
    }

    /** カシミール空洞: コントローラは 5×5×5 の手前の面の中央（間の3段の真ん中）。 */
    private static void cavity(Builder b) {
        for (int f = 0; f <= 4; f++) {
            for (int y = -2; y <= 2; y++) {
                for (int r = -2; r <= 2; r++) {
                    if (f == 0 && y == 0 && r == 0) {
                        continue;
                    }
                    boolean side = f == 0 || f == 4 || r == -2 || r == 2;
                    boolean corner = (f == 0 || f == 4) && (r == -2 || r == 2);
                    if (y == -2 || y == 2) {
                        b.put(f, y, r, SinguloBlocks.MIRROR_PLATE);
                    } else if (corner) {
                        b.put(f, y, r, SinguloBlocks.DEGENERATE_CASING);
                    } else if (!side) {
                        b.air(f, y, r);
                    }
                }
            }
        }
    }

    /** 縮退熱炉: コントローラは手前左の柱の下から2段目。7×7×9。 */
    private static void furnace(Builder b) {
        for (int y = -1; y <= 7; y++) {
            for (int f = 0; f <= 6; f++) {
                for (int r = 0; r <= 6; r++) {
                    if (f == 0 && r == 0 && y == 0) {
                        continue;
                    }
                    boolean face = y == -1 || y == 7;
                    boolean corner = (f == 0 || f == 6) && (r == 0 || r == 6);
                    boolean inside = f >= 1 && f <= 5 && r >= 1 && r <= 5;
                    if (face) {
                        boolean piston = (f == 3 && (r == 2 || r == 4)) || (r == 3 && (f == 2 || f == 4));
                        b.put(f, y, r, piston ? SinguloBlocks.DEGENERATE_FURNACE_PISTON : SinguloBlocks.DEGENERATE_CASING);
                    } else if (corner) {
                        b.put(f, y, r, SinguloBlocks.DEGENERATE_CASING);
                    } else if (inside) {
                        b.air(f, y, r);
                    }
                }
            }
        }
    }

    private static void reactor(Builder b, BlockPos controller) {
        BlockPos c = controller.above(Structures.CONTROLLER_BELOW_CENTER);
        for (Map.Entry<BlockPos, MultiblockPart.Role> e : Structures.reactorLayout(c).entrySet()) {
            Supplier<? extends Block> block = switch (e.getValue()) {
                case GYRO_DRIVE -> SinguloBlocks.GYRO_DRIVE;
                case EXTRACTION_PORT -> SinguloBlocks.EXTRACTION_PORT;
                default -> SinguloBlocks.REACTOR_SHELL;
            };
            b.world(e.getKey(), block);
        }
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
            b.out.put(p.immutable(), Blocks.AIR.defaultBlockState());
        }
    }

    /** 奥（f）・上（y）・右（r）の座標でブロックを置く。 */
    private static final class Builder {
        final Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        final BlockPos origin;
        final Direction back;
        final Direction right;

        Builder(BlockPos origin, Direction back) {
            this.origin = origin;
            this.back = back;
            this.right = back.getClockWise();
        }

        BlockPos at(int f, int y, int r) {
            return origin.relative(back, f).relative(right, r).above(y);
        }

        void put(int f, int y, int r, BlockState state) {
            out.put(at(f, y, r), state);
        }

        void put(int f, int y, int r, Supplier<? extends Block> block) {
            put(f, y, r, block.get().defaultBlockState());
        }

        void air(int f, int y, int r) {
            put(f, y, r, Blocks.AIR.defaultBlockState());
        }

        void world(BlockPos p, Supplier<? extends Block> block) {
            out.put(p.immutable(), block.get().defaultBlockState());
        }
    }

    /** 設計図の位置が今のワールドで正しいか（空気の位置は空気、ブロックの位置は同じブロック）。 */
    /** 形成済みなら大きさ（冷却塔は高さ、加速器は一辺、ほかは決まった大きさ）、未完成なら 0。ブロックの並びだけで判定する。 */
    public static int formedSize(net.minecraft.world.level.Level level, BlockPos c, Kind kind) {
        Block block = controllerBlock(kind);
        if (level.getBlockState(c).getBlock() != block) {
            return 0;
        }
        return switch (kind) {
            case COOLING_TOWER -> {
                Structures.Tower t = Structures.findTower(level, c, block);
                yield t == null ? 0 : t.height();
            }
            case PARTICLE_ACCELERATOR -> Structures.findRing(level, c);
            case DEGENERATE_COMPACTOR -> Structures.findCompactor(level, c, block);
            case CASIMIR_CAVITY -> Structures.findCavity(level, c, block);
            case DEGENERATE_FURNACE -> Structures.findFurnace(level, c, block);
            case PENROSE_REACTOR -> Structures.findReactor(level, c) == null ? 0 : 13;
            case SHIELD_TOWER -> Structures.casingShape(level, c, Structures.shieldTowerLayout(c), 0) ? 9 : 0;
            case TIPLER_CYLINDER -> Structures.casingShape(level, c, Structures.tiplerLayout(c), 6) ? 7 : 0;
            case WORMHOLE_GENERATOR -> Structures.casingShapeWithPorts(level, c, Structures.wormholeGeneratorLayout(c), 2,
                    MultiblockPart.Role.WORMHOLE_IO, new java.util.ArrayList<>()) ? 3 : 0;
        };
    }

    /** どれかのマルチブロックに部品として使われるブロック（コントローラーを除く）。 */
    private static java.util.Set<Block> partBlocks;

    public static boolean isPartBlock(Block block) {
        if (partBlocks == null) {
            java.util.Set<Block> set = new java.util.HashSet<>();
            for (Kind k : Kind.values()) {
                for (int size : k.sizes()) {
                    for (BlockState s : layout(k, BlockPos.ZERO, Direction.NORTH, size).values()) {
                        if (!s.isAir() && kindOf(s.getBlock()) == null) {
                            set.add(s.getBlock());
                        }
                    }
                }
            }
            partBlocks = set;
        }
        return partBlocks.contains(block) || block == SinguloBlocks.WORMHOLE_GENERATOR_IO.get();
    }

    /**
     * 画面を開いたままでいられる、コントローラーからの距離（ブロック）。
     * どの部品を右クリックしても画面が開くので、ふつうの 8 ブロックに構造物の大きさを足す。
     */
    public static double menuReach(net.minecraft.world.level.Level level, BlockPos controller) {
        Kind kind = kindOf(level.getBlockState(controller).getBlock());
        if (kind == null) {
            return 8;
        }
        int extent = switch (kind) {
            case PARTICLE_ACCELERATOR -> Structures.RING_MAX_SIDE + 2;
            case COOLING_TOWER -> Structures.TOWER_MAX_HEIGHT + 1;
            case PENROSE_REACTOR -> Structures.REACTOR_RADIUS * 2 + 2;
            default -> kind.defaultSize() + 1;
        };
        return 8 + extent;
    }

    /** 画面を開いたままでいられるか（プレイヤーとコントローラーの距離）。 */
    public static boolean withinMenuReach(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        double reach = menuReach(player.level(), pos);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
    }

    /** 加速器の一辺の上限（部品からコントローラーを探す範囲）。 */
    private static final int SEARCH = 33;

    /**
     * 部品 part を含む、形成済みのマルチブロックのコントローラーの位置。なければ null。
     * 近くのコントローラーを探し、設計図（4つの向きのうち、いちばん一致するもの）に part が入っているかで決める。
     */
    @Nullable
    public static BlockPos controllerOf(net.minecraft.world.level.Level level, BlockPos part) {
        if (!isPartBlock(level.getBlockState(part).getBlock())) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int minCx = (part.getX() - SEARCH) >> 4, maxCx = (part.getX() + SEARCH) >> 4;
        int minCz = (part.getZ() - SEARCH) >> 4, maxCz = (part.getZ() + SEARCH) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockPos c : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    if (Math.abs(c.getX() - part.getX()) > SEARCH || Math.abs(c.getY() - part.getY()) > SEARCH
                            || Math.abs(c.getZ() - part.getZ()) > SEARCH) {
                        continue;
                    }
                    Kind kind = kindOf(level.getBlockState(c).getBlock());
                    if (kind == null) {
                        continue;
                    }
                    double d = c.distSqr(part);
                    if (d < bestDist && contains(level, c, kind, part)) {
                        best = c.immutable();
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    private static boolean contains(net.minecraft.world.level.Level level, BlockPos c, Kind kind, BlockPos part) {
        int size = formedSize(level, c, kind);
        if (size <= 0) {
            return false;
        }
        Map<BlockPos, BlockState> bestLayout = null;
        int bestScore = -1;
        for (Direction back : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, BlockState> layout = layout(kind, c, back, size);
            int score = 0;
            for (Map.Entry<BlockPos, BlockState> e : layout.entrySet()) {
                if (matches(e.getValue(), level.getBlockState(e.getKey()))) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestLayout = layout;
            }
        }
        BlockState want = bestLayout == null ? null : bestLayout.get(part);
        return want != null && !want.isAir();
    }

    /** 組むのに要るブロックと個数（いちばん小さい形で数える。コントローラーが先頭）。JEI の説明に使う。 */
    public static java.util.LinkedHashMap<Block, Integer> partCounts(Kind kind) {
        java.util.LinkedHashMap<Block, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put(controllerBlock(kind), 1);
        for (BlockState s : layout(kind, BlockPos.ZERO, Direction.NORTH, kind.defaultSize()).values()) {
            if (!s.isAir() && s.getBlock() != controllerBlock(kind)) {
                counts.merge(s.getBlock(), 1, Integer::sum);
            }
        }
        return counts;
    }

    public static boolean matches(BlockState want, BlockState have) {
        return want.isAir() ? have.isAir() : have.is(want.getBlock());
    }
}
