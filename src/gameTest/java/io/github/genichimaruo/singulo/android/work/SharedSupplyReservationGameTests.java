package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class SharedSupplyReservationGameTests {
    private record Shop(WorkConsoleBlockEntity console, RuinAndroid worker) {}

    private static Shop workshop(GameTestHelper h, ServerPlayer player, BlockPos position) {
        var level = h.getLevel();
        var bench = position.south(2);
        var rackPosition = position.south();
        h.setBlock(position, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(rackPosition, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, position);
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, rackPosition);
        rack.setConsole(console.getBlockPos());
        console.addLink(rack.getBlockPos());
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, h.absolutePos(bench), Blocks.CRAFTING_TABLE);
        rack.items().setStackInSlot(0, location);
        var grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(0, new ItemStack(Items.IRON_INGOT));
        console.setGrid(grid);
        console.encoder().setItem(WorkConsoleBlockEntity.CARD, new ItemStack(SinguloItems.RECIPE_CARD.get()));
        h.assertTrue(console.encode(), "Fixture could not encode vanilla nugget recipe");
        rack.items().setStackInSlot(1, console.encoder().getItem(WorkConsoleBlockEntity.CARD).copy());
        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        var spawn = h.absolutePos(position.north());
        worker.makeGuard(player.getUUID(), spawn, RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(spawn));
        level.addFreshEntity(worker);
        TestScope.of(h).defer(worker::discard);
        return new Shop(console, worker);
    }

    @GameTest(template = "empty")
    public static void twoConsolesReserveSeparatePhysicalIngots(GameTestHelper h) {
        var player = TestScope.player(h);
        var chestPos = new BlockPos(3, 1, 2);
        h.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, chestPos);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT));
        var a = workshop(h, player, chestPos.west());
        var b = workshop(h, player, chestPos.east());
        var iron = new ItemStack(Items.IRON_INGOT);
        var target = new ItemStack(Items.IRON_NUGGET);
        h.assertTrue(a.console.start(player, target, 9, List.of(a.worker.getUUID()), null) == null, "First order failed");
        var orderA = a.console.orders().get(0);
        h.assertTrue(b.console.start(player, target, 9, List.of(b.worker.getUUID()), null) != null && b.console.orders().isEmpty(),
                "Second console accepted an order using the already reserved ingot");
        h.assertTrue(b.console.takeFuel(b.worker, b.console.getBlockPos(), iron, 1) == 0 && chest.countItem(Items.IRON_INGOT) == 1,
                "Unreserved pickup stole the first console's ingot");

        var level = h.getLevel();
        var saved = a.console.saveWithoutMetadata();
        var restored = SupplyReservations.load(SupplyReservations.get(level).save(new CompoundTag()));
        h.assertTrue(restored.foreign(level, b.console.getBlockPos(), h.absolutePos(chestPos), iron) == 1,
                "A reservation disappeared from the world ledger on reload");
        // Upgrade an existing order saved before individual storage identities were recorded.
        var held = saved.getList("orders", Tag.TAG_COMPOUND).getCompound(0)
                .getList("held", Tag.TAG_COMPOUND).getCompound(0);
        held.remove("store");
        SupplyReservations.get(level).clear(level, a.console.getBlockPos());
        a.console.load(saved);
        a.console.available();
        h.assertTrue(ItemCount.get(b.console.available(), iron) == 0, "Reloaded order failed to protect shared stock");

        var other = chestPos.east(2);
        h.setBlock(other, Blocks.CHEST);
        ChestBlockEntity independent = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, other);
        independent.setItem(0, iron.copy());
        h.assertTrue(b.console.start(player, target, 9, List.of(b.worker.getUUID()), null) == null,
                "A claim on the shared chest blocked the second console's own chest");
        var movedChest = chestPos.east().above();
        h.setBlock(movedChest, Blocks.CHEST);
        ChestBlockEntity replacement = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, movedChest);
        replacement.setItem(0, independent.removeItem(0, 1));
        var job = b.console.requestJob(b.worker);
        var route = b.console.planPickup(job, b.console.getBlockPos());
        h.assertTrue(route != null && route.size() == 1 && b.console.fetchAt(b.worker, job, route.get(0)), "Independent pickup failed");
        h.assertTrue(chest.countItem(Items.IRON_INGOT) == 1 && replacement.isEmpty(), "Pickup drained the other order's chest");
        a.console.cancel(orderA.id, null);
        h.assertTrue(ItemCount.get(b.console.available(), iron) == 1, "Cancellation did not release the shared ingot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoConsolesCannotReserveTheSameTank(GameTestHelper h) {
        var tankPos = new BlockPos(2, 2, 2);
        h.setBlock(tankPos.below(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(tankPos.above(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity a = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, tankPos.below()), b = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, tankPos.above());
        h.setBlock(tankPos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity tank = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, tankPos);
        tank.setSide(2, SideConfig.Face.BOTTOM, SideConfig.OUTPUT, false);
        tank.setSide(2, SideConfig.Face.TOP, SideConfig.OUTPUT, false);
        var fluid = new FluidStack(Fluids.LAVA, 1000);
        tank.forceOutputTankForTest(fluid.copy());
        var lava = FluidSamples.key(FluidSamples.of(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid, 1)));
        var first = new WorkOrder(1, new ItemStack(Items.OBSIDIAN), 1, null);
        first.pendingReserve.add(new ItemCount(lava, 1000));
        a.orders().add(first);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(first.held(null, lava) == 1000, "First console did not reserve lava"))
                .thenExecute(() -> {
                    h.assertTrue(ItemCount.get(b.stock(), lava) == 1000 && ItemCount.get(b.available(), lava) == 0,
                            "Second console advertised another order's 1000 mB as available");
                    var second = new WorkOrder(1, new ItemStack(Items.OBSIDIAN), 1, null);
                    second.pendingReserve.add(new ItemCount(lava, 1000));
                    b.orders().add(second);
                }).thenIdle(21).thenExecute(() -> {
                    h.assertTrue(b.orders().get(0).held(null, lava) == 0 && !b.orders().get(0).pendingReserve.isEmpty(),
                            "Both consoles reserved the same lava");
                    a.cancel(first.id, null);
                }).thenWaitUntil(() -> h.assertTrue(b.orders().get(0).held(null, lava) == 1000,
                        "Lava did not become available after cancellation")).thenSucceed();
    }
}
