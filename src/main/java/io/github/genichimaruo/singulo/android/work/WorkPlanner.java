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
    private static final int MAX_STEPS = 1024, MAX_VISITS = 8192;
    // Leave headroom for sums and progress percentages in execution and GUI code.
    private static final long MAX_AMOUNT = Long.MAX_VALUE / (MAX_VISITS * 1024L);

    private record Key(net.minecraft.world.item.Item item, net.minecraft.core.component.DataComponentMap components) {
        static Key of(ItemStack item) {
            ItemStack key = FluidSamples.key(item);
            return new Key(key.getItem(), key.getComponents());
        }
    }

    private static final class Context {
        final java.util.Map<Key, RecipeRackBlockEntity.Entry> recipes = new java.util.HashMap<>();
        final List<ItemCount> stock, surplus = new ArrayList<>();
        int visits;
        Context(List<RecipeRackBlockEntity.Entry> entries, List<ItemCount> stock) {
            this.stock = stock;
            for (var entry : entries) recipes.putIfAbsent(Key.of(entry.recipe().outputKey()), entry);
        }
    }

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
        /** Work/quantity limits are different from an actual recipe cycle. */
        public boolean limited;

        public String failureKey() {
            return "gui.singulo.work_console.error." + (limited ? "limit" : loop ? "loop" : "missing");
        }

        Plan(ItemStack target, long count) {
            this.target = FluidSamples.key(target);
            this.count = count;
        }

        public boolean ok() {
            return missing.isEmpty() && !loop && !limited;
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
        need(plan, target, count, null, new Context(recipes, stock), new ArrayList<>());
        return plan;
    }

    /** Use real stock and previously planned surplus separately; only real stock is reserved. */
    private static void need(Plan plan, ItemStack item, long amount,
                             @javax.annotation.Nullable net.minecraft.world.item.crafting.Ingredient alt,
                             Context context, List<Key> path) {
        if (plan.limited) return;
        if (++context.visits > MAX_VISITS || amount < 0 || amount > MAX_AMOUNT) {
            plan.limited = true;
            return;
        }
        for (int pass = 0; pass < (alt == null ? 1 : 2) && amount > 0; pass++) {
            amount = take(context.stock, plan.fromStock, item, amount, alt, pass);
            amount = take(context.surplus, null, item, amount, alt, pass);
        }
        if (amount <= 0) return;
        Key key = Key.of(item);
        RecipeRackBlockEntity.Entry recipe = context.recipes.get(key);
        if (recipe == null || path.contains(key)) {
            plan.loop |= recipe != null;
            ItemCount.add(plan.missing, item, amount);
            return;
        }
        if (path.size() >= MAX_DEPTH || plan.steps.size() >= MAX_STEPS) {
            plan.limited = true;
            return;
        }
        int per = recipe.recipe().outputCount();
        long crafts = 1 + (amount - 1) / per;
        path.add(key);
        for (ItemCount in : recipe.recipe().inputs()) {
            if (in.count < 0 || in.count > MAX_AMOUNT / crafts) {
                plan.limited = true;
                break;
            }
            need(plan, in.item, in.count * crafts, in.alt, context, path);
            if (plan.limited) break;
        }
        path.remove(path.size() - 1);
        if (plan.limited) return;
        if (plan.steps.size() >= MAX_STEPS) {
            plan.limited = true;
            return;
        }
        plan.steps.add(new Step(recipe, crafts, amount));
        ItemCount.add(context.surplus, item, crafts * per - amount);
    }

    private static long take(List<ItemCount> stock, @javax.annotation.Nullable List<ItemCount> reserved,
                             ItemStack item, long amount,
                             @javax.annotation.Nullable net.minecraft.world.item.crafting.Ingredient alt, int pass) {
        for (ItemCount c : stock) {
            if (amount <= 0) break;
            if (c.count <= 0 || c.alt != null) continue;
            boolean matches = pass == 0 ? c.matches(item) : alt != null && alt.test(c.item) && !c.matches(item);
            if (!matches) continue;
            long take = Math.min(c.count, amount);
            c.count -= take;
            amount -= take;
            if (reserved != null) ItemCount.add(reserved, c.item, take);
        }
        return amount;
    }
}
