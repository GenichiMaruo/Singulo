package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.food.FoodPrinterBlockEntity;
import io.github.genichimaruo.singulo.food.PrintedFood;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeLayout;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import io.github.genichimaruo.singulo.Singulo;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidMachineProcessingGameTests {
    private static final BlockPos AT = new BlockPos(2, 1, 2), CONSOLE = new BlockPos(1, 1, 1);

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    private static WorkConsoleBlockEntity console(GameTestHelper h) {
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
        c.encoder().setItem(WorkConsoleBlockEntity.CARD, item("recipe_card"));
        return c;
    }

    @GameTest(template = "empty")
    public static void upgradedCompactorPreservesThreeSlotSaves(GameTestHelper h) {
        var type = MachineType.DEGENERATE_COMPACTOR;
        h.setBlock(AT, SinguloBlocks.CONTROLLERS.get(type).get());
        MachineBlockEntity machine = h.getBlockEntity(AT);
        var reg = h.getLevel().registryAccess();
        var legacy = new net.neoforged.neoforge.items.ItemStackHandler(6);
        var items = List.of(item("degenerate_matter_shell"), item("steel_plate").copyWithCount(6),
                item("superconducting_coil").copyWithCount(3), item("reactor_shell").copyWithCount(24),
                item("overclock_chip"), io.github.genichimaruo.singulo.machine.CapacityUpgrades.item(2));
        for (int i = 0; i < items.size(); i++) legacy.setStackInSlot(i, items.get(i));
        var saved = machine.saveWithoutMetadata(reg);
        saved.put("items", legacy.serializeNBT(reg));
        saved.putIntArray("slot_layout", new int[]{3, 1});
        for (int version = 0; version < 2; version++) {
            machine.loadWithComponents(saved, reg);
            h.assertTrue(machine.items().getStackInSlot(3).isEmpty(), "The new input slot took an old output");
            for (int i = 0; i < items.size(); i++) {
                h.assertTrue(ItemStack.matches(items.get(i), machine.items().getStackInSlot(i < 3 ? i : i + 1)),
                        "Lost or moved the wrong legacy stack " + i);
            }
            saved.remove("slot_layout");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void androidSuppliesTwoToolsAndCombinesRemainingCatalystLife(GameTestHelper h) throws ReflectiveOperationException {
        var c = console(h);
        var type = MachineType.SINGULARITY_ENCAPSULATOR;
        h.setBlock(AT, SinguloBlocks.MACHINES.get(type).get());
        MachineBlockEntity machine = h.getBlockEntity(AT);
        var recipe = MachineBlockEntity.stationRecipes(h.getLevel(), type).stream().map(RecipeHolder::value)
                .filter(r -> r.result().is(item("singularity_core").getItem())).findFirst().orElseThrow();
        c.setMachineProcess(recipe, List.of());
        h.assertTrue(c.encode(), "Cannot record singularity_core");
        var card = RecipeData.read(c.encoder().getItem(WorkConsoleBlockEntity.CARD), h.getLevel().registryAccess());
        h.assertTrue(card.tools().size() == 2, "The second reusable tool was lost");
        var catalyst = card.tools().stream().filter(t -> t.tier() > 0).findFirst().orElseThrow();
        ItemStack fresh = card.grid().get(catalyst.slot());
        var shortByOne = io.github.genichimaruo.singulo.item.UsesHelper.consume(fresh,
                io.github.genichimaruo.singulo.item.UsesHelper.remaining(fresh) - catalyst.uses() + 1);
        var previous = io.github.genichimaruo.singulo.item.UsesHelper.consume(item("muon_catalyst"), 1);
        machine.items().setStackInSlot(type.catalystSlot(), previous.copy());
        MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        machine.items().setStackInSlot(type.catalystSlot(), shortByOne);
        MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        var work = setup(h, c, card);
        work.worker().addToCarry(fresh.copy());
        var original = card.tools().stream().filter(t -> t.tier() == 0).findFirst().orElseThrow();
        work.worker().addToCarry(card.grid().get(original.slot()).copy());
        for (int i = 0; i < 3; i++) {
            work.visit();
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        }
        h.assertTrue(MachineWatch.missingTool(h.getLevel(), machine.getBlockPos(), card) == null, "A required tool was not replenished");
        h.assertTrue(machine.inputStacks().stream().anyMatch(stack -> catalyst.crafts(fresh, stack) >= 1),
                "The loaded and spare catalyst lifetimes were not combined");
        boolean recovered = false;
        for (int slot : work.worker().carrySlots()) {
            recovered |= ItemStack.matches(previous, work.worker().inventory().getItem(slot));
        }
        h.assertTrue(recovered, "The previous partially used catalyst was left in the output");
        h.succeed();
    }

    @GameTest(template = "forge")
    public static void androidRecordsAndRoutesEveryMachineRecipe(GameTestHelper h) {
        var level = h.getLevel();
        var console = console(h);
        var recipes = RecipeTweaks.adjustAll(level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()));
        var checked = new HashSet<RecipeHolder<MachineRecipe>>();
        for (MachineType type : MachineType.values()) {
            Block block = type.isMultiblock() ? SinguloBlocks.CONTROLLERS.get(type).get() : SinguloBlocks.MACHINES.get(type).get();
            h.setBlock(AT, block);
            checkStation(h, console, AT, type.id(), recipes, checked);
        }
        h.setBlock(AT, SinguloBlocks.JET_FORGE_CORE.get());
        checkStation(h, console, AT, JetForgeBlockEntity.STATION, recipes, checked);
        var spindle = new BlockPos(15, 1, 16);
        Blueprints.layout(Blueprints.Kind.TIDAL_SPINDLE, h.absolutePos(spindle), Direction.NORTH, 7)
                .forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
        checkStation(h, console, spindle, "tidal_spindle", recipes, checked);
        h.assertTrue(checked.size() == recipes.size(), "A registered station has no Android coverage");
        h.succeed();
    }

    private static void checkStation(GameTestHelper h, WorkConsoleBlockEntity c, BlockPos at, String station,
                                     List<RecipeHolder<MachineRecipe>> recipes, HashSet<RecipeHolder<MachineRecipe>> checked) {
        var level = h.getLevel();
        var pos = h.absolutePos(at);
        int before = checked.size();
        var be = level.getBlockEntity(pos);
        var empty = be.saveWithoutMetadata(level.registryAccess());
        for (var holder : recipes) {
            var recipe = holder.value();
            if (!recipe.station().equals(station)) continue;
            c.clearGrid();
            c.setMachineProcess(recipe, List.of());
            h.assertTrue(c.encode(), "Cannot record " + holder.id());
            var card = RecipeData.read(c.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
            h.assertTrue(card != null && card.processing(), "Unreadable processing card " + holder.id());
            be.loadWithComponents(empty, level.registryAccess());
            if (be instanceof MachineBlockEntity machine) machine.selectFor(card.output(), card.grid());
            h.assertTrue(card.tools().size() == recipe.ingredients().stream().filter(in -> in.uses() > 0).count(),
                    holder.id() + " lost a reusable tool");
            for (var tool : card.tools()) {
                var sample = card.grid().get(tool.slot());
                int count = Math.max(1, (int) Math.ceil(tool.uses() / (double) io.github.genichimaruo.singulo.item.UsesHelper.remaining(sample)));
                insert(h, pos, card, sample, count, holder);
            }
            for (var in : card.inputs()) {
                insert(h, pos, card, in.item, (int) in.count, holder);
            }
            var physical = new java.util.ArrayList<ItemStack>();
            if (be instanceof MachineBlockEntity machine) {
                for (int i = 0; i < machine.type().recipeSlots(); i++) {
                    int slot = machine.type().recipeSlot(i);
                    physical.add(slot == machine.type().catalystSlot() ? machine.magazine().pooled(machine.items(), slot)
                            : recipe.isMassRecipe() && slot == 0 ? ItemStack.EMPTY : machine.items().getStackInSlot(slot));
                }
            } else if (be instanceof CosmicChamberBlockEntity chamber) {
                physical.add(chamber.items().getStackInSlot(0));
                physical.add(chamber.items().getStackInSlot(1));
            } else if (be instanceof JetForgeBlockEntity forge) {
                for (int i = 0; i < JetForgeBlockEntity.INPUTS; i++) physical.add(forge.items().getStackInSlot(JetForgeBlockEntity.SLOT_IN + i));
            }
            h.assertTrue(recipe.plan(physical) != null, holder.id() + " cannot consume the delivered stacks: " + physical);
            if (recipe.mass().isPresent()) h.assertFalse(card.inputs().isEmpty(), "Mass missing from " + holder.id());
            for (ItemStack product : card.outputs()) {
                var probe = RecipeData.processing(card.grid(), product, List.of(), ItemStack.EMPTY, RecipeData.DEFAULT_FACES);
                if (be instanceof MachineBlockEntity machine) {
                    if (FluidSamples.is(product)) machine.forceOutputTankForTest(FluidSamples.fluid(product));
                    else machine.items().setStackInSlot(machine.type().outputSlot(), product.copyWithCount(1));
                } else if (be instanceof CosmicChamberBlockEntity chamber) chamber.items().setStackInSlot(3, product.copy());
                else if (be instanceof JetForgeBlockEntity forge) forge.items().setStackInSlot(JetForgeBlockEntity.SLOT_OUT, product.copy());
                h.assertTrue(MachineWatch.outputFace(level, pos, probe) != null, holder.id() + " could not extract " + product);
            }
            checked.add(holder);
        }
        h.assertTrue(checked.size() > before, "No data recipes for " + station);
    }

    private static void insert(GameTestHelper h, BlockPos pos, RecipeData card, ItemStack sample, int count,
                               RecipeHolder<MachineRecipe> recipe) {
        var level = h.getLevel();
        Direction face = MachineWatch.inputFace(level, pos, card, sample);
        h.assertTrue(face != null, recipe.id() + " rejected " + sample);
        if (FluidSamples.liquid(sample)) {
            h.assertTrue(LiquidStores.machine(level, pos, face).fill(sample, count, false) == count, recipe.id() + " fluid did not fit");
        } else {
            var store = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, face);
            var rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(store, sample.copyWithCount(count), false);
            h.assertTrue(rest.isEmpty(), recipe.id() + " missing physical slots for " + rest);
        }
    }

    private record Processing(RuinAndroid worker, Object goal, Method method, ServerLevel level, WorkConsoleBlockEntity console,
                              WorkJob job, WorkJob.Batch batch) {
        void visit() {
            try {
                for (int i = 0; i < 10; i++) method.invoke(goal, level, console, job, batch);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Android processing visit failed", ex);
            }
        }
    }

    @GameTest(template = "forge", timeoutTicks = 2300)
    public static void androidCompletesEveryTidalSpindleRecipe(GameTestHelper h) throws ReflectiveOperationException {
        var at = new BlockPos(15, 1, 16);
        Blueprints.layout(Blueprints.Kind.TIDAL_SPINDLE, h.absolutePos(at), Direction.NORTH, 7)
                .forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
        CosmicChamberBlockEntity spindle = h.getBlockEntity(at);
        spindle.items().setStackInSlot(2, item("singularity_seed"));
        h.onEachTick(() -> spindle.energyFor(null).receiveLong(Long.MAX_VALUE, false));
        runSpecialCycles(h, at, "tidal_spindle", 6, () -> spindle.items().getStackInSlot(3));
    }

    @GameTest(template = "forge", timeoutTicks = 4700)
    public static void androidCompletesEveryJetForgeRecipe(GameTestHelper h) throws ReflectiveOperationException {
        var at = new BlockPos(15, 1, 16);
        Blueprints.layout(Blueprints.Kind.JET_FORGE, h.absolutePos(at), Direction.NORTH, JetForgeLayout.H)
                .forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
        JetForgeBlockEntity forge = h.getBlockEntity(at);
        forge.forceBlackHole();
        h.onEachTick(() -> forge.energy().receiveLong(Long.MAX_VALUE, false));
        runSpecialCycles(h, at, JetForgeBlockEntity.STATION, 2, () -> forge.items().getStackInSlot(JetForgeBlockEntity.SLOT_OUT));
    }

    private static void runSpecialCycles(GameTestHelper h, BlockPos at, String station, int minimumRecipes,
                                         Supplier<ItemStack> output) throws ReflectiveOperationException {
        var console = console(h);
        var recipes = RecipeTweaks.adjustAll(h.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()))
                .stream().filter(holder -> holder.value().station().equals(station)).toList();
        h.assertTrue(recipes.size() >= minimumRecipes, "Missing recipes for " + station);
        var sequence = h.startSequence().thenIdle(5);
        for (var holder : recipes) {
            var recipe = holder.value();
            console.clearGrid();
            console.setMachineProcess(recipe, List.of());
            h.assertTrue(console.encode(), "Cannot record " + holder.id());
            var card = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), h.getLevel().registryAccess());
            var work = setup(h, console, card, at);
            var worker = work.worker();
            worker.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade"));
            for (var in : card.inputs()) {
                if (FluidSamples.liquid(in.item)) {
                    h.assertTrue(worker.fillLiquid(in.item, in.count, false) == in.count, "Cannot load coolant for " + holder.id());
                } else {
                    h.assertTrue(worker.addToCarry(in.item.copyWithCount(Math.toIntExact(in.count))).isEmpty(),
                            "Cannot load inputs for " + holder.id());
                }
            }
            sequence.thenExecute(() -> {
                work.visit();
                h.assertTrue(Arrays.stream(worker.carrySlots()).allMatch(slot -> worker.inventory().getItem(slot).isEmpty())
                                && card.inputs().stream().filter(in -> FluidSamples.liquid(in.item)).allMatch(in -> worker.liquidAmount(in.item) == 0),
                        "Worker did not insert every input for " + holder.id());
            }).thenWaitUntil(() -> h.assertTrue(ItemStack.matches(output.get(), recipe.result()), "Awaiting product of " + holder.id()))
                    .thenExecute(() -> {
                        work.visit();
                        h.assertTrue(output.get().isEmpty() && Arrays.stream(worker.carrySlots())
                                        .anyMatch(slot -> ItemStack.matches(worker.inventory().getItem(slot), recipe.result())),
                                "Worker did not collect the full product of " + holder.id());
                    });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "empty")
    public static void androidFiresEverySinguloFurnaceRecipe(GameTestHelper h) throws ReflectiveOperationException {
        var c = console(h);
        var level = h.getLevel();
        int checked = 0;
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(Singulo.MODID)
                    || !(holder.value() instanceof AbstractCookingRecipe recipe)) continue;
            var block = recipe instanceof BlastingRecipe
                    ? Blocks.BLAST_FURNACE : Blocks.FURNACE;
            h.setBlock(AT, Blocks.AIR);
            h.setBlock(AT, block);
            AbstractFurnaceBlockEntity furnace = h.getBlockEntity(AT);
            var input = recipe.getIngredients().getFirst().getItems()[0].copyWithCount(1);
            var output = recipe.getResultItem(level.registryAccess());
            c.setProcess(List.of(input), List.of(output));
            h.assertTrue(c.encode(), "Could not record " + holder.id());
            var card = RecipeData.read(c.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
            var work = setup(h, c, card);
            work.worker().addToCarry(input.copy());
            furnace.setItem(1, new ItemStack(Items.COAL));
            work.visit();
            for (int tick = 0; tick <= recipe.getCookingTime(); tick++) {
                AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), furnace.getBlockState(), furnace);
            }
            work.visit();
            h.assertTrue(Arrays.stream(work.worker().carrySlots()).anyMatch(
                    slot -> ItemStack.matches(output, work.worker().inventory().getItem(slot))), "Missing product of " + holder.id());
            checked++;
        }
        h.assertTrue(checked > 0, "No Singulo furnace recipes were loaded");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void androidRefuelsFurnaceWithRecordedLavaBucketAndRecoversContainer(GameTestHelper h) throws ReflectiveOperationException {
        var console = console(h);
        var level = h.getLevel();
        h.setBlock(AT, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = h.getBlockEntity(AT);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (WorkConsoleMenu) console.createMenu(1, player.getInventory(), player);
        console.setProcess(List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
        menu.clicked(WorkConsoleMenu.SLOT_FUEL, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clickMenuButton(player, WorkConsoleMenu.BUTTON_ENCODE);
        var card = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
        h.assertTrue(card != null && card.fuel().is(Items.LAVA_BUCKET) && menu.getCarried().is(Items.LAVA_BUCKET),
                "Fuel slot consumed or changed the sample bucket");
        var legacy = card.save(level.registryAccess());
        var lava = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
        legacy.put("fuel", FluidSamples.of(lava).save(level.registryAccess()));
        h.assertTrue(RecipeData.load(legacy, level.registryAccess()).fuel().is(Items.LAVA_BUCKET),
                "Could not read the fuel on an older recipe card");
        var work = setup(h, console, card);
        work.worker().addToCarry(new ItemStack(Items.RAW_IRON));
        work.worker().addToCarry(new ItemStack(Items.LAVA_BUCKET));
        work.visit();
        work.visit();
        h.assertTrue(furnace.getItem(1).is(Items.LAVA_BUCKET), "Android did not refuel the furnace");
        for (int tick = 0; tick <= 200; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), furnace.getBlockState(), furnace);
        }
        work.visit();
        h.assertTrue(furnace.getItem(1).isEmpty() && work.worker().inventory().countItem(Items.BUCKET) == 1
                && work.worker().inventory().countItem(Items.IRON_INGOT) == 1, "Lost the product or the empty fuel bucket");
        // A new order can find a cold furnace with a bucket left by a previous run.
        h.setBlock(AT, Blocks.AIR);
        h.setBlock(AT, Blocks.FURNACE);
        furnace = h.getBlockEntity(AT);
        menu.setCarried(new ItemStack(Items.COAL));
        menu.clicked(WorkConsoleMenu.SLOT_FUEL, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clickMenuButton(player, WorkConsoleMenu.BUTTON_ENCODE);
        var coal = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        h.assertFalse(MachineWatch.outOfFuel(level, furnace.getBlockPos(), coal), "Fresh coal counted as an empty fuel slot");
        furnace.setItem(1, new ItemStack(Items.BUCKET));
        h.assertTrue(MachineWatch.outOfFuel(level, furnace.getBlockPos(), coal), "Parked order missed an old fuel bucket");
        furnace.setItem(0, ItemStack.EMPTY);
        furnace.setItem(2, new ItemStack(Items.BUCKET));
        var next = setup(h, console, coal);
        next.worker().addToCarry(new ItemStack(Items.COAL));
        next.worker().addToCarry(new ItemStack(Items.RAW_IRON));
        next.visit();
        h.assertTrue(furnace.getItem(1).isEmpty() && furnace.getItem(2).is(Items.BUCKET)
                && next.worker().inventory().countItem(Items.BUCKET) == 1, "Picked up the wrong bucket");
        furnace.setItem(2, ItemStack.EMPTY);
        next.visit();
        for (int tick = 0; tick <= 200; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), furnace.getBlockState(), furnace);
        }
        next.visit();
        h.assertTrue(next.worker().inventory().countItem(Items.IRON_INGOT) == 1 && next.worker().inventory().countItem(Items.COAL) == 0,
                "Coal did not resume smelting after the old bucket was removed");
        console.setProcess(List.of(new ItemStack(Items.WET_SPONGE)), List.of(new ItemStack(Items.SPONGE)));
        h.assertTrue(console.encode(), "Could not record sponge drying");
        var sponge = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
        var drying = setup(h, console, sponge);
        drying.worker().addToCarry(new ItemStack(Items.WET_SPONGE));
        // The preceding coal is still burning, so a bucket left in the fuel slot receives the water.
        furnace.setItem(1, new ItemStack(Items.BUCKET));
        drying.visit();
        drying.visit();
        h.assertTrue(furnace.getItem(1).is(Items.BUCKET), "Removed the sponge's bucket before it filled");
        for (int tick = 0; tick <= 200; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnace.getBlockPos(), furnace.getBlockState(), furnace);
        }
        drying.visit();
        h.assertTrue(drying.worker().inventory().countItem(Items.SPONGE) == 1 && drying.worker().inventory().countItem(Items.WATER_BUCKET) == 1,
                "Did not collect the dried sponge and its water");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void procedureCoordinatesRequireTheirRecordedDimension(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var console = console(h);
        var pos = h.absolutePos(AT);
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, pos, Blocks.LEVER);
        console.setMode(WorkConsoleBlockEntity.MODE_PROCEDURE);
        while (console.modeData().get(1) != ProcedureStep.USE) console.cycleStepKind(false);
        console.encoder().setItem(WorkConsoleBlockEntity.LOC_A, location);
        console.encoder().setItem(WorkConsoleBlockEntity.CARD, new ItemStack(SinguloItems.PROCEDURE_CARD.get()));
        console.encoder().setItem(WorkConsoleBlockEntity.RESULT, new ItemStack(Items.LEVER));
        h.assertTrue(console.addStep() == null && console.encode(), "Cannot record the lever procedure");
        var registries = level.registryAccess();
        var recorded = console.encoder().getItem(WorkConsoleBlockEntity.CARD);
        var carried = ItemStack.parse(registries, recorded.save(registries)).orElseThrow();
        var block = SinguloBlocks.ANDROID_RECIPE_RACK.get();
        h.setBlock(AT, block);
        RecipeRackBlockEntity rack = h.getBlockEntity(AT);
        rack.items().setStackInSlot(1, carried);
        var entries = new java.util.ArrayList<RecipeRackBlockEntity.Entry>();
        rack.entries(level, entries);
        h.assertTrue(entries.size() == 1 && entries.getFirst().location().equals(pos), "Original rack lost the procedure");
        entries.clear();
        rack.entries(nether, entries);
        h.assertTrue(entries.isEmpty(), "Nether rack offered an Overworld lever");

        // Cards and in-progress orders from older saves have coordinates but no dimension.
        var oldTag = RecipeData.read(carried, registries).save(registries);
        oldTag.getList("steps", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("dimension");
        var oldRecipe = RecipeData.load(oldTag, registries);
        oldRecipe.write(carried, registries);
        rack.entries(level, entries);
        h.assertTrue(entries.isEmpty(), "Rack guessed a dimension for an old card");
        var work = setup(h, console, oldRecipe);
        var order = new WorkOrder(work.job().orderId(), oldRecipe.output(), 1, null);
        console.orders().add(order);
        var procedure = work.goal().getClass().getDeclaredMethod("procedure", ServerLevel.class, WorkConsoleBlockEntity.class,
                WorkJob.class, WorkJob.Batch.class);
        procedure.setAccessible(true);
        procedure.invoke(work.goal(), level, console, work.job(), work.batch());
        h.assertTrue(console.order(order.id) == null, "Restored order proceeded at an unbound position");
        h.succeed();
    }

    private static Processing setup(GameTestHelper h, WorkConsoleBlockEntity c, RecipeData card) throws ReflectiveOperationException {
        return setup(h, c, card, AT);
    }

    private static Processing setup(GameTestHelper h, WorkConsoleBlockEntity c, RecipeData card, BlockPos at) throws ReflectiveOperationException {
        var a = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        a.makeGuard(null, h.absolutePos(at), RuinAndroid.MODE_GUARD, 30);
        var type = Class.forName("io.github.genichimaruo.singulo.android.AndroidWorkGoal");
        var constructor = type.getDeclaredConstructor(RuinAndroid.class);
        constructor.setAccessible(true);
        var goal = constructor.newInstance(a);
        var method = type.getDeclaredMethod("process", ServerLevel.class, WorkConsoleBlockEntity.class, WorkJob.class, WorkJob.Batch.class);
        method.setAccessible(true);
        var batch = new WorkJob.Batch(0, 1, h.absolutePos(at), card);
        var job = new WorkJob(WorkJob.Type.CRAFT, 0, 1, 0, List.of(), List.of(batch), true);
        return new Processing(a, goal, method, h.getLevel(), c, job, batch);
    }

    @GameTest(template = "empty", timeoutTicks = 2600)
    public static void androidRestoresItsMissingToolAndReusesItOnTheSameMachine(GameTestHelper h) {
        var level = h.getLevel();
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var c = console(h);
        var rackAt = new BlockPos(4, 1, 4);
        h.setBlock(rackAt, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(CONSOLE.east(), net.minecraft.world.level.block.Blocks.CHEST);
        h.setBlock(AT, SinguloBlocks.MACHINES.get(MachineType.ARCHIVE_TERMINAL).get());
        MachineBlockEntity machine = h.getBlockEntity(AT);
        h.onEachTick(() -> machine.energy().setEnergy(machine.energy().getMaxEnergyStored()));
        RecipeRackBlockEntity rack = h.getBlockEntity(rackAt);
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, h.absolutePos(CONSOLE), c.getBlockState().getBlock());
        rack.useItem(player, location, net.minecraft.world.InteractionHand.MAIN_HAND);
        WorkLocationCardItem.record(location, level, h.absolutePos(AT), machine.getBlockState().getBlock());
        rack.items().setStackInSlot(0, location);
        int column = 1;
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = h.getBlockEntity(CONSOLE.east());
        int stock = 0;
        for (String id : List.of("data_card_culture_data", "restore_culture_data")) {
            var recipe = MachineBlockEntity.stationRecipes(level, MachineType.ARCHIVE_TERMINAL).stream()
                    .filter(r -> r.id().getPath().endsWith("/" + id)).findFirst().orElseThrow().value();
            c.setMachineProcess(recipe, List.of());
            h.assertTrue(c.encode(), "Could not record " + id);
            ItemStack card = c.encoder().getItem(WorkConsoleBlockEntity.CARD).copy();
            rack.items().setStackInSlot(column++, card);
            var data = RecipeData.read(card, level.registryAccess());
            for (var in : data.inputs()) chest.setItem(stock++, in.item.copyWithCount((int) in.count * (data.tools().isEmpty() ? 1 : 2)));
        }
        var a = SinguloEntities.BLADE_ANDROID.get().create(level);
        a.makeGuard(player.getUUID(), h.absolutePos(AT), RuinAndroid.MODE_GUARD, 30);
        a.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(AT.south())));
        level.addFreshEntity(a);
        var target = item("data_card_culture_data");
        var order = c.start(player, target, 2, List.of(a.getUUID()), null);
        h.assertTrue(order == null, "Tool order could not start: " + order);
        h.succeedWhen(() -> {
            // The console pushes delivered items out to the adjacent chest every few ticks.
            h.assertTrue(c.outbox().countItem(target.getItem()) + chest.countItem(target.getItem()) == 2 && c.orders().isEmpty(),
                    "Two cards were not delivered");
            for (int i = 0; i < machine.type().inputSlots(); i++) {
                ItemStack tool = machine.items().getStackInSlot(i);
                if (tool.is(item("culture_data").getItem())) {
                    var state = io.github.genichimaruo.singulo.item.UsesHelper.get(tool);
                    h.assertTrue(state.used() == 2 && state.repairs() == 1, "The repaired original was not used exactly twice");
                    a.discard();
                    return;
                }
            }
            h.fail("The machine lost its reusable original");
        });
    }

    @GameTest(template = "tall")
    public static void androidFreezesLavaAndWaterUsingSuppliedNitrogen(GameTestHelper h) throws ReflectiveOperationException {
        Blueprints.layout(Blueprints.Kind.COOLING_TOWER, h.absolutePos(AT), Direction.SOUTH, 7)
                .forEach((p, s) -> h.getLevel().setBlockAndUpdate(p, s));
        var c = console(h);
        MachineBlockEntity tower = h.getBlockEntity(AT);
        tower.toggleMakeFree();
        for (var result : List.of(Items.OBSIDIAN, Items.PACKED_ICE)) {
            var recipe = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.CRYOGENIC_COOLING_TOWER).stream()
                    .map(RecipeHolder::value).filter(r -> r.result().is(result)).findFirst().orElseThrow();
            c.setMachineProcess(recipe, List.of());
            h.assertTrue(c.encode(), "Could not record tower recipe");
            var card = RecipeData.read(c.encoder().getItem(WorkConsoleBlockEntity.CARD), h.getLevel().registryAccess());
            var work = setup(h, c, card);
            work.worker().inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade"));
            for (var in : card.inputs()) work.worker().fillLiquid(in.item, in.count, false);
            work.visit();
            for (int i = 0; i < recipe.time() + 4; i++) {
                tower.energy().setEnergy(tower.energy().getMaxEnergyStored());
                MachineBlockEntity.serverTick(h.getLevel(), tower.getBlockPos(), tower.getBlockState(), tower);
            }
            work.visit();
            h.assertTrue(Arrays.stream(work.worker().carrySlots()).anyMatch(slot -> work.worker().inventory().getItem(slot).is(result)),
                    "The tower did not return " + result);
            for (int i = 0; i < tower.automationFluids().getTanks(); i++) h.assertTrue(tower.automationFluids().getFluidInTank(i).isEmpty(),
                    "The tower left paid coolant or feed in its tanks");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void androidCollectsBothElectrolysisProductsOnSeparateFaces(GameTestHelper h) throws ReflectiveOperationException {
        var c = console(h);
        h.setBlock(AT, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity machine = h.getBlockEntity(AT);
        var recipe = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.ELECTROLYZER).getFirst().value();
        c.setMachineProcess(recipe, List.of());
        h.assertTrue(c.encode(), "Cannot encode electrolysis");
        var card = RecipeData.read(c.encoder().getItem(WorkConsoleBlockEntity.CARD), h.getLevel().registryAccess());
        var work = setup(h, c, card);
        var a = work.worker();
        a.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade"));
        h.assertTrue(WorkConsoleBlockEntity.tankLimit(a, card) > 0, "Delivery needs separate inbound and outbound tank counts");
        for (var in : card.inputs()) a.fillLiquid(in.item, in.count, false);
        for (var side : SideConfig.Face.values()) {
            machine.tankSides(1).set(side, side == SideConfig.Face.TOP ? SideConfig.OUTPUT : SideConfig.NONE, false);
            machine.tankSides(2).set(side, side == SideConfig.Face.BOTTOM ? SideConfig.OUTPUT : SideConfig.NONE, false);
        }
        work.visit();
        for (int i = 0; i < recipe.time() + 4; i++) {
            machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        }
        work.visit();
        for (var out : card.outputs()) h.assertTrue(a.liquidAmount(out) == FluidSamples.amount(out), "Missing product " + out);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void androidPrintsRequestedFoodFromOverlappingIngredients(GameTestHelper h) throws ReflectiveOperationException {
        var c = console(h);
        h.setBlock(AT, SinguloBlocks.FOOD_PRINTER.get());
        FoodPrinterBlockEntity printer = h.getBlockEntity(AT);
        printer.select(PrintedFood.NUTRIENT_BAR.ordinal());
        for (int i = 0; i < 2; i++) printer.items().setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + i, new ItemStack(SinguloItems.FOOD_CARTRIDGE.get()));
        var inputs = List.of(new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.WHEAT, 3));
        var food = PrintedFood.SPIRAL_PASTA;
        var work = setup(h, c, RecipeData.processing(inputs, new ItemStack(food.item()), List.of(), ItemStack.EMPTY, RecipeData.DEFAULT_FACES));
        for (var in : inputs) work.worker().addToCarry(in.copy());
        work.visit();
        for (int i = 0; i < food.ticks() + 24; i++) {
            printer.energy().setEnergy(printer.energy().getMaxEnergyStored());
            FoodPrinterBlockEntity.serverTick(h.getLevel(), printer.getBlockPos(), printer.getBlockState(), printer);
        }
        work.visit();
        h.assertTrue(printer.selected() == food.ordinal(), "The wrong food is selected");
        for (int slot : work.worker().carrySlots()) {
            if (work.worker().inventory().getItem(slot).is(food.item())) {
                h.succeed();
                return;
            }
        }
        h.fail("The Android did not collect the recorded food");
    }
}
