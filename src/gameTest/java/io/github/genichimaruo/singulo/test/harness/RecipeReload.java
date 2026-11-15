package io.github.genichimaruo.singulo.test.harness;
import java.util.Collection;
import net.minecraft.world.item.crafting.*;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.InactiveProfiler;
/** Executes the same immutable RecipeMap replacement as /reload; no production access widening. */
public final class RecipeReload {
 private RecipeReload() {}
 public static void replace(RecipeManager manager, Collection<? extends RecipeHolder<?>> recipes) {
  try {
   var method=RecipeManager.class.getDeclaredMethod("apply",RecipeMap.class,ResourceManager.class,ProfilerFiller.class);
   method.setAccessible(true);
   method.invoke(manager,RecipeMap.create(new java.util.ArrayList<RecipeHolder<?>>(recipes)),null,InactiveProfiler.INSTANCE);
  } catch(ReflectiveOperationException error){throw new AssertionError("Cannot apply test recipe reload",error);}
 }
}
