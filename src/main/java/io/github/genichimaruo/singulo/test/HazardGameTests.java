package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.hazard.StrangeletBlockEntity;
import io.github.genichimaruo.singulo.item.NeutrinoScannerItem;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ストレンジレット・磁気瓶・ニュートリノ・スキャナー・水素漏れの確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class HazardGameTests {
    private static final String EMPTY = "empty";

    private HazardGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void strangeletConvertsBlocksAndBottleContainsIt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 3, 5))) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.setBlock(pos, SinguloBlocks.STRANGELET.get());
        StrangeletBlockEntity s = helper.getBlockEntity(pos);
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(s.converted() >= 1, "ストレンジ物質に変わらない: " + s.converted());
            helper.assertBlockPresent(SinguloBlocks.STRANGELET.get(), pos);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            try {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                ItemStack bottle = new ItemStack(SinguloItems.MAGNETIC_BOTTLE.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
                BlockPos abs = helper.absolutePos(pos);
                bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
                helper.assertBlockNotPresent(SinguloBlocks.STRANGELET.get(), pos);
                helper.assertTrue(bottle.getDamageValue() == 1, "磁気瓶の回数が減らない");
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(2));
                helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(SinguloBlocks.SIMPLE.get("strange_matter").get().asItem())),
                        "ストレンジ物質を回収できない");
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /** スキャナーは感度の段階に応じて鉱石を映す: 段階1は鉄まで、段階2でダイヤモンド、段階3で古代の残骸。遺構はいつでも映る。 */
    @GameTest(template = EMPTY)
    public static void neutrinoScannerFindsOresAndRuins(GameTestHelper helper) {
        BlockPos iron = new BlockPos(1, 1, 1);
        BlockPos diamond = new BlockPos(1, 1, 5);
        BlockPos debris = new BlockPos(5, 1, 1);
        BlockPos ruin = new BlockPos(5, 1, 5);
        helper.setBlock(iron, Blocks.IRON_ORE);
        helper.setBlock(diamond, Blocks.DIAMOND_ORE);
        helper.setBlock(debris, Blocks.ANCIENT_DEBRIS);
        helper.setBlock(ruin, SinguloBlocks.RUIN_PANEL.get());
        BlockPos c = helper.absolutePos(new BlockPos(3, 1, 3));
        NeutrinoScannerItem.Result t1 = NeutrinoScannerItem.scan(helper.getLevel(), c, 1);
        NeutrinoScannerItem.Result t2 = NeutrinoScannerItem.scan(helper.getLevel(), c, 2);
        NeutrinoScannerItem.Result t3 = NeutrinoScannerItem.scan(helper.getLevel(), c, 3);
        helper.assertTrue(t1.ores().contains(helper.absolutePos(iron)), "段階1で鉄鉱石が映らない");
        helper.assertFalse(t1.ores().contains(helper.absolutePos(diamond)), "段階1でダイヤモンドが映る");
        helper.assertTrue(t1.hidden() == 2, "段階1で隠れた鉱石の数が違う: " + t1.hidden());
        helper.assertTrue(t2.ores().contains(helper.absolutePos(diamond)) && !t2.ores().contains(helper.absolutePos(debris)),
                "段階2の映り方が違う");
        helper.assertTrue(t3.ores().contains(helper.absolutePos(debris)) && t3.hidden() == 0, "段階3で古代の残骸が映らない");
        helper.assertTrue(t1.ruins().contains(helper.absolutePos(ruin)), "遺構のブロックが映らない");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void hydrogenLeakNearFireExplodes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity m = helper.getBlockEntity(pos);
        // 出力の水素タンクに直接入れる（搬入では出力タンクに入らない）
        m.forceOutputTankForTest(new FluidStack(SinguloFluids.get("hydrogen"), 2000));
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.CAMPFIRE);
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.DIRT);
        helper.destroyBlock(pos);
        helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(3, 1, 4));
        helper.succeed();
    }
}
