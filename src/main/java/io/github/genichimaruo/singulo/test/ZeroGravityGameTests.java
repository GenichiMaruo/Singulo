package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.gravity.GravityReceiverBlockEntity;
import io.github.genichimaruo.singulo.gravity.ZeroGravity;
import io.github.genichimaruo.singulo.gravity.ZeroGravityControllerBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 無重力ブロック: 受電器から電力を受けると上の空間が無重力になり、コントロールボックスで高さと電力が変わる。大きな集まりも配れる。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ZeroGravityGameTests {
    private static final String EMPTY = "empty";

    private ZeroGravityGameTests() {}

    private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        z.setPersistenceRequired();
        return z;
    }

    /** 上の5ブロックの中にいる生き物は重力が0、その外は元のまま。電力が切れると戻る。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void zeroGravityBlockLiftsWeightAbove(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos block = new BlockPos(2, 1, 2), receiver = new BlockPos(3, 1, 2), source = new BlockPos(4, 1, 2);
        helper.setBlock(block, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(source, SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        Zombie inside = zombie(helper, new BlockPos(2, 4, 2));
        Zombie outside = zombie(helper, new BlockPos(0, 2, 0));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(ZeroGravity.weightless(inside), "無重力の空間にいるのに重力がある");
            helper.assertFalse(ZeroGravity.weightless(outside), "外にいるのに無重力になった");
            helper.assertTrue(helper.getBlockState(block).getValue(io.github.genichimaruo.singulo.gravity.GravityPanelBlock.POWERED),
                    "無重力ブロックに電力が届かない");
            helper.setBlock(source, Blocks.AIR);
        });
        helper.runAtTickTime(100, () -> {
            helper.assertFalse(ZeroGravity.weightless(inside), "電力が切れても無重力のまま");
            inside.discard();
            outside.discard();
            helper.succeed();
        });
    }

    /** コントロールボックスで高さを8にすると、7ブロック上も無重力になり、電力は1つあたり costPerBlock(8)。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void controllerRaisesHeightAtExponentialCost(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(2, 1, 2), controller = new BlockPos(1, 1, 3);
        BlockPos receiver = new BlockPos(3, 1, 2), source = new BlockPos(4, 1, 2);
        helper.setBlock(a, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(b, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        helper.setBlock(controller, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(source, SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        ZeroGravityControllerBlockEntity box = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, controller);
        box.setHeight(8);
        Zombie high = zombie(helper, new BlockPos(1, 8, 2));
        helper.assertTrue(ZeroGravity.costPerBlock(9) > ZeroGravity.costPerBlock(8) * 3 / 2, "高さ1ブロックで電力が1.5倍以上にならない");
        helper.assertTrue(ZeroGravity.costPerBlock(10_000) == Long.MAX_VALUE, "とても高いときに電力が飽和しない");
        helper.runAtTickTime(40, () -> {
            GravityReceiverBlockEntity r = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, receiver);
            helper.assertTrue(r.demand() == 2 * ZeroGravity.costPerBlock(8), "集まりの電力が高さ8の2つ分でない: " + r.demand());
            helper.assertTrue(ZeroGravity.weightless(high), "高さ8の中（7ブロック上）が無重力にならない");
            high.discard();
            helper.succeed();
        });
    }

    /** 4,096枚（以前の上限）を超える大きな集まりでも、すべてのパネルに電力が届く。17×17×17 = 4,913 枚（読み込まれた範囲に収まる立方体）。 */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "large_panels")
    public static void largePanelGroupIsFullyPowered(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
        int side = 17;
        var low = SinguloBlocks.LOW_GRAVITY_PANEL.get().defaultBlockState();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                for (int z = 0; z < side; z++) {
                    level.setBlock(origin.offset(x, y, z), low, 2);
                }
            }
        }
        BlockPos receiver = origin.below();
        level.setBlockAndUpdate(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get().defaultBlockState());
        level.setBlockAndUpdate(receiver.north(), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState());
        helper.runAtTickTime(60, () -> {
            GravityReceiverBlockEntity r = (GravityReceiverBlockEntity) level.getBlockEntity(receiver);
            int total = side * side * side;
            helper.assertTrue(r != null && r.panels() == total, "集まりの枚数が数えられない: " + (r == null ? -1 : r.panels()));
            helper.assertTrue(r.activePanels() == total, "すべてのパネルが働かない: " + r.activePanels());
            helper.assertTrue(level.getBlockState(origin.offset(side - 1, side - 1, side - 1))
                    .getValue(io.github.genichimaruo.singulo.gravity.GravityPanelBlock.POWERED), "いちばん遠いパネルに届かない");
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < side; y++) {
                    for (int z = 0; z < side; z++) {
                        level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            level.setBlockAndUpdate(receiver, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(receiver.north(), Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }
}
