package io.github.genichimaruo.singulo.mixin;

import com.google.gson.JsonElement;
import io.github.genichimaruo.singulo.recipe.RecipePatches;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleJsonResourceReloadListener.class)
abstract class RecipePatchMixin {
    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At("RETURN"), cancellable = true)
    private void singulo$expandRecipePatches(ResourceManager resources, ProfilerFiller profiler,
                                             CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> callback) {
        if ((Object) this instanceof RecipeManager) {
            var recipes = callback.getReturnValue();
            var expanded = RecipePatches.expand(recipes, resources);
            if (expanded != recipes) callback.setReturnValue(expanded);
        }
    }
}
