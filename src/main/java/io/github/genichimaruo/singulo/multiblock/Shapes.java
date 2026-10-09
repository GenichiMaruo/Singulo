package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.multiblock.ShapeSpec.Slot;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * マルチブロックの形（粒子加速器とペンローズ・リアクター以外）。どれも中に入れない閉じた形で、中は窓から見える。
 * コントローラは外から見える面の、いちばん下の層（地面に接する層、または土台のすぐ上）に置き、枠（箱の辺）には置かない。
 * 外装板はマルチブロック搬入出ポートと入れ替えられる。
 */
public final class Shapes {
    private Shapes() {}

    public static final int TOWER_MIN_HEIGHT = 7;
    public static final int TOWER_MAX_HEIGHT = 15;

    /** 形成できた形。size は大きさ（冷却塔は高さ、ほかは決まった大きさ）、ports は搬入出ポート。 */
    public record Found(ShapeSpec spec, Direction back, int size, List<BlockPos> ports) {
        public BlockPos pos(BlockPos controller, int x, int y, int z) {
            return spec.pos(controller, back, x, y, z);
        }

        public Vec3 center(BlockPos controller) {
            return spec.center(controller, back);
        }
    }

    /** 箱の側面での、面に沿った横の位置（x か z）。 */
    private static int along(int x, int z, int w, int d) {
        return z == 0 || z == d - 1 ? x : z;
    }

    // ------------------------------------------------------------------ 縮退熱炉（5×5×7）

    /**
     * 縮退熱炉: 箱の辺は枠、上下の面の中心は圧縮ピストン、側面は中央の縦3段が観察窓でその両わきが放熱フィン、
     * 中の四隅の柱は熱交換管、中の十字は空気（押しつぶす部屋）。コントローラは側面の下から2段目の中央。
     */
    public static final ShapeSpec FURNACE = new ShapeSpec(5, 7, 5, 2, 1, 0, (x, y, z) -> {
        int e = ShapeSpec.edges(x, y, z, 5, 7, 5);
        if (e >= 2) {
            return Slot.part(SinguloBlocks.DEGENERATE_FURNACE_FRAME);
        }
        if (e == 0) {
            return x != 2 && z != 2 ? Slot.part(SinguloBlocks.DEGENERATE_FURNACE_TUBE) : Slot.AIR;
        }
        if (y == 0 || y == 6) {
            return x == 2 && z == 2 ? Slot.part(SinguloBlocks.DEGENERATE_FURNACE_PISTON) : Slot.panel(SinguloBlocks.DEGENERATE_FURNACE_SHELL);
        }
        int u = along(x, z, 5, 5);
        if (y >= 2 && y <= 4) {
            return Slot.part(u == 2 ? SinguloBlocks.DEGENERATE_FURNACE_WINDOW : SinguloBlocks.DEGENERATE_FURNACE_FIN);
        }
        return y == 1 && u == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.DEGENERATE_FURNACE_SHELL);
    });

    // ------------------------------------------------------------------ 縮退圧縮炉（5×5×5）

    /**
     * 縮退圧縮炉: 箱の辺は圧縮フレーム、天面の中心はラム（上から打ち下ろす）、底面の中心はアンビル（受け台）。
     * 側面の最上段は冷却口、中段の中央は観察窓、残りは装甲板。中の 3×3×3 は空気（押しつぶす部屋）。
     */
    public static final ShapeSpec COMPACTOR = new ShapeSpec(5, 5, 5, 2, 1, 0, (x, y, z) -> {
        int e = ShapeSpec.edges(x, y, z, 5, 5, 5);
        if (e >= 2) {
            return Slot.part(SinguloBlocks.DEGENERATE_COMPACTOR_FRAME);
        }
        if (e == 0) {
            return Slot.AIR;
        }
        boolean center = x == 2 && z == 2;
        if (y == 4) {
            return center ? Slot.part(SinguloBlocks.DEGENERATE_COMPACTOR_RAM) : Slot.panel(SinguloBlocks.DEGENERATE_COMPACTOR_PLATE);
        }
        if (y == 0) {
            return center ? Slot.part(SinguloBlocks.DEGENERATE_COMPACTOR_ANVIL) : Slot.panel(SinguloBlocks.DEGENERATE_COMPACTOR_PLATE);
        }
        int u = along(x, z, 5, 5);
        if (y == 3) {
            return Slot.part(SinguloBlocks.DEGENERATE_COMPACTOR_VENT);
        }
        if (y == 2 && u == 2) {
            return Slot.part(SinguloBlocks.DEGENERATE_COMPACTOR_WINDOW);
        }
        return y == 1 && u == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.DEGENERATE_COMPACTOR_PLATE);
    });

    // ------------------------------------------------------------------ C空洞（5×5×5）

    /**
     * C空洞: 真空容器。箱の角8つは真空ポンプ、残りの辺は容器の枠。天面と底面の内側 3×3 は向かい合う2枚の大きな鏡面。
     * 側面は中段の中央が観察窓でその両わきが磁気シールド、残りは容器壁。中の 3×3×3 は空気（真空）。
     */
    public static final ShapeSpec CAVITY = new ShapeSpec(5, 5, 5, 2, 1, 0, (x, y, z) -> {
        int e = ShapeSpec.edges(x, y, z, 5, 5, 5);
        if (e == 3) {
            return Slot.part(SinguloBlocks.CASIMIR_CAVITY_PUMP);
        }
        if (e == 2) {
            return Slot.part(SinguloBlocks.CASIMIR_CAVITY_FRAME);
        }
        if (e == 0) {
            return Slot.AIR;
        }
        if (y == 0 || y == 4) {
            return Slot.part(SinguloBlocks.MIRROR_PLATE);
        }
        int u = along(x, z, 5, 5);
        if (y == 2) {
            return Slot.part(u == 2 ? SinguloBlocks.CASIMIR_CAVITY_WINDOW : SinguloBlocks.CASIMIR_CAVITY_SHIELD);
        }
        return y == 1 && u == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.CASIMIR_CAVITY_WALL);
    });

    // ------------------------------------------------------------------ 極低温冷却塔（5×5、高さ7〜15）

    private static final ShapeSpec[] TOWERS = new ShapeSpec[TOWER_MAX_HEIGHT + 1];

    /**
     * 極低温冷却塔（高さ h）: 双曲面の塔。底は 5×5 の基部、下の2段と上から2段目は 5×5 の外壁の輪、
     * その間のくびれは 3×3 の細い胴（観察窓と、3段ごとの冷却帯）。てっぺんは頂部リムと通気格子でふさぐ。
     * 中心の列は熱交換コア（高さ1〜h−2）。コントローラは手前の面の下から2段目の中央。
     */
    public static ShapeSpec tower(int h) {
        if (h < TOWER_MIN_HEIGHT || h > TOWER_MAX_HEIGHT) {
            throw new IllegalArgumentException("tower height " + h);
        }
        if (TOWERS[h] == null) {
            TOWERS[h] = new ShapeSpec(5, h, 5, 2, 1, 0, (x, y, z) -> towerSlot(x, y, z, h));
        }
        return TOWERS[h];
    }

    /** 冷却塔のくびれ（細い胴）の段か。 */
    public static boolean towerWaist(int y, int h) {
        return y >= 3 && y <= h - 3;
    }

    private static Slot towerSlot(int x, int y, int z, int h) {
        int dx = Math.abs(x - 2), dz = Math.abs(z - 2), m = Math.max(dx, dz);
        if (y == 0) {
            return Slot.part(SinguloBlocks.COOLING_TOWER_BASE);
        }
        if (y == h - 1) {
            return Slot.part(m == 2 ? SinguloBlocks.COOLING_TOWER_RIM : SinguloBlocks.COOLING_TOWER_GRATE);
        }
        if (m == 0) {
            return Slot.part(SinguloBlocks.HEAT_EXCHANGE_CORE);
        }
        if (towerWaist(y, h)) {
            if (m == 2) {
                return Slot.IGNORE;
            }
            if ((y - 3) % 3 == 1) {
                return Slot.part(SinguloBlocks.COOLING_TOWER_COOLANT_BAND);
            }
            return Slot.part(dx == 1 && dz == 1 ? SinguloBlocks.COOLING_TOWER_CASING : SinguloBlocks.COOLING_TOWER_GLASS);
        }
        // 下の2段と、上から2段目（5×5 の輪）
        if (m == 1) {
            return Slot.AIR;
        }
        if (dx == 2 && dz == 2) {
            return Slot.part(SinguloBlocks.COOLING_TOWER_CASING);
        }
        return y == 1 && x == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.COOLING_TOWER_CASING);
    }

    // ------------------------------------------------------------------ イベントホライズン・シールド発生塔（5×5、高さ9）

    /**
     * シールド発生塔: 5×5 の基壇（2段）から、3×3 の本体（4段）、細い尖塔（3段）へとすぼまる塔。
     * 基壇の2段目の内側は電力コイル、本体は四隅が装甲で各面の中央が導波管（中を昇るエネルギーが見える）、中心は空気。
     * 尖塔は本体・導波管・放射冠の順。コントローラは基壇の2段目の手前の中央。
     */
    public static final ShapeSpec SHIELD_TOWER = new ShapeSpec(5, 9, 5, 2, 1, 0, (x, y, z) -> {
        int dx = Math.abs(x - 2), dz = Math.abs(z - 2), m = Math.max(dx, dz);
        if (y == 0) {
            return Slot.part(SinguloBlocks.SHIELD_TOWER_PLINTH);
        }
        if (y == 1) {
            if (m < 2) {
                return Slot.part(SinguloBlocks.SHIELD_TOWER_COIL);
            }
            if (dx == 2 && dz == 2) {
                return Slot.part(SinguloBlocks.SHIELD_TOWER_PLINTH);
            }
            return x == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.SHIELD_TOWER_PLINTH);
        }
        if (y <= 5) {
            if (m == 2) {
                return Slot.IGNORE;
            }
            if (m == 0) {
                return Slot.AIR;
            }
            return Slot.part(dx == 1 && dz == 1 ? SinguloBlocks.SHIELD_TOWER_BODY : SinguloBlocks.SHIELD_TOWER_WAVEGUIDE);
        }
        if (m > 0) {
            return Slot.IGNORE;
        }
        return Slot.part(y == 6 ? SinguloBlocks.SHIELD_TOWER_BODY : y == 7 ? SinguloBlocks.SHIELD_TOWER_WAVEGUIDE
                : SinguloBlocks.SHIELD_TOWER_CROWN);
    });

    // ------------------------------------------------------------------ Tシリンダー（5×5×9）

    /**
     * Tシリンダー: 回転円柱の格納筒。箱の辺は枠、上下の面の中心は軸受、側面は中央の縦5段が観察窓、
     * 窓の両わきの中段に時間結晶ホルダー、残りは外殻。中の 3×3×7 は空気（円柱が回る）。
     */
    public static final ShapeSpec TIPLER = new ShapeSpec(5, 9, 5, 2, 1, 0, (x, y, z) -> {
        int e = ShapeSpec.edges(x, y, z, 5, 9, 5);
        if (e >= 2) {
            return Slot.part(SinguloBlocks.TIPLER_FRAME);
        }
        if (e == 0) {
            return Slot.AIR;
        }
        if (y == 0 || y == 8) {
            return x == 2 && z == 2 ? Slot.part(SinguloBlocks.TIPLER_BEARING) : Slot.panel(SinguloBlocks.TIPLER_HOUSING);
        }
        int u = along(x, z, 5, 5);
        if (u == 2 && y >= 2 && y <= 6) {
            return Slot.part(SinguloBlocks.TIPLER_WINDOW);
        }
        if (u != 2 && y == 4) {
            return Slot.part(SinguloBlocks.TIPLER_HOLDER);
        }
        return y == 1 && u == 2 && z == 0 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.TIPLER_HOUSING);
    });

    // ------------------------------------------------------------------ ワームホール生成器（5×5×5 の球）

    /**
     * ワームホール生成器: 球形の閉じ込め容器。上下は 3×3（中心は場の収束器）、間の3段は角を落とした輪で、
     * 赤道の段は各面の中央が観察窓、その両わきが場コイル。中の 3×3×3 は空気（口が生まれる）。
     * コントローラは底の 3×3 の手前の列の中央（地面に接し、手前から見える）。
     */
    public static final ShapeSpec WORMHOLE_GENERATOR = new ShapeSpec(5, 5, 5, 2, 0, 1, (x, y, z) -> {
        int dx = Math.abs(x - 2), dz = Math.abs(z - 2), m = Math.max(dx, dz);
        if (y == 0 || y == 4) {
            if (m == 2) {
                return Slot.IGNORE;
            }
            if (m == 0) {
                return Slot.part(SinguloBlocks.WORMHOLE_GENERATOR_FOCUSER);
            }
            return y == 0 && x == 2 && z == 1 ? Slot.CONTROLLER : Slot.panel(SinguloBlocks.WORMHOLE_GENERATOR_SHELL);
        }
        if (m < 2) {
            return Slot.AIR;
        }
        if (dx == 2 && dz == 2) {
            return Slot.IGNORE;
        }
        if (y == 2) {
            int u = dz == 2 ? x : z;
            return Slot.part(u == 2 ? SinguloBlocks.WORMHOLE_GENERATOR_WINDOW : SinguloBlocks.WORMHOLE_GENERATOR_COIL);
        }
        return Slot.panel(SinguloBlocks.WORMHOLE_GENERATOR_SHELL);
    });

    // ------------------------------------------------------------------ 種類ごと

    /** 種類と大きさの形。粒子加速器とペンローズ・リアクターは null（別の判定）。 */
    @Nullable
    public static ShapeSpec spec(Blueprints.Kind kind, int size) {
        return switch (kind) {
            case COOLING_TOWER -> tower(Math.max(TOWER_MIN_HEIGHT, Math.min(TOWER_MAX_HEIGHT, size)));
            case DEGENERATE_COMPACTOR -> COMPACTOR;
            case CASIMIR_CAVITY -> CAVITY;
            case DEGENERATE_FURNACE -> FURNACE;
            case SHIELD_TOWER -> SHIELD_TOWER;
            case TIPLER_CYLINDER -> TIPLER;
            case WORMHOLE_GENERATOR -> WORMHOLE_GENERATOR;
            case PARTICLE_ACCELERATOR, PENROSE_REACTOR -> null;
        };
    }

    /** controller を含む、形成できている形。できていなければ null。 */
    @Nullable
    public static Found find(Blueprints.Kind kind, Level level, BlockPos controller) {
        Block block = Blueprints.controllerBlock(kind);
        if (!level.getBlockState(controller).is(block)) {
            return null;
        }
        if (kind == Blueprints.Kind.COOLING_TOWER) {
            // 高さは天面（頂部リム）の位置で決まるので、高い方から試す
            for (int h = TOWER_MAX_HEIGHT; h >= TOWER_MIN_HEIGHT; h--) {
                Found f = find(tower(h), h, level, controller, block);
                if (f != null) {
                    return f;
                }
            }
            return null;
        }
        ShapeSpec spec = spec(kind, 0);
        return spec == null ? null : find(spec, spec.height(), level, controller, block);
    }

    @Nullable
    private static Found find(ShapeSpec spec, int size, Level level, BlockPos controller, Block block) {
        List<BlockPos> ports = new ArrayList<>();
        Direction back = spec.find(level, controller, block, ports);
        return back == null ? null : new Found(spec, back, size, List.copyOf(ports));
    }
}
