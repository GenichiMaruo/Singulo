package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import io.github.genichimaruo.singulo.registry.*;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.github.genichimaruo.singulo.wormhole.*;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidWormholeOrderGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), count);
    }

    private static void record(GameTestHelper h, RecipeRackBlockEntity rack, int row, BlockPos at, RecipeData recipe) {
        var card = new ItemStack(SinguloItems.RECIPE_CARD.get());
        recipe.write(card, h.getLevel().registryAccess());
        var loc = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        WorkLocationCardItem.record(loc, h.getLevel(), at, h.getLevel().getBlockState(at).getBlock());
        rack.items().setStackInSlot(row * RecipeRackBlockEntity.COLS, loc);
        rack.items().setStackInSlot(row * RecipeRackBlockEntity.COLS + 1, card);
    }

    @GameTest(template = "huge", timeoutTicks = 2400)
    public static void manufacturesMatchedPairs(GameTestHelper h) {
        var level = h.getLevel();
        var player = TestScope.player(h);
        var kind = Blueprints.Kind.WORMHOLE_GENERATOR;
        for (var p : BlockPos.betweenClosed(new BlockPos(1, 0, 1), new BlockPos(12, 0, 12))) h.setBlock(p, Blocks.STONE);
        var generator = h.absolutePos(new BlockPos(6, 2, 7));
        var back = Direction.SOUTH;
        Blueprints.layout(kind, generator, back, 5).forEach(level::setBlockAndUpdate);
        var chargePort = Shapes.spec(kind, 5).pos(generator, back, 3, 4, 3);
        level.setBlockAndUpdate(chargePort, SinguloBlocks.MULTIBLOCK_PORT.get().defaultBlockState());
        var consolePos = new BlockPos(4, 1, 4);
        var stabilizerPos = new BlockPos(8, 1, 4);
        var rackPos = new BlockPos(3, 1, 6);
        h.setBlock(consolePos, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(consolePos.west(), Blocks.CHEST);
        h.setBlock(stabilizerPos, SinguloBlocks.WORMHOLE_STABILIZER.get());
        h.setBlock(rackPos, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, consolePos);
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, rackPos);
        WormholeStabilizerBlockEntity stabilizer = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, stabilizerPos);
        ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, consolePos.west());
        rack.setConsole(console.getBlockPos());
        console.addLink(rack.getBlockPos());
        chest.setItem(0, item("exotic_matter", 8));
        chest.setItem(1, item("wormhole_mouth_casing", 4));
        var old = UnstableMouthItem.createPair(level)[0];
        var oldSealed = new ItemStack(SinguloBlocks.WORMHOLE_MOUTH.get());
        SinguloComponents.set(oldSealed, SinguloComponents.WORMHOLE.get(), SinguloComponents.get(old, SinguloComponents.WORMHOLE.get()));
        record(h, rack, 0, generator, RecipeData.processing(List.of(), old.copy(), List.of(), ItemStack.EMPTY));
        record(h, rack, 1, stabilizer.getBlockPos(), RecipeData.processing(
                List.of(old.copy(), item("exotic_matter", 2), item("wormhole_mouth_casing", 1)), oldSealed, List.of(), ItemStack.EMPTY));

        var target = new ItemStack(SinguloBlocks.WORMHOLE_MOUTH.get());
        var unrelated = oldSealed.copy();
        SinguloComponents.set(unrelated, SinguloComponents.WORMHOLE.get(), SinguloComponents.get(UnstableMouthItem.createPair(level)[0], SinguloComponents.WORMHOLE.get()));
        var orphanPlan = WorkPlanner.plan(target, 2, List.of(), List.of(new ItemCount(oldSealed, 1), new ItemCount(unrelated, 1)));
        h.assertTrue(!orphanPlan.ok(), "Two unmatched orphans fulfilled a pair request");
        h.assertTrue(!WormholeOrders.matches(oldSealed, unrelated), "A request for a specific pair matched another one");
        var existing = WorkPlanner.plan(target, 2, console.recipes(),
                List.of(new ItemCount(oldSealed, 1), new ItemCount(unrelated, 2)), level);
        h.assertTrue(existing.ok() && existing.steps.isEmpty() && ItemCount.get(existing.fromStock, unrelated) == 2
                && ItemCount.get(existing.fromStock, oldSealed) == 0, "Existing full pair was regenerated or mixed with an orphan");
        var specific = WorkPlanner.plan(oldSealed, 2, console.recipes(),
                List.of(new ItemCount(oldSealed, 1), new ItemCount(unrelated, 2)), level);
        h.assertTrue(!specific.ok() && specific.steps.isEmpty(), "An exact pair request generated a different pair");
        var expired = old.copy();
        SinguloComponents.set(expired, SinguloComponents.WORMHOLE.get(), new WormholeData(5, level.getGameTime() - UnstableMouthItem.LIFETIME));
        var expiredPlan = WorkPlanner.plan(item("unstable_wormhole_mouth", 1), 2, console.recipes(),
                List.of(new ItemCount(expired, 2)), level);
        h.assertTrue(expiredPlan.ok() && expiredPlan.fromStock.isEmpty() && expiredPlan.steps.size() == 1, "Expired mouths were reserved");
        var named = unrelated.copy();
        named.setHoverName(net.minecraft.network.chat.Component.literal("Other"));
        h.assertTrue(!WormholeOrders.matches(target, named) && !new ItemCount(target, 1).matches(unrelated),
                "Non-link components or exact physical inventory identity were ignored");
        var split = new WorkOrder(42, target, 4, null);
        console.orders().add(split);
        split.hold(console.getBlockPos(), oldSealed, 1);
        split.hold(console.getBlockPos().east(), unrelated, 1);
        split.hold(console.getBlockPos().east(3), oldSealed, 1);
        split.hold(console.getBlockPos().east(4), unrelated, 1);
        var trip = new WorkJob(WorkJob.Type.DELIVER, split.id, 0, 2, List.of(new ItemCount(target, 2)), List.of(), false);
        var route = console.planPickup(trip, console.getBlockPos());
        h.assertTrue(route != null && route.size() == 2 && route.stream().flatMap(p -> p.items().stream())
                .allMatch(p -> ItemStack.isSameItemSameTags(p.item, oldSealed)),
                "Nearest ports supplied unrelated halves instead of a reserved pair");
        console.orders().remove(split);

        var worker = SinguloEntities.BLADE_ANDROID.get().create(level);
        var start = h.absolutePos(consolePos.east());
        worker.makeGuard(player.getUUID(), start, RuinAndroid.MODE_GUARD, 30);
        worker.moveTo(Vec3.atBottomCenterOf(start));
        level.addFreshEntity(worker);
        var second = UnstableMouthItem.createPair(level)[0];
        worker.addToCarry(second.copy());
        worker.addToCarry(old.copy());
        worker.addToCarry(second.copy());
        var slots = stabilizer.automationItems();
        slots.insertItem(0, old.copy(), false);
        h.assertTrue(ItemStack.isSameItemSameTags(old, WormholeOrders.nextMouth(worker, slots, item("unstable_wormhole_mouth", 1))),
                "Stabilizer chose an unrelated mouth instead of the mate already in progress");
        stabilizer.items().setStackInSlot(0, ItemStack.EMPTY);
        for (int i : worker.carrySlots()) worker.inventory().setItem(i, ItemStack.EMPTY);
        h.onEachTick(() -> {
            var energy = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, chargePort, Direction.UP);
            if (energy != null) energy.receiveEnergy(Integer.MAX_VALUE, false);
            stabilizer.energy().receiveEnergy(Integer.MAX_VALUE, false);
        });
        var issue = console.start(player, target, 4, List.of(worker.getUUID()), null);
        h.assertTrue(issue == null, "Could not order two new pairs: " + issue);
        h.succeedWhen(() -> {
            h.assertTrue(console.orders().isEmpty(), "Order still active: " + console.orders().stream().map(o -> o.status + "/" + o.delivered
                    + " " + o.steps.stream().map(s -> s.done + "/" + s.total).toList()).toList());
            var pairs = new HashMap<WormholeData, Integer>();
            for (var container : List.<net.minecraft.world.Container>of(console.outbox(), chest)) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    var stack = container.getItem(slot);
                    if (!stack.is(target.getItem())) continue;
                    var data = SinguloComponents.get(stack, SinguloComponents.WORMHOLE.get());
                    h.assertTrue(data != null && !data.equals(SinguloComponents.get(old, SinguloComponents.WORMHOLE.get())) && !data.placed(),
                            "Received the card sample or a mouth without its generated identity");
                    pairs.merge(data, stack.getCount(), Integer::sum);
                }
            }
            h.assertTrue(pairs.size() == 2 && pairs.values().stream().allMatch(n -> n == 2),
                    "Order did not deliver two distinct, matched pairs: " + pairs + ", " + console.message()
                            + ", worker=" + worker.blockPosition().subtract(generator) + " relative to generator, fuel="
                            + stabilizer.items().getStackInSlot(WormholeStabilizerBlockEntity.SLOT_FUEL));
            h.assertTrue(chest.countItem(item("exotic_matter", 1).getItem()) == 0
                            && chest.countItem(item("wormhole_mouth_casing", 1).getItem()) == 0, "Stabilizer did not consume its materials");
            worker.discard();
        });
    }
}
