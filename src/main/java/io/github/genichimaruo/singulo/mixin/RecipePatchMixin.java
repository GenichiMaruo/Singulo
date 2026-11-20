package io.github.genichimaruo.singulo.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import io.github.genichimaruo.singulo.recipe.RecipePatches;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RecipeManager.class)
abstract class RecipePatchMixin {
    @WrapOperation(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectoryWithModifier(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;Ljava/util/function/Consumer;)V"))
    private void singulo$expandRecipePatches(ResourceManager resources, FileToIdConverter lister, DynamicOps<JsonElement> ops,
                                             Codec<Recipe<?>> codec, Map<Identifier, Recipe<?>> result,
                                             Consumer<Map<Identifier, JsonElement>> downstream, Operation<Void> original) {
        original.call(resources, lister, ops, codec, result, (Consumer<Map<Identifier, JsonElement>>) jsons -> {
            var expanded = RecipePatches.expand(jsons, resources);
            if (expanded != jsons) {
                jsons.clear();
                jsons.putAll(expanded);
            }
            downstream.accept(jsons);
        });
    }
}
