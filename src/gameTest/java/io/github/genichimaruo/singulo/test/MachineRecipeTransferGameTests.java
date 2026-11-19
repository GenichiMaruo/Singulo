package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class MachineRecipeTransferGameTests {
    private static MachineMenu menu(GameTestHelper h) {
        return menu(h, MachineType.SINGULARITY_ENCAPSULATOR);
    }

    private static MachineMenu menu(GameTestHelper h, MachineType type) {
        h.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(type).get());
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        return new MachineMenu(1, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), machine);
    }

    private static MachineRecipe recipe(MachineRecipe.ItemInput... inputs) {
        return new MachineRecipe("singularity_encapsulator", 2, 20, 10, List.of(inputs), List.of(), Optional.empty(),
                Optional.empty(), new ItemStack(Items.DIAMOND), List.of(), 0, 0, ItemStack.EMPTY);
    }

    private static MachineRecipe.ItemInput iron(int count) {
        return new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), count, 0);
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void manualAssemblyTransfersOneSetAndReturnsDisplacedParts(GameTestHelper h) {
        var menu = menu(h, MachineType.PRECISION_ASSEMBLER);
        var machine = (MachineBlockEntity)h.getLevel().getBlockEntity(menu.pos());
        int index = RecipeSelection.indexOf(h.getLevel(), menu.type(), Singulo.id("precision_assembler/android_tank_upgrade"));
        var recipe = RecipeSelection.byIndex(h.getLevel(), menu.type(), index).value();
        int inventory = menu.playerSlotStart();
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var input = recipe.ingredients().get(i);
            menu.getSlot(inventory + i).set(input.ingredient().getItems()[0].copyWithCount(input.count() * 3));
        }
        menu.getSlot(menu.type().outputSlot()).set(new ItemStack(Items.GOLD_INGOT, 12));
        var spent = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("spent_muon_catalyst")), 3);
        menu.getSlot(menu.type().spentCatalystSlot()).set(spent.copy());
        menu.getSlot(menu.assemblySlot(0)).set(new ItemStack(Items.DIRT));
        var plan = MachineRecipeTransfer.plan(menu, recipe, false);
        h.assertTrue(plan != null && menu.getSlot(menu.assemblySlot(0)).getItem().is(Items.DIRT), "Preview changed the board");
        MachineRecipeTransfer.apply(menu, plan);
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
            var part = recipe.assemblyInput(cell);
            var placed = machine.assemblyGrid().getStackInSlot(cell);
            h.assertTrue(part == null ? placed.isEmpty() : placed.getCount() == 1 && part.ingredient().test(placed),
                    "Wrong part or amount at assembly cell " + cell);
        }
        int dirt = 0;
        for (int i = inventory; i < inventory + 36; i++)
            if (menu.getSlot(i).getItem().is(Items.DIRT)) dirt += menu.getSlot(i).getItem().getCount();
        h.assertTrue(dirt == 1 && menu.getSlot(menu.type().outputSlot()).getItem().getCount() == 12
                && ItemStack.matches(spent, menu.getSlot(menu.type().spentCatalystSlot()).getItem()),
                "Transfer disturbed an output or lost a displaced part");
        for (int i = 0; i < recipe.ingredients().size(); i++)
            h.assertTrue(menu.getSlot(inventory + i).getItem().getCount() == recipe.ingredients().get(i).count() * 2,
                    "Ordinary transfer took more than one set");
        var more = MachineRecipeTransfer.plan(menu, recipe, false);
        h.assertTrue(more != null, "Could not add a second set");
        MachineRecipeTransfer.apply(menu, more);
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++)
            if (recipe.assemblyInput(cell) != null) h.assertTrue(machine.assemblyGrid().getStackInSlot(cell).getCount() == 2,
                    "A second click did not top up cell " + cell);
        var bulk = MachineRecipeTransfer.plan(menu, recipe, true);
        h.assertTrue(bulk != null, "Could not bulk transfer");
        MachineRecipeTransfer.apply(menu, bulk);
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++)
            h.assertTrue(machine.assemblyGrid().getStackInSlot(cell).getCount() == (recipe.assemblyInput(cell) == null ? 0 : 3),
                    "Shift-transfer did not fill three complete sets at " + cell);
        for (int i = inventory; i < inventory + 36; i++) {
            var remaining = menu.getSlot(i).getItem();
            h.assertTrue(recipe.ingredients().stream().noneMatch(in -> in.ingredient().test(remaining)),
                    "Transferred parts remained in inventory: " + remaining);
        }
        var glass = machine.assemblyGrid().getStackInSlot(4).copyWithCount(10);
        h.assertTrue(machine.assemblyGrid().insertItem(4, glass, false).isEmpty(), "Could not stock spare glass");
        var stocked = MachineRecipeTransfer.plan(menu, recipe, false);
        h.assertTrue(stocked != null, "Transfer rejected a complete stocked board");
        MachineRecipeTransfer.apply(menu, stocked);
        h.assertTrue(machine.assemblyGrid().getStackInSlot(4).getCount() == 13 && machine.assemblyGrid().getStackInSlot(6).getCount() == 3,
                "Clicking a complete diagram removed spare materials from its cells");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void pairedMirrorsTransferAsOneBoardWithOneCatalyst(GameTestHelper h) {
        var menu = menu(h, MachineType.PRECISION_ASSEMBLER);
        var machine = (MachineBlockEntity)h.getLevel().getBlockEntity(menu.pos());
        var id = Singulo.id("precision_assembler/casimir_cavity_mirror_plate_pair");
        int index = RecipeSelection.indexOf(h.getLevel(), menu.type(), id);
        var pair = RecipeSelection.byIndex(h.getLevel(), menu.type(), index).value();
        for (int i = 0; i < pair.ingredients().size(); i++) {
            var part = pair.ingredients().get(i);
            int oneSet = part.uses() > 0 ? 1 : part.count() / 2;
            menu.getSlot(menu.playerSlotStart() + i).set(part.ingredient().getItems()[0].copyWithCount(oneSet));
        }
        h.assertTrue(MachineRecipeTransfer.plan(menu, pair, false) == null, "One mirror's materials filled the paired board");
        for (int i = 0; i < pair.ingredients().size(); i++) {
            var part = pair.ingredients().get(i);
            if (part.uses() == 0) menu.getSlot(menu.playerSlotStart() + i).getItem().setCount(part.count());
        }
        var placement = MachineRecipeTransfer.plan(menu, pair, false);
        h.assertTrue(placement != null, "JEI transfer failed for two mirrors and one catalyst");
        MachineRecipeTransfer.apply(menu, placement);
        var plan = pair.assemblyPlan(machine.assemblyInputs());
        h.assertTrue(plan != null && plan.uses()[MachineRecipe.ASSEMBLY_CELLS] == 1500 && pair.result().getCount() == 2,
                "The transferred board did not prepare two mirrors at their regular cost");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferCountsAndPreviewPreserveInventory(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.playerSlotStart();
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
        int inventory = menu.playerSlotStart();
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
        menu.getSlot(menu.playerSlotStart()).set(new ItemStack(Items.IRON_INGOT, 12));
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4)), true);
        h.assertTrue(plan != null, "Shift transfer failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 12
                && menu.getSlot(menu.playerSlotStart()).getItem().getCount() == 3, "Shift transfer ignored existing inputs or recipe counts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferBacktracksForOverlappingIngredients(GameTestHelper h) {
        var menu = menu(h);
        int inventory = menu.playerSlotStart();
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
        menu.getSlot(menu.playerSlotStart()).set(new ItemStack(Items.IRON_INGOT, 12));
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
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(1, 1, 1));
        machine.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        menu.getSlot(menu.playerSlotStart()).set(new ItemStack(Items.IRON_INGOT, 2));
        var registries = h.getLevel().registryAccess();
        h.runAtTickTime(10, () -> {
            long before = machine.saveWithoutMetadata().getLong("inputs_changed_at");
            h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(64)), true) == null, "Impossible transfer planned");
            h.assertTrue(MachineRecipeTransfer.plan(menu, recipe(iron(4)), true) != null, "Possible transfer failed");
            long after = machine.saveWithoutMetadata().getLong("inputs_changed_at");
            h.assertTrue(before == after && machine.items().getStackInSlot(0).getCount() == 3,
                    "Planning changed the machine: " + before + " -> " + after);
            h.succeed();
        });
    }

    /** JEI の見積もりは、装填中の触媒の残りをサーバーと同じ値で数える（1回分足りないときに搬入できると見せない）。 */
    @GameTest(template = "empty", batch = "jei_transfer")
    public static void loadedCatalystEstimateMatchesTheServer(GameTestHelper h) {
        var menu = menu(h);
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(1, 1, 1));
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        int max = io.github.genichimaruo.singulo.item.UsesHelper.get(catalyst).max();
        ItemStack worn = catalyst.copy();
        io.github.genichimaruo.singulo.item.UsesHelper.consume(worn, max - 7);
        machine.items().setStackInSlot(menu.type().catalystSlot(), worn);
        h.runAtTickTime(5, () -> {
            int remaining = io.github.genichimaruo.singulo.item.UsesHelper.remaining(machine.magazine().loaded());
            h.assertTrue(remaining > 0, "Catalyst was not loaded");
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeBlockPos(menu.pos());
            buf.writeVarInt(menu.type().ordinal());
            var client = MachineMenu.client(2, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), buf);
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
        int inventory = menu.playerSlotStart();
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
        var menu = menu(h, MachineType.PRECISION_ASSEMBLER);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        int inventory = menu.playerSlotStart();
        menu.getSlot(inventory).set(CapacityUpgrades.item(1));
        menu.quickMoveStack(player, inventory);
        h.assertTrue(menu.getSlot(menu.type().capacitySlot()).getItem().getCount() == 1, "Empty capacity slot was not filled");
        menu.getSlot(inventory).set(CapacityUpgrades.item(1));
        menu.quickMoveStack(player, inventory);
        h.assertTrue(CapacityUpgrades.tier(menu.getSlot(menu.assemblySlot(0)).getItem()) == 1 && menu.getSlot(inventory).getItem().isEmpty(),
                "Second upgrade did not go to the manual board: " + menu.getSlot(menu.assemblySlot(0)).getItem());
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
        int inventory = menu.playerSlotStart();
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
        int inventory = menu.playerSlotStart();
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

    private static MachineMenu fullInventoryAssembly(GameTestHelper h) {
        var menu = menu(h, MachineType.PRECISION_ASSEMBLER);
        for (int i = 0; i < 36; i++) menu.getSlot(menu.playerSlotStart() + i).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.getSlot(menu.assemblySlot(1)).set(new ItemStack(Items.DIRT, 3));
        return menu;
    }

    private static MachineRecipe singleIronAssembly() {
        return new MachineRecipe("precision_assembler", 2, 20, 10, List.of(iron(1)), List.of(), Optional.empty(),
                Optional.empty(), new ItemStack(Items.DIAMOND), List.of(), 0, 0, ItemStack.EMPTY);
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void assemblyTransferCanEmptyTheSmallerIngredientStack(GameTestHelper h) {
        var menu = fullInventoryAssembly(h);
        int inventory = menu.playerSlotStart();
        menu.getSlot(inventory).set(new ItemStack(Items.IRON_INGOT, 64));
        menu.getSlot(inventory + 1).set(new ItemStack(Items.IRON_INGOT));
        var plan = MachineRecipeTransfer.plan(menu, singleIronAssembly(), false);
        h.assertTrue(plan != null, "A single iron ingot could free space for the displaced stack");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(menu.getSlot(menu.assemblySlot(0)).getItem().getCount() == 1
                        && menu.getSlot(menu.assemblySlot(1)).getItem().isEmpty()
                        && ItemStack.matches(menu.getSlot(inventory).getItem(), new ItemStack(Items.IRON_INGOT, 64))
                        && ItemStack.matches(menu.getSlot(inventory + 1).getItem(), new ItemStack(Items.DIRT, 3)),
                "Single transfer lost or moved the wrong stack");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void assemblyMaxTransferCanSkipAnIntermediateCountThatDoesNotFit(GameTestHelper h) {
        var menu = fullInventoryAssembly(h);
        int inventory = menu.playerSlotStart();
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sample"));
        menu.getSlot(inventory).set(named.copy());
        menu.getSlot(inventory + 1).set(new ItemStack(Items.IRON_INGOT, 64));
        var plan = MachineRecipeTransfer.plan(menu, singleIronAssembly(), true);
        h.assertTrue(plan != null, "Could not transfer a full ingredient stack");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(menu.getSlot(menu.assemblySlot(0)).getItem(), new ItemStack(Items.IRON_INGOT, 64))
                        && menu.getSlot(menu.assemblySlot(1)).getItem().isEmpty()
                        && ItemStack.matches(menu.getSlot(inventory).getItem(), named)
                        && ItemStack.matches(menu.getSlot(inventory + 1).getItem(), new ItemStack(Items.DIRT, 3)),
                "Max transfer settled for one named ingot even though a full stack fits");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiRepairsOnlyAnExhaustedObservationLog(GameTestHelper h) {
        var menu = menu(h, MachineType.ARCHIVE_TERMINAL);
        var recipe = MachineBlockEntity.stationRecipes(h.getLevel(), menu.type()).stream()
                .filter(r -> r.id().equals(Singulo.id("archive_terminal/restore_observation_log"))).findFirst().orElseThrow().value();
        int inventory = menu.playerSlotStart();
        for (var part : recipe.ingredients()) menu.getSlot(inventory++).set(part.ingredient().getItems()[0].copyWithCount(part.count()));
        ItemStack fresh = recipe.restore().orElseThrow().getItems()[0].copy();
        menu.getSlot(inventory).set(fresh.copy());
        h.assertTrue(MachineRecipeTransfer.plan(menu, recipe, false) == null, "JEI offered to repair a fresh log");
        var worn = io.github.genichimaruo.singulo.item.UsesHelper.consume(fresh, io.github.genichimaruo.singulo.item.UsesHelper.remaining(fresh));
        menu.getSlot(inventory + 1).set(worn.copy());
        var plan = MachineRecipeTransfer.plan(menu, recipe, false);
        h.assertTrue(plan != null, "Exhausted log was not offered for repair");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(menu.getSlot(recipe.ingredients().size()).getItem(), worn)
                && ItemStack.matches(menu.getSlot(inventory).getItem(), fresh), "JEI took the wrong log");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferRestorePreservesComponentsAndRejectsWrongStation(GameTestHelper h) {
        var menu = menu(h, MachineType.PRECISION_ASSEMBLER);
        var target = new ItemStack(Items.IRON_PICKAXE);
        target.setDamageValue(100);
        menu.getSlot(menu.playerSlotStart()).set(target.copy());
        var restore = new MachineRecipe("precision_assembler", 2, 20, 10, List.of(), List.of(), Optional.empty(),
                Optional.of(Ingredient.of(Items.IRON_PICKAXE)), ItemStack.EMPTY, List.of(), 0, 0, ItemStack.EMPTY);
        var plan = MachineRecipeTransfer.plan(menu, restore, true);
        h.assertTrue(plan != null, "Restore target transfer failed");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(menu.getSlot(menu.assemblySlot(0)).getItem(), target), "Restore transfer changed target components");
        var wrong = new MachineRecipe("compressor", 1, 20, 10, List.of(iron(1)), List.of(), Optional.empty(),
                Optional.empty(), new ItemStack(Items.DIAMOND), List.of(), 0, 0, ItemStack.EMPTY);
        h.assertTrue(MachineRecipeTransfer.plan(menu, wrong, false) == null, "Accepted another machine's recipe");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "jei_transfer")
    public static void jeiTransferUsesAlreadyLoadedCatalystWithoutMovingIt(GameTestHelper h) {
        var menu = menu(h);
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(1, 1, 1));
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        machine.items().setStackInSlot(menu.type().catalystSlot(), catalyst.copy());
        machine.magazine().tick(machine.items(), menu.type().catalystSlot(), s -> s);
        menu.getSlot(menu.playerSlotStart()).set(new ItemStack(Items.IRON_INGOT, 4));
        var input = new MachineRecipe.ItemInput(Ingredient.of(catalyst.getItem()), 1, 20, 1);
        var plan = MachineRecipeTransfer.plan(menu, recipe(iron(4), input), false);
        h.assertTrue(plan != null, "JEI required a spare despite a usable loaded catalyst");
        MachineRecipeTransfer.apply(menu, plan);
        h.assertTrue(ItemStack.matches(machine.magazine().loaded(), catalyst)
                && menu.getSlot(menu.type().catalystSlot()).getItem().isEmpty(), "Transfer moved or duplicated active catalyst");
        h.succeed();
    }
}
