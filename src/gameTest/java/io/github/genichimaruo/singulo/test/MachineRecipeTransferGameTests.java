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

    /** 触媒の要らないレシピ・予備で足りるレシピを搬入しても、触媒枠の予備は装置に残る。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferKeepsReserveCatalysts(GameTestHelper h) {
        var menu = menu(h);
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        int slot = menu.type().catalystSlot();
        menu.getSlot(slot).set(catalyst.copyWithCount(16));
        menu.getSlot(menu.type().itemSlots()).set(new ItemStack(Items.IRON_INGOT, 12));
        var plain = MachineRecipeTransfer.plan(menu, recipe(iron(4)), false);
        h.assertTrue(plain != null, "Transfer without a catalyst failed");
        MachineRecipeTransfer.apply(menu, plain);
        h.assertTrue(menu.getSlot(slot).getItem().getCount() == 16, "Reserve catalysts moved out: " + menu.getSlot(slot).getItem());
        var input = new MachineRecipe.ItemInput(Ingredient.of(catalyst.getItem()), 1, 20, 1);
        var served = MachineRecipeTransfer.plan(menu, recipe(iron(4), input), false);
        h.assertTrue(served != null, "Transfer served by the reserve failed");
        MachineRecipeTransfer.apply(menu, served);
        h.assertTrue(menu.getSlot(slot).getItem().getCount() == 16, "Reserve catalysts rewritten: " + menu.getSlot(slot).getItem());
        h.succeed();
    }

    /** 搬入を試すだけでは、装置の中身が変わったことにならない（材料の判定を待っている装置の待ち時間を崩さない）。 */
    @GameTest(template = "empty", batch = "jei_transfer", timeoutTicks = 60)
    public static void jeiTransferPlanningDoesNotTouchMachineSlots(GameTestHelper h) {
        var menu = menu(h);
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        machine.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        menu.getSlot(menu.type().itemSlots()).set(new ItemStack(Items.IRON_INGOT, 2));
        var registries = h.getLevel().registryAccess();
        h.runAtTickTime(10, () -> {
            long before = machine.saveWithoutMetadata(registries).getLong("inputs_changed_at");
            h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(64)), true) == null, "Impossible transfer planned");
            h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(4)), true) != null, "Possible transfer failed");
            long after = machine.saveWithoutMetadata(registries).getLong("inputs_changed_at");
            h.assertTrue(before == after && machine.items().getStackInSlot(0).getCount() == 3,
                    "Planning changed the machine: " + before + " -> " + after);
            h.succeed();
        });
    }

    /** JEI の見積もりは、装填中の触媒の残りをサーバーと同じ値で数える（1回分足りないときに搬入できると見せない）。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void loadedCatalystEstimateMatchesTheServer(GameTestHelper h) {
        var menu = menu(h);
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        int max = io.github.genichimaruo.singulo.item.UsesHelper.get(catalyst).max();
        ItemStack worn = catalyst.copy();
        io.github.genichimaruo.singulo.item.UsesHelper.consume(worn, max - 7);
        machine.items().setStackInSlot(menu.type().catalystSlot(), worn);
        h.runAtTickTime(5, () -> {
            int remaining = io.github.genichimaruo.singulo.item.UsesHelper.remaining(machine.magazine().loaded());
            h.assertTrue(remaining > 0, "Catalyst was not loaded");
            var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            buf.writeBlockPos(menu.pos());
            buf.writeVarInt(menu.type().ordinal());
            var client = MachineMenu.client(2, TestScope.player(h).getInventory(), buf);
            var server = machine.syncData();
            for (int slot = 0; slot < server.getCount(); slot++) client.setData(slot, server.get(slot));
            int estimated = io.github.genichimaruo.singulo.item.UsesHelper.remaining(client.loadedCatalyst());
            h.assertTrue(estimated == remaining, "Client estimate " + estimated + " differs from the server's " + remaining);
            h.succeed();
        });
    }

    /** 最大搬入でも、触媒枠の予備はそのまま残り、まるごと使う触媒は回数分そろえる。使用回数で払う材料は1個だけ置く。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiMaxTransferPlansCatalystsPerBatch(GameTestHelper h) {
        var menu = menu(h);
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        int slot = menu.type().catalystSlot();
        int inventory = menu.type().itemSlots();
        menu.getSlot(slot).set(catalyst.copyWithCount(1));
        menu.getSlot(inventory).set(catalyst.copyWithCount(8));
        menu.getSlot(inventory + 1).set(new ItemStack(Items.IRON_INGOT, 16));
        var whole = new MachineRecipe.ItemInput(Ingredient.of(catalyst.getItem()), 1, 0);
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4), whole), true);
        h.assertTrue(plan != null, "Max transfer with a whole-item catalyst failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 16 && menu.getSlot(slot).getItem().getCount() == 4,
                "Catalysts not planned per batch: inputs " + menu.getSlot(0).getItem() + ", catalyst " + menu.getSlot(slot).getItem());
        var data = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("control_unit")));
        h.assertTrue(io.github.genichimaruo.singulo.item.UsesHelper.hasUses(data), "Control unit fixture has no uses");
        for (int i = 0; i < menu.type().inputSlots(); i++) menu.getSlot(i).set(ItemStack.EMPTY);
        menu.getSlot(inventory + 2).set(data.copyWithCount(1));
        menu.getSlot(inventory + 3).set(new ItemStack(Items.IRON_INGOT, 2));
        var uses = new MachineRecipe.ItemInput(Ingredient.of(data.getItem()), 2, 1);
        h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(1), uses), false) != null,
                "A uses ingredient with count 2 cannot be planned from one item");
        h.succeed();
    }

    /** 時間の場で加速しても、1回に払う電力は蓄電容量まで（容量アップグレードが1 tick 分に足りていれば止まらない）。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void timeFieldStepsStayWithinTheBuffer(GameTestHelper h) {
        h.assertTrue(MachineBlockEntity.affordableSteps(2, 3_500_000, 4_000_000) == 1, "Accelerated step exceeds the Mk2 buffer");
        h.assertTrue(MachineBlockEntity.affordableSteps(2, 1_000_000, 4_000_000) == 2, "Affordable acceleration was cut");
        h.assertTrue(MachineBlockEntity.affordableSteps(3, 0, 100) == 3 && MachineBlockEntity.affordableSteps(2, 500, 100) == 1,
                "Edge cases of the buffer limit");
        h.succeed();
    }

    /** 容量アップグレードの Shift クリック: 専用枠が空いていれば装着、埋まっていれば材料の欄へ（Mk2・Mk3 の材料になる）。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void capacityUpgradeShiftClickFallsBackToInputs(GameTestHelper h) {
        var menu = menu(h);
        var player = TestScope.player(h);
        int inventory = menu.type().itemSlots();
        menu.getSlot(inventory).set(CapacityUpgrades.item(1));
        menu.quickMoveStack(player, inventory);
        h.assertTrue(menu.getSlot(menu.type().capacitySlot()).getItem().getCount() == 1, "Empty capacity slot was not filled");
        menu.getSlot(inventory).set(CapacityUpgrades.item(1));
        menu.quickMoveStack(player, inventory);
        h.assertTrue(CapacityUpgrades.tier(menu.getSlot(0).getItem()) == 1 && menu.getSlot(inventory).getItem().isEmpty(),
                "Second upgrade did not go to the input slots: " + menu.getSlot(0).getItem());
        h.succeed();
    }

    /** 容量アップグレードの案内: 足りる段階を示し、Mk3 でも足りなければ段階を示さない。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void capacityHintNeverPointsAtAnUpgradeThatCannotHelp(GameTestHelper h) {
        h.assertTrue(CapacityUpgrades.requiredTier(500_000) == 1 && CapacityUpgrades.requiredTier(2_000_000) == 2
                && CapacityUpgrades.requiredTier(40_000_000) == 3, "Wrong capacity tier");
        h.assertTrue(CapacityUpgrades.requiredTier(60_000_000) == 0, "Mk3 suggested for a demand above its buffer");
        h.assertTrue(CapacityUpgrades.tier(CapacityUpgrades.item(2)) == 2 && CapacityUpgrades.tier(new ItemStack(Items.DIRT)) == 0,
                "Cached upgrade items do not match");
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
