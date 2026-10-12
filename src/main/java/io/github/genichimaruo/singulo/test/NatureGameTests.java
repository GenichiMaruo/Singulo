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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        KeraunosTowerBlockEntity be = helper.getBlockEntity(tower);
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
        KeraunosTowerBlockEntity be = helper.getBlockEntity(tower);
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
        DeepSeaCollectorBlockEntity collector = helper.getBlockEntity(sea);
        helper.assertTrue(collector.rate() == 0 && DeepSeaCollectorBlockEntity.depth(level, helper.absolutePos(sea)) < DeepSeaCollectorBlockEntity.MIN_DEPTH,
                "浅いのに動く");
        BlockPos voidPos = new BlockPos(5, 3, 5);
        helper.setBlock(voidPos, SinguloBlocks.VOID_COLLECTOR.get());
        helper.assertFalse(VoidCollectorBlockEntity.overVoid(level, helper.absolutePos(voidPos)), "ジ・エンドの外で虚空とみなした");
        helper.succeed();
    }

    /**
     * 隕石クレーター: 地面の高さを読んで描き込む。すり鉢は地面より低く、中心の隕石には核が1つ。描き込む範囲の外の地面は
     * 元のまま（四角く削らない）。核を掘り出すと、コンパスはもう指さない。
     */
    // テストの場所から遠く離れた所に描く（ほかのテストの場所を荒らさないように）
    @GameTest(template = EMPTY, batch = "meteor_crater", timeoutTicks = 100)
    public static void craterIsCarvedIntoTheGround(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 4000);
        var heightmap = net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;
        // テストの世界は薄い平地なので、厚さ10ブロックの地面（石の上に土と草）を作る
        int ground = base.getY() + 12;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                for (int y = base.getY(); y <= ground; y++) {
                    level.setBlock(p.set(base.getX() + x, y, base.getZ() + z),
                            (y == ground ? Blocks.GRASS_BLOCK : y >= ground - 2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
                }
            }
        }
        BlockPos center = new BlockPos(base.getX(), ground, base.getZ());
        int corner = level.getHeight(heightmap, center.getX() + 14, center.getZ() + 14) - 1;
        helper.assertTrue(corner == ground, "地面を作れていない: " + corner + " / " + ground);
        io.github.genichimaruo.singulo.nature.MeteorCraterPiece piece = new io.github.genichimaruo.singulo.nature.MeteorCraterPiece(center, 1234L);
        var box = piece.getBoundingBox();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                var chunkBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx * 16, level.getMinBuildHeight(), cz * 16,
                        cx * 16 + 15, level.getMaxBuildHeight(), cz * 16 + 15);
                piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random, chunkBox,
                        new net.minecraft.world.level.ChunkPos(cx, cz), center);
            }
        }
        BlockPos core = piece.corePos();
        helper.assertTrue(level.getBlockState(core).is(SinguloBlocks.METEORITE_CORE.get()), "隕石の芯に核がない: " + core);
        // すり鉢（縁の内側。隕石とそのかけらの上は数えない）は元の地面より低い
        int lower = 0;
        int counted = 0;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            double d = piece.radius(a) * 0.75;
            int x = (int) Math.floor(center.getX() + 0.5 + Math.cos(a) * d);
            int z = (int) Math.floor(center.getZ() + 0.5 + Math.sin(a) * d);
            int top = level.getHeight(heightmap, x, z) - 1;
            var state = level.getBlockState(new BlockPos(x, top, z));
            if (state.is(SinguloBlocks.METEORITE_CRUST.get()) || state.is(SinguloBlocks.METEORIC_IRON_CHUNK.get())
                    || state.is(SinguloBlocks.STARDUST_CLUSTER.get()) || state.is(SinguloBlocks.METEORITE_CORE.get())) {
                continue;
            }
            counted++;
            if (top < ground) {
                lower++;
            }
        }
        helper.assertTrue(counted >= 8 && lower == counted, "すり鉢が地面より低くない: " + lower + " / " + counted);
        helper.assertTrue(level.getHeight(heightmap, center.getX() + 14, center.getZ() + 14) - 1 == corner, "描き込む範囲の外の地面が変わった");
        helper.assertFalse(io.github.genichimaruo.singulo.nature.MeteorCores.mined(level, core), "掘る前から掘り出し済みになっている");
        level.destroyBlock(core, false);
        helper.assertTrue(io.github.genichimaruo.singulo.nature.MeteorCores.mined(level, core), "核を掘り出しても覚えていない");
        level.setBlock(core, SinguloBlocks.METEORITE_CORE.get().defaultBlockState(), 3);
        helper.assertTrue(io.github.genichimaruo.singulo.nature.MeteorCores.mined(level, core), "置き直した核をまた指す");
        helper.succeed();
    }
}
