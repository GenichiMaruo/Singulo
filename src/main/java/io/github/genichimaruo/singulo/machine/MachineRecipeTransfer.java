package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shared client preview/server validation. Planning only touches copies; commit is atomic. */
public final class MachineRecipeTransfer {
    private MachineRecipeTransfer() {}

    public record Plan(List<Integer> slots, List<ItemStack> items, List<ItemStack> inventory) {}
    private record Need(int slot, int count, boolean reusable, Predicate<ItemStack> matches) {}

    @Nullable
    public static Plan plan(MachineMenu menu, MachineRecipe recipe, boolean maxTransfer) {
        if (!menu.type().id().equals(recipe.station()) || recipe.mass().isPresent()) return null;
        List<Integer> slots = new ArrayList<>();
        List<Need> needs = new ArrayList<>();
        boolean board = menu.type() == MachineType.PRECISION_ASSEMBLER;
        if (board) {
            if (!recipe.fitsAssemblyBoard()) return null;
            for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                int slot = menu.assemblySlot(cell);
                slots.add(slot);
                int index = recipe.boardIngredientIndex(cell);
                if (index == MachineRecipe.BOARD_RESTORE) {
                    needs.add(new Need(slot, 1, false, recipe::canRestoreFrom));
                } else if (index >= 0) {
                    var part = recipe.ingredients().get(index);
                    needs.add(new Need(slot, 1, part.uses() > 0, part.uses() > 0
                            ? stack -> part.usesFrom(stack.copyWithCount(1)) > 0 : part.ingredient()::test));
                }
            }
        } else {
            for (int i = 0; i < menu.type().inputSlots(); i++) slots.add(i);
        }
        // The catalyst reserve is only rewritten when the recipe needs a catalyst that is not already there,
        // so reserves stocked for later recipes stay in the machine.
        int input = 0;
        boolean catalystUsed = false;
        for (MachineRecipe.ItemInput ingredient : recipe.ingredients()) {
            if (board && !recipe.assemblyCatalyst(ingredient)) continue;
            boolean catalyst = board || menu.type().hasCatalystSlot()
                    && ingredient.ingredient().items().map(holder -> new ItemStack(holder)).anyMatch(s -> CatalystHelper.tierOf(s) > 0);
            int target;
            if (catalyst) {
                if (catalystUsed) return null;
                catalystUsed = true;
                target = menu.type().catalystSlot();
                // A stocked reserve that can pay by uses already serves every batch. Whole-item catalysts are planned
                // per batch; allocate() keeps at least the stocked reserve in the slot.
                ItemStack reserve = menu.getSlot(target).getItem();
                if (ingredient.uses() > 0 && (ingredient.usesFrom(CatalystMagazine.pooled(menu.loadedCatalyst(), reserve)) > 0
                        || ingredient.usesFrom(reserve.copyWithCount(1)) > 0)) {
                    continue;
                }
                slots.add(target);
            } else {
                if (input >= menu.type().inputSlots()) return null;
                target = input++;
            }
            // Machines pay a uses ingredient from a single item with enough uses left, whatever its count says
            needs.add(new Need(target, ingredient.uses() > 0 ? 1 : Math.max(1, ingredient.count()), ingredient.uses() > 0, stack -> ingredient.uses() > 0
                    ? ingredient.usesFrom(stack.copyWithCount(1)) > 0 : ingredient.ingredient().test(stack)));
        }
        if (!board && recipe.restore().isPresent()) {
            if (input >= menu.type().inputSlots()) return null;
            needs.add(new Need(input, 1, true, recipe::canRestoreFrom));
        }
        if (needs.isEmpty()) return null; // Fluid-only recipes have no items to transfer.
        // One search budget for every batch count, so a max transfer costs at most a few thousand steps per packet
        int[] budget = {8192};
        List<ItemStack> pool = new ArrayList<>();
        for (int slot : slots) pool.add(menu.transferStack(slot));
        for (int i = 0; i < 36; i++) pool.add(menu.getSlot(menu.playerSlotStart() + i).getItem().copy());
        List<Integer> sources = new ArrayList<>();
        if (board) {
            // Empty smaller player stacks first to make room for displaced parts in a full inventory.
            for (int i = slots.size(); i < pool.size(); i++) if (!pool.get(i).isEmpty()) sources.add(i);
            sources.sort(Comparator.comparingInt(i -> pool.get(i).getCount()));
        }
        int remainingSources = board ? slots.size() : pool.size();
        for (int i = 0; i < remainingSources; i++) if (!pool.get(i).isEmpty()) sources.add(i);

        int stocked = board && !maxTransfer ? stockedAssemblySets(menu, needs, pool) : 0;
        int batchLimit = maxTransfer ? batchLimit(menu, needs, pool, sources) : (int) Math.min(Integer.MAX_VALUE, (long) stocked + 1);
        // A full inventory may fit one set or a whole stack but no intermediate count.
        for (int batches = batchLimit; batches >= Math.max(1, stocked) && budget[0] > 0; batches--) {
            Plan plan = allocate(menu, slots, needs, 0, batches, pool, sources, new int[pool.size()], new ItemStack[slots.size()], budget);
            if (plan != null) return plan;
        }
        return null;
    }

    /** Bound the search by item stack limits and the available stock shared by repeated cells in a diagram. */
    private static int batchLimit(MachineMenu menu, List<Need> needs, List<ItemStack> pool, List<Integer> sources) {
        int limit = Integer.MAX_VALUE;
        var demand = new HashMap<BitSet, Long>();
        for (Need need : needs) {
            BitSet matches = new BitSet(pool.size());
            long available = 0;
            int capacity = 0;
            for (int i : sources) {
                ItemStack sample = pool.get(i);
                if (!need.matches().test(sample) || !menu.getSlot(need.slot()).mayPlace(sample)) continue;
                matches.set(i);
                available += sample.getCount();
                capacity = Math.max(capacity, menu.stackLimit(need.slot(), sample));
            }
            if (Math.min(available, capacity) < need.count()) return 0;
            if (need.reusable()) continue;
            long perBatch = demand.merge(matches, (long) need.count(), Long::sum);
            limit = (int) Math.min(limit, Math.min(capacity / need.count(), available / perBatch));
        }
        return demand.isEmpty() ? 1 : limit;
    }

    /** A normal click tops up a matching diagram by one set, including the layout JEI uses for shapeless recipes. */
    private static int stockedAssemblySets(MachineMenu menu, List<Need> needs, List<ItemStack> pool) {
        int sets = Integer.MAX_VALUE, matched = 0;
        for (Need need : needs) {
            if (need.slot() < menu.type().itemSlots()) continue;
            ItemStack stack = pool.get(need.slot() - menu.assemblySlot(0));
            if (stack.isEmpty() || !need.matches().test(stack)) return 0;
            matched++;
            if (!need.reusable()) sets = Math.min(sets, stack.getCount());
        }
        long occupied = pool.subList(0, MachineRecipe.ASSEMBLY_CELLS).stream().filter(s -> !s.isEmpty()).count();
        return matched == occupied && sets != Integer.MAX_VALUE ? sets : 0;
    }

    /**
     * Assigns needs[index..] from the pool. {@code used} counts what each pool stack has given so far, so a step
     * costs no copies; it is undone on backtracking. Slot limits come from MachineMenu#stackLimit, never from
     * Slot#getMaxStackSize(ItemStack), which empties and refills item-handler slots and looks like a contents change.
     */
    @Nullable
    private static Plan allocate(MachineMenu menu, List<Integer> slots, List<Need> needs, int index, int batches,
                                 List<ItemStack> pool, List<Integer> sources, int[] used, ItemStack[] placed, int[] budget) {
        if (--budget[0] < 0) return null;
        if (index == needs.size()) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = slots.size(); i < pool.size(); i++) inventory.add(pool.get(i).copyWithCount(pool.get(i).getCount() - used[i]));
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : placed) items.add(stack == null ? ItemStack.EMPTY : stack.copy());
            List<ItemStack> leftovers = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) leftovers.add(pool.get(i).copyWithCount(pool.get(i).getCount() - used[i]));
            if (menu.type() == MachineType.PRECISION_ASSEMBLER) {
                // Keep surplus in matching material cells when another ingredient limits the batch count.
                for (Need need : needs) {
                    if (need.slot() < menu.type().itemSlots() || need.reusable()) continue;
                    int cell = need.slot() - menu.assemblySlot(0);
                    ItemStack left = leftovers.get(cell), target = items.get(cell);
                    if (!ItemStack.isSameItemSameComponents(left, target)) continue;
                    int keep = Math.clamp(menu.stackLimit(need.slot(), target) - target.getCount(), 0, left.getCount());
                    target.grow(keep);
                    left.shrink(keep);
                }
            }
            int inventoryLimit = menu.getSlot(menu.playerSlotStart()).getMaxStackSize();
            for (ItemStack left : leftovers) if (!returnToInventory(left, inventory, inventoryLimit)) return null;
            return new Plan(List.copyOf(slots), items, inventory);
        }
        Need need = needs.get(index);
        Slot target = menu.getSlot(need.slot());
        int at = slots.indexOf(need.slot());
        List<ItemStack> tried = new ArrayList<>();
        boolean board = menu.type() == MachineType.PRECISION_ASSEMBLER;
        for (int offset = 0; offset < pool.size(); offset++) {
            int candidate = board ? (at + offset) % pool.size() : offset;
            ItemStack sample = pool.get(candidate);
            if (sample.getCount() - used[candidate] <= 0 || !need.matches().test(sample)) continue;
            if (!target.mayPlace(sample)) continue;
            if (tried.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, sample))) continue;
            tried.add(sample);
            long requested = (long) need.count() * (need.reusable() ? 1 : batches);
            ItemStack stocked = pool.get(at);
            if (need.slot() == menu.type().catalystSlot() && menu.type().hasCatalystSlot()
                    && ItemStack.isSameItemSameComponents(stocked, sample)) {
                requested = Math.max(requested, stocked.getCount());   // keep the stocked reserve in the machine
            }
            if (requested > menu.stackLimit(need.slot(), sample)) continue;
            int count = (int) requested;
            // Reusable templates/catalysts are placed once, regardless of shift-click batch count.
            int[] taken = new int[pool.size()];
            int missing = count;
            // Keep matching stock in place and top it up from the player before moving other cells' materials.
            if (board && ItemStack.isSameItemSameComponents(stocked, sample)) {
                taken[at] = Math.min(missing, stocked.getCount() - used[at]);
                used[at] += taken[at];
                missing -= taken[at];
            }
            for (int i : sources) {
                if (missing == 0) break;
                if (board && i == at) continue;
                if (ItemStack.isSameItemSameComponents(pool.get(i), sample)) {
                    taken[i] = Math.min(missing, pool.get(i).getCount() - used[i]);
                    used[i] += taken[i];
                    missing -= taken[i];
                }
            }
            if (missing == 0) {
                placed[at] = sample.copyWithCount(count);
                Plan result = allocate(menu, slots, needs, index + 1, batches, pool, sources, used, placed, budget);
                if (result != null) return result;
                placed[at] = null;
            }
            for (int i = 0; i < pool.size(); i++) used[i] -= taken[i];
        }
        return null;
    }

    private static boolean returnToInventory(ItemStack stack, List<ItemStack> inventory, int inventoryLimit) {
        for (ItemStack stored : inventory) {
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack)) {
                int count = Math.min(stack.getCount(), Math.min(inventoryLimit, stored.getMaxStackSize()) - stored.getCount());
                if (count > 0) { stored.grow(count); stack.shrink(count); }
            }
        }
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            if (inventory.get(i).isEmpty()) {
                int count = Math.min(stack.getCount(), Math.min(inventoryLimit, stack.getMaxStackSize()));
                inventory.set(i, stack.copyWithCount(count));
                stack.shrink(count);
            }
        }
        return stack.isEmpty();
    }

    public static void apply(MachineMenu menu, Plan plan) {
        for (int i = 0; i < plan.slots().size(); i++) menu.setTransferStack(plan.slots().get(i), plan.items().get(i));
        for (int i = 0; i < 36; i++) menu.getSlot(menu.playerSlotStart() + i).set(plan.inventory().get(i));
        menu.broadcastChanges();
    }
}
