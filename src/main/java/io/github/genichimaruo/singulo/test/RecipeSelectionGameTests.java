package io.github.genichimaruo.singulo.test;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Automatic bulk recipe selection is supported by the singularity encapsulator. */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeSelectionGameTests {
    private static final MachineType TYPE = MachineType.SINGULARITY_ENCAPSULATOR;
    // Its condensate, exotic matter and computing module are also part of the genesis core recipe.
    private static final String AMBIGUOUS = "awakening_record_core";
    private static final String UNIQUE = "awakening_echo_core";

    private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, SinguloBlocks.MACHINES.get(TYPE).get());
        MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, pos);
        h.onEachTick(() -> machine.energy().setEnergy(machine.energy().getMaxEnergyStored()));
        return machine;
    }

    private static int index(GameTestHelper h, String name) {
        int index = RecipeSelection.indexOf(h.getLevel(), TYPE, Singulo.id(TYPE.id() + "/" + name));
        h.assertTrue(index >= 0, "Missing encapsulator recipe " + name);
        return index;
    }

    private static RecipeHolder<MachineRecipe> recipe(GameTestHelper h, String name) {
        return RecipeSelection.byIndex(h.getLevel(), TYPE, index(h, name));
    }

    private static void load(MachineBlockEntity machine, MachineRecipe recipe) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var part = recipe.ingredients().get(i);
            machine.items().setStackInSlot(i, part.ingredient().items().map(net.minecraft.world.item.ItemStack::new).toArray(net.minecraft.world.item.ItemStack[]::new)[0].copyWithCount(part.count()));
        }
    }

    private static void run(GameTestHelper h, MachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        }
    }

    private static ItemStack output(MachineBlockEntity machine) {
        return machine.items().getStackInSlot(TYPE.outputSlot());
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void restoredInputsWaitBeforeCrafting(GameTestHelper h) {
        var source = machine(h, new BlockPos(1, 1, 1));
        h.runAtTickTime(70, () -> {
            load(source, recipe(h, AMBIGUOUS).value());
            var registry = h.getLevel().registryAccess();
            var restored = new MachineBlockEntity(source.getBlockPos(), source.getBlockState());
            io.github.genichimaruo.singulo.test.harness.TestNbt.load(restored, source.saveWithoutMetadata(registry), registry);
            restored.setLevel(h.getLevel());
            restored.onLoad();
            run(h, restored, 1);
            h.assertTrue(restored.status() == MachineBlockEntity.Status.IDLE, "Reload bypassed the input settling period");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void partialLoadWaitsBeforeCraftingAmbiguousRecipe(GameTestHelper h) {
        var automatic = machine(h, new BlockPos(1, 1, 1));
        var fixed = machine(h, new BlockPos(4, 1, 1));
        var holder = recipe(h, AMBIGUOUS);
        h.assertTrue(RecipeSelection.ambiguous(h.getLevel(), TYPE, holder), "Record core must also be a partial genesis core");
        load(automatic, holder.value());
        load(fixed, holder.value());
        fixed.selectRecipe(index(h, AMBIGUOUS));
        h.runAtTickTime(RecipeSelection.SETTLE_TICKS / 2, () -> {
            h.assertTrue(automatic.status() == MachineBlockEntity.Status.IDLE && fixed.status() == MachineBlockEntity.Status.RUNNING,
                    "Only the selected machine should start during input settling");
        });
        h.runAtTickTime(RecipeSelection.SETTLE_TICKS + 3, () -> {
            run(h, automatic, holder.value().time());
            run(h, fixed, holder.value().time());
            h.assertTrue(ItemStack.matches(output(automatic), holder.value().result()) && ItemStack.matches(output(fixed), holder.value().result()),
                    "Automatic or fixed selection failed to finish the record core");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void autoNeedsExactInputs(GameTestHelper h) {
        var exact = machine(h, new BlockPos(1, 1, 1));
        var extra = machine(h, new BlockPos(3, 1, 1));
        var fixed = machine(h, new BlockPos(5, 1, 1));
        var recipe = recipe(h, UNIQUE).value();
        for (var machine : List.of(exact, extra, fixed)) load(machine, recipe);
        extra.items().setStackInSlot(3, new ItemStack(Items.DIRT));
        fixed.items().setStackInSlot(3, new ItemStack(Items.DIRT));
        fixed.selectRecipe(index(h, UNIQUE));
        for (var machine : List.of(exact, extra, fixed)) run(h, machine, recipe.time() + 1);
        h.assertTrue(ItemStack.matches(output(exact), recipe.result()), "Exact inputs did not craft automatically");
        h.assertTrue(output(extra).isEmpty(), "Automatic selection accepted unrelated inputs");
        h.assertTrue(ItemStack.matches(output(fixed), recipe.result()) && fixed.items().getStackInSlot(3).is(Items.DIRT),
                "Fixed selection did not preserve the extra input");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void selectionIsSavedAndCleared(GameTestHelper h) {
        var machine = machine(h, new BlockPos(1, 1, 1));
        machine.selectRecipe(index(h, UNIQUE));
        var tag = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        var id = recipe(h, UNIQUE).id().identifier();
        h.assertTrue(id.toString().equals(tag.getStringOr("selected_recipe", "")), "Selection was not saved");
        machine.selectRecipe(-1);
        h.assertTrue(machine.selectedRecipe() == null, "Selection was not cleared");
        io.github.genichimaruo.singulo.test.harness.TestNbt.load(machine, tag, h.getLevel().registryAccess());
        h.assertTrue(id.equals(machine.selectedRecipe()), "Selection did not survive reload");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "recipe_reload")
    public static void ambiguityCacheFollowsRecipeReload(GameTestHelper h) {
        var manager = h.getLevel().recipeAccess();
        var original = List.copyOf(manager.getRecipes());
        var holder = recipe(h, AMBIGUOUS);
        var machine = machine(h, new BlockPos(1, 1, 1));
        load(machine, holder.value());
        run(h, machine, 1);
        h.assertTrue(RecipeSelection.ambiguous(h.getLevel(), TYPE, holder) && machine.status() == MachineBlockEntity.Status.IDLE,
                "Fixture did not wait for a potentially larger recipe");
        try {
            io.github.genichimaruo.singulo.test.harness.RecipeReload.replace(manager, List.of(holder));
            h.assertFalse(RecipeSelection.ambiguous(h.getLevel(), TYPE, holder), "Removed recipe still causes ambiguity");
            run(h, machine, 1);
            h.assertTrue(machine.status() == MachineBlockEntity.Status.RUNNING, "Unchanged inputs did not resume after reload");
        } finally {
            io.github.genichimaruo.singulo.test.harness.RecipeReload.replace(manager, original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "recipe_reload")
    public static void selectedIndexFollowsRecipeReload(GameTestHelper h) {
        var manager = h.getLevel().recipeAccess();
        var original = List.copyOf(manager.getRecipes());
        var machine = machine(h, new BlockPos(1, 1, 1));
        var selected = recipe(h, UNIQUE);
        int before = index(h, UNIQUE);
        machine.selectRecipe(before);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "SelectionTester"));
        var menu = new MachineMenu(1, player.getInventory(), machine);
        h.assertTrue(before > 0 && menu.value(MachineBlockEntity.D_SELECTED) == before + 1, "Initial choice not synchronized");
        try {
            io.github.genichimaruo.singulo.test.harness.RecipeReload.replace(manager, List.of(selected));
            h.assertTrue(menu.value(MachineBlockEntity.D_SELECTED) == 1, "Selected recipe kept its obsolete GUI index");
            h.assertTrue(selected.id().identifier().equals(machine.selectedRecipe()), "Reload changed the selected recipe ID");
        } finally {
            io.github.genichimaruo.singulo.test.harness.RecipeReload.replace(manager, original);
        }
        h.succeed();
    }
}
