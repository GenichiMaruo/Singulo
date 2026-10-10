package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.nature.DeepSeaCollectorBlockEntity;
import io.github.genichimaruo.singulo.nature.KeraunosTowerBlockEntity;
import io.github.genichimaruo.singulo.nature.MeteoriteCrustBlock;
import io.github.genichimaruo.singulo.nature.VoidCollectorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 隕石クレーターの自然物、雷ガラス、深海・虚空の収集装置。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class NatureGameTests {
    private static final String EMPTY = "empty";

    private NatureGameTests() {}

    /** ケラウノス放電塔: 電力をためて撃つと、砂の上の避雷針へ雷が飛び、砂が雷ガラス塊になる。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void keraunosTowerTurnsSandIntoFulgurite(GameTestHelper helper) {
        BlockPos tower = new BlockPos(1, 1, 1);
        BlockPos sand = new BlockPos(5, 1, 5);
        helper.setBlock(tower, SinguloBlocks.KERAUNOS_TOWER.get());
        helper.setBlock(sand.below(), Blocks.STONE);
        helper.setBlock(sand, Blocks.SAND);
        helper.setBlock(sand.above(), Blocks.LIGHTNING_ROD);
        KeraunosTowerBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, tower);
        be.energy().setEnergy(be.energy().getMaxEnergyStored());
        helper.assertTrue(be.fire(null), "撃てない");
        helper.assertFalse(be.fire(null), "ためている間にもう一度撃てる");
        helper.runAtTickTime(KeraunosTowerBlockEntity.CHARGE_TICKS + 10, () -> {
            helper.assertBlockPresent(SinguloBlocks.FULGURITE_BLOCK.get(), sand);
            helper.assertTrue(be.energy().getEnergyStored() == be.energy().getMaxEnergyStored() - KeraunosTowerBlockEntity.COST,
                    "電力の使い方が違う");
            helper.succeed();
        });
    }

    /** 避雷針がないと撃たない（電力も使わない）。 */
    @GameTest(template = EMPTY)
    public static void keraunosTowerNeedsALightningRod(GameTestHelper helper) {
        BlockPos tower = new BlockPos(1, 1, 1);
        helper.setBlock(tower, SinguloBlocks.KERAUNOS_TOWER.get());
        KeraunosTowerBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, tower);
        be.energy().setEnergy(be.energy().getMaxEnergyStored());
        helper.assertFalse(be.fire(null), "避雷針がないのに撃った");
        helper.assertTrue(be.energy().getEnergyStored() == be.energy().getMaxEnergyStored(), "撃たないのに電力を使った");
        helper.succeed();
    }

    /** 自然の雷でも、砂の上の避雷針に落ちれば雷ガラス塊ができる。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void naturalLightningOnRodMakesFulgurite(GameTestHelper helper) {
        BlockPos sand = new BlockPos(3, 1, 3);
        helper.setBlock(sand.below(), Blocks.STONE);
        helper.setBlock(sand, Blocks.SAND);
        helper.setBlock(sand.above(), Blocks.LIGHTNING_ROD);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        BlockPos at = helper.absolutePos(sand.above(2));
        bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(bolt);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(SinguloBlocks.FULGURITE_BLOCK.get(), sand);
            helper.succeed();
        });
    }

    /** クレーターに最初からある溶融殻だけが、隣に星屑の結晶を育てる。 */
    @GameTest(template = EMPTY)
    public static void naturalCrustGrowsStardust(GameTestHelper helper) {
        BlockPos natural = new BlockPos(2, 2, 2);
        BlockPos placed = new BlockPos(5, 2, 5);
        helper.setBlock(natural, SinguloBlocks.METEORITE_CRUST.get().defaultBlockState().setValue(MeteoriteCrustBlock.NATURAL, true));
        helper.setBlock(placed, SinguloBlocks.METEORITE_CRUST.get());
        RandomSource rnd = RandomSource.create(1);
        var level = helper.getLevel();
        for (int i = 0; i < 200; i++) {
            BlockPos a = helper.absolutePos(natural);
            level.getBlockState(a).randomTick(level, a, rnd);
            BlockPos b = helper.absolutePos(placed);
            if (level.getBlockState(b).isRandomlyTicking()) {
                level.getBlockState(b).randomTick(level, b, rnd);
            }
        }
        int grown = 0;
        int strays = 0;
        for (Direction d : Direction.values()) {
            if (helper.getBlockState(natural.relative(d)).is(SinguloBlocks.STARDUST_CLUSTER.get())) {
                grown++;
            }
            if (helper.getBlockState(placed.relative(d)).is(SinguloBlocks.STARDUST_CLUSTER.get())) {
                strays++;
            }
        }
        helper.assertTrue(grown > 0, "クレーターの殻に結晶が育たない");
        helper.assertTrue(strays == 0, "置き直した殻にまで結晶が育つ");
        helper.succeed();
    }

    /** 深海圧力収集器は水の深さを数え、浅い所や陸では動かない。虚空捕集器はジ・エンドの外では動かない。 */
    @GameTest(template = EMPTY)
    public static void collectorsNeedTheirPlace(GameTestHelper helper) {
        BlockPos sea = new BlockPos(2, 1, 2);
        helper.setBlock(sea, SinguloBlocks.DEEP_SEA_COLLECTOR.get());
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.WATER);
        }
        var level = helper.getLevel();
        helper.assertTrue(DeepSeaCollectorBlockEntity.depth(level, helper.absolutePos(sea)) == 5, "水の深さの数え方が違う");
        DeepSeaCollectorBlockEntity collector = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, sea);
        helper.assertTrue(collector.rate() == 0 && DeepSeaCollectorBlockEntity.depth(level, helper.absolutePos(sea)) < DeepSeaCollectorBlockEntity.MIN_DEPTH,
                "浅いのに動く");
        BlockPos voidPos = new BlockPos(5, 3, 5);
        helper.setBlock(voidPos, SinguloBlocks.VOID_COLLECTOR.get());
        helper.assertFalse(VoidCollectorBlockEntity.overVoid(level, helper.absolutePos(voidPos)), "ジ・エンドの外で虚空とみなした");
        helper.succeed();
    }
}
