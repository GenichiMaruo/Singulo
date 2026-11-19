package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidBucketCraftingGameTests {
    private static final BlockPos CONSOLE = new BlockPos(2, 1, 2), CHEST = CONSOLE.west(), TANK = CONSOLE.east();
    private static final BlockPos RACK = new BlockPos(2, 1, 5), TABLE = new BlockPos(4, 1, 4);

    private static RecipeData recipe() {
        var grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(7, new ItemStack(Items.LAVA_BUCKET));
        grid.set(8, new ItemStack(Items.OBSIDIAN));
        return new RecipeData(grid, new ItemStack(Items.CRYING_OBSIDIAN));
    }

    private static void equipTank(RuinAndroid worker) {
        worker.inventory().setItem(RuinAndroid.SLOT_TANK, new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("singulo", "android_tank_upgrade"))));
    }

    private static RuinAndroid tankWorker(GameTestHelper h, java.util.UUID owner, int amount) {
        var worker = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        var pos = h.absolutePos(CONSOLE.south());
        worker.makeGuard(owner, pos, RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(pos));
        equipTank(worker);
        worker.fillTanks(new FluidStack(Fluids.LAVA, amount), false);
        h.getLevel().addFreshEntity(worker);
        return worker;
    }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void preloadedWorkerCraftsWithNoExternalStorage(GameTestHelper h) {
        var level = h.getLevel();
        var player = TestScope.player(h);
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        WorkConsoleBlockEntity console = h.getBlockEntity(CONSOLE);
        console.setOwner(player.getUUID());
        RecipeRackBlockEntity rack = h.getBlockEntity(RACK);
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, h.absolutePos(CONSOLE), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        rack.useItem(player, location, InteractionHand.MAIN_HAND);
        WorkLocationCardItem.record(location, level, h.absolutePos(TABLE), Blocks.CRAFTING_TABLE);
        rack.items().setStackInSlot(0, location);
        var grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(4, new ItemStack(Items.LAVA_BUCKET));
        var target = new ItemStack(Items.MAGMA_BLOCK);
        var card = new ItemStack(SinguloItems.RECIPE_CARD.get());
        new RecipeData(grid, target).write(card, level.registryAccess());
        rack.items().setStackInSlot(1, card);
        var original = AndroidOrders.recipes(level, console.getBlockPos(), player.getUUID()).recipes().getFirst();
        h.assertTrue(original.materials().getFirst().sample().is(Items.LAVA_BUCKET), "Original API lost its bucket input");
        var fluidVariants = AndroidOrders.fluidCraftingRecipes(level, console.getBlockPos(), player.getUUID()).recipes();
        h.assertTrue(fluidVariants.size() == 1, "Missing validated fluid recipe");
        var variant = fluidVariants.getFirst();
        var material = variant.materials().getFirst();
        h.assertTrue(material.liquid() && material.count() == 1000
                && AndroidEscrows.fluidOf(material.sample()).getFluid().isSame(Fluids.LAVA)
                && variant.outputs().size() == 1 && variant.outputs().getFirst().sample().is(Items.MAGMA_BLOCK),
                "Fluid recipe changed units, product, or promised a phantom bucket");
        h.assertTrue(AndroidOrders.fluidCraftingRecipes(level, console.getBlockPos(), java.util.UUID.randomUUID()).state()
                == AndroidOrders.SnapshotState.REJECTED, "Non-owner read fluid recipes");
        var overridingCard = new ItemStack(SinguloItems.RECIPE_CARD.get());
        var competingGrid = NonNullList.withSize(9, ItemStack.EMPTY);
        competingGrid.set(4, new ItemStack(Items.MAGMA_CREAM));
        new RecipeData(competingGrid, target).write(overridingCard, level.registryAccess());
        rack.items().setStackInSlot(1, overridingCard);
        rack.items().setStackInSlot(2, card);
        h.assertTrue(AndroidOrders.fluidCraftingRecipes(level, console.getBlockPos(), player.getUUID()).recipes().isEmpty(),
                "Fluid snapshot exposed a card which the planner never selects");
        rack.items().setStackInSlot(1, card);
        rack.items().setStackInSlot(2, ItemStack.EMPTY);
        var worker = tankWorker(h, player.getUUID(), 500);
        var other = tankWorker(h, player.getUUID(), 500);
        var ids = List.of(other.getUUID(), worker.getUUID());
        h.assertTrue(console.start(player, target, 1, ids, null) != null, "Fluid in separate workers was counted as one supply");
        worker.fillTanks(new FluidStack(Fluids.LAVA, 750), false);
        h.assertTrue(console.preview(player, target, 1, false).ok(), "Console preview did not see an available worker's tank");
        h.assertTrue(console.start(player, target, 1, ids, null) == null, "Order refused the preloaded tank");
        var order = console.orders().getFirst();
        h.assertTrue(order.workers.size() == 1 && order.workers.contains(worker.getUUID()) && order.held.isEmpty(),
                "The tank must stay with its assigned worker");
        h.assertTrue(console.start(player, target, 1, ids, null) != null, "Busy worker's fluid was reserved twice");
        var saved = worker.saveWorkState();
        worker.restoreWorkState(saved);
        var lava = new FluidStack(Fluids.LAVA, 1);
        var water = new FluidStack(Fluids.WATER, 1);
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(console.orders().isEmpty() && console.outbox().countItem(Items.MAGMA_BLOCK) == 1 && worker.workConsole() == null,
                    "Worker-only order did not deliver and return to idle: " + worker.state() + "/" + worker.stall());
            h.assertTrue(worker.tankAmount(lava) == 250 && other.tankAmount(lava) == 500,
                    "Craft changed the unspent tank balance or used another worker's lava");
            h.assertTrue(console.outbox().countItem(Items.BUCKET) == 0 && worker.inventory().countItem(Items.BUCKET) == 0,
                    "Tank-only order created a bucket");
        }).thenExecute(() -> {
            console.outbox().clearContent();
            h.setBlock(CHEST, Blocks.CHEST);
            ChestBlockEntity chest = h.getBlockEntity(CHEST);
            chest.setItem(0, new ItemStack(Items.LAVA_BUCKET));
            h.assertTrue(worker.fillTanks(lava.copyWithAmount(875), false) == 875
                    && worker.fillTanks(water.copyWithAmount(375), false) == 375, "Failed to prepare two onboard balances");
            h.assertTrue(console.start(player, target, 1, List.of(worker.getUUID()), null) == null, "Real-bucket order refused");
        }).thenWaitUntil(() -> {
            ChestBlockEntity chest = h.getBlockEntity(CHEST);
            h.assertTrue(console.orders().isEmpty() && worker.workConsole() == null,
                    "Real-bucket order did not finish without an external tank: " + worker.state() + "/" + worker.stall());
            h.assertTrue(console.outbox().countItem(Items.MAGMA_BLOCK) + chest.countItem(Items.MAGMA_BLOCK) == 1
                    && chest.countItem(Items.BUCKET) == 1 && chest.countItem(Items.LAVA_BUCKET) == 0,
                    "Real-bucket order must consume its bucket and return one empty bucket");
            h.assertTrue(worker.tankAmount(lava) == 1125 && worker.tankAmount(water) == 375,
                    "Item-only order changed a preloaded tank");
            worker.discard();
            other.discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1800)
    public static void realBucketAndTankOrderReturnsExactlyOneEmptyBucket(GameTestHelper h) {
        var level = h.getLevel();
        var player = TestScope.player(h);
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(CHEST, Blocks.CHEST);
        h.setBlock(TANK, SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        WorkConsoleBlockEntity console = h.getBlockEntity(CONSOLE);
        ChestBlockEntity chest = h.getBlockEntity(CHEST);
        FluidDronePortBlockEntity tank = h.getBlockEntity(TANK);
        RecipeRackBlockEntity rack = h.getBlockEntity(RACK);
        var location = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(location, level, h.absolutePos(CONSOLE), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        rack.useItem(player, location, InteractionHand.MAIN_HAND);
        WorkLocationCardItem.record(location, level, h.absolutePos(TABLE), Blocks.CRAFTING_TABLE);
        rack.items().setStackInSlot(0, location);
        var card = new ItemStack(SinguloItems.RECIPE_CARD.get());
        var recipe = recipe();
        recipe.write(card, level.registryAccess());
        rack.items().setStackInSlot(1, card);
        chest.setItem(0, new ItemStack(Items.OBSIDIAN, 3));
        var lava = FluidSamples.key(FluidSamples.of(new FluidStack(Fluids.LAVA, 1)));
        tank.receiveTank().fill(new FluidStack(Fluids.LAVA, 999), FluidAction.EXECUTE);
        var shortPlan = console.plan(recipe.output(), 1);
        h.assertTrue(!shortPlan.ok() && ItemCount.get(shortPlan.missing, lava) == 1
                        && tank.receiveTank().getFluidAmount() == 999, "A partial bucket must be one mB short");
        chest.setItem(1, new ItemStack(Items.LAVA_BUCKET));
        tank.receiveTank().fill(new FluidStack(Fluids.LAVA, 1176), FluidAction.EXECUTE);
        var plan = console.plan(recipe.output(), 3);
        h.assertTrue(plan.ok() && plan.steps.size() == 2 && ItemCount.get(plan.fromStock, lava) == 2000
                && ItemCount.get(plan.fromStock, new ItemStack(Items.LAVA_BUCKET)) == 1, "Mixed order reserved the wrong amounts");

        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        worker.makeGuard(player.getUUID(), h.absolutePos(CONSOLE.south()), RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(h.absolutePos(CONSOLE.south())));
        equipTank(worker);
        level.addFreshEntity(worker);
        var error = console.start(player, recipe.output(), 3, List.of(worker.getUUID()), null);
        h.assertTrue(error == null, "Ordinary order rejected: " + error);
        var saved = console.saveWithoutMetadata(level.registryAccess());
        console.loadWithComponents(saved, level.registryAccess());
        h.succeedWhen(() -> {
            // The console automatically empties its outbox into the adjacent chest.
            h.assertTrue(console.outbox().countItem(Items.CRYING_OBSIDIAN) + chest.countItem(Items.CRYING_OBSIDIAN) == 3,
                    "Products missing: chest obsidian="
                    + chest.countItem(Items.OBSIDIAN) + ", buckets=" + chest.countItem(Items.BUCKET) + ", storage lava="
                    + tank.receiveTank().getFluidAmount() + ", carried lava=" + worker.liquidAmount(lava) + ", worker="
                    + worker.state() + "/" + worker.stall() + ", orders=" + console.orders().stream().map(o ->
                    o.status + "/" + o.delivered + "/" + o.steps.stream().map(s -> s.done + "+" + s.inProgress + "/" + s.total).toList()).toList()
                    + ", message=" + console.message());
            h.assertTrue(chest.countItem(Items.BUCKET) == 1 && chest.countItem(Items.LAVA_BUCKET) == 0,
                    "Physical bucket remainder was lost or duplicated");
            h.assertTrue(tank.receiveTank().getFluidAmount() == 175 && worker.liquidAmount(lava) == 0,
                    "Tank debit must be exactly 1000 mB per substituted slot");
            h.assertTrue(console.orders().isEmpty() && chest.countItem(Items.OBSIDIAN) == 0, "Order did not finish");
            worker.discard();
        });
    }

    @GameTest(template = "empty")
    public static void producedFluidBlocksDependentCraftUntilReady(GameTestHelper h) {
        var lava = FluidSamples.of(new FluidStack(Fluids.LAVA, 1000));
        var source = RecipeData.processing(NonNullList.withSize(9, ItemStack.EMPTY), lava, List.of(), ItemStack.EMPTY);
        var craft = recipe();
        var stock = List.of(new ItemCount(new ItemStack(Items.OBSIDIAN), 1));
        var entries = List.of(new RecipeRackBlockEntity.Entry(source, CONSOLE, null),
                new RecipeRackBlockEntity.Entry(craft, TABLE, Blocks.CRAFTING_TABLE));
        var plan = WorkPlanner.plan(craft.output(), 1, entries, stock, h.getLevel());
        h.assertTrue(plan.ok() && plan.steps.size() == 2, "Missing liquid-producing subrecipe");
        var order = WorkOrder.create(1, plan, null);
        h.assertTrue(order.steps.get(1).after.equals(List.of(0)), "Fluid-key quantity lost the dependency");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothOnboardTanksShareOneBalance(GameTestHelper h) {
        var worker = tankWorker(h, TestScope.player(h).getUUID(), 1);
        var lava = new FluidStack(Fluids.LAVA, 1);
        var sample = FluidSamples.of(lava);
        int capacity = worker.fluidCapacity();
        worker.fillTanks(lava.copyWithAmount(capacity + 999), false);
        worker.retainTanksForWork();
        h.assertTrue(worker.tankFluids().size() == 2 && worker.personalLiquid(sample) == capacity + 1000L
                && worker.carryEmpty(), "Two tanks lost or double-counted their preloaded balance");
        worker.consumeWorkLiquid(sample, 1000);
        worker.fillLiquid(sample, 500, false);
        h.assertTrue(worker.tankAmount(lava) == capacity + 500 && worker.personalLiquid(sample) == capacity
                && !worker.carryEmpty(), "Shared cargo must remain distinct from retained fluid of the same type");
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(TANK, SinguloBlocks.FLUID_DRONE_PORT.get());
        WorkConsoleBlockEntity console = h.getBlockEntity(CONSOLE);
        FluidDronePortBlockEntity tank = h.getBlockEntity(TANK);
        h.assertTrue(console.depositAt(worker, null, h.absolutePos(CONSOLE)) && worker.carryEmpty()
                && tank.sendTank().getFluidAmount() == 500 && worker.tankAmount(lava) == capacity,
                "Return moved fluid belonging to the worker");
        worker.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCraftKeepsLiquidAndRetryConsumesItOnce(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        equipTank(worker);
        var recipe = recipe().withTankBuckets(1 << 7);
        var slots = worker.carrySlots();
        for (int slot : slots) worker.inventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        worker.inventory().setItem(slots[0], new ItemStack(Items.OBSIDIAN, 2));
        var fluid = new FluidStack(Fluids.LAVA, 999);
        worker.fillTanks(fluid, false);
        var goalField = RuinAndroid.class.getDeclaredField("workGoal");
        goalField.setAccessible(true);
        Object goal = goalField.get(worker);
        var craft = goal.getClass().getDeclaredMethod("craftOnce", ServerLevel.class, WorkConsoleBlockEntity.class,
                WorkJob.class, BlockPos.class, RecipeData.class);
        craft.setAccessible(true);
        var at = h.absolutePos(TABLE);
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        h.assertTrue(!(boolean) craft.invoke(goal, level, null, null, at, recipe)
                && worker.tankAmount(fluid) == 999, "Partial tank consumed on failure");
        worker.fillTanks(fluid.copyWithAmount(1), false);
        h.assertTrue(!(boolean) craft.invoke(goal, level, null, null, at, recipe)
                        && worker.tankAmount(fluid) == 1000 && worker.inventory().getItem(slots[0]).getCount() == 2,
                "Blocked output consumed a material");
        worker.inventory().setItem(slots[1], ItemStack.EMPTY);
        h.assertTrue((boolean) craft.invoke(goal, level, null, null, at, recipe)
                        && worker.tankAmount(fluid) == 0 && worker.inventory().getItem(slots[0]).getCount() == 1
                        && worker.inventory().getItem(slots[1]).is(Items.CRYING_OBSIDIAN),
                "Craft did not use exactly one bucket of lava on retry");
        worker.inventory().setItem(slots[2], new ItemStack(Items.LAVA_BUCKET));
        var stale = new RecipeData(recipe.grid(), recipe.output().copyWithCount(2));
        h.assertTrue(!(boolean) craft.invoke(goal, level, null, null, at, stale)
                        && worker.inventory().getItem(slots[2]).is(Items.LAVA_BUCKET)
                        && worker.inventory().getItem(slots[0]).is(Items.OBSIDIAN),
                "A changed recipe output consumed its physical inputs");
        worker.discard();
        h.succeed();
    }
}
