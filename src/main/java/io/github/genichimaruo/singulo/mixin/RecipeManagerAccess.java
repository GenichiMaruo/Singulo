package io.github.genichimaruo.singulo.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Identity changes when recipes are replaced, without copying the complete recipe registry per query. */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccess {
    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> singulo$recipes();
}
