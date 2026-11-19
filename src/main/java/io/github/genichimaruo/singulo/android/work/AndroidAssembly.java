package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Translate the counts on a processing card into one physical placement by the worker at the table. */
public final class AndroidAssembly {
    private static final int CELLS = MachineRecipe.ASSEMBLY_CELLS;
    private final MachineRecipe recipe;
    private final int[] cardSlots;
    private final List<RecipeData.Tool> tools;

    private AndroidAssembly(MachineRecipe recipe, int[] cardSlots, List<RecipeData.Tool> tools) {
        this.recipe = recipe;
        this.cardSlots = cardSlots;
        this.tools = List.copyOf(tools);
    }

    @Nullable
    public static MachineBlockEntity table(Level level, BlockPos at) {
        return level.getBlockEntity(at) instanceof MachineBlockEntity m && m.type() == MachineType.PRECISION_ASSEMBLER ? m : null;
    }

    // Orders keep the same card for repeated visits. A datapack/config reload replaces the station list.
    private record Cached(List<MachineRecipe> recipes, @Nullable AndroidAssembly assembly) {}
    private static final Map<RecipeData, Cached> CACHE = new WeakHashMap<>();

    @Nullable
    public static synchronized AndroidAssembly find(Level level, RecipeData card) {
        var recipes = MachineBlockEntity.stationRecipes(level, MachineType.PRECISION_ASSEMBLER);
        var cached = CACHE.get(card);
        if (cached != null && recipes == cached.recipes) return cached.assembly;
        AndroidAssembly found = null;
        for (var holder : recipes) {
            var recipe = holder.value();
            if (!ItemStack.isSameItem(recipe.result(), card.output()) && !ItemStack.isSameItem(recipe.byproduct(), card.output())) continue;
            found = match(recipe, card);
            if (found != null) break;
        }
        CACHE.put(card, new Cached(recipes, found));
        return found;
    }

    @Nullable
    private static AndroidAssembly match(MachineRecipe recipe, RecipeData card) {
        if (!recipe.fitsAssemblyBoard()) return null;
        List<Integer> units = new ArrayList<>();
        for (int slot = 0; slot < card.grid().size(); slot++) {
            var stack = card.grid().get(slot);
            if (stack.getCount() > CELLS + 1 - units.size() || FluidSamples.liquid(stack)) return null;
            for (int n = 0; n < stack.getCount(); n++) units.add(slot);
        }
        int[] ingredients = new int[CELLS + 1], owners = new int[units.size()], mapping = new int[CELLS + 1];
        Arrays.fill(owners, -1);
        Arrays.fill(mapping, -1);
        for (int i = 0; i < CELLS; i++) ingredients[i] = recipe.boardIngredientIndex(i);
        ingredients[CELLS] = MachineRecipe.BOARD_EMPTY;
        for (int i = 0; i < recipe.ingredients().size(); i++) if (recipe.assemblyCatalyst(recipe.ingredients().get(i))) ingredients[CELLS] = i;
        // Augmenting paths allow a tag and a narrower ingredient to share alternatives without depending on card order.
        for (int cell = 0; cell <= CELLS; cell++) {
            if (ingredients[cell] == MachineRecipe.BOARD_EMPTY) continue;
            if (!assign(recipe, card, ingredients, cell, units, owners, new boolean[units.size()])) {
                // A hand-recorded card may assume an already installed reusable catalyst.
                if (cell != CELLS || recipe.ingredients().get(ingredients[cell]).uses() == 0) return null;
            }
        }
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] < 0) return null;
            mapping[owners[i]] = units.get(i);
        }
        // Some upgrades copy the input's name, contents and stored energy to the finished item.
        ItemStack result = recipe.assemble(card.grid(), recipe.plan(card.grid()));
        List<RecipeData.Tool> tools = new ArrayList<>();
        for (int cell = 0; cell <= CELLS; cell++) {
            if (mapping[cell] < 0) continue;
            int ingredient = ingredients[cell];
            if (ingredient == MachineRecipe.BOARD_RESTORE) {
                result = UsesHelper.restore(card.grid().get(mapping[cell]), result);
            } else if (recipe.ingredients().get(ingredient).uses() > 0) {
                var tool = recipe.ingredients().get(ingredient);
                tools.add(new RecipeData.Tool(mapping[cell], tool.uses(), tool.tier()));
            }
        }
        if (!produces(result, card.output()) && !produces(recipe.byproduct(), card.output())) return null;
        if (card.guaranteedExtras()) for (var product : card.extras())
            if (!produces(result, product) && !produces(recipe.byproduct(), product)) return null;
        return new AndroidAssembly(recipe, mapping, tools);
    }

    private static boolean produces(ItemStack product, ItemStack requested) {
        return ItemStack.isSameItemSameTags(product, requested) && requested.getCount() <= product.getCount();
    }

    private static boolean assign(MachineRecipe recipe, RecipeData card, int[] ingredients, int cell, List<Integer> units,
                                  int[] owners, boolean[] visited) {
        int index = ingredients[cell];
        for (int i = 0; i < units.size(); i++) {
            if (visited[i]) continue;
            var sample = card.grid().get(units.get(i));
            boolean accepts;
            if (index == MachineRecipe.BOARD_RESTORE) {
                accepts = recipe.canRestoreFrom(sample);
            } else {
                var need = recipe.ingredients().get(index);
                accepts = need.tier() > 0 ? CatalystHelper.tierOf(sample) >= need.tier() : need.ingredient().test(sample);
            }
            if (!accepts) continue;
            visited[i] = true;
            if (owners[i] < 0 || assign(recipe, card, ingredients, owners[i], units, owners, visited)) {
                owners[i] = cell;
                return true;
            }
        }
        return false;
    }

    List<RecipeData.Tool> tools() { return tools; }

    public boolean returnedCatalyst(RecipeData card, ItemStack stack) {
        return CatalystHelper.tierOf(stack) > 0 && tools.stream().anyMatch(t -> t.tier() > 0)
                && !ItemStack.isSameItem(stack, card.output()) && card.extras().stream().noneMatch(s -> ItemStack.isSameItem(s, stack));
    }

    private int toolCell(RecipeData.Tool tool) {
        for (int i = 0; i <= CELLS; i++) if (cardSlots[i] == tool.slot()) return i;
        throw new IllegalArgumentException("Tool missing from the assembly card");
    }

    ItemStack loadedTool(MachineBlockEntity machine, RecipeData.Tool tool) {
        int cell = toolCell(tool);
        return cell == CELLS ? machine.magazine().pooled(machine.items(), machine.type().catalystSlot())
                : machine.assemblyGrid().getStackInSlot(cell);
    }

    public boolean supplyTool(MachineBlockEntity machine, RecipeData.Tool tool, RuinAndroid worker, ItemStack sample) {
        int cell = toolCell(tool);
        for (int slot : worker.carrySlots()) {
            var stack = worker.inventory().getItem(slot);
            if (!ItemStack.isSameItemSameTags(stack, sample)) continue;
            var one = stack.copyWithCount(1);
            if (cell < CELLS) {
                if (!machine.assemblyGrid().insertItem(cell, one, false).isEmpty()) return false;
            } else {
                int reserve = machine.type().catalystSlot();
                var rest = machine.items().insertItem(reserve, one, false);
                if (!rest.isEmpty() && !machine.magazine().absorb(rest, machine.items(), reserve, false)) return false;
            }
            worker.inventory().removeItem(slot, 1);
            worker.compactCarry();
            return true;
        }
        return false;
    }

    /** Never disturb a player's partly filled board. Matching reusable tools may remain between runs. */
    public boolean canPlace(MachineBlockEntity machine) {
        for (int cell = 0; cell < CELLS; cell++) {
            var stack = machine.assemblyGrid().getStackInSlot(cell);
            if (stack.isEmpty()) continue;
            int index = recipe.boardIngredientIndex(cell);
            if (index < 0 || recipe.ingredients().get(index).uses() <= 0 || recipe.ingredients().get(index).usesFrom(stack) <= 0) return false;
        }
        return true;
    }

    /** Verify the complete board and carry payment before moving any item. Server-thread operation. */
    public boolean place(MachineBlockEntity machine, RecipeData card, RuinAndroid worker) {
        if (!canPlace(machine)) return false;
        var board = new ArrayList<>(machine.assemblyInputs());
        var slots = worker.carrySlots();
        int[] consumed = new int[slots.length];
        for (int cell = 0; cell <= CELLS; cell++) {
            if (cardSlots[cell] < 0 || !board.get(cell).isEmpty()) continue;
            var sample = card.grid().get(cardSlots[cell]);
            ItemStack actual = ItemStack.EMPTY;
            for (int i = 0; i < slots.length; i++) {
                var stack = worker.inventory().getItem(slots[i]);
                if (stack.getCount() <= consumed[i] || !WormholeOrders.matches(sample, stack)) continue;
                consumed[i]++;
                actual = stack.copyWithCount(1);
                break;
            }
            if (actual.isEmpty()) return false;
            if (cell < CELLS && !machine.assemblyGrid().insertItem(cell, actual, true).isEmpty()) return false;
            board.set(cell, actual);
        }
        if (recipe.assemblyPlan(board) == null) return false;
        if (cardSlots[CELLS] >= 0 && machine.catalyst().isEmpty()
                && !machine.items().insertItem(machine.type().catalystSlot(), board.get(CELLS), false).isEmpty()) return false;
        for (int i = 0; i < slots.length; i++) {
            if (consumed[i] > 0) worker.inventory().removeItem(slots[i], consumed[i]);
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (machine.assemblyGrid().getStackInSlot(cell).isEmpty() && !board.get(cell).isEmpty())
                machine.assemblyGrid().setStackInSlot(cell, board.get(cell));
        }
        worker.compactCarry();
        return true;
    }
}
