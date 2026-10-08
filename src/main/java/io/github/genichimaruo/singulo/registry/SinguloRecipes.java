package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Singulo.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Singulo.MODID);

    public static final Supplier<RecipeType<MachineRecipe>> MACHINE = TYPES.register("machine",
            () -> RecipeType.simple(Singulo.id("machine")));
    public static final Supplier<RecipeSerializer<MachineRecipe>> MACHINE_SERIALIZER = SERIALIZERS.register("machine",
            MachineRecipe.Serializer::new);

    private SinguloRecipes() {}
}
