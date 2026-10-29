package io.github.genichimaruo.singulo.recipe;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * 本modの装置で処理するレシピ（type: singulo:machine）。tools/gen_data.py が recipes.py から生成する。
 *
 * @param station      処理する装置の id（MachineType#id）
 * @param time         処理時間（tick）
 * @param energy       処理中の消費電力（FE/t）
 * @param ingredients  アイテム材料。uses &gt; 0 のものは消費せず使用回数を減らす。tier &gt; 0 のものはそのティア以上の触媒で払える
 * @param mass         質量材料（圧縮機の質量モード）
 * @param restore      修復・復元の対象。成果物は対象の使用回数を回復したもの
 * @param minSize      マルチブロックの大きさの下限（冷却塔の高さなど）。0なら条件なし
 * @param priority     大きいほど先に試す（混成ボーナスのように、通常のレシピより優先したいもの）
 * @param byproduct    成果物と一緒に出る副産物（粗銅の精錬のビスマスなど）。空ならなし
 */
public record MachineRecipe(String station, int stage, int time, int energy, List<ItemInput> ingredients,
                            List<FluidInput> fluidIngredients, Optional<MassInput> mass, Optional<Ingredient> restore,
                            ItemStack result, List<FluidStack> fluidResults, int minSize, int priority, ItemStack byproduct)
        implements Recipe<MachineRecipe.Input> {

    /**
     * アイテム材料。tier &gt; 0 は触媒の寿命で払う材料で、ingredient（そのティアの触媒）のほか、上位の触媒でも払える。
     * 上位の触媒は1段ごとに使用回数−20%・処理速度+25%（2段まで。触媒を使う装置と同じ規則）。
     */
    public record ItemInput(Ingredient ingredient, int count, int uses, int tier) {
        public ItemInput(Ingredient ingredient, int count, int uses) {
            this(ingredient, count, uses, 0);
        }

        /** 使用回数で払う材料を stack で払うときの使用回数。払えなければ 0。 */
        public int usesFrom(ItemStack stack) {
            int pay = uses;
            if (tier > 0) {
                int have = CatalystHelper.tierOf(stack);
                if (have < tier) {
                    return 0;
                }
                pay = CatalystHelper.higherUses(uses, have - tier);
            } else if (!ingredient.test(stack)) {
                return 0;
            }
            return UsesHelper.canUse(stack, pay) ? pay : 0;
        }
    }

    public record FluidInput(Fluid fluid, int amount) {

        public boolean matches(FluidStack stack) {
            return stack.getFluid().isSame(fluid) && stack.getAmount() >= amount;
        }
    }

    public record MassInput(double amount, boolean metalOnly) {
    }

    /** 装置の入力スロットの中身。 */
    public static final class Input extends net.minecraft.world.SimpleContainer {
        private final List<ItemStack> items;
        public Input(List<ItemStack> items) { super(items.toArray(ItemStack[]::new)); this.items = items; }
        public List<ItemStack> items() { return items; }
        @Override
        public ItemStack getItem(int index) {
            return items.get(index);
        }

        public int size() {
            return items.size();
        }
    }

    /**
     * 材料の取り方。consume[i] はスロット i から減らす個数、uses[i] はスロット i のアイテムから減らす使用回数、
     * restoreSlot は修復対象のスロット（なければ -1）、speed は上位の触媒で払ったときの処理速度の倍率。
     */
    public record Plan(int[] consume, int[] uses, int restoreSlot, double speed) {}

    /** 入力スロットから材料を取れるなら、その取り方を返す。 */
    @Nullable
    public Plan plan(List<ItemStack> slots) {
        int n = slots.size();
        int[] left = new int[n];
        int[] consume = new int[n];
        int[] uses = new int[n];
        boolean[] claimed = new boolean[n];
        for (int i = 0; i < n; i++) {
            left[i] = slots.get(i).getCount();
        }
        int restoreSlot = -1;
        if (restore.isPresent()) {
            for (int i = 0; i < n; i++) {
                ItemStack s = slots.get(i);
                if (!claimed[i] && restore.get().test(s) && UsesHelper.canRestore(s)) {
                    restoreSlot = i;
                    claimed[i] = true;
                    consume[i] = 1;
                    left[i] -= 1;
                    break;
                }
            }
            if (restoreSlot < 0) {
                return null;
            }
        }
        double speed = 1;
        for (ItemInput in : ingredients) {
            if (in.uses() > 0) {
                // 払えるもののうち、ティアのいちばん低い触媒で払う（上位の触媒はとっておく）
                int found = -1;
                int foundTier = 0;
                for (int i = 0; i < n; i++) {
                    ItemStack s = slots.get(i);
                    if (claimed[i] || in.usesFrom(s) == 0) {
                        continue;
                    }
                    int tier = CatalystHelper.tierOf(s);
                    if (found < 0 || tier < foundTier) {
                        found = i;
                        foundTier = tier;
                    }
                }
                if (found < 0) {
                    return null;
                }
                claimed[found] = true;
                uses[found] = in.usesFrom(slots.get(found));
                if (in.tier() > 0) {
                    speed *= CatalystHelper.higherSpeed(foundTier - in.tier());
                }
                continue;
            }
            int need = in.count();
            for (int i = 0; i < n && need > 0; i++) {
                ItemStack s = slots.get(i);
                if (claimed[i] || left[i] <= 0 || !in.ingredient().test(s)) {
                    continue;
                }
                int take = Math.min(need, left[i]);
                left[i] -= take;
                consume[i] += take;
                need -= take;
            }
            if (need > 0) {
                return null;
            }
        }
        return new Plan(consume, uses, restoreSlot, speed);
    }

    public boolean isMassRecipe() {
        return mass.isPresent();
    }

    @Override
    public boolean matches(Input input, Level level) {
        return mass.isEmpty() && plan(input.items()) != null;
    }

    @Override
    public ItemStack assemble(Input input, net.minecraft.core.RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        restore.ifPresent(list::add);
        ingredients.forEach(in -> list.add(in.ingredient()));
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SinguloRecipes.MACHINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SinguloRecipes.MACHINE.get();
    }

    @Override
    public net.minecraft.resources.ResourceLocation getId() { return id(); }

    // Legacy recipes receive ids outside their constructor. Use weak *identity*
    // keys: equal recipes from a later login must not inherit an older weak key,
    // and mutable ingredient/stack hashes must never invalidate their ids.
    private static final java.lang.ref.ReferenceQueue<MachineRecipe> ID_QUEUE = new java.lang.ref.ReferenceQueue<>();
    private static final java.util.Map<RecipeKey, net.minecraft.resources.ResourceLocation> IDS = new java.util.HashMap<>();
    private static final class RecipeKey extends java.lang.ref.WeakReference<MachineRecipe> {
        private final int hash;
        RecipeKey(MachineRecipe recipe) { super(recipe, ID_QUEUE); hash = System.identityHashCode(recipe); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RecipeKey key && get() != null && get() == key.get();
        }
    }
    private static void drainIds() {
        java.lang.ref.Reference<? extends MachineRecipe> key;
        while ((key = ID_QUEUE.poll()) != null) IDS.remove(key);
    }
    public MachineRecipe withId(net.minecraft.resources.ResourceLocation id) {
        synchronized (IDS) { drainIds(); IDS.put(new RecipeKey(this), id); }
        return this;
    }
    public MachineRecipe value() { return this; }
    public net.minecraft.resources.ResourceLocation id() {
        synchronized (IDS) { drainIds(); return IDS.getOrDefault(new RecipeKey(this), io.github.genichimaruo.singulo.Singulo.id(station)); }
    }

    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        public MachineRecipe fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
            var ingredients = new java.util.ArrayList<ItemInput>();
            if (json.has("ingredients")) for (var e : json.getAsJsonArray("ingredients")) {
                var o = e.getAsJsonObject();
                ingredients.add(new ItemInput(Ingredient.fromJson(o.get("ingredient")), integer(o, "count", 1), integer(o, "uses", 0), integer(o, "tier", 0)));
            }
            var fluids = new java.util.ArrayList<FluidInput>();
            if (json.has("fluid_ingredients")) for (var e : json.getAsJsonArray("fluid_ingredients")) {
                var o = e.getAsJsonObject();
                fluids.add(new FluidInput(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(new net.minecraft.resources.ResourceLocation(o.get(o.has("fluid") ? "fluid" : "id").getAsString())), integer(o, "amount", 0)));
            }
            var outputs = new java.util.ArrayList<FluidStack>();
            if (json.has("fluid_results")) for (var e : json.getAsJsonArray("fluid_results")) {
                var o = e.getAsJsonObject();
                outputs.add(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(new net.minecraft.resources.ResourceLocation(o.get(o.has("fluid") ? "fluid" : "id").getAsString())), integer(o, "amount", 0)));
            }
            Optional<MassInput> mass = Optional.empty();
            if (json.has("mass")) { var o = json.getAsJsonObject("mass"); mass = Optional.of(new MassInput(o.get("amount").getAsDouble(), o.has("metal_only") && o.get("metal_only").getAsBoolean())); }
            ItemStack result = ItemStack.EMPTY;
            if (json.has("result")) {
                var o = json.getAsJsonObject("result");
                String item = o.get(o.has("id") ? "id" : "item").getAsString();
                result = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(item)), integer(o, "count", 1));
                if (o.has("components")) {
                    var c = o.getAsJsonObject("components");
                    for (var entry : c.entrySet()) result.getOrCreateTag().put(entry.getKey(), com.mojang.serialization.JsonOps.INSTANCE.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, entry.getValue()));
                }
            }
            var recipe = new MachineRecipe(json.get("station").getAsString(), integer(json,"stage",1), integer(json,"time",1), integer(json,"energy",0), ingredients, fluids, mass,
                    json.has("restore") ? Optional.of(Ingredient.fromJson(json.get("restore"))) : Optional.empty(), result, outputs, integer(json,"min_size",0), integer(json,"priority",0), json.has("byproduct") ? readOutput(json.getAsJsonObject("byproduct")) : ItemStack.EMPTY);
            return recipe.withId(id);
        }
        private static ItemStack readOutput(com.google.gson.JsonObject json) {
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(
                    json.get(json.has("id") ? "id" : "item").getAsString())), integer(json, "count", 1));
        }
        private static int integer(com.google.gson.JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
        public MachineRecipe fromNetwork(net.minecraft.resources.ResourceLocation id, FriendlyByteBuf buf) {
            var ingredients = buf.readList(b -> new ItemInput(Ingredient.fromNetwork(b), b.readVarInt(), b.readVarInt(), b.readVarInt()));
            var fluids = buf.readList(b -> new FluidInput(net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(b.readVarInt()), b.readVarInt()));
            Optional<MassInput> mass = buf.readBoolean() ? Optional.of(new MassInput(buf.readDouble(),buf.readBoolean())) : Optional.empty();
            Optional<Ingredient> restore = buf.readBoolean() ? Optional.of(Ingredient.fromNetwork(buf)) : Optional.empty();
            ItemStack result = buf.readItem();
            var outputs = buf.readList(FluidStack::readFromPacket);
            var recipe = new MachineRecipe(buf.readUtf(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),ingredients,fluids,mass,restore,result,outputs,buf.readVarInt(),buf.readVarInt(),buf.readItem());
            return recipe.withId(id);
        }
        public void toNetwork(FriendlyByteBuf buf, MachineRecipe r) {
            buf.writeCollection(r.ingredients(), (b,v) -> { v.ingredient().toNetwork(b); b.writeVarInt(v.count()); b.writeVarInt(v.uses()); b.writeVarInt(v.tier()); });
            buf.writeCollection(r.fluidIngredients(), (b,v) -> { b.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(v.fluid())); b.writeVarInt(v.amount()); });
            buf.writeBoolean(r.mass().isPresent()); r.mass().ifPresent(v -> { buf.writeDouble(v.amount()); buf.writeBoolean(v.metalOnly()); });
            buf.writeBoolean(r.restore().isPresent()); r.restore().ifPresent(v -> v.toNetwork(buf));
            buf.writeItem(r.result()); buf.writeCollection(r.fluidResults(), (b,v) -> v.writeToPacket(b));
            buf.writeUtf(r.station()); buf.writeVarInt(r.stage()); buf.writeVarInt(r.time()); buf.writeVarInt(r.energy()); buf.writeVarInt(r.minSize()); buf.writeVarInt(r.priority()); buf.writeItem(r.byproduct());
        }
    }
}
