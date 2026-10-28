package io.github.genichimaruo.singulo.android.work;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;

@GameTestHolder("singulo_scaling")
@PrefixGameTestTemplate(false)
public final class OptimizationGameTests {
    @GameTest(template = "empty")
    public static void blockedPathRetriesAreBoundedAndRecover(GameTestHelper h) {
        var retry = new PathRetry();
        BlockPos target = new BlockPos(5, 1, 5);
        int queries = 0;
        for (int tick = 0; tick < 600; tick++) if (retry.allow(target, tick, 10)) queries++;
        h.assertTrue(queries == 60, "Unreachable path was retried every tick");
        h.assertTrue(retry.allow(target.east(), 599, 10), "Changed destination did not trigger immediate search");
        h.assertFalse(retry.allow(target.east(), 600, 10), "Repeated path query bypassed cooldown");
        h.assertTrue(retry.allow(target.east(), 609, 10), "Opening a passage could never be retried");
        retry.reset();
        h.assertTrue(retry.allow(target.east(), 609, 10), "New job retained the preceding retry deadline");
        io.github.genichimaruo.singulo.Singulo.LOGGER.info("Optimization check: blocked paths, 600 repeated attempts -> {} path queries", queries);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cachedStorageRemainsLiveAndInvalidates(GameTestHelper h) {
        BlockPos source = new BlockPos(1, 1, 1), target = source.east();
        h.setBlock(source, SinguloBlocks.ANDROID_SUPPLY_PORT.get()); h.setBlock(target, Blocks.CHEST);
        SupplyPortBlockEntity port = h.getBlockEntity(source);
        ChestBlockEntity chest = h.getBlockEntity(target);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        var first = port.adjacentItems.get(Direction.EAST);
        h.assertTrue(first != null && first.getStackInSlot(0).getCount() == 3, "Initial storage missing");
        for (int i = 0; i < 100; i++) h.assertTrue(port.adjacentItems.get(Direction.EAST) == first, "Connection was unnecessarily rebuilt");
        BlockPos absoluteTarget = h.absolutePos(target);
        if (Boolean.getBoolean("singulo.testBenchmarks")) {
        for (int i = 0; i < 1000; i++) {
            h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, absoluteTarget, Direction.WEST);
            port.adjacentItems.get(Direction.EAST);
        }
        long start = System.nanoTime();
        long directSlots = 0, cachedSlots = 0;
        for (int i = 0; i < 20000; i++) directSlots += h.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, absoluteTarget, Direction.WEST).getSlots();
        long directNanos = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 20000; i++) cachedSlots += port.adjacentItems.get(Direction.EAST).getSlots();
        long cachedNanos = System.nanoTime() - start;
        h.assertTrue(directSlots == cachedSlots, "Cached storage exposes different slots");
        io.github.genichimaruo.singulo.Singulo.LOGGER.info("Optimization check: 20000 storage lookups, direct {} ms, cached {} ms", directNanos / 1e6, cachedNanos / 1e6);
        }
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        h.assertTrue(port.adjacentItems.get(Direction.EAST).getStackInSlot(0).getCount() == 7, "Cached inventory quantity became stale");
        h.setBlock(target, Blocks.AIR);
        h.assertTrue(port.adjacentItems.get(Direction.EAST) == null, "Removed storage retained its capability");
        h.setBlock(target, Blocks.CHEST);
        ChestBlockEntity replacement = h.getBlockEntity(target);
        replacement.setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
        h.assertTrue(port.adjacentItems.get(Direction.EAST).getStackInSlot(0).is(Items.GOLD_INGOT), "Replacement reused the old inventory");
        h.getLevel().invalidateCapabilities(h.absolutePos(target));
        h.assertTrue(port.adjacentItems.get(Direction.EAST).getStackInSlot(0).getCount() == 9, "Explicit capability invalidation broke lookup");
        port.setRemoved(); port.clearRemoved();
        h.assertTrue(port.adjacentItems.get(Direction.EAST).getStackInSlot(0).getCount() == 9, "Revived owner queried a retired cache");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void denseReadyQueueAvoidsRepeatedDependencyScans(GameTestHelper h) {
        var order = new WorkOrder(1, new ItemStack(Items.STICK), 4096, null);
        var recipe = new RecipeData(Collections.nCopies(9, ItemStack.EMPTY), new ItemStack(Items.STICK));
        for (int i = 0; i < 512; i++) {
            var step = new WorkOrder.StepState(recipe, new BlockPos(i, 1, 0), Blocks.CRAFTING_TABLE, 1);
            if (i < 256) step.done = 1;
            else for (int j = 0; j < 256; j++) step.after.add(j);
            order.steps.add(step);
        }
        int workers = Boolean.getBoolean("singulo.testBenchmarks") ? 100 : 2;
        long start = System.nanoTime();
        int baseline = 0;
        for (int worker = 0; worker < workers; worker++) for (var step : order.steps) {
            if (!step.finished() && order.ready(step)) baseline++;
        }
        long scan = System.nanoTime() - start;
        start = System.nanoTime();
        int queued = 0;
        List<Integer> first = order.readySteps();
        for (int worker = 0; worker < workers; worker++) {
            h.assertTrue(order.readySteps() == first, "Unchanged ready queue was rebuilt");
            queued += order.readySteps().size();
        }
        long indexed = System.nanoTime() - start;
        h.assertTrue(baseline == queued && queued == 256 * workers, "Ready queue changed candidate availability");
        order.steps.getFirst().done = 0;
        h.assertTrue(order.readySteps().size() == 1 && order.readySteps().getFirst() == 0, "Same-tick dependency regression remained cached");
        order.steps.getFirst().done = 1;
        h.assertTrue(order.readySteps().size() == 256, "Completed prerequisite did not wake its dependents");
        var small = new WorkOrder(2, recipe.output(), 1, null);
        small.steps.add(new WorkOrder.StepState(recipe, BlockPos.ZERO, Blocks.CRAFTING_TABLE, 1));
        h.assertTrue(small.readySteps() == null, "Small jobs paid for a dense dependency queue");
        io.github.genichimaruo.singulo.Singulo.LOGGER.info("Optimization check: 512 steps, {} workers; dependency scans {} ms, ready queue {} ms", workers, scan / 1e6, indexed / 1e6);
        var console = new WorkConsoleBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)), SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState());
        console.setLevel(h.getLevel()); console.orders().add(order);
        var worker = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        worker.makeGuard(null, console.getBlockPos(), 0, 30);
        worker.assignWork(console.getBlockPos(), order.id); order.workers.add(worker.getUUID());
        order.steps.get(256).inProgress = 1; // Prevent multi-step batching; this worker must use the ready queue.
        var job = console.requestJob(worker);
        h.assertTrue(job.type() == WorkJob.Type.CRAFT && job.batch().getFirst().step() == 257,
                "Dense scheduling ignored readiness, current leases or nearest-machine order");
        worker.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void commonViewsAreSharedWithoutLeakingViewerCandidates(GameTestHelper h) {
        var alice = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var bob = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var worker = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        int[] reads = {0};
        var console = new WorkConsoleBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)), SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState()) {
            @Override public List<ItemCount> available() { reads[0]++; return List.of(new ItemCount(new ItemStack(Items.IRON_INGOT), 42)); }
            @Override public List<io.github.genichimaruo.singulo.android.RuinAndroid> candidates(net.minecraft.world.entity.player.Player p) {
                return p == alice ? List.of(worker) : List.of();
            }
        };
        console.setLevel(h.getLevel());
        for (int i = 0; i < 20; i++) WorkConsoleView.build(console, alice, null);
        h.assertTrue(reads[0] == 20, "Baseline view did not collect stock per viewer");
        reads[0] = 0;
        CompoundTag common = WorkConsoleView.common(console);
        for (int i = 0; i < 20; i++) {
            var view = WorkConsoleView.forPlayer(console, i % 2 == 0 ? alice : bob, null, common);
            h.assertTrue(view.getList("androids", 10).size() == (i % 2 == 0 ? 1 : 0), "Shared snapshot leaked viewer-specific candidates");
        }
        h.assertTrue(reads[0] == 1 && !common.contains("androids"), "Common view was rebuilt or mutated");
        var menu = (WorkConsoleMenu) console.createMenu(1, alice.getInventory(), alice);
        var view = WorkConsoleView.forPlayer(console, alice, null, common);
        int sent = 0;
        for (int i = 0; i < 100; i++) if (menu.shouldSend(view.copy())) sent++;
        h.assertTrue(sent == 1, "Identical periodic snapshots were sent again");
        var changed = view.copy(); changed.putBoolean("powered", true);
        h.assertTrue(menu.shouldSend(changed), "A changed field was suppressed");
        var reopened = (WorkConsoleMenu) console.createMenu(2, alice.getInventory(), alice);
        h.assertTrue(reopened.shouldSend(view), "Reopening failed to send an initial snapshot");
        io.github.genichimaruo.singulo.Singulo.LOGGER.info("Optimization check: 20 viewers, stock scans 20 -> {}; 100 unchanged views, sends 100 -> {}", reads[0], sent);
        worker.discard(); alice.discard(); bob.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void cachedDoubleChestIsNotCountedTwice(GameTestHelper h) {
        BlockPos left = new BlockPos(2, 1, 1), right = left.east();
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        h.setBlock(left.south(), SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        h.setBlock(right.south(), SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        ChestBlockEntity a = h.getBlockEntity(left), b = h.getBlockEntity(right);
        a.setItem(0, new ItemStack(Items.IRON_INGOT, 3)); b.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        for (int repeat = 0; repeat < 2; repeat++) {
            var seen = new HashSet<BlockPos>();
            var stores = new ArrayList<net.neoforged.neoforge.items.IItemHandler>();
            SupplyPortBlockEntity.collect(h.getLevel(), h.absolutePos(left.south()), seen, stores);
            SupplyPortBlockEntity.collect(h.getLevel(), h.absolutePos(right.south()), seen, stores);
            int count = 0;
            for (var store : stores) for (int slot = 0; slot < store.getSlots(); slot++) count += store.getStackInSlot(slot).getCount();
            h.assertTrue(stores.size() == 1 && count == 10, "Two cached ports double-counted one chest");
        }
        h.succeed();
    }
}
