package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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
        if (menu.type().hasCatalystSlot()) slots.add(menu.type().catalystSlot());
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
                    slots.remove(Integer.valueOf(target));
                    continue;
                }
            } else {
                if (input >= menu.type().inputSlots()) return null;
                target = input++;
            }
            needs.add(new Need(target, Math.max(1, ingredient.count()), ingredient.uses() > 0, stack -> ingredient.uses() > 0
                    ? ingredient.usesFrom(stack.copyWithCount(1)) > 0 : ingredient.ingredient().test(stack)));
        }
        if (recipe.restore().isPresent()) {
            if (input >= menu.type().inputSlots()) return null;
            needs.add(new Need(input, 1, true, recipe.restore().get()::test));
        }
        if (needs.isEmpty()) return null; // Fluid-only recipes have no items to transfer.
        Plan best = null;
        for (int batches = 1; batches <= (maxTransfer ? 64 : 1); batches++) {
            List<ItemStack> pool = new ArrayList<>();
            for (int slot : slots) pool.add(menu.getSlot(slot).getItem().copy());
            for (int i = 0; i < 36; i++) pool.add(menu.getSlot(menu.type().itemSlots() + i).getItem().copy());
            List<ItemStack> placed = new ArrayList<>();
            for (int ignored : slots) placed.add(ItemStack.EMPTY);
            Plan next = allocate(menu, slots, needs, 0, batches, pool, placed, new int[]{4096});
            if (next != null) best = next;
        }
        return best;
    }

    @Nullable
    private static Plan allocate(MachineMenu menu, List<Integer> slots, List<Need> needs, int index, int batches,
                                 List<ItemStack> pool, List<ItemStack> placed, int[] budget) {
        if (--budget[0] < 0) return null;
        if (index == needs.size()) {
            List<ItemStack> inventory = copy(pool.subList(slots.size(), pool.size()));
            for (ItemStack stack : pool.subList(0, slots.size())) {
                if (!returnToInventory(stack.copy(), inventory)) return null;
            }
            return new Plan(List.copyOf(slots), placed, inventory);
        }
        Need need = needs.get(index);
        List<ItemStack> tried = new ArrayList<>();
        for (int candidate = 0; candidate < pool.size(); candidate++) {
            ItemStack sample = pool.get(candidate);
            if (sample.isEmpty() || !need.matches().test(sample) || !menu.getSlot(need.slot()).mayPlace(sample)) continue;
            if (tried.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, sample))) continue;
            tried.add(sample);
            long requested = (long) need.count() * (need.reusable() ? 1 : batches);
            if (requested > menu.getSlot(need.slot()).getMaxStackSize(sample)) continue;
            int count = (int) requested;
            // Reusable templates/catalysts are placed once, regardless of shift-click batch count.
            List<ItemStack> remaining = copy(pool);
            int missing = count;
            for (ItemStack stack : remaining) {
                if (ItemStack.isSameItemSameComponents(stack, sample)) {
                    int taken = Math.min(missing, stack.getCount());
                    stack.shrink(taken);
                    missing -= taken;
                }
            }
            if (missing > 0) continue;
            List<ItemStack> nextPlaced = copy(placed);
            nextPlaced.set(slots.indexOf(need.slot()), sample.copyWithCount(count));
            Plan result = allocate(menu, slots, needs, index + 1, batches, remaining, nextPlaced, budget);
            if (result != null) return result;
        }
        return null;
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        return new ArrayList<>(stacks.stream().map(ItemStack::copy).toList());
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
