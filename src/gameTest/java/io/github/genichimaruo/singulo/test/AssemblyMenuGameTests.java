package io.github.genichimaruo.singulo.test;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AssemblyMenuGameTests {
    private static ItemStack craftStockUpgrade(GameTestHelper h) {
        var steel = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("steel_plate")));
        var circuit = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("basic_circuit")));
        var chest = new ItemStack(Items.CHEST);
        var input = CraftingInput.of(3, 3, List.of(steel, chest, steel, chest, circuit, chest, steel, chest, steel));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        return recipe.value().assemble(input, h.getLevel().registryAccess());
    }

    @GameTest(template = "empty")
    public static void expandedAssemblyStockSurvivesClicksReloadRemovalAndBreaking(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "StockTester"));
        var menu = new MachineMenu(1, player.getInventory(), machine);
        int upgradeSlot = menu.type().assemblyUpgradeSlot(), inventory = menu.playerSlotStart(), first = menu.assemblySlot(0);
        var upgrade = BuiltInRegistries.ITEM.get(Singulo.id("assembly_stock_upgrade"));
        menu.getSlot(inventory).set(craftStockUpgrade(h));
        menu.clicked(inventory, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(menu.getSlot(upgradeSlot).getItem().is(upgrade) && menu.stackLimit(first, new ItemStack(Items.BUCKET)) == 64
                && menu.stackLimit(first, new ItemStack(Items.IRON_PICKAXE)) == 1, "Unexpected expanded stack limits");
        ItemStack part = new ItemStack(Items.IRON_INGOT, 64);
        part.setHoverName(Component.literal("Stock"));
        var grid = machine.assemblyGrid();
        for (int cell = 0; cell < 2; cell++) {
            menu.setCarried(part.copy());
            menu.clicked(menu.assemblySlot(cell), 0, ClickType.PICKUP, player);
        }
        menu.setCarried(part.copy());
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        for (int cell = 0; cell < 2; cell++)
            menu.clicked(menu.assemblySlot(cell), AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
        h.assertTrue(menu.getCarried().isEmpty() && grid.getStackInSlot(0).getCount() == 96 && grid.getStackInSlot(1).getCount() == 96,
                "Dragging a stack across two full ordinary stacks lost its share");
        for (int i = 0; i < 2; i++) {
            menu.getSlot(inventory).set(part.copy());
            menu.clicked(inventory, 0, ClickType.QUICK_MOVE, player);
        }
        menu.setCarried(part.copy());
        menu.clicked(first, 0, ClickType.PICKUP, player);
        h.assertTrue(grid.getStackInSlot(0).getCount() == 256 && menu.getCarried().getCount() == 32
                && menu.getSlot(first).getItem().getCount() == 64 && menu.assemblyExcess(menu.getSlot(first)) == 192,
                "Filling a cell failed to cap it at 256 with a legal visible stack");
        menu.clicked(first, 1, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 32, "A full cell accepted another part");
        menu.clicked(inventory, 0, ClickType.PICKUP, player);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT));
        menu.clicked(first, 0, ClickType.SWAP, player);
        h.assertTrue(grid.getStackInSlot(0).getCount() == 256 && player.getInventory().getItem(0).is(Items.DIRT),
                "Number-key swap replaced a stocked cell with another material");
        menu.clicked(first, 1, ClickType.SWAP, player);
        h.assertTrue(ItemStack.matches(player.getInventory().getItem(1), part) && grid.getStackInSlot(0).getCount() == 192,
                "Empty hotbar slot received the wrong quantity");
        menu.clicked(upgradeSlot, 0, ClickType.PICKUP, player);
        menu.clicked(upgradeSlot, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(upgradeSlot, 2, ClickType.SWAP, player);
        menu.clicked(upgradeSlot, 1, ClickType.THROW, player);
        h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(upgradeSlot).getItem().is(upgrade)
                && menu.quickMoveStack(player, upgradeSlot).isEmpty()
                && machine.items().extractItem(upgradeSlot, 1, false).isEmpty(), "A stocked board allowed its upgrade to be removed");
        var registry = h.getLevel().registryAccess();
        var saved = machine.saveWithoutMetadata();
        grid.setStackInSlot(0, ItemStack.EMPTY);
        machine.loadWithComponents(saved, registry);
        h.assertTrue(ItemStack.matches(grid.getStackInSlot(0), part.copyWithCount(192)) && grid.getStackInSlot(1).getCount() == 96,
                "Reload lost stocked parts or their components");
        menu.clicked(menu.assemblySlot(1), 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(grid.getStackInSlot(1).isEmpty() && player.getInventory().countItem(Items.IRON_INGOT) == 192,
                "Shift-extraction failed to split the entire cell across normal inventory stacks");
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            h.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "Oversized player inventory stack");
        }
        grid.extractItem(0, 64, false);
        grid.extractItem(0, 63, false);
        h.assertTrue(grid.getStackInSlot(0).getCount() == 65 && !menu.getSlot(upgradeSlot).mayPickup(player),
                "Upgrade became removable at 65");
        grid.extractItem(0, 1, false);
        menu.clicked(upgradeSlot, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().is(upgrade) && !menu.getSlot(upgradeSlot).hasItem()
                && !grid.insertItem(0, part.copy(), false).isEmpty(), "Could not uninstall at 64 or still accepted more parts");
        menu.clicked(upgradeSlot, 0, ClickType.PICKUP, player);
        for (int i = 0; i < 2; i++) grid.insertItem(0, part.copy(), false);
        var absolute = machine.getBlockPos();
        h.getLevel().destroyBlock(absolute, false);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1)).stream().map(ItemEntity::getItem)
                .filter(s -> ItemStack.isSameItemSameTags(s, part)).toList();
        h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum() == 192 && drops.stream().allMatch(s -> s.getCount() <= 64),
                "Breaking the assembler lost parts or dropped oversized stacks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clicksDragShiftAndCloseConservePhysicalAssemblyParts(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AssemblyTester"));
        var menu = new MachineMenu(1, player.getInventory(), machine);
        int inventory = menu.playerSlotStart();
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 64));
        menu.clicked(inventory, 0, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(0), 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(menu.assemblySlot(0)).getItem().getCount() == 64,
                "Left click did not place the whole stack");
        menu.clicked(menu.assemblySlot(0), 1, ClickType.PICKUP, player);
        menu.clicked(menu.assemblySlot(1), 1, ClickType.PICKUP, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        for (int cell = 2; cell <= 3; cell++) {
            menu.clicked(menu.assemblySlot(cell), AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        }
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
        int[] counts = {32, 1, 15, 15};
        for (int cell = 0; cell < counts.length; cell++)
            h.assertTrue(machine.assemblyGrid().getStackInSlot(cell).getCount() == counts[cell], "Incorrect click or drag count at " + cell);
        int output = menu.type().outputSlot(), spent = menu.type().spentCatalystSlot();
        menu.clicked(output, 0, ClickType.PICKUP, player);
        menu.clicked(spent, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 1 && !menu.getSlot(output).hasItem() && !menu.getSlot(spent).hasItem(),
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
            h.assertTrue(part.is(Items.IRON_INGOT), "Reopened board has an unexpected ingredient");
            placed += part.getCount();
        }
        h.assertTrue(placed == 49 && player.getInventory().countItem(Items.IRON_INGOT) == 15
                        && !reopened.getSlot(reopened.assemblySlot(19)).hasItem() && menu.getCarried().isEmpty(),
                "Shift-click or cancelled drag lost or duplicated parts");
        int empty = reopened.assemblySlot(20);
        reopened.setCarried(new ItemStack(Items.BUCKET, 16));
        reopened.clicked(empty, 0, ClickType.PICKUP, player);
        var extra = machine.assemblyGrid().insertItem(20, new ItemStack(Items.BUCKET), false);
        h.assertTrue(extra.getCount() == 1 && reopened.getSlot(empty).getItem().getCount() == 16
                        && reopened.stackLimit(empty, new ItemStack(Items.IRON_PICKAXE)) == 1, "Cell ignored the item's own stack limit");
        h.succeed();
    }
}
