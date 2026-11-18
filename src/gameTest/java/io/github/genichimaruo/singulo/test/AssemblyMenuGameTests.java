package io.github.genichimaruo.singulo.test;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AssemblyMenuGameTests {
    @GameTest(template = "empty")
    public static void clicksDragShiftAndCloseConservePhysicalAssemblyParts(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity machine = h.getBlockEntity(pos);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AssemblyTester"));
        var menu = new MachineMenu(1, player.getInventory(), machine);
        int inventory = menu.playerSlotStart();
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 12));
        menu.clicked(inventory, 0, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(0), 0, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(1), 1, ClickType.PICKUP, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        for (int cell = 2; cell <= 3; cell++) {
            menu.clicked(menu.assemblySlot(cell), AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        }
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
        for (int cell = 0; cell < 4; cell++) {
            h.assertTrue(machine.assemblyGrid().getStackInSlot(cell).getCount() == 1, "Left/right click or drag overfilled a cell");
        }
        int output = menu.type().outputSlot(), spent = menu.type().spentCatalystSlot();
        menu.clicked(output, 0, ClickType.PICKUP, player);
        menu.clicked(spent, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 8 && !menu.getSlot(output).hasItem() && !menu.getSlot(spent).hasItem(),
                "An output slot accepted a manual ingredient");
        h.assertTrue(menu.type().inputSlots() == 0 && !menu.clickMenuButton(player, MachineMenu.BUTTON_SELECT),
                "The assembler exposed a bulk input or accepted an automatic recipe selection");
        menu.clicked(inventory, 0, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(0), 0, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(4), 0, ClickType.PICKUP, player);
        h.assertTrue(!menu.getSlot(menu.assemblySlot(0)).hasItem() && menu.getSlot(menu.assemblySlot(4)).hasItem(),
                "Could not move a placed part to another cell");
        menu.clicked(menu.assemblySlot(1), 0, ClickType.QUICK_MOVE, player);
        menu.clicked(inventory, 0, ClickType.QUICK_MOVE, player);
        var shell = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("spent_time_crystal_catalyst"));
        machine.items().setStackInSlot(output, new ItemStack(Items.DIRT, 3));
        machine.items().setStackInSlot(spent, new ItemStack(shell));
        menu.clicked(spent, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(player.getInventory().countItem(shell) == 1 && menu.getSlot(output).getItem().getCount() == 3,
                "Extracting a spent catalyst disturbed the product");
        menu.clicked(output, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(!menu.getSlot(output).hasItem() && player.getInventory().countItem(Items.DIRT) == 3, "Could not retrieve product");
        menu.clicked(menu.assemblySlot(3), 0, ClickType.PICKUP, player);
        // Close with a drag still in progress. Its candidate cells never received any items.
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 1), ClickType.QUICK_CRAFT, player);
        menu.clicked(menu.assemblySlot(19), AbstractContainerMenu.getQuickcraftMask(1, 1), ClickType.QUICK_CRAFT, player);
        menu.removed(player);
        var reopened = new MachineMenu(2, player.getInventory(), machine);
        int placed = 0;
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
            ItemStack part = reopened.getSlot(reopened.assemblySlot(cell)).getItem();
            if (part.isEmpty()) continue;
            h.assertTrue(part.is(Items.IRON_INGOT) && part.getCount() == 1, "Reopened board has an invalid stack");
            placed++;
        }
        h.assertTrue(placed == 11 && !reopened.getSlot(reopened.assemblySlot(19)).hasItem()
                        && player.getInventory().countItem(Items.IRON_INGOT) == 1 && menu.getCarried().isEmpty(),
                "Shift-click or cancelled drag lost or duplicated parts");
        h.succeed();
    }
}
