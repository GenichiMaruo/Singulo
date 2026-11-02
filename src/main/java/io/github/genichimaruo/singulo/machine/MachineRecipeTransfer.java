package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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
        for (int i = 0; i < menu.type().inputSlots(); i++) slots.add(i);
        // The catalyst reserve is only rewritten when the recipe needs a catalyst that is not already there,
        // so reserves stocked for later recipes stay in the machine.
        List<Need> needs = new ArrayList<>();
        int input = 0;
        boolean catalystUsed = false;
        for (MachineRecipe.ItemInput ingredient : recipe.ingredients()) {
            boolean catalyst = menu.type().hasCatalystSlot()
                    && Arrays.stream(ingredient.ingredient().getItems()).anyMatch(s -> CatalystHelper.tierOf(s) > 0);
            int target;
            if (catalyst) {
                if (catalystUsed) return null;
                catalystUsed = true;
                target = menu.type().catalystSlot();
                if (ingredient.uses() > 0 && ingredient.usesFrom(menu.loadedCatalyst()) > 0) {
                    continue;
                }
                // A stocked reserve that can pay by uses already serves every batch. Whole-item catalysts are planned
                // per batch; allocate() keeps at least the stocked reserve in the slot.
                ItemStack reserve = menu.getSlot(target).getItem();
                if (ingredient.uses() > 0 && !reserve.isEmpty() && ingredient.usesFrom(reserve.copyWithCount(1)) > 0) {
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
        if (recipe.restore().isPresent()) {
            if (input >= menu.type().inputSlots()) return null;
            needs.add(new Need(input, 1, true, recipe.restore().get()::test));
        }
        if (needs.isEmpty()) return null; // Fluid-only recipes have no items to transfer.
        Plan best = null;
        // One search budget for every batch count, so a max transfer costs at most a few thousand steps per packet
        int[] budget = {8192};
        List<ItemStack> pool = new ArrayList<>();
        for (int slot : slots) pool.add(menu.getSlot(slot).getItem().copy());
        for (int i = 0; i < 36; i++) pool.add(menu.getSlot(menu.type().itemSlots() + i).getItem().copy());
        for (int batches = 1; batches <= (maxTransfer ? 64 : 1) && budget[0] > 0; batches++) {
            Plan next = allocate(menu, slots, needs, 0, batches, pool, new int[pool.size()], new ItemStack[slots.size()], budget);
            // A small count can fail where a larger one fits (moving a whole stack frees inventory space),
            // but once a count fits, a larger count that fails means the materials or the slot ran out.
            if (next == null && best != null) break;
            if (next != null) best = next;
        }
        return best;
    }

    /**
     * Assigns needs[index..] from the pool. {@code used} counts what each pool stack has given so far, so a step
     * costs no copies; it is undone on backtracking. Slot limits come from MachineMenu#stackLimit, never from
     * Slot#getMaxStackSize(ItemStack), which empties and refills item-handler slots and looks like a contents change.
     */
    @Nullable
    private static Plan allocate(MachineMenu menu, List<Integer> slots, List<Need> needs, int index, int batches,
                                 List<ItemStack> pool, int[] used, ItemStack[] placed, int[] budget) {
        if (--budget[0] < 0) return null;
        if (index == needs.size()) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = slots.size(); i < pool.size(); i++) inventory.add(pool.get(i).copyWithCount(pool.get(i).getCount() - used[i]));
            for (int i = 0; i < slots.size(); i++) {
                ItemStack left = pool.get(i).copyWithCount(pool.get(i).getCount() - used[i]);
                if (!returnToInventory(left, inventory)) return null;
            }
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : placed) items.add(stack == null ? ItemStack.EMPTY : stack.copy());
            return new Plan(List.copyOf(slots), items, inventory);
        }
        Need need = needs.get(index);
        Slot target = menu.getSlot(need.slot());
        int at = slots.indexOf(need.slot());
        List<ItemStack> tried = new ArrayList<>();
        for (int candidate = 0; candidate < pool.size(); candidate++) {
            ItemStack sample = pool.get(candidate);
            if (sample.getCount() - used[candidate] <= 0 || !need.matches().test(sample) || !target.mayPlace(sample)) continue;
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
            for (int i = 0; i < pool.size() && missing > 0; i++) {
                if (ItemStack.isSameItemSameComponents(pool.get(i), sample)) {
                    taken[i] = Math.min(missing, pool.get(i).getCount() - used[i]);
                    used[i] += taken[i];
                    missing -= taken[i];
                }
            }
            if (missing == 0) {
                placed[at] = sample.copyWithCount(count);
                Plan result = allocate(menu, slots, needs, index + 1, batches, pool, used, placed, budget);
                if (result != null) return result;
                placed[at] = null;
            }
            for (int i = 0; i < pool.size(); i++) used[i] -= taken[i];
        }
        return null;
    }

    private static boolean returnToInventory(ItemStack stack, List<ItemStack> inventory) {
        for (ItemStack stored : inventory) {
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack)) {
                int count = Math.min(stack.getCount(), Math.min(64, stored.getMaxStackSize()) - stored.getCount());
                if (count > 0) { stored.grow(count); stack.shrink(count); }
            }
        }
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            if (inventory.get(i).isEmpty()) {
                int count = Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize()));
                inventory.set(i, stack.copyWithCount(count));
                stack.shrink(count);
            }
        }
        return stack.isEmpty();
    }

    public static void apply(MachineMenu menu, Plan plan) {
        for (int i = 0; i < plan.slots().size(); i++) menu.getSlot(plan.slots().get(i)).set(plan.items().get(i));
        for (int i = 0; i < 36; i++) menu.getSlot(menu.type().itemSlots() + i).set(plan.inventory().get(i));
        menu.broadcastChanges();
    }
}
