package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * 注文の計算。完成品を count 個そろえるのに、素材置き場の在庫をどれだけ使い、どのレシピを何回作るかを決める。
 * <ul>
 * <li>在庫があれば先に使う（中間素材も）。足りない分だけ、レシピで作る（同じ物を作るレシピが複数あれば、先に並んだもの）。</li>
 * <li>作る工程は、素材を作る工程が先に来る順（そのまま上から進めればよい）に並ぶ。</li>
 * <li>レシピがなく在庫も足りない物は「足りない物」。ループするレシピ（鉄インゴット ⇄ 鉄ブロックなど）も、作れない扱い。</li>
 * </ul>
 */
public final class WorkPlanner {
    private static final int MAX_DEPTH = 24;

    private WorkPlanner() {}

    /** 1つの工程: レシピを crafts 回作る（needed は要る数。作る数との差が余り）。 */
    public record Step(RecipeRackBlockEntity.Entry entry, long crafts, long needed) {}

    public static final class Plan {
        public final ItemStack target;
        public final long count;
        public final List<Step> steps = new ArrayList<>();
        /** 素材置き場から使う物（中間素材の在庫も含む）。 */
        public final List<ItemCount> fromStock = new ArrayList<>();
        /** 足りない物（在庫もレシピもない）。 */
        public final List<ItemCount> missing = new ArrayList<>();
        /** ループするレシピがあった。 */
        public boolean loop;

        Plan(ItemStack target, long count) {
            this.target = target.copyWithCount(1);
            this.count = count;
        }

        public boolean ok() {
            return missing.isEmpty() && !loop;
        }

        /** 必要な素材の一覧（在庫から使う物と、足りない物）。 */
        public List<ItemCount> materials() {
            List<ItemCount> out = new ArrayList<>();
            for (ItemCount c : fromStock) {
                ItemCount.add(out, c.item, c.count);
            }
            for (ItemCount c : missing) {
                ItemCount.add(out, c.item, c.count);
            }
            return out;
        }
    }

    /**
     * 計算する。stock は使える在庫（ほかの注文が予約した分を引いたもの）で、この中で書き換える（写しを渡す）。
     */
    public static Plan plan(ItemStack target, long count, List<RecipeRackBlockEntity.Entry> recipes, List<ItemCount> stock) {
        Plan plan = new Plan(target, count);
        need(plan, target, count, recipes, stock, new ArrayList<>());
        return plan;
    }

    private static void need(Plan plan, ItemStack item, long amount, List<RecipeRackBlockEntity.Entry> recipes, List<ItemCount> stock,
                             List<ItemStack> path) {
        // 在庫から使う
        for (ItemCount c : stock) {
            if (c.matches(item) && c.count > 0) {
                long take = Math.min(c.count, amount);
                c.count -= take;
                amount -= take;
                ItemCount.add(plan.fromStock, item, take);
                break;
            }
        }
        if (amount <= 0) {
            return;
        }
        RecipeRackBlockEntity.Entry recipe = null;
        for (RecipeRackBlockEntity.Entry e : recipes) {
            if (ItemStack.isSameItemSameComponents(e.recipe().output(), item)) {
                recipe = e;
                break;
            }
        }
        boolean looping = false;
        for (ItemStack p : path) {
            looping |= ItemStack.isSameItemSameComponents(p, item);
        }
        if (recipe == null || looping || path.size() >= MAX_DEPTH) {
            plan.loop |= recipe != null;
            ItemCount.add(plan.missing, item, amount);
            return;
        }
        int per = Math.max(1, recipe.recipe().output().getCount());
        long crafts = (amount + per - 1) / per;
        path.add(item);
        for (ItemCount in : recipe.recipe().inputs()) {
            need(plan, in.item, in.count * crafts, recipes, stock, path);
        }
        path.remove(path.size() - 1);
        plan.steps.add(new Step(recipe, crafts, amount));
        // 余った分は、あとで使える在庫になる（素材置き場へ戻すので）
        long surplus = crafts * per - amount;
        if (surplus > 0) {
            boolean found = false;
            for (ItemCount c : stock) {
                if (c.matches(item)) {
                    c.count += surplus;
                    found = true;
                    break;
                }
            }
            if (!found) {
                stock.add(new ItemCount(item, surplus));
            }
        }
    }
}
