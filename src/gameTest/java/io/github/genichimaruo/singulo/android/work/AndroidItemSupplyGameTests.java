package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidItemSupplyGameTests {
    @GameTest(template = "empty")
    public static void sharedMachineSlotIsReservedAndCollectedThroughItsOutputFace(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        var type = MachineType.PRECISION_ASSEMBLER;
        h.setBlock(pos.below(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(pos.above(), SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        h.setBlock(pos, SinguloBlocks.MACHINES.get(type).get());
        WorkConsoleBlockEntity console = h.getBlockEntity(pos.below());
        SupplyPortBlockEntity port = h.getBlockEntity(pos.above());
        MachineBlockEntity machine = h.getBlockEntity(pos);
        port.setConsole(console.getBlockPos());
        console.addLink(port.getBlockPos());
        var ingot = new ItemStack(Items.COPPER_INGOT);
        int slot = type.outputSlot();
        machine.items().setStackInSlot(slot, ingot.copyWithCount(6));

        machine.setSide(0, SideConfig.Face.BOTTOM, SideConfig.INPUT, false);
        machine.setSide(0, SideConfig.Face.TOP, SideConfig.INPUT, false);
        h.assertTrue(ItemCount.get(console.stock(), ingot) == 0 && !console.plan(ingot, 1).ok(),
                "Two input faces exposed inaccessible stock");
        machine.setSide(0, SideConfig.Face.BOTTOM, SideConfig.OUTPUT, false);
        machine.setSide(0, SideConfig.Face.TOP, SideConfig.OUTPUT, false);
        h.assertTrue(ItemCount.get(console.stock(), ingot) == 6 && !console.plan(ingot, 7).ok(),
                "Two output faces counted the same six ingots twice");
        machine.setSide(0, SideConfig.Face.BOTTOM, SideConfig.INPUT, false);
        h.assertTrue(ItemCount.get(console.stock(), ingot) == 6 && console.plan(ingot, 6).ok(),
                "The console's input face hid the port's output face");

        var player = TestScope.player(h);
        var worker = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        BlockPos stand = h.absolutePos(pos.below().south());
        worker.makeGuard(player.getUUID(), stand, RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(stand));
        h.getLevel().addFreshEntity(worker);
        TestScope.of(h).defer(worker::discard);
        h.assertTrue(console.start(player, ingot, 6, List.of(worker.getUUID()), null) == null, "Stock delivery was refused");
        var order = console.orders().getFirst();
        h.assertTrue(order.held(console.getBlockPos(), ingot) == 0 && order.held(port.getBlockPos(), ingot) == 6,
                "The six ingots must be reserved at the output port");
        var job = console.requestJob(worker);
        var route = console.planPickup(job, stand);
        h.assertTrue(route != null && route.size() == 1 && route.getFirst().point().equals(port.getBlockPos())
                && ItemCount.get(route.getFirst().items(), ingot) == 6, "Worker was not sent to the output port for six ingots");
        h.assertTrue(console.fetchAt(worker, job, route.getFirst()) && worker.inventory().countItem(Items.COPPER_INGOT) == 6
                && machine.items().getStackInSlot(slot).isEmpty() && order.held(null, ingot) == 0,
                "Pickup did not move the physical ingots and clear their reservation");
        h.succeed();
    }
}
