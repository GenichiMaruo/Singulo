package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class PortablePrinterStorageGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Singulo.id(id)), count);
    }

    @GameTest(template = "empty")
    public static void printerKeepsSurplusAfterChangingMemory(GameTestHelper h) {
        ItemStack printer = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        IEnergyStorage storage = PortableAndroidPrinterItem.energy(printer);
        PortableAndroidPrinterItem.setContents(printer, List.of(item("android_memory_gunner", 1)));
        h.assertTrue(storage != null && storage.receiveEnergy(Integer.MAX_VALUE, false) == 240_000, "Gunner charge capacity");
        PortableAndroidPrinterItem.setContents(printer, List.of(item("android_memory_blade", 1)));
        h.assertTrue(storage.getEnergyStored() == 240_000 && storage.receiveEnergy(1, false) == 0
                && printer.getBarWidth() == 13, "Surplus should remain available without accepting further charge");
        int cost = PortableAndroidPrinterItem.printFe(printer, 0);
        h.assertTrue(storage.extractEnergy(cost, true) == 80_000 && storage.getEnergyStored() == 240_000, "Simulated draw changed charge");
        h.assertTrue(storage.extractEnergy(cost, false) == 80_000 && PortableAndroidPrinterItem.energy(printer).getEnergyStored() == 160_000,
                "A blade print's draw destroyed surplus charge");
        storage.extractEnergy(Integer.MAX_VALUE, false);
        h.assertTrue(storage.receiveEnergy(Integer.MAX_VALUE, false) == 80_000, "Recharging used the previous memory's capacity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void printerMenuTracksRefillAndSkipsDismantlingGuards(GameTestHelper h) {
        ServerPlayer player = TestScope.player(h);
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        RuinAndroid guard = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        guard.snapTo(player.position());
        guard.makeGuard(player.getUUID(), at, RuinAndroid.MODE_FOLLOW, 30);
        guard.loseStation();
        h.getLevel().addFreshEntity(guard);
        ItemStack printer = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        PortableAndroidPrinterItem.setContents(printer, List.of(ItemStack.EMPTY, item("android_kit", 1), item("android_repair_kit", 4)));
        player.setItemInHand(InteractionHand.MAIN_HAND, printer);
        PortablePrinterMenu menu = new PortablePrinterMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        player.tickCount = 20;
        printer.inventoryTick(h.getLevel(), player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        h.assertTrue(guard.inventory().getItem(RuinAndroid.SLOT_REPAIR).getCount() == 4, "Guard did not receive repair kits");
        menu.clicked(PortableAndroidPrinterItem.SLOT_KIT, 0, ContainerInput.PICKUP, player);
        h.assertTrue(AndroidStationBlockEntity.isKit(menu.getCarried()) && PortableAndroidPrinterItem.repairKits(printer) == 0
                && menu.item(0, PortableAndroidPrinterItem.SLOT_REPAIR).isEmpty(), "Editing an open printer restored transferred kits");
        guard.dismantle();
        PortableAndroidPrinterItem.setContents(printer, List.of(ItemStack.EMPTY, ItemStack.EMPTY, item("android_repair_kit", 3)));
        printer.inventoryTick(h.getLevel(), player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        menu.broadcastChanges();
        h.assertTrue(guard.inventory().getItem(RuinAndroid.SLOT_REPAIR).isEmpty() && PortableAndroidPrinterItem.repairKits(printer) == 3
                && menu.item(0, PortableAndroidPrinterItem.SLOT_REPAIR).getCount() == 3, "Repair stock went to a dismantling guard");
        guard.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void printerMenuLocksItsOffhandStack(GameTestHelper h) {
        ServerPlayer player = TestScope.player(h);
        ItemStack opened = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        ItemStack other = opened.copy();
        PortableAndroidPrinterItem.setContents(opened, List.of(ItemStack.EMPTY, item("android_kit", 1)));
        PortableAndroidPrinterItem.setContents(other, List.of(ItemStack.EMPTY, item("android_kit", 5)));
        player.setItemInHand(InteractionHand.OFF_HAND, opened);
        player.getInventory().setItem(9, other);
        PortablePrinterMenu menu = new PortablePrinterMenu(1, player.getInventory(), InteractionHand.OFF_HAND);
        menu.clicked(PortableAndroidPrinterItem.slots(opened), Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
        h.assertTrue(player.getOffhandItem() == opened && player.getInventory().getItem(9) == other, "Offhand printer moved during editing");
        player.setItemInHand(InteractionHand.OFF_HAND, other.copy());
        h.assertTrue(!menu.stillValid(player), "A different printer kept the original menu valid");
        menu.clicked(PortableAndroidPrinterItem.SLOT_KIT, 0, ContainerInput.PICKUP, player);
        h.assertTrue(menu.getCarried().isEmpty() && !menu.clickMenuButton(player, PortablePrinterMenu.BUTTON_SELECT)
                && PortableAndroidPrinterItem.kits(player.getOffhandItem()) == 5 && PortableAndroidPrinterItem.kits(opened) == 1,
                "An invalid menu altered a printer");
        h.succeed();
    }
}
