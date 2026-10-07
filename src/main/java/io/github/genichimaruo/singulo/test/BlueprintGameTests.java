package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.command.SinguloCommands;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 設計図（組み立てコマンドとホロ投影機が使う）どおりに組むと、形の判定が通ること。向きも変えて確かめる。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class BlueprintGameTests {
    private static final String HUGE = "huge";

    private BlueprintGameTests() {}

    private static void check(GameTestHelper helper, Blueprints.Kind kind, int size, BlockPos rel, Direction back, int expect) {
        Level level = helper.getLevel();
        BlockPos c = helper.absolutePos(rel);
        SinguloCommands.place(helper.getLevel(), kind, c, back, size);
        Block block = Blueprints.controllerBlock(kind);
        int got = switch (kind) {
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
            case WORMHOLE_GENERATOR -> Structures.casingShape(level, c, Structures.wormholeGeneratorLayout(c), 2) ? 3 : 0;
        };
        helper.assertTrue(got == expect, kind.id() + "（" + back + "）が形成されない: " + got);
        helper.succeed();
    }

    @GameTest(template = HUGE)
    public static void blueprintCoolingTower(GameTestHelper helper) {
        check(helper, Blueprints.Kind.COOLING_TOWER, 10, new BlockPos(5, 1, 5), Direction.EAST, 10);
    }

    @GameTest(template = HUGE)
    public static void blueprintAccelerator(GameTestHelper helper) {
        check(helper, Blueprints.Kind.PARTICLE_ACCELERATOR, 8, new BlockPos(7, 1, 2), Direction.SOUTH, 8);
    }

    @GameTest(template = HUGE)
    public static void blueprintCompactor(GameTestHelper helper) {
        check(helper, Blueprints.Kind.DEGENERATE_COMPACTOR, 3, new BlockPos(5, 2, 5), Direction.WEST, 3);
    }

    @GameTest(template = HUGE)
    public static void blueprintCavity(GameTestHelper helper) {
        check(helper, Blueprints.Kind.CASIMIR_CAVITY, 5, new BlockPos(2, 3, 7), Direction.EAST, 5);
    }

    @GameTest(template = HUGE)
    public static void blueprintFurnace(GameTestHelper helper) {
        check(helper, Blueprints.Kind.DEGENERATE_FURNACE, 9, new BlockPos(2, 2, 2), Direction.NORTH.getOpposite(), 9);
    }

    @GameTest(template = HUGE)
    public static void blueprintReactor(GameTestHelper helper) {
        check(helper, Blueprints.Kind.PENROSE_REACTOR, 13, new BlockPos(7, 1, 7), Direction.NORTH, 13);
    }

    @GameTest(template = HUGE)
    public static void blueprintShieldTower(GameTestHelper helper) {
        check(helper, Blueprints.Kind.SHIELD_TOWER, 9, new BlockPos(7, 1, 7), Direction.NORTH, 9);
    }

    @GameTest(template = HUGE)
    public static void blueprintTipler(GameTestHelper helper) {
        check(helper, Blueprints.Kind.TIPLER_CYLINDER, 7, new BlockPos(7, 1, 7), Direction.NORTH, 7);
    }

    @GameTest(template = HUGE)
    public static void blueprintWormholeGenerator(GameTestHelper helper) {
        check(helper, Blueprints.Kind.WORMHOLE_GENERATOR, 3, new BlockPos(7, 1, 7), Direction.NORTH, 3);
    }

    /** 形成済みなら部品からコントローラーが見つかり、1つ欠けると見つからない（コントローラーの画面を開くのに使う）。 */
    @GameTest(template = HUGE)
    public static void partsFindTheirFormedController(GameTestHelper helper) {
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        SinguloCommands.place(helper.getLevel(), Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 3);
        BlockPos part = null;
        for (var e : Blueprints.layout(Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 3).entrySet()) {
            if (!e.getValue().isAir() && !e.getKey().equals(c)) {
                part = e.getKey();
                break;
            }
        }
        BlockPos p = part;
        helper.assertTrue(p != null && c.equals(Blueprints.controllerOf(helper.getLevel(), p)), "部品からコントローラーが見つからない");
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), c, Blueprints.Kind.DEGENERATE_COMPACTOR) == 3, "形成済みと判定されない");
        BlockPos other = null;
        for (var e : Blueprints.layout(Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 3).entrySet()) {
            if (!e.getValue().isAir() && !e.getKey().equals(c) && !e.getKey().equals(p)) {
                other = e.getKey();
                break;
            }
        }
        helper.getLevel().removeBlock(other, false);
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), c, Blueprints.Kind.DEGENERATE_COMPACTOR) == 0, "欠けても形成済みのまま");
        helper.assertTrue(Blueprints.controllerOf(helper.getLevel(), p) == null, "未完成なのに部品からコントローラーが見つかる");
        helper.succeed();
    }
}
