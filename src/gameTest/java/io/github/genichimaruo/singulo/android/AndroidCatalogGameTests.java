package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.compat.CraftingInput;
import io.github.genichimaruo.singulo.android.work.*;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidCatalogGameTests {
    @GameTest(template = "empty", timeoutTicks = 1800)
    public static void androidDeliversBismuthFromCopperRefiningCard(GameTestHelper h) {
        var level = h.getLevel();
        var player = TestScope.player(h);
        var at = new BlockPos(2, 1, 2);
        var rackPos = at.south(3);
        var machinePos = new BlockPos(4, 1, 4);
        var machineBlock = SinguloBlocks.MACHINES.get(MachineType.CATALYTIC_REACTOR).get();
        h.setBlock(at, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(at.west(), Blocks.CHEST);
        h.setBlock(at.east(), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(rackPos, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(machinePos, machineBlock);
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, at);
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, rackPos);
        ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, at.west());
        FluidDronePortBlockEntity tank = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, at.east());
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, machinePos);
        chest.setItem(0, new ItemStack(Items.RAW_COPPER, 8));
        tank.receiveTank().fill(new FluidStack(Fluids.WATER, 250),
                FluidAction.EXECUTE);
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, console.getBlockPos(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        rack.useItem(player, location, InteractionHand.MAIN_HAND);
        WorkLocationCardItem.record(location, level, machine.getBlockPos(), machineBlock);
        rack.items().setStackInSlot(0, location);
        var holder = level.getRecipeManager().byKey(Singulo.id("catalytic_reactor/copper_ingot_refined")).orElseThrow();
        var refining = (MachineRecipe) holder;
        console.setMachineProcess(refining, List.of());
        console.encoder().setItem(WorkConsoleBlockEntity.CARD, new ItemStack(SinguloItems.RECIPE_CARD.get()));
        h.assertTrue(console.encode(), "Cannot record copper refining");
        rack.items().setStackInSlot(1, console.encoder().getItem(WorkConsoleBlockEntity.CARD).copy());
        var target = refining.byproduct();
        // One batch must also suffice when two downstream steps use its separate outputs.
        var copperPlate = RecipeData.processing(List.of(refining.result()), new ItemStack(Items.LIGHTNING_ROD), List.of(), ItemStack.EMPTY);
        var assembly = RecipeData.processing(List.of(copperPlate.output(), target), new ItemStack(Items.COMPASS), List.of(), ItemStack.EMPTY);
        var entries = new ArrayList<>(console.recipes());
        entries.add(new RecipeRackBlockEntity.Entry(copperPlate, machine.getBlockPos(), machineBlock));
        entries.add(new RecipeRackBlockEntity.Entry(assembly, machine.getBlockPos(), machineBlock));
        var combined = WorkPlanner.plan(assembly.output(), 1, entries, console.available(), level);
        h.assertTrue(combined.ok() && combined.steps.size() == 3, "Reserved the same refining inputs twice");
        var order = WorkOrder.create(0, combined, player.getUUID());
        h.assertTrue(order.steps.get(2).after.equals(List.of(0, 1)), "Missing dependency on the bismuth producer");
        var products = WorkConsoleView.build(console, player, null).getList("entries", Tag.TAG_COMPOUND);
        h.assertTrue(products.stream().map(CompoundTag.class::cast).anyMatch(
                row -> row.getBoolean("craft") && ItemCount.load(row, level.registryAccess()).matches(target)), "Bismuth missing from order screen");
        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        worker.makeGuard(player.getUUID(), h.absolutePos(at.south()), RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at.south())));
        worker.inventory().setItem(RuinAndroid.SLOT_TANK, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_tank_upgrade"))));
        level.addFreshEntity(worker);
        h.assertTrue(console.start(player, target, 1, List.of(worker.getUUID()), null) == null, "Cannot order bismuth");
        h.onEachTick(() -> machine.energy().setEnergy(machine.energy().getMaxEnergyStored()));
        h.succeedWhen(() -> {
            h.assertTrue(console.orders().isEmpty() && chest.countItem(target.getItem()) + console.outbox().countItem(target.getItem()) == 1,
                    "Bismuth order has not arrived");
            h.assertTrue(chest.countItem(Items.COPPER_INGOT) == 6, "Copper did not return to stock");
            h.assertTrue(chest.countItem(Items.RAW_COPPER) == 0 && tank.receiveTank().getFluidAmount() == 0,
                    "Refining did not consume one batch");
            worker.discard();
        });
    }

    @GameTest(template = "empty")
    public static void androidRecordsPlansAndCraftsEverySinguloTableRecipe(GameTestHelper h) throws Exception {
        h.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(1, 1, 1));
        BlockPos benchPos = new BlockPos(3, 1, 1);
        h.setBlock(benchPos, Blocks.CRAFTING_TABLE);
        BlockPos bench = h.absolutePos(benchPos);
        var level = h.getLevel();
        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        worker.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(
                BuiltInRegistries.ITEM.get(Singulo.id("android_large_backpack_upgrade"))));
        var goal = new AndroidWorkGoal(worker);
        var craft = AndroidWorkGoal.class.getDeclaredMethod("craftOnce", ServerLevel.class, WorkConsoleBlockEntity.class,
                WorkJob.class, BlockPos.class, RecipeData.class);
        craft.setAccessible(true);
        var job = WorkJob.simple(WorkJob.Type.CRAFT, 1);
        int checked = 0;
        var craftedItems = new HashSet<Item>();
        List<String> problems = new ArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!holder.getId().getNamespace().equals(Singulo.MODID)) continue;
            var recipe = holder;
            var grid = NonNullList.withSize(9, ItemStack.EMPTY);
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            var ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient.isEmpty()) continue;
                h.assertTrue(ingredient.getItems().length > 0, "Empty ingredient in " + holder.getId());
                grid.set(i / width * 3 + i % width, ingredient.getItems()[0].copyWithCount(1));
            }
            var input = CraftingInput.of(3, 3, grid);
            var expected = recipe.assemble(input, level.registryAccess());
            h.assertTrue(recipe.matches(input, level) && !expected.isEmpty(), "Unusable sample grid for " + holder.getId());
            console.setGrid(grid);
            console.encoder().setItem(WorkConsoleBlockEntity.CARD, new ItemStack(SinguloItems.RECIPE_CARD.get()));
            if (!console.encode()) {
                problems.add(holder.getId() + " failed to encode");
                continue;
            }
            RecipeData card = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
            if (card == null || !ItemStack.matches(expected, card.output())) {
                problems.add(holder.getId() + " recorded " + (card == null ? "no result" : card.output()) + " instead of " + expected);
                continue;
            }
            var stock = new ArrayList<ItemCount>();
            for (int slot : worker.carrySlots()) worker.inventory().setItem(slot, ItemStack.EMPTY);
            for (var stack : grid) {
                if (stack.isEmpty()) continue;
                ItemCount.add(stock, stack, 1);
                h.assertTrue(worker.addToCarry(stack.copy()).isEmpty(), "Sample did not fit: " + holder.getId());
            }
            var entry = new RecipeRackBlockEntity.Entry(card, bench, Blocks.CRAFTING_TABLE);
            var plan = WorkPlanner.plan(expected, expected.getCount(), List.of(entry), stock);
            if (!plan.ok() || !(boolean) craft.invoke(goal, level, console, job, bench, card)) {
                problems.add(holder.getId() + " could not plan or craft");
                continue;
            }
            var products = new ArrayList<ItemCount>();
            ItemCount.add(products, expected, expected.getCount());
            for (var rest : recipe.getRemainingItems(input)) ItemCount.add(products, rest, rest.getCount());
            for (int slot : worker.carrySlots()) {
                var stack = worker.inventory().getItem(slot);
                ItemCount.add(products, stack, -stack.getCount());
            }
            if (products.stream().anyMatch(product -> product.count != 0)) problems.add(holder.getId() + " wrong products or remainders");
            checked++;
            craftedItems.add(expected.getItem());
        }
        for (var stone : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            if (stone.getId().getNamespace().equals(Singulo.MODID) && !craftedItems.contains(
                    stone.getResultItem(level.registryAccess()).getItem())) problems.add(stone.getId() + " has no workbench route");
        }
        Singulo.LOGGER.info("Android workbench catalogue: {} recipes crafted", checked);
        h.assertTrue(checked > 0 && problems.isEmpty(), "Android workbench catalogue: " + problems);
        h.succeed();
    }
}
