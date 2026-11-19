package io.github.genichimaruo.singulo.test.harness;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.Arrays;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

public final class AssemblyFixture {
    private AssemblyFixture() {}

    /** Place one batch from the supplied stacks, preserving components and leaving surplus in the stacks. */
    public static void place(GameTestHelper h, MachineBlockEntity machine, String recipePath, ItemStack... materials) {
        var id = Singulo.id("precision_assembler/" + recipePath);
        var recipe = MachineBlockEntity.stationRecipes(h.getLevel(), machine.type()).stream()
                .filter(entry -> entry.id().equals(id)).findFirst().orElseThrow().value();
        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
            h.assertTrue(machine.assemblyGrid().getStackInSlot(cell).isEmpty(), "Assembly cell already occupied: " + cell);
            var part = recipe.assemblyInput(cell);
            if (part == null) continue;
            var material = Arrays.stream(materials).filter(stack -> !stack.isEmpty() && part.ingredient().test(stack))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing material for " + id + ": " + part));
            var rest = machine.assemblyGrid().insertItem(cell, material.split(1), false);
            h.assertTrue(rest.isEmpty(), "Rejected material for " + id + " at " + cell);
        }
    }
}
