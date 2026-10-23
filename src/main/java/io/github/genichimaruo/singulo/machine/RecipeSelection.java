package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/**
 * 作るものを選べる装置（{@link MachineType#selectsRecipe()}、精密組立台・特異点封入台）のレシピの選び方。
 *
 * <p>材料を種類と個数だけで指定するので、あるレシピの材料の一部が別のレシピの材料と同じになる（超伝導コイルと鋼板だけの
 * Tシリンダー軸受は、ほかの多くの設備の途中の状態と同じ）。材料を入れている途中で別のものが作られないよう、
 * <ul>
 *   <li>指定なし（自動）: 入っている材料の種類がレシピとぴったり一致したときに作る。ほかのレシピの途中の状態にもなりうる
 *       組み合わせ（材料の種類が、ほかのレシピの材料の種類に含まれる）は、入れるのが {@link #SETTLE_TICKS} tick 止まってから作る。</li>
 *   <li>指定あり: 指定したレシピだけを作る。ほかの物が入っていてもよい。</li>
 * </ul>
 */
public final class RecipeSelection {
    /** 自動で、ほかのレシピの途中と同じ組み合わせを作り始めるまでに、入れるのが止まっているべき時間（tick）。 */
    public static final int SETTLE_TICKS = 60;

    private RecipeSelection() {}

    /** 選べるレシピ（質量・材料なしのものを除き、ID順）。画面のボタンの番号と同期の値はこの並びの番号。 */
    public static List<MachineRecipe> selectable(Level level, MachineType type) {
        return MachineRecipeIndex.selectable(level, type);
    }

    /** 選べるレシピの並びでの番号（なければ -1）。 */
    public static int indexOf(Level level, MachineType type, @Nullable ResourceLocation id) {
        if (id == null) {
            return -1;
        }
        List<MachineRecipe> list = selectable(level, type);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** 番号から（範囲外なら null）。 */
    @Nullable
    public static MachineRecipe byIndex(Level level, MachineType type, int index) {
        List<MachineRecipe> list = selectable(level, type);
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** 入力スロット（触媒スロットを除く）の空でないものを、すべてこの取り方で使うか。 */
    public static boolean usesEveryInput(MachineType type, List<ItemStack> inputs, MachineRecipe.Plan plan) {
        for (int i = 0; i < type.inputSlots() && i < inputs.size(); i++) {
            if (!inputs.get(i).isEmpty() && plan.consume()[i] == 0 && plan.uses()[i] == 0 && plan.restoreSlot() != i) {
                return false;
            }
        }
        return true;
    }

    /** レシピマネージャーごとの「ほかのレシピの途中になりうるか」の結果（レシピの再読み込みで作り直す）。 */
    private record AmbiguityCache(Object recipes, Map<ResourceLocation, Boolean> values) {}
    private static final Map<RecipeManager, AmbiguityCache> AMBIGUOUS = new WeakHashMap<>();

    public static synchronized void clear() {
        synchronized (AMBIGUOUS) { AMBIGUOUS.clear(); }
    }

    /**
     * このレシピの材料の種類が、同じ装置のほかのレシピの材料の種類にすべて含まれるか。含まれるなら、ほかのレシピの
     * 材料を入れている途中でも同じ状態になるので、自動では作らない（作るものの指定が要る）。
     */
    public static boolean ambiguous(Level level, MachineType type, MachineRecipe recipe) {
        Map<ResourceLocation, Boolean> cache;
        synchronized (AMBIGUOUS) {
            RecipeManager manager = level.getRecipeManager();
            Object snapshot = MachineRecipeIndex.snapshot(level);
            AmbiguityCache known = AMBIGUOUS.get(manager);
            // /reload replaces the immutable recipe collection inside the SAME manager.
            if (known == null || known.recipes() != snapshot) {
                known = new AmbiguityCache(snapshot, new HashMap<>());
                AMBIGUOUS.put(manager, known);
            }
            cache = known.values();
        }
        synchronized (cache) {
            Boolean known = cache.get(recipe.id());
            if (known != null) {
                return known;
            }
            Set<String> mine = kinds(recipe.value());
            boolean result = false;
            for (MachineRecipe other : selectable(level, type)) {
                if (!other.id().equals(recipe.id()) && kinds(other.value()).containsAll(mine)) {
                    result = true;
                    break;
                }
            }
            cache.put(recipe.id(), result);
            return result;
        }
    }

    /** 材料の種類（アイテムの材料・復元する物・液体）を、比べられる文字列の集まりにする。 */
    private static Set<String> kinds(MachineRecipe r) {
        Set<String> out = new HashSet<>();
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            out.add(key(in.ingredient()));
        }
        r.restore().ifPresent(i -> out.add(key(i)));
        for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
            out.add("fluid:" + BuiltInRegistries.FLUID.getKey(in.fluid()));
        }
        return out;
    }

    private static String key(Ingredient ingredient) {
        List<String> ids = new ArrayList<>();
        for (ItemStack s : ingredient.getItems()) {
            ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        }
        ids.sort(null);
        return String.join("|", ids);
    }
}
