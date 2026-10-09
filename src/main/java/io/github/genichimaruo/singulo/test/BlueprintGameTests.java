package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.command.SinguloCommands;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
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
        int got = Blueprints.formedSize(level, c, kind);
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
        check(helper, Blueprints.Kind.DEGENERATE_COMPACTOR, 5, new BlockPos(5, 2, 5), Direction.WEST, 5);
    }

    @GameTest(template = HUGE)
    public static void blueprintCavity(GameTestHelper helper) {
        check(helper, Blueprints.Kind.CASIMIR_CAVITY, 5, new BlockPos(2, 3, 7), Direction.EAST, 5);
    }

    @GameTest(template = HUGE)
    public static void blueprintFurnace(GameTestHelper helper) {
        check(helper, Blueprints.Kind.DEGENERATE_FURNACE, 7, new BlockPos(4, 2, 2), Direction.NORTH.getOpposite(), 7);
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
        check(helper, Blueprints.Kind.TIPLER_CYLINDER, 9, new BlockPos(7, 1, 7), Direction.NORTH, 9);
    }

    @GameTest(template = HUGE)
    public static void blueprintWormholeGenerator(GameTestHelper helper) {
        check(helper, Blueprints.Kind.WORMHOLE_GENERATOR, 5, new BlockPos(7, 1, 7), Direction.NORTH, 5);
    }

    /** 形成済みなら部品からコントローラーが見つかり、1つ欠けると見つからない（コントローラーの画面を開くのに使う）。 */
    @GameTest(template = HUGE)
    public static void partsFindTheirFormedController(GameTestHelper helper) {
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        SinguloCommands.place(helper.getLevel(), Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 5);
        BlockPos part = null;
        for (var e : Blueprints.layout(Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 5).entrySet()) {
            if (!e.getValue().isAir() && !e.getKey().equals(c)) {
                part = e.getKey();
                break;
            }
        }
        BlockPos p = part;
        helper.assertTrue(p != null && c.equals(Blueprints.controllerOf(helper.getLevel(), p)), "部品からコントローラーが見つからない");
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), c, Blueprints.Kind.DEGENERATE_COMPACTOR) == 5, "形成済みと判定されない");
        BlockPos other = null;
        for (var e : Blueprints.layout(Blueprints.Kind.DEGENERATE_COMPACTOR, c, Direction.WEST, 5).entrySet()) {
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

    /** 搬入出ポートは外装板の位置に置け、右クリックでコントローラーが見つかる。枠の位置に置くと形成されない。 */
    @GameTest(template = HUGE)
    public static void multiblockPortOnPanelsOnly(GameTestHelper helper) {
        BlockPos rel = new BlockPos(7, 2, 3);
        TestBuild.build(helper, Blueprints.Kind.CASIMIR_CAVITY, rel, Direction.SOUTH, 5);
        BlockPos port = TestBuild.port(helper, Blueprints.Kind.CASIMIR_CAVITY, rel, Direction.SOUTH, 5, 1, 3, 0);
        BlockPos c = helper.absolutePos(rel);
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), c, Blueprints.Kind.CASIMIR_CAVITY) == 5, "外装板の位置のポートで形成されない");
        helper.assertTrue(c.equals(Blueprints.controllerOf(helper.getLevel(), port)), "ポートからコントローラーが見つからない");
        // 枠（辺）の位置
        BlockPos frame = TestBuild.at(helper, Blueprints.Kind.CASIMIR_CAVITY, rel, Direction.SOUTH, 5, 0, 2, 0);
        helper.getLevel().setBlockAndUpdate(frame, io.github.genichimaruo.singulo.registry.SinguloBlocks.MULTIBLOCK_PORT.get().defaultBlockState());
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), c, Blueprints.Kind.CASIMIR_CAVITY) == 0, "枠にポートを置いても形成される");
        helper.succeed();
    }

    /** 冷却塔は高さ7〜15のどれでも形成でき、高さがわかる。 */
    @GameTest(template = HUGE)
    public static void coolingTowerHeights(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        TestBuild.build(helper, Blueprints.Kind.COOLING_TOWER, rel, Direction.SOUTH, 7);
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), helper.absolutePos(rel), Blueprints.Kind.COOLING_TOWER) == 7, "高さ7で形成されない");
        BlockPos rel2 = new BlockPos(10, 1, 2);
        TestBuild.build(helper, Blueprints.Kind.COOLING_TOWER, rel2, Direction.SOUTH, 13);
        helper.assertTrue(Blueprints.formedSize(helper.getLevel(), helper.absolutePos(rel2), Blueprints.Kind.COOLING_TOWER) == 13, "高さ13で形成されない");
        helper.succeed();
    }
}
