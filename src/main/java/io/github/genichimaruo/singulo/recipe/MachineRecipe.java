package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 本modの装置で処理するレシピ（type: singulo:machine）。tools/gen_data.py が recipes.py から生成する。
 *
 * @param station      処理する装置の id（MachineType#id）
 * @param time         処理時間（tick）
 * @param energy       処理中の消費電力（FE/t）
 * @param ingredients  アイテム材料。uses &gt; 0 のものは消費せず使用回数を減らす
 * @param mass         質量材料（圧縮機の質量モード）
 * @param restore      修復・復元の対象。成果物は対象の使用回数を回復したもの
 * @param minSize      マルチブロックの大きさの下限（冷却塔の高さなど）。0なら条件なし
 * @param priority     大きいほど先に試す（混成ボーナスのように、通常のレシピより優先したいもの）
 */
public record MachineRecipe(String station, int stage, int time, int energy, List<ItemInput> ingredients,
                            List<FluidInput> fluidIngredients, Optional<MassInput> mass, Optional<Ingredient> restore,
                            ItemStack result, List<FluidStack> fluidResults, int minSize, int priority) implements Recipe<MachineRecipe.Input> {

    public record ItemInput(Ingredient ingredient, int count, int uses) {
        public static final Codec<ItemInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(ItemInput::count),
                Codec.INT.optionalFieldOf("uses", 0).forGetter(ItemInput::uses)
        ).apply(i, ItemInput::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemInput> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ItemInput::ingredient,
                ByteBufCodecs.VAR_INT, ItemInput::count,
                ByteBufCodecs.VAR_INT, ItemInput::uses,
                ItemInput::new);
    }

    public record FluidInput(Fluid fluid, int amount) {
        public static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.registries.BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidInput::fluid),
                Codec.INT.fieldOf("amount").forGetter(FluidInput::amount)
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
                Codec.DOUBLE.fieldOf("amount").forGetter(MassInput::amount),
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
     * restoreSlot は修復対象のスロット（なければ -1）。
     */
    public record Plan(int[] consume, int[] uses, int restoreSlot) {}

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("station").forGetter(MachineRecipe::station),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(MachineRecipe::stage),
            Codec.INT.fieldOf("time").forGetter(MachineRecipe::time),
            Codec.INT.optionalFieldOf("energy", 0).forGetter(MachineRecipe::energy),
            ItemInput.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(MachineRecipe::ingredients),
            FluidInput.CODEC.listOf().optionalFieldOf("fluid_ingredients", List.of()).forGetter(MachineRecipe::fluidIngredients),
            MassInput.CODEC.optionalFieldOf("mass").forGetter(MachineRecipe::mass),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("restore").forGetter(MachineRecipe::restore),
            ItemStack.CODEC.optionalFieldOf("result", ItemStack.EMPTY).forGetter(MachineRecipe::result),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_results", List.of()).forGetter(MachineRecipe::fluidResults),
            Codec.INT.optionalFieldOf("min_size", 0).forGetter(MachineRecipe::minSize),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority)
    ).apply(i, MachineRecipe::new));

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
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.result);
                FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.fluidResults);
                buf.writeVarInt(r.minSize);
                buf.writeVarInt(r.priority);
            },
            buf -> new MachineRecipe(
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    ItemInput.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.optional(MassInput.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt()));

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
        for (ItemInput in : ingredients) {
            if (in.uses() > 0) {
                int found = -1;
                for (int i = 0; i < n; i++) {
                    ItemStack s = slots.get(i);
                    if (!claimed[i] && in.ingredient().test(s) && UsesHelper.canUse(s, in.uses())) {
                        found = i;
                        break;
                    }
                }
                if (found < 0) {
                    return null;
                }
                claimed[found] = true;
                uses[found] = in.uses();
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
        return new Plan(consume, uses, restoreSlot);
    }

    public boolean isMassRecipe() {
        return mass.isPresent();
    }

    @Override
    public boolean matches(Input input, Level level) {
        return mass.isEmpty() && plan(input.items()) != null;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
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

    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        @Override
        public MapCodec<MachineRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
