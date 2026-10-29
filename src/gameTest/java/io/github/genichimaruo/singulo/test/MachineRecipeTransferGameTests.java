package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class MachineRecipeTransferGameTests {
    private static MachineMenu menu(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        return new MachineMenu(1, TestScope.player(h).getInventory(), machine);
    }

    private static MachineRecipe recipe(MachineRecipe.ItemInput... inputs) {
        return new MachineRecipe("precision_assembler", 2, 20, 10, List.of(inputs), List.of(), Optional.empty(),
                Optional.empty(), new ItemStack(Items.DIAMOND), List.of(), 0, 0, ItemStack.EMPTY);
    }

    private static MachineRecipe.ItemInput iron(int count) {
        return new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), count, 0);
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferCountsAndPreviewPreserveInventory(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.type().itemSlots();
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 12));
        menu.getSlot(0).set(new ItemStack(Items.DIRT, 3));
        menu.getSlot(menu.type().outputSlot()).set(new ItemStack(Items.DIAMOND, 2));
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4)), false);
        h.assertTrue(plan != null && menu.getSlot(0).getItem().is(Items.DIRT)
                && menu.getSlot(inventory).getItem().getCount() == 12, "Preview mutated inventory");
        MachineRecipeTransfer.apply(menu, plan);
        int dirt = 0, iron = 0;
        for (int i = inventory; i < inventory + 36; i++) {
            var stack = menu.getSlot(i).getItem();
            if (stack.is(Items.DIRT)) dirt += stack.getCount();
            if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
        }
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 4 && iron == 8 && dirt == 3
                && menu.getSlot(menu.type().outputSlot()).getItem().getCount() == 2, "Transfer lost/duplicated items or touched outputs");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferFullInventoryAndMissingMaterialsAreAtomic(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.type().itemSlots();
        for (int i = 0; i < 36; i++) menu.getSlot(inventory + i).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 64));
        menu.getSlot(0).set(new ItemStack(Items.DIRT, 3));
        h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(4)), false) == null, "Full inventory accepted displaced items");
        h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(65)), false) == null, "Oversized requirement accepted");
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 3 && menu.getSlot(inventory).getItem().getCount() == 64,
                "Failed transfer changed inventory");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferShiftClickAndExistingInputsConserveCounts(GameTestHelper h) {
        var menu = menu(h);
        menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT, 3));
        menu.getSlot(menu.type().itemSlots()).set(new ItemStack(Items.IRON_INGOT, 12));
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4)), true);
        h.assertTrue(plan != null, "Shift transfer failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 12
                && menu.getSlot(menu.type().itemSlots()).getItem().getCount() == 3, "Shift transfer ignored existing inputs or recipe counts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferBacktracksForOverlappingIngredients(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.type().itemSlots();
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(inventory + 1).set(new ItemStack(Items.GOLD_INGOT));
        var either = new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1, 0);
        var plan = MachineRecipeTransfer.plan(menu, recipe(either, iron(1)), false);
        h.assertTrue(plan != null, "Overlapping ingredient alternatives failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(0).getItem().is(Items.GOLD_INGOT) && menu.getSlot(1).getItem().is(Items.IRON_INGOT),
                "Alternatives allocated incorrectly");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferCatalystUsesDedicatedSlotAndPreservesLifetime(GameTestHelper h) {
        var menu = menu(h);
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        int inventory = menu.type().itemSlots();
        menu.getSlot(inventory).set(catalyst.copyWithCount(3));
        menu.getSlot(inventory + 1).set(new ItemStack(Items.IRON_INGOT, 12));
        var input = new MachineRecipe.ItemInput(Ingredient.of(catalyst.getItem()), 1, 20, 1);
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4), input), true);
        h.assertTrue(plan != null, "Catalyst transfer failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(menu.getSlot(menu.type().catalystSlot()).getItem(), catalyst)
                && menu.getSlot(0).getItem().getCount() == 12 && menu.getSlot(inventory).getItem().getCount() == 2,
                "Catalyst was consumed, duplicated or placed in a material slot");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferMaxCanFreeInventorySpaceByMovingWholeStack(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.type().itemSlots();
        for (int i = 0; i < 36; i++) menu.getSlot(inventory + i).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 64));
        menu.getSlot(0).set(new ItemStack(Items.DIRT, 3));
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4)), true);
        h.assertTrue(plan != null, "Max transfer failed to use space freed by the whole ingredient stack");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 64 && menu.getSlot(inventory).getItem().is(Items.DIRT)
                && menu.getSlot(inventory).getItem().getCount() == 3, "Full inventory max transfer lost displaced items");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferRestorePreservesComponentsAndRejectsWrongStation(GameTestHelper h) {
        var menu = menu(h);
        var target = new ItemStack(Items.IRON_PICKAXE);
        target.setDamageValue(100);
        menu.getSlot(menu.type().itemSlots()).set(target.copy());
        var restore = new MachineRecipe("precision_assembler", 2, 20, 10, List.of(), List.of(), Optional.empty(),
                Optional.of(Ingredient.of(Items.IRON_PICKAXE)), ItemStack.EMPTY, List.of(), 0, 0, ItemStack.EMPTY);
        var plan = MachineRecipeTransfer.plan(menu, restore, true);
        h.assertTrue(plan != null, "Restore target transfer failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), target), "Restore transfer changed target components");
        var wrong = new MachineRecipe("compressor", 1, 20, 10, List.of(iron(1)), List.of(), Optional.empty(),
                Optional.empty(), new ItemStack(Items.DIAMOND), List.of(), 0, 0, ItemStack.EMPTY);
        h.assertTrue(MachineRecipeTransfer.plan(menu, wrong, false) == null, "Accepted another machine's recipe");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferUsesAlreadyLoadedCatalystWithoutMovingIt(GameTestHelper h) {
        var menu = menu(h);
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        machine.items().setStackInSlot(menu.type().catalystSlot(), catalyst.copy());
        machine.magazine().tick(machine.items(), menu.type().catalystSlot(), s -> s);
        menu.getSlot(menu.type().itemSlots()).set(new ItemStack(Items.IRON_INGOT, 4));
        var input = new MachineRecipe.ItemInput(Ingredient.of(catalyst.getItem()), 1, 20, 1);
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4), input), false);
        h.assertTrue(plan != null, "JEI required a spare despite a usable loaded catalyst");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(machine.magazine().loaded(), catalyst)
                && menu.getSlot(menu.type().catalystSlot()).getItem().isEmpty(), "Transfer moved or duplicated active catalyst");
        h.succeed();
    }
}
