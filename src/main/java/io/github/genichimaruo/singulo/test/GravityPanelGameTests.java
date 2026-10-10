package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.gravity.GravityEffects;
import io.github.genichimaruo.singulo.gravity.GravityPanelBlock;
import io.github.genichimaruo.singulo.gravity.GravityReceiverBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 重力パネル: 受電器からつながったパネルへの配電（近い順）、見た目の接続、乗ったときの重力。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class GravityPanelGameTests {
    private static final String EMPTY = "empty";

    private GravityPanelGameTests() {}

    private static boolean powered(GameTestHelper helper, BlockPos pos) {
        BlockState s = helper.getBlockState(pos);
        return s.getBlock() instanceof GravityPanelBlock && s.getValue(GravityPanelBlock.POWERED);
    }

    /** 電力が足りないと、受電器に近い順に払える枚数だけ働く。種類のちがうパネルも伝う。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void receiverPowersNearestPanelsFirst(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(1, 1, 1);
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        // 受電器から東へ5枚（3枚目だけ高重力）
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), x == 4 ? SinguloBlocks.HIGH_GRAVITY_PANEL.get() : SinguloBlocks.LOW_GRAVITY_PANEL.get());
        }
        GravityReceiverBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, receiver);
        int per = GravityReceiverBlockEntity.perPanel();
        helper.onEachTick(() -> be.energy().receiveLong(3L * per, false));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.panels() == 5, "つながったパネルの数が違う: " + be.panels());
            for (int x = 2; x <= 6; x++) {
                boolean want = x <= 4;
                helper.assertTrue(powered(helper, new BlockPos(x, 1, 1)) == want, "近い順に働いていない: x=" + x);
            }
            helper.succeed();
        });
    }

    /** 受電器が壊されると、パネルは止まる。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void panelsStopWhenReceiverIsRemoved(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(1, 1, 1);
        BlockPos panel = new BlockPos(2, 1, 1);
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(panel, SinguloBlocks.LOW_GRAVITY_PANEL.get());
        GravityReceiverBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, receiver);
        helper.onEachTick(() -> {
            if (!be.isRemoved()) {
                be.energy().receiveLong(1_000, false);
            }
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(powered(helper, panel), "電力があるのに働かない");
            helper.setBlock(receiver, Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                helper.assertFalse(powered(helper, panel), "受電器を壊しても止まらない");
                helper.succeed();
            });
        });
    }

    /** 衝撃発電パネル: 上に落ちてもダメージを受けず、落ちた高さに応じてためた電力で、つながった重力パネルを働かせる。 */
    // 引き寄せる物（マイクロブラックホールなど）のテストと同時に動くと落下距離が消えるので、別の組で動かす
    @GameTest(template = "tall", batch = "impact_generator", timeoutTicks = 100)
    public static void impactGeneratorAbsorbsFallAndPowersPanels(GameTestHelper helper) {
        BlockPos gen = new BlockPos(2, 1, 2);
        helper.setBlock(gen, SinguloBlocks.IMPACT_GENERATOR.get());
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.LOW_GRAVITY_PANEL.get());
        helper.setBlock(new BlockPos(4, 1, 2), SinguloBlocks.LOW_GRAVITY_PANEL.get());
        io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, gen);
        // 防具立て（AI がなく、まっすぐ落ちる）
        var pig = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 10, 2));
        float health = pig.getHealth();
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(pig.getHealth() == health, "落下ダメージを受けた: " + pig.getHealth());
            helper.assertTrue(be.stored() > 0 || be.activePanels() > 0, "落ちても発電しない");
            helper.assertTrue(powered(helper, new BlockPos(3, 1, 2)) && powered(helper, new BlockPos(4, 1, 2)),
                    "ためた電力で重力パネルが働かない");
            pig.discard();
            helper.succeed();
        });
    }

    /** 同じ種類のパネルどうしは見た目がつながり、ちがう種類とはつながらない。 */
    @GameTest(template = EMPTY)
    public static void panelsJoinOnlyTheSameKind(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.LOW_GRAVITY_PANEL.get());
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.LOW_GRAVITY_PANEL.get());
        helper.setBlock(new BlockPos(2, 1, 3), SinguloBlocks.HIGH_GRAVITY_PANEL.get());
        BlockState a = helper.getBlockState(new BlockPos(2, 1, 2));
        helper.assertTrue(a.getValue(GravityPanelBlock.CONNECTIONS.get(Direction.EAST)), "同じ種類とつながらない");
        helper.assertFalse(a.getValue(GravityPanelBlock.CONNECTIONS.get(Direction.SOUTH)), "ちがう種類とつながる");
        helper.assertFalse(a.getValue(GravityPanelBlock.CONNECTIONS.get(Direction.WEST)), "何もない方とつながる");
        helper.succeed();
    }

    /** 働いている低重力パネルに乗ると重力が変わり、パネル以外の地面に降りると元に戻る。 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void standingOnPoweredPanelChangesGravityUntilLanding(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(1, 1, 2);
        BlockPos panel = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(panel, SinguloBlocks.LOW_GRAVITY_PANEL.get());
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
        GravityReceiverBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, receiver);
        helper.onEachTick(() -> be.energy().receiveLong(1_000, false));
        // 防具立て（AI を持たず、重力で動く生き物）
        var stand = helper.spawn(EntityType.ARMOR_STAND, panel.above());
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(GravityEffects.current(stand) == GravityPanelBlock.Kind.LOW, "低重力にならない");
            BlockPos stone = helper.absolutePos(new BlockPos(5, 2, 2));
            stand.teleportTo(stone.getX() + 0.5, stone.getY(), stone.getZ() + 0.5);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(GravityEffects.current(stand) == null, "パネル以外に降りても元に戻らない");
                stand.discard();
                helper.succeed();
            });
        });
    }
}
