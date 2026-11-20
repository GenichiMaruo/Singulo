package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

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
                            Optional<ItemStackTemplate> resultTemplate, List<FluidStackTemplate> fluidResultTemplates, int minSize, int priority, Optional<ItemStackTemplate> byproductTemplate, List<String> shape)
        implements Recipe<MachineRecipe.Input> {

    public static final int ASSEMBLY_CELLS = 21;
    public static final int BOARD_EMPTY = -1, BOARD_RESTORE = -2;

    public MachineRecipe {
        shape = List.copyOf(shape);
    }

    /** Keep recipe loading independent of item component initialization. */
    // Validate the components as a player-sized stack, then restore the machine quantity.
    private static ItemStack createMachineStack(ItemStackTemplate template) {
        return template.withCount(1).create().copyWithCount(template.count());
    }
    public ItemStack result() { return resultTemplate.map(MachineRecipe::createMachineStack).orElse(ItemStack.EMPTY); }
    public List<FluidStack> fluidResults() { return fluidResultTemplates.stream().map(FluidStackTemplate::create).toList(); }
    public ItemStack byproduct() { return byproductTemplate.map(MachineRecipe::createMachineStack).orElse(ItemStack.EMPTY); }

    /** Compatibility constructor for runtime callers and tests with already initialized stacks. */
    public MachineRecipe(String station, int stage, int time, int energy, List<ItemInput> ingredients,
                         List<FluidInput> fluidIngredients, Optional<MassInput> mass, Optional<Ingredient> restore,
                         ItemStack result, List<FluidStack> fluidResults, int minSize, int priority, ItemStack byproduct) {
        this(station, stage, time, energy, ingredients, fluidIngredients, mass, restore,
             result, fluidResults, minSize, priority, byproduct, List.of());
    }

    public MachineRecipe(String station, int stage, int time, int energy, List<ItemInput> ingredients,
                         List<FluidInput> fluidIngredients, Optional<MassInput> mass, Optional<Ingredient> restore,
                         ItemStack result, List<FluidStack> fluidResults, int minSize, int priority, ItemStack byproduct, List<String> shape) {
        this(station, stage, time, energy, ingredients, fluidIngredients, mass, restore,
             template(result), fluidResults.stream().map(FluidStackTemplate::fromNonEmptyStack).toList(), minSize, priority, template(byproduct), shape);
    }
    private static Optional<ItemStackTemplate> template(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    /**
     * アイテム材料。tier &gt; 0 は触媒の寿命で払う材料で、ingredient（そのティアの触媒）のほか、上位の触媒でも払える。
     * 上位の触媒は1段ごとに使用回数−20%・処理速度+25%（2段まで。触媒を使う装置と同じ規則）。
     */
    public record ItemInput(Ingredient ingredient, int count, int uses, int tier) {
        public static final Codec<ItemInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                RecipeNumbers.POSITIVE.optionalFieldOf("count", 1).forGetter(ItemInput::count),
                RecipeNumbers.NONNEGATIVE.optionalFieldOf("uses", 0).forGetter(ItemInput::uses),
                Codec.INT.optionalFieldOf("tier", 0).forGetter(ItemInput::tier)
        ).apply(i, ItemInput::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemInput> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ItemInput::ingredient,
                ByteBufCodecs.VAR_INT, ItemInput::count,
                ByteBufCodecs.VAR_INT, ItemInput::uses,
                ByteBufCodecs.VAR_INT, ItemInput::tier,
                ItemInput::new);

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
        public static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.registries.BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidInput::fluid),
                RecipeNumbers.POSITIVE.fieldOf("amount").forGetter(FluidInput::amount)
        ).apply(i, FluidInput::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), FluidInput::fluid,
                ByteBufCodecs.VAR_INT, FluidInput::amount,
                FluidInput::new);

        public boolean matches(FluidStack stack) {
            return stack.getFluid().isSame(fluid) && stack.getAmount() >= amount;
        }
    }

    public record MassInput(double amount, boolean metalOnly) {
        public static final Codec<MassInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.validate(m -> Double.isFinite(m) && m > 0 ? DataResult.success(m)
                        : DataResult.error(() -> "Mass must be positive and finite")).fieldOf("amount").forGetter(MassInput::amount),
                Codec.BOOL.optionalFieldOf("metal_only", false).forGetter(MassInput::metalOnly)
        ).apply(i, MassInput::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, MassInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, MassInput::amount,
                ByteBufCodecs.BOOL, MassInput::metalOnly,
                MassInput::new);
    }

    /** 装置の入力スロットの中身。 */
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return items.get(index);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    /**
     * 材料の取り方。consume[i] はスロット i から減らす個数、uses[i] はスロット i のアイテムから減らす使用回数、
     * restoreSlot は修復対象のスロット（なければ -1）、speed は上位の触媒で払ったときの処理速度の倍率。
     */
    public record Plan(int[] consume, int[] uses, int restoreSlot, double speed) {}

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.<MachineRecipe>mapCodec(i -> i.group(
            Codec.STRING.fieldOf("station").forGetter(MachineRecipe::station),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(MachineRecipe::stage),
            RecipeNumbers.POSITIVE.fieldOf("time").forGetter(MachineRecipe::time),
            RecipeNumbers.NONNEGATIVE.optionalFieldOf("energy", 0).forGetter(MachineRecipe::energy),
            ItemInput.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(MachineRecipe::ingredients),
            FluidInput.CODEC.listOf().optionalFieldOf("fluid_ingredients", List.of()).forGetter(MachineRecipe::fluidIngredients),
            MassInput.CODEC.optionalFieldOf("mass").forGetter(MachineRecipe::mass),
            Ingredient.CODEC.optionalFieldOf("restore").forGetter(MachineRecipe::restore),
            RecipeNumbers.STACK.optionalFieldOf("result").forGetter(MachineRecipe::resultTemplate),
            RecipeNumbers.FLUID.listOf().optionalFieldOf("fluid_results", List.of()).forGetter(MachineRecipe::fluidResultTemplates),
            Codec.INT.optionalFieldOf("min_size", 0).forGetter(MachineRecipe::minSize),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            RecipeNumbers.STACK.optionalFieldOf("byproduct").forGetter(MachineRecipe::byproductTemplate),
            Codec.STRING.listOf().optionalFieldOf("shape", List.of()).forGetter(MachineRecipe::shape)
    ).apply(i, MachineRecipe::new)).validate(MachineRecipe::validateAssemblyShape);

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                buf.writeUtf(r.station);
                buf.writeVarInt(r.stage);
                buf.writeVarInt(r.time);
                buf.writeVarInt(r.energy);
                ItemInput.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.ingredients);
                FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.fluidIngredients);
                ByteBufCodecs.optional(MassInput.STREAM_CODEC).encode(buf, r.mass);
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).encode(buf, r.restore);
                ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).encode(buf, r.resultTemplate);
                FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.fluidResultTemplates);
                buf.writeVarInt(r.minSize);
                buf.writeVarInt(r.priority);
                ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).encode(buf, r.byproductTemplate);
                buf.writeCollection(r.shape, (b, row) -> b.writeUtf(row, 5));
            },
            buf -> new MachineRecipe(
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    ItemInput.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.optional(MassInput.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).decode(buf),
                    ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).decode(buf),
                    FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).decode(buf), buf.readList(b -> b.readUtf(5))));

    public boolean hasAssemblyShape() {
        return !shape.isEmpty();
    }

    public boolean assemblyCatalyst(ItemInput input) {
        if (hasAssemblyShape()) return input.uses() > 0;
        return input.tier() > 0 || java.util.Arrays.stream(input.ingredient().items().map(ItemStack::new).toArray(ItemStack[]::new))
                .anyMatch(stack -> CatalystHelper.tierOf(stack) > 0);
    }

    /** Can this recipe be laid out once on the assembly board and its single catalyst slot? */
    public boolean fitsAssemblyBoard() {
        if (!station.equals("precision_assembler") || mass.isPresent() || !fluidIngredients.isEmpty() || !fluidResultTemplates.isEmpty()) return false;
        if (hasAssemblyShape()) return true;
        long cells = restore.isPresent() ? 1 : 0;
        boolean catalyst = false;
        for (ItemInput input : ingredients) {
            if (assemblyCatalyst(input)) {
                if (catalyst || input.uses() <= 0 && input.count() != 1) return false;
                catalyst = true;
            } else {
                cells += input.uses() > 0 ? 1 : input.count();
            }
        }
        return cells <= ASSEMBLY_CELLS;
    }

    /** For shapeless datapacks, lay out one unit per cell in ingredient order, followed by the restoration item. */
    public int boardIngredientIndex(int cell) {
        if (cell < 0 || cell >= ASSEMBLY_CELLS) return BOARD_EMPTY;
        if (hasAssemblyShape()) return assemblyIngredientIndex(cell);
        for (int i = 0; i < ingredients.size(); i++) {
            ItemInput input = ingredients.get(i);
            if (assemblyCatalyst(input)) continue;
            int count = input.uses() > 0 ? 1 : input.count();
            if (cell < count) return i;
            cell -= count;
        }
        return cell == 0 && restore.isPresent() ? BOARD_RESTORE : BOARD_EMPTY;
    }

    /** Convert a board slot to a row-major five-by-five square, skipping the four corners. */
    public static int assemblySquare(int gridSlot) {
        if (gridSlot < 0 || gridSlot >= ASSEMBLY_CELLS) return -1;
        return gridSlot + (gridSlot < 3 ? 1 : gridSlot < 18 ? 2 : 3);
    }

    public int assemblyIngredientIndex(int gridSlot) {
        int square = assemblySquare(gridSlot);
        if (!hasAssemblyShape() || square < 0) return -1;
        char symbol = shape.get(square / 5).charAt(square % 5);
        return symbol == ' ' ? -1 : symbol - 'A';
    }

    @Nullable
    public ItemInput assemblyInput(int gridSlot) {
        int index = assemblyIngredientIndex(gridSlot);
        return index < 0 ? null : ingredients.get(index);
    }

    private DataResult<MachineRecipe> validateAssemblyShape() {
        if (!hasAssemblyShape()) return DataResult.success(this);
        if (!station.equals("precision_assembler") || restore.isPresent() || mass.isPresent())
            return DataResult.error(() -> "shape requires a precision assembler recipe without restore or mass");
        if (shape.size() != 5 || shape.stream().anyMatch(row -> row.length() != 5))
            return DataResult.error(() -> "shape must have five rows of five characters");
        int[] counts = new int[ingredients.size()];
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            char cell = shape.get(y).charAt(x);
            if (cell == ' ') continue;
            if ((x == 0 || x == 4) && (y == 0 || y == 4))
                return DataResult.error(() -> "The four corners of an assembly shape must be empty");
            int index = cell - 'A';
            if (cell < 'A' || cell > 'Z' || index >= counts.length)
                return DataResult.error(() -> "Unknown assembly ingredient symbol: " + cell);
            counts[index]++;
        }
        int catalysts = 0;
        for (int i = 0; i < counts.length; i++) {
            ItemInput input = ingredients.get(i);
            if (input.uses > 0) catalysts++;
            if (counts[i] != (input.uses > 0 ? 0 : input.count))
                return DataResult.error(() -> "Assembly cell counts must match the consumed ingredients");
        }
        if (catalysts > 1) return DataResult.error(() -> "The assembly board has one catalyst slot");
        return DataResult.success(this);
    }

    /** The 21 physical assembly cells in row order, followed by the active catalyst. */
    @Nullable
    public Plan assemblyPlan(List<ItemStack> boardWithCatalyst) {
        if (boardWithCatalyst.size() != ASSEMBLY_CELLS + 1) return null;
        if (!hasAssemblyShape()) {
            Plan shapeless = unorderedAssemblyPlan(boardWithCatalyst);
            if (shapeless == null) return null;
            for (int i = 0; i < ASSEMBLY_CELLS; i++) {
                ItemStack stack = boardWithCatalyst.get(i);
                if (!stack.isEmpty() && shapeless.consume[i] == 0 && shapeless.uses[i] == 0) return null;
            }
            return shapeless;
        }
        if (!matchesAssemblyMaterials(boardWithCatalyst)) return null;
        double speed = 1;
        int cost = 0;
        ItemStack catalyst = boardWithCatalyst.get(ASSEMBLY_CELLS);
        for (ItemInput input : ingredients) {
            if (input.uses <= 0) continue;
            cost = input.usesFrom(catalyst);
            if (cost == 0) return null;
            if (input.tier > 0) speed = CatalystHelper.higherSpeed(CatalystHelper.tierOf(catalyst) - input.tier);
        }
        int[] consume = new int[boardWithCatalyst.size()], uses = new int[boardWithCatalyst.size()];
        for (int i = 0; i < ASSEMBLY_CELLS; i++) if (!boardWithCatalyst.get(i).isEmpty()) consume[i] = 1;
        uses[ASSEMBLY_CELLS] = cost;
        return new Plan(consume, uses, -1, speed);
    }

    /** Check the physical diagram independently of the separately loaded catalyst. */
    public boolean matchesAssemblyMaterials(List<ItemStack> board) {
        if (!hasAssemblyShape() || board.size() < ASSEMBLY_CELLS) return false;
        for (int i = 0; i < ASSEMBLY_CELLS; i++) {
            ItemInput part = assemblyInput(i);
            ItemStack stack = board.get(i);
            if (part == null ? !stack.isEmpty() : stack.isEmpty() || !part.ingredient.test(stack)) return false;
        }
        return true;
    }

    public boolean canRestoreFrom(ItemStack stack) {
        return restore.isPresent() && restore.get().test(stack) && UsesHelper.canRestore(stack);
    }

    /** 入力スロットから材料を取れるなら、その取り方を返す。 */
    @Nullable
    public Plan plan(List<ItemStack> slots) {
        return plan(slots, false);
    }

    /** Each occupied material cell supplies one part per craft, even when it holds a stack. */
    @Nullable
    public Plan unorderedAssemblyPlan(List<ItemStack> slots) {
        return plan(slots, true);
    }

    @Nullable
    private Plan plan(List<ItemStack> slots, boolean assemblyBoard) {
        int n = slots.size();
        int[] left = new int[n];
        int[] consume = new int[n];
        int[] uses = new int[n];
        boolean[] claimed = new boolean[n];
        for (int i = 0; i < n; i++) {
            int count = slots.get(i).getCount();
            left[i] = assemblyBoard && i < ASSEMBLY_CELLS ? Math.min(1, count) : count;
        }
        int restoreSlot = -1;
        if (restore.isPresent()) {
            for (int i = 0; i < n; i++) {
                ItemStack s = slots.get(i);
                if (!claimed[i] && canRestoreFrom(s)) {
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

    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return assemble(input.items(), plan(input.items()));
    }

    public ItemStack assemble(List<ItemStack> inputs, @Nullable Plan plan) {
        ItemStack out = result();
        if (plan != null && out.is(SinguloItems.PORTABLE_ANDROID_PRINTER_2.get()) && out.getCount() == 1) {
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack input = inputs.get(i);
                if (plan.consume()[i] == 1 && input.is(SinguloItems.PORTABLE_ANDROID_PRINTER.get())) {
                    // The Mk1's four slots are the first set in the Mk2. Copy the stored FE, including any excess.
                    out.copyFrom(input, DataComponents.CONTAINER, SinguloComponents.ENERGY.get(), DataComponents.CUSTOM_NAME);
                    break;
                }
            }
        }
        return out;
    }

    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result();
    }

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
    public RecipeSerializer<MachineRecipe> getSerializer() {
        return SinguloRecipes.MACHINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<MachineRecipe> getType() {
        return SinguloRecipes.MACHINE.get();
    }

    @Override public ItemStack assemble(Input input) { return result(); }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
    }
    @Override public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC;
    }
    public static final RecipeSerializer<MachineRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
}
