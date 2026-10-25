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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60)
    public static void receiverPowersNearestPanelsFirst(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60)
    public static void panelsStopWhenReceiverIsRemoved(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = "tall", batch = "impact_generator", timeoutTicks = 100)
    public static void impactGeneratorAbsorbsFallAndPowersPanels(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    /** 受電器 A（x=0）・パネル6枚（x=1..6）・受電器 B（x=7）の一列を置く。 */
    private static GravityReceiverBlockEntity[] twoReceivers(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        helper.setBlock(new BlockPos(7, 1, 1), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        for (int x = 1; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), SinguloBlocks.LOW_GRAVITY_PANEL.get());
        }
        return new GravityReceiverBlockEntity[] {io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, new BlockPos(0, 1, 1)), io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, new BlockPos(7, 1, 1))};
    }

    /** 受電器が複数あっても、どれか1台だけで集まり全体を働かせられる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60)
    public static void oneOfSeveralReceiversPowersAllPanels(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GravityReceiverBlockEntity[] r = twoReceivers(helper);
        int per = GravityReceiverBlockEntity.perPanel();
        helper.onEachTick(() -> r[1].energy().receiveLong(6L * per, false));
        helper.runAtTickTime(30, () -> {
            for (int x = 1; x <= 6; x++) {
                helper.assertTrue(powered(helper, new BlockPos(x, 1, 1)), "片方の受電器だけでは働かない: x=" + x);
            }
            helper.assertTrue(r[1].supplyingPanels() == 6 && r[0].supplyingPanels() == 0,
                    "払った枚数が違う: A=" + r[0].supplyingPanels() + " B=" + r[1].supplyingPanels());
            helper.assertTrue(r[0].panels() == 6 && r[0].activePanels() == 6, "A の表示が違う: " + r[0].activePanels() + "/" + r[0].panels());
            helper.succeed();
        });
    }

    /**
     * どちらにも受け持ちの分の電力があれば、パネルごとに近いほうの受電器が払う（受電器は集まり全体の1 tick 分までしか受け取らないので、
     * 半分ずつ届ける）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void nearestReceiverPaysForEachPanel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GravityReceiverBlockEntity[] r = twoReceivers(helper);
        int per = GravityReceiverBlockEntity.perPanel();
        helper.onEachTick(() -> {
            r[0].energy().receiveLong(3L * per, false);
            r[1].energy().receiveLong(3L * per, false);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(r[0].supplyingPanels() == 3 && r[1].supplyingPanels() == 3,
                    "近いほうが払っていない: A=" + r[0].supplyingPanels() + " B=" + r[1].supplyingPanels());
            helper.succeed();
        });
    }

    /** 近い受電器の電力が弱いときは、払えない分を遠い受電器が払う。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 60)
    public static void fartherReceiverCoversWeakSupply(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        GravityReceiverBlockEntity[] r = twoReceivers(helper);
        int per = GravityReceiverBlockEntity.perPanel();
        helper.onEachTick(() -> {
            r[0].energy().receiveLong(per, false);
            r[1].energy().receiveLong(6L * per, false);
        });
        helper.runAtTickTime(30, () -> {
            for (int x = 1; x <= 6; x++) {
                helper.assertTrue(powered(helper, new BlockPos(x, 1, 1)), "遠い受電器が補わない: x=" + x);
            }
            helper.assertTrue(r[0].supplyingPanels() == 1 && r[1].supplyingPanels() == 5,
                    "払った枚数が違う: A=" + r[0].supplyingPanels() + " B=" + r[1].supplyingPanels());
            helper.succeed();
        });
    }

    /** 衝撃発電パネルは、ためた電力をケーブルへ送り出す（ケーブルから受け取りはしない）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 100)
    public static void impactGeneratorFeedsCables(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos gen = new BlockPos(1, 1, 3);
        helper.setBlock(gen, SinguloBlocks.IMPACT_GENERATOR.get());
        io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, gen);
        be.absorb(5);
        for (int z = 2; z >= 1; z--) {
            helper.setBlock(new BlockPos(1, 1, z), SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        }
        BlockPos kilnPos = new BlockPos(1, 1, 0);
        helper.setBlock(kilnPos, SinguloBlocks.MACHINES.get(io.github.genichimaruo.singulo.machine.MachineType.KILN).get());
        io.github.genichimaruo.singulo.machine.MachineBlockEntity kiln = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, kilnPos);
        var cap = io.github.genichimaruo.singulo.compat.Capabilities.get(helper.getLevel(), io.github.genichimaruo.singulo.compat.Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(gen), Direction.NORTH);
        helper.assertTrue(cap != null && cap.canExtract() && !cap.canReceive(), "ケーブル用の口が取り出し専用でない");
        helper.assertTrue(cap.receiveEnergy(1_000, false) == 0, "ケーブルから受け取ってしまう");
        helper.succeedWhen(() -> helper.assertTrue(kiln.energy().getEnergyStored() > 0 && be.stored() < 5_000,
                "ケーブルの先へ送られない: kiln=" + kiln.energy().getEnergyStored() + " gen=" + be.stored()));
    }

    /** 衝撃発電パネルが外へ出せるのは、何面につないでも、取り出される分と合わせて1 tick に PUSH_PER_TICK まで。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 40)
    public static void impactGeneratorOutputIsSharedAcrossFaces(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos gen = new BlockPos(3, 3, 3);
        helper.setBlock(gen, SinguloBlocks.IMPACT_GENERATOR.get());
        io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, gen);
        be.absorb(10);
        java.util.List<io.github.genichimaruo.singulo.machine.MachineBlockEntity> sinks = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) {
            helper.setBlock(gen.relative(d), SinguloBlocks.MACHINES.get(io.github.genichimaruo.singulo.machine.MachineType.KILN).get());
            sinks.add(io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, gen.relative(d)));
        }
        long before = be.stored();
        GravityReceiverBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(gen), helper.getBlockState(gen), be);
        long sent = sinks.stream().mapToLong(s -> s.energy().getEnergyStored()).sum();
        helper.assertTrue(sent == io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlockEntity.PUSH_PER_TICK,
                "6面で1 tick に出した量が上限と違う: " + sent);
        helper.assertTrue(before - be.stored() == sent, "出した量と減った量が合わない");
        helper.assertTrue(be.output().extractEnergy(1_000, false) == 0, "同じ tick に、上限を超えて取り出せる");
        // そのあとも自分の tick で送り続けるが、1 tick あたりは上限まで（3 tick で上限の3倍まで）
        long afterFirst = be.stored();
        helper.runAfterDelay(3, () -> {
            long next = afterFirst - be.stored();
            helper.assertTrue(next > 0 && next <= 3L * io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlockEntity.PUSH_PER_TICK,
                    "続く3 tick に出した量が上限と違う: " + next);
            helper.succeed();
        });
    }

    /** 同じ種類のパネルどうしは見た目がつながり、ちがう種類とはつながらない。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void panelsJoinOnlyTheSameKind(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 80)
    public static void standingOnPoweredPanelChangesGravityUntilLanding(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
