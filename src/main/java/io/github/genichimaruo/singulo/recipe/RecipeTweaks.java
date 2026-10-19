package io.github.genichimaruo.singulo.recipe;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.data.Difficulty;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CreativeCatalystItem;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.item.UsesItem;
import io.github.genichimaruo.singulo.machine.MachineType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * サーバー設定（とプリセット）を、読み込んだレシピへ実行時に当てはめる。
 * <p>
 * 優先順位: データパックのレシピJSON ＜ 既定値から変えた個別の設定 ＜ プリセットの倍率。
 * 個別の設定が既定値のままなら、JSON（データパックで書き換えた値を含む）をそのまま使う。
 * 装置の加工（{@code MachineBlockEntity}）と JEI の表示は、どちらもこの結果を使う。
 * <p>
 * 当てはめるもの: ネザースターの数（エキゾチック物質・シンギュラリティ・コア）、エキゾチック物質の出力数、
 * 人工星核の可否と必要ジェット凝縮体数、失活触媒の回収率、時間結晶の育成時間、縮退物質殻の岩石Lv3・金属の核・冷却材、
 * 殻1個からの炉殻の数、残響の欠片1個のスカルク数。
 */
public final class RecipeTweaks {
    private RecipeTweaks() {}

    /** 当てはめた結果（元のレシピ → 調整後。無効にしたレシピは DISABLED）。設定が変わったら作り直す。 */
    private static final Map<MachineRecipe, MachineRecipe> CACHE = new IdentityHashMap<>();
    private static final MachineRecipe DISABLED = new MachineRecipe("", 0, 1, 0, List.of(), List.of(), java.util.Optional.empty(),
            java.util.Optional.empty(), ItemStack.EMPTY, List.of(), 0, 0, ItemStack.EMPTY);
    private static String stamp = "";
    private static long stampCheckedAt;

    /** 設定を当てはめたレシピ。設定で無効になったレシピは null。 */
    @Nullable
    public static synchronized MachineRecipe adjust(ResourceLocation id, MachineRecipe recipe) {
        if (!ServerConfig.SPEC.isLoaded()) {
            return recipe;
        }
        refreshStamp();
        MachineRecipe cached = CACHE.get(recipe);
        if (cached == null) {
            MachineRecipe made = compute(id, recipe);
            cached = made == null ? DISABLED : made;
            if (CACHE.size() > 4096) {
                CACHE.clear();
            }
            CACHE.put(recipe, cached);
        }
        return cached == DISABLED ? null : cached;
    }

    /** 当てはめた結果をすぐに捨てる（試験で設定を書き換えたとき。ふだんは1秒ごとに設定の変化を見ている）。 */
    public static synchronized void invalidate() {
        CACHE.clear();
        stamp = "";
    }

    /** レシピの一覧に当てはめる（無効になったものは除く）。 */
    public static List<MachineRecipe> adjustAll(Iterable<MachineRecipe> holders) {
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe h : holders) {
            MachineRecipe r = adjust(h.id(), h.value());
            if (r != null) {
                out.add(r == h.value() ? h : r.withId(h.id()));
            }
        }
        return out;
    }

    /** 設定が変わっていたら、当てはめた結果を捨てる（調べるのは1秒に1回まで）。 */
    private static void refreshStamp() {
        long now = System.nanoTime();
        if (now - stampCheckedAt < 1_000_000_000L && !stamp.isEmpty()) {
            return;
        }
        stampCheckedAt = now;
        String s = ServerConfig.PRESET.get() + '|' + ServerConfig.STARS_PER_EXOTIC_BATCH.get() + '|' + ServerConfig.EXOTIC_BATCH_SIZE.get()
                + '|' + ServerConfig.STARS_PER_SINGULARITY_CORE.get() + '|' + ServerConfig.ALLOW_ARTIFICIAL_STAR_CORE.get()
                + '|' + ServerConfig.ARTIFICIAL_STAR_CORE_JET_CONDENSATE.get() + '|' + ServerConfig.SPENT_RECYCLE_RATIO.get()
                + '|' + ServerConfig.TIME_CRYSTAL_GROWTH_TICKS.get() + '|' + ServerConfig.SHELL_ROCK_LV3.get()
                + '|' + ServerConfig.SHELL_METAL_CORES.get() + '|' + ServerConfig.SHELL_NITROGEN_MB.get()
                + '|' + ServerConfig.REACTOR_PLATES_PER_SHELL.get() + '|' + ServerConfig.SCULK_PER_SHARD.get()
                + '|' + ServerConfig.LIFETIME_MULTIPLIER.get() + '|' + ServerConfig.MUON_LIFETIME_MINUTES.get()
                + '|' + ServerConfig.BOSE_LIFETIME_MINUTES.get() + '|' + ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.get()
                + '|' + ServerConfig.SINGULARITY_LIFETIME_MINUTES.get();
        if (!s.equals(stamp)) {
            stamp = s;
            CACHE.clear();
        }
    }

    /** 設定値。既定値のままなら JSON の値 jsonValue を使う。 */
    private static int configured(ForgeConfigSpec.IntValue value, int jsonValue) {
        int v = value.get();
        return v == value.getDefault() ? jsonValue : v;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(Singulo.id(path));
    }

    private static Predicate<Ingredient> accepts(Item item) {
        return ing -> ing.test(new ItemStack(item));
    }

    /** ネザースター（タグ singulo:star_core）の材料か。 */
    private static boolean isStar(Ingredient ing) {
        return ing.test(new ItemStack(Items.NETHER_STAR));
    }

    @Nullable
    private static MachineRecipe compute(ResourceLocation id, MachineRecipe r) {
        if (!Singulo.MODID.equals(id.getNamespace())) {
            return r;
        }
        String path = id.getPath();
        List<MachineRecipe.ItemInput> items = new ArrayList<>(r.ingredients());
        List<MachineRecipe.FluidInput> fluids = new ArrayList<>(r.fluidIngredients());
        ItemStack result = r.result();
        int time = r.time();

        switch (path) {
            case "casimir_cavity/exotic_matter" -> {
                int stars = Difficulty.stars(configured(ServerConfig.STARS_PER_EXOTIC_BATCH, countOf(items, RecipeTweaks::isStar)));
                setCount(items, RecipeTweaks::isStar, stars);
                result = result.copyWithCount(Math.max(1, configured(ServerConfig.EXOTIC_BATCH_SIZE, result.getCount())));
            }
            case "singularity_encapsulator/singularity_core" -> {
                int stars = Difficulty.stars(configured(ServerConfig.STARS_PER_SINGULARITY_CORE, countOf(items, RecipeTweaks::isStar)));
                setCount(items, RecipeTweaks::isStar, stars);
            }
            case "singularity_encapsulator/artificial_star_core" -> {
                Predicate<Ingredient> jet = accepts(item("jet_condensate"));
                setCount(items, jet, Math.max(1, configured(ServerConfig.ARTIFICIAL_STAR_CORE_JET_CONDENSATE, countOf(items, jet))));
            }
            case "time_crystal_incubator/time_crystal_catalyst" -> time = Math.max(20, configured(ServerConfig.TIME_CRYSTAL_GROWTH_TICKS, time));
            case "degenerate_compactor/degenerate_matter_shell" -> {
                Predicate<Ingredient> rock = accepts(item("compressed_block_3"));
                Predicate<Ingredient> metal = accepts(item("compressed_metal_block_2"));
                setCount(items, rock, Difficulty.compression(configured(ServerConfig.SHELL_ROCK_LV3, countOf(items, rock))));
                setCount(items, metal, Difficulty.compression(configured(ServerConfig.SHELL_METAL_CORES, countOf(items, metal))));
                setNitrogen(fluids, amount -> fitTank(Difficulty.compression(configured(ServerConfig.SHELL_NITROGEN_MB, amount))));
            }
            case "degenerate_compactor/degenerate_matter_shell_from_strange_matter" -> {
                Predicate<Ingredient> metal = accepts(item("compressed_metal_block_2"));
                setCount(items, metal, Difficulty.compression(configured(ServerConfig.SHELL_METAL_CORES, countOf(items, metal))));
                // ストレンジ物質はすでに縮退しているので、冷却材は通常の殻の半分（設定 shellNitrogenMb も半分に換算する）
                setNitrogen(fluids, amount -> {
                    int v = ServerConfig.SHELL_NITROGEN_MB.get();
                    int base = v == ServerConfig.SHELL_NITROGEN_MB.getDefault() ? amount : (int) Math.round(v / 2.0);
                    return fitTank(Difficulty.compression(base));
                });
            }
            case "degenerate_compactor/reactor_shell" ->
                    result = result.copyWithCount(Math.max(1, configured(ServerConfig.REACTOR_PLATES_PER_SHELL, result.getCount())));
            case "echo_resonator/echo_shard" -> {
                Predicate<Ingredient> sculk = accepts(Items.SCULK);
                setCount(items, sculk, Math.max(1, configured(ServerConfig.SCULK_PER_SHARD, countOf(items, sculk))));
            }
            default -> {
                if (path.startsWith("catalytic_reactor/") && path.endsWith("_recycled")) {
                    double ratio = ServerConfig.SPENT_RECYCLE_RATIO.get();
                    double def = ServerConfig.SPENT_RECYCLE_RATIO.getDefault();
                    if (ratio != def) {
                        if (ratio <= 0) {
                            return null;                                     // 回収率0: リサイクルできない
                        }
                        // 既定（25%）で失活触媒 n 個なら、回収率 ratio では n × 0.25 / ratio 個
                        for (int i = 0; i < items.size(); i++) {
                            MachineRecipe.ItemInput in = items.get(i);
                            if (in.uses() == 0 && in.ingredient().getItems().length > 0
                                    && BuiltInRegistries.ITEM.getKey(in.ingredient().getItems()[0].getItem()).getPath().startsWith("spent_")) {
                                int n = Math.max(1, (int) Math.round(in.count() * def / ratio));
                                items.set(i, new MachineRecipe.ItemInput(in.ingredient(), n, 0));
                            }
                        }
                    }
                }
            }
        }

        // 触媒を寿命（使用回数）で使う材料は、設定とプリセットで変えた新品の寿命に比例させる（JSONは既定の寿命での値）。
        // 新品の寿命を基準にするので、純度の低い・使いかけの触媒が安く使えるようにはならない
        for (int i = 0; i < items.size(); i++) {
            MachineRecipe.ItemInput in = items.get(i);
            if (in.uses() <= 0) {
                continue;
            }
            for (ItemStack s : in.ingredient().getItems()) {
                if (s.getItem() instanceof UsesItem u && u.isCatalyst() && !(s.getItem() instanceof CreativeCatalystItem)) {
                    int fresh = UsesHelper.baseMax(new ItemStack(u));
                    int def = u.defaultMax();
                    if (def > 0 && fresh != def) {
                        int scaled = (int) Math.max(1, Math.min(fresh, Math.round((double) in.uses() * fresh / def)));
                        items.set(i, new MachineRecipe.ItemInput(in.ingredient(), in.count(), scaled));
                    }
                    break;
                }
            }
        }

        // 人工星核を使わせない設定なら、ネザースターの材料を本物のネザースターだけにする
        if (!ServerConfig.ALLOW_ARTIFICIAL_STAR_CORE.get()) {
            Item artificial = item("artificial_star_core");
            for (int i = 0; i < items.size(); i++) {
                MachineRecipe.ItemInput in = items.get(i);
                if (isStar(in.ingredient()) && in.ingredient().test(new ItemStack(artificial))) {
                    items.set(i, new MachineRecipe.ItemInput(Ingredient.of(Items.NETHER_STAR), in.count(), in.uses()));
                }
            }
        }

        if (items.equals(r.ingredients()) && fluids.equals(r.fluidIngredients()) && time == r.time()
                && ItemStack.matches(result, r.result())) {
            return r;
        }
        return new MachineRecipe(r.station(), r.stage(), time, r.energy(), List.copyOf(items), List.copyOf(fluids), r.mass(),
                r.restore(), result, r.fluidResults(), r.minSize(), r.priority(), r.byproduct());
    }

    /** 液体の材料は装置のタンク（{@link MachineType#TANK_CAPACITY}）1つに収まる量まで（超えると加工が始まらない）。 */
    private static int fitTank(int amount) {
        return Math.min(amount, MachineType.TANK_CAPACITY);
    }

    private static int countOf(List<MachineRecipe.ItemInput> items, Predicate<Ingredient> which) {
        for (MachineRecipe.ItemInput in : items) {
            if (in.uses() == 0 && which.test(in.ingredient())) {
                return in.count();
            }
        }
        return 0;
    }

    /** which に当たる材料の数を count にする（0 なら材料から外す）。もとの材料になければ何もしない。 */
    private static void setCount(List<MachineRecipe.ItemInput> items, Predicate<Ingredient> which, int count) {
        for (int i = 0; i < items.size(); i++) {
            MachineRecipe.ItemInput in = items.get(i);
            if (in.uses() == 0 && which.test(in.ingredient())) {
                if (count <= 0) {
                    items.remove(i);
                } else if (count != in.count()) {
                    items.set(i, new MachineRecipe.ItemInput(in.ingredient(), count, 0));
                }
                return;
            }
        }
    }

    private static void setNitrogen(List<MachineRecipe.FluidInput> fluids, java.util.function.IntUnaryOperator amount) {
        for (int i = 0; i < fluids.size(); i++) {
            MachineRecipe.FluidInput in = fluids.get(i);
            if (BuiltInRegistries.FLUID.getKey(in.fluid()).getPath().equals("liquid_nitrogen")) {
                int a = amount.applyAsInt(in.amount());
                if (a <= 0) {
                    fluids.remove(i);
                } else {
                    fluids.set(i, new MachineRecipe.FluidInput(in.fluid(), a));
                }
                return;
            }
        }
    }
}
