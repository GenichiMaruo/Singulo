package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineReport;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 工場モニター（0.21.0）: そばのサーバーラックで範囲が広がり、止まっている装置とドローンの困りごとを数え、まとめは送っても変わらない。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FactoryMonitorGameTests {
    private static final BlockPos MONITOR = new BlockPos(1, 1, 1);

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
