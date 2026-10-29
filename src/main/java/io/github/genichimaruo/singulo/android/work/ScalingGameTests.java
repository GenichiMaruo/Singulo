package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Large-order arithmetic and bounded server work, independent of renderer/tick timing. */
@GameTestHolder("singulo_scaling")
@PrefixGameTestTemplate(false)
public final class ScalingGameTests {
    private static ItemStack named(int id) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("scaling-" + id));
        return s;
    }

    private static RecipeRackBlockEntity.Entry recipe(ItemStack out, ItemStack... inputs) {
        var grid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < inputs.length; i++) grid.set(i, inputs[i]);
        return new RecipeRackBlockEntity.Entry(new RecipeData(RecipeData.MODE_PROCESSING, grid, out, List.of()),
                BlockPos.ZERO, Blocks.FURNACE);
    }

    @GameTest(template = "empty")
    public static void fourThousandItemsWithSixHundredRecipes(GameTestHelper h) {
        var recipes = new ArrayList<RecipeRackBlockEntity.Entry>();
        for (int i = 0; i < 598; i++) recipes.add(recipe(named(i), new ItemStack(Items.DIAMOND)));
        recipes.add(recipe(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
        recipes.add(recipe(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS, 2)));
        var plan = WorkPlanner.plan(new ItemStack(Items.STICK), 4096, recipes,
                new ArrayList<>(List.of(new ItemCount(new ItemStack(Items.OAK_LOG), 512))));
        h.assertTrue(plan.ok() && plan.steps.size() == 2, "A bulk order expanded into individual crafts");
        h.assertTrue(plan.steps.get(0).crafts() == 512 && plan.steps.get(1).crafts() == 1024,
                "Bulk intermediate counts are wrong");
        h.assertTrue(ItemCount.get(plan.fromStock, new ItemStack(Items.OAK_LOG)) == 512, "Wrong material reservation");
        WorkOrder saved = WorkOrder.load(WorkOrder.create(1, plan, null).save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(saved != null && saved.count == 4096 && saved.steps.size() == 2
                && !saved.ready(saved.steps.get(1)), "Reload lost quantities or dependencies");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void futureSurplusIsNotReservedFromExistingStock(GameTestHelper h) {
        var recipes = List.of(recipe(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)),
                recipe(named(1), new ItemStack(Items.OAK_PLANKS, 3)),
                recipe(named(2), new ItemStack(Items.OAK_PLANKS, 3)), recipe(named(3), named(1), named(2)));
        var plan = WorkPlanner.plan(named(3), 1, recipes,
                new ArrayList<>(List.of(new ItemCount(new ItemStack(Items.OAK_LOG), 2))));
        h.assertTrue(plan.ok(), "Could not reuse the intermediate surplus");
        h.assertTrue(ItemCount.get(plan.fromStock, new ItemStack(Items.OAK_PLANKS)) == 0,
                "Future planks are incorrectly shown and reserved as existing materials");
        h.assertTrue(ItemCount.get(plan.fromStock, new ItemStack(Items.OAK_LOG)) == 2, "Wrong log count");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multipliedMaterialCountsCannotWrapToZero(GameTestHelper h) {
        var recipes = new ArrayList<RecipeRackBlockEntity.Entry>();
        for (int i = 0; i < 12; i++) recipes.add(recipe(named(i), named(i + 1).copyWithCount(64)));
        var plan = WorkPlanner.plan(named(0), 4096, recipes, new ArrayList<>());
        h.assertFalse(plan.ok(), "Multiplication overflow accepted an order without any materials");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void branchingPlansHaveBoundedWork(GameTestHelper h) {
        // A shallow DAG can still unfold into millions of visits. Abort the old implementation
        // deterministically rather than freezing the GameTest server to demonstrate the problem.
        var recipes = new ArrayList<RecipeRackBlockEntity.Entry>() {
            int visits;
            @Override public java.util.Iterator<RecipeRackBlockEntity.Entry> iterator() {
                var delegate = super.iterator();
                return new java.util.Iterator<>() {
                    public boolean hasNext() { return delegate.hasNext(); }
                    public RecipeRackBlockEntity.Entry next() {
                        if (++visits > 100_000) throw new IllegalStateException("Recipe scans exceeded the work budget");
                        return delegate.next();
                    }
                };
            }
        };
        for (int i = 0; i < 22; i++) {
            recipes.add(recipe(named(2 * i), named(2 * i + 2), named(2 * i + 3)));
            recipes.add(recipe(named(2 * i + 1), named(2 * i + 2), named(2 * i + 3)));
        }
        var plan = WorkPlanner.plan(named(0), 4096, recipes, new ArrayList<>());
        h.assertFalse(plan.ok(), "Unavailable leaf materials were ignored");
        h.assertTrue(plan.steps.size() <= 1024, "Unbounded number of serialized steps");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rackCacheSeesEditsAndDoesNotLoadRemoteProcedures(GameTestHelper h) {
        var level = h.getLevel();
        var rack = new RecipeRackBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)),
                io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_RECIPE_RACK.get().defaultBlockState());
        ItemStack card = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.PROCEDURE_CARD.get());
        BlockPos remote = h.absolutePos(new BlockPos(20000, 1, 20000));
        h.assertFalse(level.isLoaded(remote), "Remote fixture was already loaded");
        RecipeData.procedure(List.of(new ProcedureStep(ProcedureStep.MINE, remote, remote, ItemStack.EMPTY)), named(1))
                .write(card, level.registryAccess());
        rack.items().setStackInSlot(1, card);
        var first = new ArrayList<RecipeRackBlockEntity.Entry>();
        var second = new ArrayList<RecipeRackBlockEntity.Entry>();
        rack.entries(level, first);
        rack.entries(level, second);
        h.assertFalse(level.isLoaded(remote), "Opening a recipe catalogue synchronously loaded a remote chunk");
        h.assertTrue(first.getFirst().recipe() == second.getFirst().recipe(), "Unchanged cards were decoded again");
        RecipeData.procedure(List.of(new ProcedureStep(ProcedureStep.MINE, remote, remote, ItemStack.EMPTY)), named(2))
                .write(card, level.registryAccess());
        second.clear(); rack.entries(level, second);
        h.assertTrue(ItemStack.isSameItemSameComponents(second.getFirst().recipe().output(), named(2)), "In-place edit stayed cached");
        rack.items().setStackInSlot(1, ItemStack.EMPTY);
        second.clear(); rack.entries(level, second);
        h.assertTrue(second.isEmpty(), "Removed recipe remained cached");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sixteenStandingOrdersReadStockOnce(GameTestHelper h) throws Exception {
        var console = new WorkConsoleBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)),
                io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState()) {
            int reads;
            @Override public List<ItemCount> stock() {
                reads++;
                var all = new ArrayList<ItemCount>();
                for (int i = 0; i < 16; i++) all.add(new ItemCount(named(i), 4096));
                return all;
            }
        };
        console.setLevel(h.getLevel());
        for (int i = 0; i < 16; i++) console.standing().add(new StandingOrder(i, named(i), 4096,
                StandingOrder.KEEP, null, List.of(), null));
        var run = WorkConsoleBlockEntity.class.getDeclaredMethod("runStanding", net.minecraft.server.level.ServerLevel.class);
        run.setAccessible(true); run.invoke(console, h.getLevel());
        h.assertTrue(console.reads == 1 && console.orders().isEmpty(), "Standing rules repeatedly scanned the same stock");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedAreaDoesNotConsumeStackedCards(GameTestHelper h) {
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        player.setShiftKeyDown(true);
        ItemStack cards = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.AREA_CARD.get(), 8);
        BlockPos first = h.absolutePos(new BlockPos(1, 1, 1));
        AreaCardItem.recordFirst(cards, h.getLevel(), first);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cards);
        BlockPos second = first.east(AreaCardItem.MAX_SIZE);
        var hit = new net.minecraft.world.phys.BlockHitResult(second.getCenter(), net.minecraft.core.Direction.UP, second, false);
        var result = cards.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result == net.minecraft.world.InteractionResult.FAIL && cards.getCount() == 8, "Rejected corner consumed one card");
        h.assertTrue(first.equals(AreaCardItem.first(cards)) && AreaCardItem.second(cards) == null, "Rejected corner changed the saved area");
        player.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void timeFieldsMatchNaiveQueriesAcrossFiveHundredSites(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(0, 100, 0));
        var owners = new ArrayList<BlockPos>();
        long start = System.nanoTime();
        try {
            for (int i = 0; i < 512; i++) {
                BlockPos at = origin.offset(i * 128, 0, -i * 128);
                owners.add(at);
                io.github.genichimaruo.singulo.machine.TimeFields.set(level, at, at, 16, true);
            }
            for (int round = 0; round < 20; round++) for (BlockPos at : owners) {
                h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, at) == 0, "Field affects its own generator");
                h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, at.west(16)) == 1, "Chunk boundary excluded sphere edge");
                h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, at.west(17)) == 0, "Field leaked outside radius");
                io.github.genichimaruo.singulo.machine.TimeFields.set(level, at, at, 16, true);
            }
            BlockPos owner = owners.getFirst(), moved = owner.north(80);
            io.github.genichimaruo.singulo.machine.TimeFields.set(level, owner, moved, 8, true);
            h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, owner.east()) == 0, "Moved field left stale coverage");
            h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, moved) == 1, "Moved center lost coverage");
            io.github.genichimaruo.singulo.machine.TimeFields.remove(level, owner);
            h.assertTrue(io.github.genichimaruo.singulo.machine.TimeFields.state(level, moved) == 0, "Removed field left stale coverage");
        } finally {
            for (BlockPos owner : owners) io.github.genichimaruo.singulo.machine.TimeFields.remove(level, owner);
        }
        io.github.genichimaruo.singulo.Singulo.LOGGER.info("Scaling check: 512 time fields, 30720 queries and 10240 unchanged updates: {} ms",
                (System.nanoTime() - start) / 1_000_000.0);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "scaling_catalogue", timeoutTicks = 600)
    public static void threeHundredMachinesShareDecodedRecipes(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 150, 0));
        var blocks = new ArrayList<BlockPos>();
        var registry = io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get();
        level.setBlockAndUpdate(origin, registry.defaultBlockState()); blocks.add(origin);
        var console = (WorkConsoleBlockEntity) level.getBlockEntity(origin);
        BlockPos chestPos = origin.south(); blocks.add(chestPos);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
        for (int i = 0; i < 8; i++) chest.setItem(i, new ItemStack(Items.OAK_LOG, 64));
        try {
            for (int r = 0; r < 50; r++) {
                BlockPos rp = origin.offset(-2 - r % 5 * 2, 0, r / 5 * 2);
                blocks.add(rp);
                level.setBlockAndUpdate(rp, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_RECIPE_RACK.get().defaultBlockState());
                var rack = (RecipeRackBlockEntity) level.getBlockEntity(rp);
                rack.setConsole(origin); console.addLink(rp);
                for (int row = 0; row < 6; row++) {
                    int machine = r * 6 + row;
                    BlockPos mp = origin.offset(2 + machine % 20 * 2, 0, machine / 20 * 2);
                    blocks.add(mp); level.setBlockAndUpdate(mp, Blocks.FURNACE.defaultBlockState());
                    ItemStack loc = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get());
                    WorkLocationCardItem.record(loc, level, mp, Blocks.FURNACE);
                    rack.items().setStackInSlot(row * RecipeRackBlockEntity.COLS, loc);
                    for (int col = 1; col <= 2; col++) {
                        int n = machine * 2 + col - 1;
                        RecipeData data = n == 598 ? recipe(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)).recipe()
                                : n == 599 ? recipe(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS, 2)).recipe()
                                : recipe(named(n), new ItemStack(Items.DIAMOND)).recipe();
                        ItemStack card = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.RECIPE_CARD.get());
                        data.write(card, level.registryAccess());
                        rack.items().setStackInSlot(row * RecipeRackBlockEntity.COLS + col, card);
                    }
                }
            }
            var entries = console.recipes();
            h.assertTrue(entries.size() == 600, "Lost registered machines/recipes");
            long start = System.nanoTime();
            for (int repeat = 0; repeat < 50; repeat++) {
                var again = console.recipes();
                for (int e = 0; e < 600; e++) h.assertTrue(again.get(e).recipe() == entries.get(e).recipe(), "Unchanged recipe decoded repeatedly");
                var plan = console.plan(new ItemStack(Items.STICK), 4096);
                h.assertTrue(plan.ok() && plan.steps.size() == 2 && plan.fromStock.getFirst().count == 512, "Large catalogue changed order calculation");
            }
            io.github.genichimaruo.singulo.Singulo.LOGGER.info("Scaling check: 300 placed machines, 50 racks, 600 recipes, 50 bulk plans: {} ms",
                    (System.nanoTime() - start) / 1_000_000.0);
        } finally {
            for (BlockPos pos : blocks) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hundredWorkersDoNotShareAnActiveWorkbench(GameTestHelper h) {
        var console = new WorkConsoleBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)),
                io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState());
        console.setLevel(h.getLevel());
        var workers = new ArrayList<io.github.genichimaruo.singulo.android.BladeAndroid>();
        try {
            for (int i = 0; i < 100; i++) {
                var worker = io.github.genichimaruo.singulo.registry.SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
                worker.makeGuard(null, console.getBlockPos(), 0, 30);
                workers.add(worker);
            }
            for (int i = 0; i < 300; i++) {
                var order = new WorkOrder(i + 1, named(i), 4096, null);
                var data = recipe(named(i)).recipe();
                order.steps.add(new WorkOrder.StepState(data, console.getBlockPos().offset(i, 0, 8), Blocks.CRAFTING_TABLE, 4096));
                for (var worker : workers) order.workers.add(worker.getUUID());
                console.orders().add(order);
            }
            var occupied = new java.util.HashSet<BlockPos>();
            long start = System.nanoTime();
            for (var worker : workers) {
                worker.assignWork(console.getBlockPos(), 1);
                WorkJob job = console.requestJob(worker);
                h.assertTrue(job.type() == WorkJob.Type.CRAFT && !job.batch().isEmpty(), "Worker received no available task: index=" + occupied.size() + " type=" + job.type() + " slots=" + worker.carryCapacity() + " orders=" + console.orders().size());
                for (var batch : job.batch()) h.assertTrue(occupied.add(batch.location()), "Two workers leased the same machine");
            }
            io.github.genichimaruo.singulo.Singulo.LOGGER.info("Scaling check: 100 scheduling workers, 300 bulk orders: {} ms",
                    (System.nanoTime() - start) / 1_000_000.0);
        } finally { workers.forEach(net.minecraft.world.entity.Entity::discard); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void largeAreaSearchIsBoundedAndPreservesLayerPriority(GameTestHelper h) {
        var search = new WorkAreaSearch();
        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 0, 0, 127, 127, 127);
        int[] calls = {0};
        BlockPos expected = new BlockPos(127, 127, 127), result = null;
        for (int tick = 0; tick < 20 && result == null; tick++) {
            calls[0] = 0;
            result = search.find(box, WorkAreaSearch.Order.TOP_DOWN, BlockPos.ZERO, tick, p -> {
                calls[0]++;
                return p.equals(expected) || p.equals(BlockPos.ZERO);
            });
            h.assertTrue(calls[0] <= WorkAreaSearch.PER_TICK, "One area search exceeded its tick budget");
            if (result == null) {
                int before = calls[0];
                search.find(box, WorkAreaSearch.Order.TOP_DOWN, BlockPos.ZERO, tick, p -> { calls[0]++; return false; });
                h.assertTrue(calls[0] == before, "Repeated polls bypassed the tick budget");
            }
        }
        h.assertTrue(expected.equals(result), "Large scan lost the highest layer or nearest target");
        search.reset();
        result = search.find(box, WorkAreaSearch.Order.BOTTOM_UP, BlockPos.ZERO, 100, p -> p.equals(BlockPos.ZERO));
        for (int tick = 101; result == null && tick < 120; tick++) result = search.find(box, WorkAreaSearch.Order.BOTTOM_UP, BlockPos.ZERO, tick, p -> p.equals(BlockPos.ZERO));
        h.assertTrue(BlockPos.ZERO.equals(result), "Bottom-first search changed priority");
        search.reset();
        var plane = new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 0, 0, 127, 0, 127);
        search.find(plane, WorkAreaSearch.Order.NEAREST, BlockPos.ZERO, 200, p -> p.equals(BlockPos.ZERO));
        for (int tick = 201; tick < 220 && search.pending(); tick++) {
            h.assertTrue(search.find(plane, WorkAreaSearch.Order.NEAREST, BlockPos.ZERO, tick, p -> false) == null, "Removed candidate remained usable");
        }
        h.assertFalse(search.pending(), "Area search did not finish");
        h.succeed();
    }
}
