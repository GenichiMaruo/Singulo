package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.wormhole.UnstableMouthItem;
import io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity;
import io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/** A processing card describes a type of mouth. The machine assigns the link to the physical pair. */
public final class WormholeOrders {
    private WormholeOrders() {}

    private static boolean mouth(ItemStack stack) {
        return stack.getItem() instanceof UnstableMouthItem || stack.is(SinguloBlocks.WORMHOLE_MOUTH.get().asItem());
    }

    public static boolean pattern(ItemStack stack) {
        return mouth(stack) && !stack.has(SinguloComponents.WORMHOLE.get());
    }

    private static ItemStack sample(ItemStack stack) {
        var data = stack.get(SinguloComponents.WORMHOLE.get());
        if (!mouth(stack) || data == null || data.placed()) return stack;
        var copy = stack.copy();
        copy.remove(SinguloComponents.WORMHOLE.get());
        return copy;
    }

    public static boolean matches(ItemStack wanted, ItemStack actual) {
        if (ItemStack.isSameItemSameComponents(wanted, actual)) return true;
        return wanted.is(actual.getItem()) && pattern(wanted) && actual.has(SinguloComponents.WORMHOLE.get())
                && ItemStack.isSameItemSameComponents(wanted, sample(actual));
    }

    public static boolean matchesInput(ItemCount wanted, ItemStack actual) {
        return wanted.alt != null ? wanted.matches(actual) : matches(wanted.item, actual);
    }

    /** Fill the second stabilizer slot with the mate of the mouth already processing, even after the first is sealed. */
    public static ItemStack nextMouth(RuinAndroid worker, IItemHandler machine, ItemStack wanted) {
        var waiting = new java.util.HashSet<io.github.genichimaruo.singulo.wormhole.WormholeData>();
        for (int i = 0; i < machine.getSlots(); i++) {
            var stack = machine.getStackInSlot(i);
            if (mouth(stack) && stack.has(SinguloComponents.WORMHOLE.get())) waiting.add(stack.get(SinguloComponents.WORMHOLE.get()));
        }
        var seen = new java.util.HashSet<io.github.genichimaruo.singulo.wormhole.WormholeData>();
        var first = ItemStack.EMPTY;
        var paired = ItemStack.EMPTY;
        for (int i : worker.carrySlots()) {
            var stack = worker.inventory().getItem(i);
            var link = stack.get(SinguloComponents.WORMHOLE.get());
            if (link == null || !matches(wanted, stack) || UnstableMouthItem.expired(stack, worker.level())) continue;
            if (waiting.contains(link)) return stack;
            if (first.isEmpty()) first = stack;
            if (!seen.add(link) && paired.isEmpty()) paired = stack;
        }
        return paired.isEmpty() ? first : paired;
    }

    static long inStock(Level level, java.util.List<ItemCount> stock, ItemStack wanted, long requested) {
        long complete = 0, total = 0;
        for (var item : stock) {
            if (item.count <= 0 || !matches(wanted, item.item) || pattern(item.item) || UnstableMouthItem.expired(item.item, level)) continue;
            complete += item.count - item.count % 2;
            total += item.count;
        }
        long paired = Math.min(requested - requested % 2, complete);
        return paired + Math.min(requested % 2, total - paired);
    }

    /** Both mouths must be extractable from the same controller before starting to collect a pair. */
    public static ItemStack collectSample(ItemStack wanted, long remaining, IItemHandler out) {
        if (remaining < 2 || !pattern(wanted)) return wanted;
        var singles = new java.util.HashSet<WorkPlanner.Key>();
        for (int slot = 0; slot < out.getSlots(); slot++) {
            var stack = out.getStackInSlot(slot);
            if (!matches(wanted, stack) || !stack.has(SinguloComponents.WORMHOLE.get())) continue;
            var possible = out.extractItem(slot, 2, true);
            if (!possible.isEmpty() && (possible.getCount() >= 2 || !singles.add(WorkPlanner.Key.of(possible)))) return possible;
        }
        return ItemStack.EMPTY;
    }

    static RecipeData recipe(Level level, BlockPos location, RecipeData card) {
        if (!card.processing() || !mouth(card.output()) || !level.isLoaded(location)) return card;
        var controller = MachineWatch.controller(level, location);
        boolean generator = controller instanceof WormholeGeneratorBlockEntity && card.output().getItem() instanceof UnstableMouthItem;
        boolean stabilizer = controller instanceof WormholeStabilizerBlockEntity && card.output().is(SinguloBlocks.WORMHOLE_MOUTH.get().asItem());
        if (!generator && !stabilizer) return card;
        var grid = stabilizer ? card.grid().stream().map(WormholeOrders::sample).toList() : card.grid();
        var output = sample(card.output());
        // A single generator cycle produces both unstackable mouths even when the ghost slot holds one.
        if (generator) output = output.copyWithCount(2);
        return RecipeData.processing(grid, output, card.extras(), card.fuel(), card.faces());
    }
}
