package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.DroneStationBlockEntity;
import io.github.genichimaruo.singulo.genesis.GenesisCoreBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineReport;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 工場モニター（0.21.0）: そばのサーバーラックで範囲が広がり、止まっている装置とドローンの困りごとを数え、まとめは送っても変わらない。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FactoryMonitorGameTests {
    private static final BlockPos MONITOR = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void monitorSendsNearestStationsAndCountsAllTheirEnergy(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos monitor = new BlockPos(0, 1, 0);
        h.setBlock(monitor, SinguloBlocks.DESK_MONITOR.get());
        BlockPos at = h.absolutePos(monitor);
        var before = FactoryMonitor.scan(level, at);
        var sites = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < 256; i++) {
            sites.add(new BlockPos(1 + i % 7, 1 + i / 49, i / 7 % 7));
        }
        BlockPos farthest = new BlockPos(7, 7, 7);
        sites.add(farthest);
        try {
            for (BlockPos pos : sites) {
                h.setBlock(pos, SinguloBlocks.DRONE_STATION.get());
                DroneStationBlockEntity station = h.getBlockEntity(pos);
                station.energy().setEnergy(100_000);
            }
            var snapshot = FactoryMonitor.scan(level, at);
            var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                FactoryMonitor.Snapshot.CODEC.encode(buf, snapshot);
                var received = FactoryMonitor.Snapshot.CODEC.decode(buf);
                h.assertTrue(received.stored() - before.stored() == 100_000L * sites.size()
                                && received.capacity() - before.capacity() == (long) DroneStationBlockEntity.CAPACITY * sites.size(),
                        "Station energy or capacity missing from the totals");
                h.assertTrue(received.droneTotals().stations() - before.droneTotals().stations() == sites.size()
                                && received.droneTotals().trouble() - before.droneTotals().trouble() == sites.size(),
                        "Station or trouble totals were truncated");
                h.assertTrue(received.drones().size() == FactoryMonitor.MAX_DRONE_DETAILS
                                && received.drones().stream().noneMatch(d -> d.pos().equals(h.absolutePos(farthest)))
                                && received.drones().stream().anyMatch(d -> d.pos().equals(h.absolutePos(sites.getFirst()))),
                        "Monitor did not keep the nearest stations");
            } finally {
                buf.release();
            }
        } finally {
            for (BlockPos pos : sites) h.setBlock(pos, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "large", timeoutTicks = 160)
    public static void monitorTracksBrokenAndRepairedGenesisEngine(GameTestHelper h) {
        BlockPos corePos = new BlockPos(4, 1, 4);
        TestBuild.build(h, Blueprints.Kind.GENESIS_ENGINE, corePos, Direction.NORTH, 7);
        h.setBlock(MONITOR, SinguloBlocks.DESK_MONITOR.get());
        GenesisCoreBlockEntity core = h.getBlockEntity(corePos);
        var player = TestScope.player(h);
        BlockPos pylon = corePos.offset(GenesisCoreBlockEntity.PYLONS[0]);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(core.formed(), "Genesis Engine has not formed"))
                .thenExecute(() -> {
                    h.assertTrue(core.useItem(player, new ItemStack(GenesisCoreBlockEntity.primordialSeed())), "Could not kindle engine");
                    h.setBlock(pylon, Blocks.AIR);
                })
                .thenWaitUntil(() -> h.assertFalse(core.formed(), "Engine still formed after removing a pylon"))
                .thenExecute(() -> {
                    var snapshot = FactoryMonitor.scan(h.getLevel(), h.absolutePos(MONITOR));
                    h.assertTrue(snapshot.stalled().stream().anyMatch(e -> e.pos().equals(core.getBlockPos())
                            && e.state() == MachineReport.State.BROKEN), "Interrupted engine missing from monitor");
                    h.setBlock(pylon, SinguloBlocks.SIMPLE.get("genesis_pylon").get());
                })
                .thenWaitUntil(() -> h.assertTrue(core.formed(), "Engine did not reform"))
                .thenExecute(() -> {
                    h.assertTrue(core.stage() == GenesisCoreBlockEntity.KINDLING && core.report().state() == MachineReport.State.RUN,
                            "Repair did not resume kindling");
                    var snapshot = FactoryMonitor.scan(h.getLevel(), h.absolutePos(MONITOR));
                    h.assertFalse(snapshot.stalled().stream().anyMatch(e -> e.pos().equals(core.getBlockPos())),
                            "Repaired engine still listed as stalled");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void monitorListsStalledMachinesAndDrones(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(MONITOR, SinguloBlocks.DESK_MONITOR.get());
        h.setBlock(MONITOR.east(), SinguloBlocks.SERVER_RACK.get());
        h.setBlock(MONITOR.east(2), SinguloBlocks.SERVER_RACK.get());
        BlockPos kilnAt = new BlockPos(5, 1, 5);
        h.setBlock(kilnAt, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = h.getBlockEntity(kilnAt);
        kiln.items().setStackInSlot(MachineType.KILN.recipeSlot(0), new ItemStack(Items.DIRT));
        BlockPos stationAt = new BlockPos(5, 1, 1);
        h.setBlock(stationAt, SinguloBlocks.DRONE_STATION.get());
        // 専用の画面を持つ大型マルチブロック（ペンローズ炉・ジェット鍛造炉・創世炉）も数える。組みかけなら未形成
        BlockPos[] big = {new BlockPos(1, 1, 5), new BlockPos(3, 1, 5), new BlockPos(1, 1, 3)};
        h.setBlock(big[0], SinguloBlocks.CORE_CONTROLLER.get());
        h.setBlock(big[1], SinguloBlocks.JET_FORGE_CORE.get());
        h.setBlock(big[2], SinguloBlocks.GENESIS_CORE.get());
        h.succeedWhen(() -> {
            FactoryMonitor.Snapshot s = FactoryMonitor.scan(level, h.absolutePos(MONITOR));
            for (BlockPos b : big) {
                h.assertTrue(s.stalled().stream().anyMatch(e -> e.pos().equals(h.absolutePos(b)) && e.state() == MachineReport.State.BROKEN),
                        "An unformed " + h.getBlockState(b).getBlock() + " is not listed: " + s.stalled());
            }
            h.assertTrue(s.racks() == 2 && s.range() == FactoryMonitor.BASE_RANGE + 2 * FactoryMonitor.RACK_RANGE,
                    "Racks do not widen the range: " + s.racks() + " / " + s.range());
            h.assertTrue(s.machines() >= 1 && s.capacity() > 0, "The kiln is not counted");
            h.assertTrue(s.stalled().stream().anyMatch(e -> e.pos().equals(h.absolutePos(kilnAt)) && e.state() == MachineReport.State.WARN),
                    "The kiln with dirt is not listed as stalled: " + s.stalled());
            // 隣のテストの場所も範囲に入ることがあるので、自分のステーションを探す
            h.assertTrue(s.drones().stream().anyMatch(d -> d.pos().equals(h.absolutePos(stationAt)) && d.trouble() >= 1
                    && !d.problem().getString().isEmpty()), "A station without memory is not in trouble: " + s.drones());
            // 送っても同じ
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            FactoryMonitor.Snapshot.CODEC.encode(buf, s);
            FactoryMonitor.Snapshot back = FactoryMonitor.Snapshot.CODEC.decode(buf);
            h.assertTrue(back.range() == s.range() && back.stalled().size() == s.stalled().size()
                    && back.stalled().get(0).reason().getString().equals(s.stalled().get(0).reason().getString())
                    && back.drones().size() == s.drones().size() && back.produce() == s.produce(), "The snapshot changes when sent");
        });
    }
}
