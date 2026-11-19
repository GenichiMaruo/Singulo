package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidType;

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
    // 実行と画面で、合計や進み具合の割合を計算してもあふれないよう、余裕を残す
    private static final long MAX_AMOUNT = Long.MAX_VALUE / (MAX_VISITS * 1024L);

    record Key(net.minecraft.world.item.Item item, net.minecraft.nbt.CompoundTag components) {
        static Key of(ItemStack item) {
            ItemStack key = FluidSamples.liquid(item) ? FluidSamples.key(item) : item;
            return new Key(key.getItem(), key.getTag() == null ? null : key.getTag().copy());
        }
    }

    private static final class Context {
        final java.util.Map<Key, RecipeRackBlockEntity.Entry> recipes;
        final List<ItemCount> stock, surplus = new ArrayList<>();
        final Level level;
        int visits;
        Context(List<RecipeRackBlockEntity.Entry> entries, List<ItemCount> stock, @Nullable Level level) {
            this.stock = stock;
            this.level = level;
            this.recipes = mainRecipes(entries);
            if (level != null) for (var c : stock) {
                if (io.github.genichimaruo.singulo.wormhole.UnstableMouthItem.expired(c.item, level)) c.count = 0;
            }
            // Explicit main products take precedence over co-products from other cards.
            for (var entry : entries) {
                var recipe = entry.recipe();
                if (!recipe.processing()) continue;
                for (int i = 0; i < recipe.extras().size(); i++) {
                    var extra = recipe.extras().get(i);
                    if (extra.isEmpty()) continue;
                    int index = i;
                    recipes.computeIfAbsent(Key.of(extra), key -> new RecipeRackBlockEntity.Entry(
                            recipe.orderExtra(index), entry.location(), entry.block()));
                }
            }
        }
    }

    private WorkPlanner() {}

    private static java.util.Map<Key, RecipeRackBlockEntity.Entry> mainRecipes(List<RecipeRackBlockEntity.Entry> entries) {
        var selected = new java.util.LinkedHashMap<Key, RecipeRackBlockEntity.Entry>();
        for (var entry : entries) selected.putIfAbsent(Key.of(entry.recipe().outputKey()), entry);
        return selected;
    }

    /** The same one-card-per-main-product preference used to plan an order. */
    public static List<RecipeRackBlockEntity.Entry> preferredMainRecipes(List<RecipeRackBlockEntity.Entry> entries) {
        return List.copyOf(mainRecipes(entries).values());
    }

    /** 1つの工程: レシピを crafts 回作る（needed は要る数。作る数との差が余り）。 */
    public record Step(RecipeRackBlockEntity.Entry entry, long crafts, long needed) {}

    public static final class Plan {
        public final ItemStack target;
        public final long count;
        public final List<Step> steps = new ArrayList<>();
        /** 素材置き場から使う物（中間素材の在庫も含む）。 */
        public final List<ItemCount> fromStock = new ArrayList<>();
        /** Preloaded fluids reserved on the one worker chosen for this plan. */
        public final List<ItemCount> fromWorker = new ArrayList<>();
        /** 足りない物（在庫もレシピもない）。 */
        public final List<ItemCount> missing = new ArrayList<>();
        /** ループするレシピがあった。 */
        public boolean loop;
        /** 作業量・数の上限による失敗（レシピの循環とは別）。 */
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
            for (ItemCount c : fromWorker) {
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
        return plan(target, count, recipes, stock, null);
    }

    public static Plan plan(ItemStack target, long count, List<RecipeRackBlockEntity.Entry> recipes, List<ItemCount> stock, @Nullable Level level) {
        Plan plan = new Plan(target, count);
        need(plan, target, count, null, new Context(recipes, stock, level), new ArrayList<>());
        return plan;
    }

    /** 実際の在庫と、先に計画した工程の余りを分けて使う（予約するのは実際の在庫だけ）。 */
    private static void need(Plan plan, ItemStack item, long amount,
                             @javax.annotation.Nullable net.minecraft.world.item.crafting.Ingredient alt,
                             Context context, List<Key> path) {
        if (plan.limited) {
            return;
        }
        if (++context.visits > MAX_VISITS || amount < 0 || amount > MAX_AMOUNT) {
            plan.limited = true;
            return;
        }
        if (alt != null) {
            // 同じ種類の別の物も使う: 取り出しの優先度の高い資材口にある物から。同じなら、触媒は寿命の短いもの、
            // ほかの物は見本と同じ物から
            amount = takeRanked(context.stock, plan.fromStock, item, amount, alt);
            amount = takeRanked(context.surplus, null, item, amount, alt);
        } else {
            amount = take(context.stock, plan.fromStock, item, amount);
            amount = take(context.surplus, null, item, amount);
        }
        if (amount <= 0) {
            return;
        }
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
        long remaining = amount;
        for (BucketRun run : bucketRuns(recipe, crafts, context)) {
            for (ItemCount in : run.entry().recipe().inputs()) {
                if (in.count < 0 || in.count > MAX_AMOUNT / run.crafts()) {
                    plan.limited = true;
                    break;
                }
                need(plan, in.item, in.count * run.crafts(), in.alt, context, path);
            }
            if (plan.steps.size() >= MAX_STEPS) {
                plan.limited = true;
            }
            if (plan.limited) {
                break;
            }
            long needed = Math.min(remaining, run.crafts() * per);
            plan.steps.add(new Step(run.entry(), run.crafts(), needed));
            remaining -= needed;
        }
        path.remove(path.size() - 1);
        if (plan.limited) {
            return;
        }
        ItemCount.add(context.surplus, item, crafts * per - amount);
        // A registered machine cycle yields all its co-products from the same inputs.
        if (recipe.recipe().guaranteedExtras()) {
            for (ItemStack extra : recipe.recipe().extras()) {
                long units = FluidSamples.amount(extra);
                if (units > MAX_AMOUNT / crafts) {
                    plan.limited = true;
                    return;
                }
                ItemCount.add(context.surplus, FluidSamples.key(extra), units * crafts);
            }
        }
    }

    private record BucketSupply(int slots, long physical) {}
    private record BucketRun(RecipeRackBlockEntity.Entry entry, long crafts) {}

    /** Physical buckets first. Split only where an ingredient changes source, even on very large orders. */
    private static List<BucketRun> bucketRuns(RecipeRackBlockEntity.Entry entry, long crafts, Context context) {
        RecipeData recipe = entry.recipe();
        int eligible = context.level == null ? 0 : BucketCrafting.eligible(context.level, recipe);
        if (eligible == 0) {
            return List.of(new BucketRun(entry, crafts));
        }
        // Overlapping bucket alternatives share one balance. Previewing it must not consume the actual stock.
        List<ItemCount> budget = new ArrayList<>();
        for (var source : List.of(context.stock, context.surplus)) {
            for (ItemCount held : source) {
                if (held.count > 0 && held.alt == null) {
                    var copy = new ItemCount(held.item, held.count);
                    copy.takePriority = held.takePriority;
                    budget.add(copy);
                }
            }
        }
        List<BucketSupply> supplies = new ArrayList<>();
        while (eligible != 0) {
            int first = Integer.numberOfTrailingZeros(eligible);
            ItemStack bucket = recipe.grid().get(first);
            var alt = recipe.alt(first);
            int slots = 0;
            for (int i = first; i < 9; i++) {
                if ((eligible & (1 << i)) != 0 && ItemStack.isSameItemSameTags(bucket, recipe.grid().get(i))
                        && alt == recipe.alt(i)) {
                    slots |= 1 << i;
                }
            }
            eligible &= ~slots;
            long wanted = Integer.bitCount(slots) * crafts;
            long lacking = alt == null ? take(budget, null, bucket, wanted) : takeRanked(budget, null, bucket, wanted, alt);
            if (lacking == 0) {
                continue;
            }
            ItemStack sample = FluidSamples.key(BucketCrafting.fluid(bucket));
            long needMb = lacking * FluidType.BUCKET_VOLUME;
            long mb = needMb - take(budget, null, sample, needMb);
            boolean canMakeFluid = context.recipes.containsKey(Key.of(sample));
            if (mb == 0 && !canMakeFluid) {
                continue;
            }
            if (!canMakeFluid && context.recipes.containsKey(Key.of(bucket))) {
                lacking = mb / FluidType.BUCKET_VOLUME;
            }
            supplies.add(new BucketSupply(slots, wanted - lacking));
        }
        if (supplies.isEmpty()) {
            return List.of(new BucketRun(entry, crafts));
        }
        List<BucketRun> runs = new ArrayList<>();
        for (long done = 0; done < crafts;) {
            long repeat = crafts - done;
            int mask = 0;
            for (BucketSupply supply : supplies) {
                int slots = supply.slots();
                int per = Integer.bitCount(slots);
                long left = supply.physical() - done * per;
                if (left >= per) {
                    repeat = Math.min(repeat, left / per);
                } else {
                    if (left > 0) {
                        repeat = 1;
                        for (int i = 0; i < left; i++) {
                            slots &= slots - 1;
                        }
                    }
                    mask |= slots;
                }
            }
            var resolved = mask == 0 ? entry : new RecipeRackBlockEntity.Entry(recipe.withTankBuckets(mask), entry.location(), entry.block());
            runs.add(new BucketRun(resolved, repeat));
            done += repeat;
        }
        return runs;
    }

    /** stock のうち alt に合う物を、取り出しの優先度の高い順（同じなら、触媒は寿命の短い順、ほかは見本と同じ物から）に取る。 */
    private static long takeRanked(List<ItemCount> stock, @javax.annotation.Nullable List<ItemCount> reserved, ItemStack item,
                                   long amount, net.minecraft.world.item.crafting.Ingredient alt) {
        List<ItemCount> found = new java.util.ArrayList<>();
        for (ItemCount c : stock) {
            if (c.count > 0 && c.alt == null && alt.test(c.item)) {
                found.add(c);
            }
        }
        boolean catalyst = io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(item);
        found.sort(java.util.Comparator.<ItemCount>comparingInt(c -> -c.takePriority)
                .thenComparingInt(c -> catalyst ? ItemCount.lifeOrder(c.item) : c.matches(item) ? 0 : 1));
        for (ItemCount c : found) {
            if (amount <= 0) {
                break;
            }
            long take = Math.min(c.count, amount);
            c.count -= take;
            amount -= take;
            if (reserved != null) {
                ItemCount.add(reserved, c.item, take);
            }
        }
        return amount;
    }

    /** stock のうち item と同じ物を取る。 */
    static long take(List<ItemCount> stock, @javax.annotation.Nullable List<ItemCount> reserved,
                             ItemStack item, long amount) {
        if (WormholeOrders.pattern(item)) {
            // Take whole physical pairs first. Distinct orphans do not make a usable link.
            long pairs = take(stock, reserved, item, amount - amount % 2, true);
            return pairs + take(stock, reserved, item, amount % 2, false);
        }
        return take(stock, reserved, item, amount, false);
    }

    private static long take(List<ItemCount> stock, @Nullable List<ItemCount> reserved, ItemStack item, long amount, boolean pairs) {
        for (ItemCount c : stock) {
            if (amount <= 0) {
                break;
            }
            if (c.count <= 0 || c.alt != null) {
                continue;
            }
            if (!WormholeOrders.matches(item, c.item) || reserved != null && WormholeOrders.pattern(c.item)) {
                continue;
            }
            long take = Math.min(c.count, amount);
            if (pairs) take -= take % 2;
            c.count -= take;
            amount -= take;
            if (reserved != null) {
                ItemCount.add(reserved, c.item, take);
            }
        }
        return amount;
    }
}
