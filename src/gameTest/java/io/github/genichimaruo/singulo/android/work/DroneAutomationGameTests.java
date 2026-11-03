package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class DroneAutomationGameTests {
    private static final BlockPos PORT = new BlockPos(3, 2, 3);
    private static DronePortBlockEntity port(GameTestHelper h) {
        h.setBlock(PORT, SinguloBlocks.DRONE_PORT.get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PORT);
    }

    @GameTest(template = "empty")
    public static void androidStockExcludesOutboundCargoAndSealedCrates(GameTestHelper h) {
        h.setBlock(PORT.west(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PORT.west());
        var port = port(h);
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.IRON_INGOT, 64));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_RECEIVE, new ItemStack(Items.IRON_INGOT, 7));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get(), 4));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_ARRIVED, DroneCrateItem.pack(
                new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 9))));
        var stock = console.stock();
        h.assertTrue(stock.size() == 1 && stock.get(0).item.is(Items.IRON_INGOT) && stock.get(0).count == 7,
                "Planning counted cargo, reserve crates or sealed deliveries as available materials");
        var view = port.androidItems(Direction.WEST);
        h.assertTrue(view.extractItem(DronePortBlockEntity.SLOT_ARRIVED, 1, false).isEmpty(), "Android stole a sealed delivery");
        var face = SideConfig.faceOf(Direction.NORTH, Direction.WEST);
        port.setSide(DronePortBlockEntity.CHANNEL_ITEMS, face, SideConfig.INPUT, false);
        h.assertTrue(console.stock().isEmpty() && view.getStackInSlot(DronePortBlockEntity.SLOT_RECEIVE).isEmpty(),
                "Changing a side left stale accessible stock");
        port.setSide(DronePortBlockEntity.CHANNEL_ITEMS, face, SideConfig.OUTPUT, false);
        h.assertTrue(console.stock().get(0).count == 7, "Re-enabled arrivals were not visible");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void androidDeliveryKeepsOverflowUntilDroneCargoHasSpace(GameTestHelper h) {
        var port = port(h);
        h.setBlock(PORT.west(), SinguloBlocks.ANDROID_DELIVERY_PORT.get());
        DeliveryPortBlockEntity delivery = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PORT.west());
        for (int i = 0; i < DronePortBlockEntity.CARGO; i++)
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO + i, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(delivery.insert(new ItemStack(Items.IRON_INGOT, 40)).isEmpty(), "Delivery rejected products");
        // Same push used by the delivery port's server tick; a full destination must retain everything.
        var box = new net.minecraft.world.SimpleContainer(1);
        box.setItem(0, delivery.handler().extractItem(0, 40, false));
        SupplyPortBlockEntity.pushOut(h.getLevel(), delivery.getBlockPos(), box);
        h.assertTrue(box.getItem(0).getCount() == 40, "Full drone cargo discarded products");
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.IRON_INGOT, 50));
        SupplyPortBlockEntity.pushOut(h.getLevel(), delivery.getBlockPos(), box);
        h.assertTrue(box.getItem(0).getCount() == 26 && port.items().getStackInSlot(DronePortBlockEntity.SLOT_CARGO).getCount() == 64,
                "Partial delivery lost or duplicated products");
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO + 1, ItemStack.EMPTY);
        SupplyPortBlockEntity.pushOut(h.getLevel(), delivery.getBlockPos(), box);
        h.assertTrue(box.isEmpty() && port.items().getStackInSlot(DronePortBlockEntity.SLOT_CARGO + 1).getCount() == 26,
                "Delivery did not resume after freeing a slot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyCrateRecoveryHonorsFacesSimulationAndReload(GameTestHelper h) {
        var port = port(h);
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get(), 6));
        var face = SideConfig.faceOf(Direction.NORTH, Direction.WEST);
        var view = port.androidItems(Direction.WEST);
        h.assertTrue(view.getStackInSlot(0).isEmpty(), "Android took reserve crates without an output face");
        port.setSide(DronePortBlockEntity.CHANNEL_CRATES, face, SideConfig.OUTPUT, true);
        h.assertTrue(view.extractItem(0, 4, true).getCount() == 4 && port.items().getStackInSlot(0).getCount() == 6,
                "Simulation changed crate stock");
        h.assertTrue(view.extractItem(0, 4, false).getCount() == 4, "Crate output was not extractable");
        var saved = port.saveWithoutMetadata();
        var restored = new DronePortBlockEntity(port.getBlockPos(), port.getBlockState());
        restored.load(saved);
        h.assertTrue(restored.androidItems(Direction.WEST).extractItem(0, 16, true).getCount() == 2,
                "Reload lost the crate count or output configuration");
        h.assertTrue(restored.androidItems(Direction.EAST).extractItem(0, 16, true).isEmpty(), "Crates leaked through an input-only face");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void crateAutoEjectDoesNotEnableCargoAutoEject(GameTestHelper h) {
        var port = port(h);
        h.setBlock(PORT.below(), Blocks.CHEST);
        ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PORT.below());
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        port.items().setStackInSlot(0, new ItemStack(SinguloItems.PACKING_CRATE.get(), 6));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_RECEIVE, new ItemStack(Items.DIAMOND, 7));
        port.setSide(DronePortBlockEntity.CHANNEL_CRATES, SideConfig.Face.BOTTOM, SideConfig.OUTPUT, true);
        port.setEjectEnabled(DronePortBlockEntity.CHANNEL_CRATES, true);
        h.runAfterDelay(20, () -> {
            h.assertTrue(port.items().getStackInSlot(0).getCount() == 6, "Full chest lost reserve crates");
            chest.setItem(0, ItemStack.EMPTY);
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(chest.getItem(0).is(SinguloItems.PACKING_CRATE.get()) && chest.getItem(0).getCount() == 6,
                    "Crate ejection did not resume");
            h.assertTrue(port.items().getStackInSlot(DronePortBlockEntity.SLOT_RECEIVE).getCount() == 7,
                    "Crate ejection enabled the cargo master switch");
            h.succeed();
        });
    }
}
