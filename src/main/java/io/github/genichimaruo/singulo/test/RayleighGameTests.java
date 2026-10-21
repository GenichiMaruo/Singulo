package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.LabPower;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** レイリー散乱パネル: FE で点き、真下の日照センサーは真昼の値。研究所のものは研究所の電力で点き、壊すと何も残らない。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RayleighGameTests {
    private static final String EMPTY = "empty";

    private RayleighGameTests() {}

    /** 天井（y 5）のパネルの下の日照センサー: 点いていれば15（反転は0）。電力が切れ、ためた分も使い切ると、空の値に戻る（天井の下なので0）。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void poweredPanelMakesNoonBelow(GameTestHelper helper) {
        BlockPos panel = new BlockPos(2, 5, 2), source = new BlockPos(2, 6, 2);
        BlockPos sensor = new BlockPos(2, 1, 2), inverted = new BlockPos(3, 1, 2), panel2 = new BlockPos(3, 5, 2);
        // 屋根と壁で囲んで日を遮る（パネル以外は不透明。中に空の光が入らない）
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int y = 1; y <= 5; y++) {
                    if (y == 5 || x == 0 || x == 5 || z == 0 || z == 5) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        helper.setBlock(panel, SinguloBlocks.RAYLEIGH_PANEL.get());
        helper.setBlock(panel2, SinguloBlocks.RAYLEIGH_PANEL.get());
        helper.setBlock(source, SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        helper.setBlock(new BlockPos(3, 6, 2), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        helper.setBlock(sensor, Blocks.DAYLIGHT_DETECTOR);
        helper.setBlock(inverted, Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.INVERTED, true));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(helper.getBlockState(panel).getValue(RayleighPanelBlock.LIT), "FE を送っているのに点かない");
            helper.assertTrue(helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, helper.absolutePos(sensor)) >= 10,
                    "真下が明るくならない");
            helper.assertTrue(helper.getBlockState(sensor).getValue(DaylightDetectorBlock.POWER) == 15, "日照センサーが真昼の値にならない: "
                    + helper.getBlockState(sensor).getValue(DaylightDetectorBlock.POWER));
            helper.assertTrue(helper.getBlockState(inverted).getValue(DaylightDetectorBlock.POWER) == 0, "反転した日照センサーが0にならない");
            helper.setBlock(source, Blocks.AIR);
        });
        helper.runAtTickTime(60 + io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlockEntity.CAPACITY / io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlockEntity.USAGE + 40, () -> {
            helper.assertFalse(helper.getBlockState(panel).getValue(RayleighPanelBlock.LIT), "電力が切れても点いたまま");
            int expected = vanillaSignal(helper, sensor);
            helper.assertTrue(helper.getBlockState(sensor).getValue(DaylightDetectorBlock.POWER) == expected,
                    "消えたあと、ふつうの日照センサーの値に戻らない: " + helper.getBlockState(sensor).getValue(DaylightDetectorBlock.POWER)
                            + " (expected " + expected + ")");
            helper.succeed();
        });
    }

    /** バニラの日照センサー（反転なし）が、その場所で出す値（DaylightDetectorBlock.updateSignalStrength と同じ式）。 */
    private static int vanillaSignal(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        int i = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, helper.absolutePos(pos)) - level.getSkyDarken();
        float f = level.getSunAngle(1.0F);
        if (i > 0) {
            float f1 = f < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            f += (f1 - f) * 0.2F;
            i = Math.round(i * net.minecraft.util.Mth.cos(f));
        }
        return net.minecraft.util.Mth.clamp(i, 0, 15);
    }

    /** 研究所のパネル: 研究所の電力で点き、FE は受けず、壊しても何も落とさない。 */
    @GameTest(template = EMPTY)
    public static void labPanelFollowsFacilityPowerAndDropsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 2);
        helper.setBlock(pos, SinguloBlocks.LAB_RAYLEIGH_PANEL.get());
        BlockPos abs = helper.absolutePos(pos);
        helper.assertTrue(helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, abs, null) == null,
                "研究所のパネルが FE を受ける");
        LabPower.restore(helper.getLevel(), new BoundingBox(abs.getX() - 1, abs.getY() - 1, abs.getZ() - 1, abs.getX() + 1, abs.getY() + 1, abs.getZ() + 1));
        helper.assertTrue(helper.getBlockState(pos).getValue(RayleighPanelBlock.LIT), "研究所の電力で点かない");
        var drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(pos), helper.getLevel(), abs, null);
        helper.assertTrue(drops.isEmpty(), "研究所のパネルが何かを落とす: " + drops);
        var made = net.minecraft.world.level.block.Block.getDrops(SinguloBlocks.RAYLEIGH_PANEL.get().defaultBlockState(), helper.getLevel(), abs, null,
                null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        helper.assertTrue(made.size() == 1 && made.get(0).is(SinguloBlocks.RAYLEIGH_PANEL.get().asItem()), "作ったパネルが自分を落とさない");
        helper.succeed();
    }
}
