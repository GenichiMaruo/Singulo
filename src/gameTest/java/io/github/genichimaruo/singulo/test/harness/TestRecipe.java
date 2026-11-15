package io.github.genichimaruo.singulo.test.harness;
public final class TestRecipe {
 public static net.minecraft.world.item.ItemStack result(net.minecraft.world.item.crafting.Recipe<?> recipe, net.minecraft.world.level.Level level) {
  if(recipe instanceof io.github.genichimaruo.singulo.recipe.MachineRecipe machine) return machine.result();
  var displays=recipe.display();
  return displays.isEmpty()?net.minecraft.world.item.ItemStack.EMPTY:displays.getFirst().result().resolveForFirstStack(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level));
 }
}