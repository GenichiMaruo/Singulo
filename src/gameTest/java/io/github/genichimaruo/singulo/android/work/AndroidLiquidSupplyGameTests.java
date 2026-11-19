package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidLiquidSupplyGameTests {
    private static SupplyPortBlockEntity supply(GameTestHelper h, BlockPos pos, WorkConsoleBlockEntity console) {
        h.setBlock(pos, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        SupplyPortBlockEntity port = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        port.setConsole(console.getBlockPos());
        console.addLink(port.getBlockPos());
        return port;
    }

    private static MachineBlockEntity tank(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        machine.forceOutputTankForTest(new FluidStack(Fluids.LAVA, 1000));
        return machine;
    }

    @GameTest(template = "empty")
    public static void sharedTankFacesAndReservations(GameTestHelper h) {
        BlockPos shared = new BlockPos(1, 2, 1), other = new BlockPos(4, 2, 1);
        h.setBlock(shared.below(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, shared.below());
        var secondPoint = supply(h, shared.above(), console);
        var thirdPoint = supply(h, other.below(), console);
        var sharedTank = tank(h, shared);
        sharedTank.setSide(2, SideConfig.Face.BOTTOM, SideConfig.NONE, false);
        var lava = FluidSamples.of(new FluidStack(Fluids.LAVA, 1));
        h.assertTrue(LiquidStores.at(h.getLevel(), console.getBlockPos()).amount(lava) == 0,
                "Input-only side offered lava");
        h.assertTrue(ItemCount.get(console.stock(), lava) == 1000,
                "First, input-only face hid the output accessible from a linked port");
        sharedTank.setSide(2, SideConfig.Face.BOTTOM, SideConfig.OUTPUT, false);
        tank(h, other);
        h.assertTrue(ItemCount.get(console.stock(), lava) == 2000, "Shared tank counted twice");

        var order = new WorkOrder(1, new ItemStack(Items.OBSIDIAN), 1, null);
        var need = new ItemCount(lava, 2000);
        order.pendingReserve.add(need);
        console.orders().add(order);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(order.pendingReserve.isEmpty(), "Still waiting for lava"))
                .thenExecute(() -> {
                    h.assertTrue(order.held(console.getBlockPos(), lava) == 1000
                                    && order.held(secondPoint.getBlockPos(), lava) == 0
                                    && order.held(thirdPoint.getBlockPos(), lava) == 1000,
                            "The same tank was reserved from two different points");
                    var android = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
                    android.inventory().setItem(RuinAndroid.SLOT_TANK,
                            new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_tank_upgrade"))));
                    var job = new WorkJob(WorkJob.Type.CRAFT, order.id, 1, 1, List.of(need), List.of(), false);
                    var route = console.planPickup(job, console.getBlockPos());
                    h.assertTrue(route.size() == 2, "Missing pickup from the independent tank");
                    for (var pickup : route) h.assertTrue(console.fetchAt(android, job, pickup), "Reserved tank could not be drained");
                    h.assertTrue(android.liquidAmount(lava) == 2000 && ItemCount.get(console.stock(), lava) == 0,
                            "Tank-to-android pickup lost or duplicated lava");
                }).thenSucceed();
    }
}
