package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 精密組立台の作るものの指定（{@link RecipeSelection}）。材料を入れている途中で、その一部だけでできる別のものが
 * 作られないこと。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeSelectionGameTests {
    private static final String EMPTY = "empty";

    private RecipeSelectionGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static MachineBlockEntity assembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        MachineBlockEntity m = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, pos);
        helper.onEachTick(() -> m.energy().setEnergy(m.energy().getMaxEnergyStored()));
        return m;
    }

    private static int indexOf(GameTestHelper helper, String path) {
        int i = RecipeSelection.indexOf(helper.getLevel(), MachineType.PRECISION_ASSEMBLER, Singulo.id("precision_assembler/" + path));
        helper.assertTrue(i >= 0, "レシピがない: " + path);
        return i;
    }

    private static ItemStack output(MachineBlockEntity m) {
        return m.items().getStackInSlot(m.type().outputSlot());
    }

    /** Chunk loading must not bypass the automatic input settling period. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void restoredInputsWaitBeforeCrafting(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        MachineBlockEntity source = assembler(helper, pos);
        helper.runAtTickTime(70, () -> {
            var level = helper.getLevel();
            source.items().setStackInSlot(0, new ItemStack(item("superconducting_coil"), 16));
            source.items().setStackInSlot(1, new ItemStack(item("steel_plate"), 32));
            var tag = source.saveWithoutMetadata();
            var absolute = helper.absolutePos(pos);
            var state = level.getBlockState(absolute);
            MachineBlockEntity restored = new MachineBlockEntity(absolute, state);
            restored.load(tag);
            restored.setLevel(level);
            restored.onLoad();
            restored.energy().setEnergy(restored.energy().getMaxEnergyStored());
            MachineBlockEntity.serverTick(level, absolute, state, restored);
            helper.assertTrue(restored.status() == MachineBlockEntity.Status.IDLE,
                    "Reload bypassed the input settling period: " + restored.status());
            helper.succeed();
        });
    }

    /** 超伝導コイルと鋼板だけ（Tシリンダー軸受の材料）は、ほかの設備の途中でもあるので、入れ終わって少し待ってから自動で作る。指定すればすぐ作る。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void partialLoadWaitsBeforeCraftingAmbiguousRecipe(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        MachineBlockEntity auto = assembler(helper, new BlockPos(1, 1, 1));
        MachineBlockEntity fixed = assembler(helper, new BlockPos(4, 1, 1));
        for (MachineBlockEntity m : new MachineBlockEntity[]{auto, fixed}) {
            m.items().setStackInSlot(0, new ItemStack(item("superconducting_coil"), 16));
            m.items().setStackInSlot(1, new ItemStack(item("steel_plate"), 32));
        }
        helper.assertTrue(RecipeSelection.ambiguous(helper.getLevel(), MachineType.PRECISION_ASSEMBLER,
                RecipeSelection.byIndex(helper.getLevel(), MachineType.PRECISION_ASSEMBLER, indexOf(helper, "tipler_bearing"))),
                "Tシリンダー軸受がほかのレシピの途中と判定されない");
        fixed.selectRecipe(indexOf(helper, "tipler_bearing"));
        // 入れている途中（入れるのが止まって SETTLE_TICKS 経つまで）は、自動では作り始めない
        helper.runAtTickTime(RecipeSelection.SETTLE_TICKS / 2, () -> {
            helper.assertTrue(auto.status() == MachineBlockEntity.Status.IDLE, "入れている途中で自動が動き出した: " + auto.status());
        });
        // 入れ終わって止まっていれば、自動でも作る（0.11.0 では、いつまでも始まらなかった）
        helper.runAtTickTime(RecipeSelection.SETTLE_TICKS + 160, () -> {
            helper.assertTrue(output(auto).is(item("tipler_bearing")), "入れ終わっても自動で作られない: " + output(auto));
            helper.assertTrue(output(fixed).is(item("tipler_bearing")), "指定したのに作られない: " + output(fixed));
            helper.succeed();
        });
    }

    /** 自動: ほかのレシピの途中になりえない組み合わせはそのまま作る。使わない物が混ざっていたら作らない。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void autoNeedsExactInputs(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        MachineBlockEntity exact = assembler(helper, new BlockPos(1, 1, 1));
        MachineBlockEntity extra = assembler(helper, new BlockPos(4, 1, 1));
        MachineBlockEntity fixed = assembler(helper, new BlockPos(7, 1, 1));
        for (MachineBlockEntity m : new MachineBlockEntity[]{exact, extra, fixed}) {
            m.items().setStackInSlot(0, new ItemStack(item("entangled_element"), 4));
            m.items().setStackInSlot(1, new ItemStack(item("basic_circuit")));
            m.items().setStackInSlot(2, new ItemStack(item("coolant_cartridge")));
        }
        extra.items().setStackInSlot(3, new ItemStack(Items.DIRT));
        fixed.items().setStackInSlot(3, new ItemStack(Items.DIRT));
        fixed.selectRecipe(indexOf(helper, "quantum_computing_module"));
        helper.runAtTickTime(300, () -> {
            helper.assertTrue(output(exact).is(item("quantum_computing_module")), "ぴったりの材料で作られない");
            helper.assertTrue(output(extra).isEmpty(), "使わない物が混ざっているのに自動で作られた");
            helper.assertTrue(output(fixed).is(item("quantum_computing_module")), "指定すると余分な物があっても作られるはず");
            helper.assertTrue(fixed.items().getStackInSlot(3).is(Items.DIRT), "余分な物が消えた");
            helper.succeed();
        });
    }

    /** 指定は保存され、指定を外すと自動に戻る。 */
    @GameTest(template = EMPTY)
    public static void selectionIsSavedAndCleared(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        MachineBlockEntity m = assembler(helper, new BlockPos(1, 1, 1));
        m.selectRecipe(indexOf(helper, "tipler_bearing"));
        var tag = m.saveWithoutMetadata();
        helper.assertTrue("singulo:precision_assembler/tipler_bearing".equals(tag.getString("selected_recipe")), "指定が保存されない");
        m.selectRecipe(-1);
        helper.assertTrue(m.selectedRecipe() == null, "指定が外れない");
        m.load(tag);
        helper.assertTrue(m.selectedRecipe() != null, "指定が読み込まれない");
        helper.succeed();
    }
    /** /reload replaces recipes in the same RecipeManager, rather than replacing the manager itself. */
    @GameTest(template = EMPTY, batch = "recipe_reload")
    public static void ambiguityCacheFollowsRecipeReload(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var manager = helper.getLevel().getRecipeManager();
        var original = java.util.List.copyOf(manager.getRecipes());
        var bearing = RecipeSelection.byIndex(helper.getLevel(), MachineType.PRECISION_ASSEMBLER, indexOf(helper, "tipler_bearing"));
        helper.assertTrue(RecipeSelection.ambiguous(helper.getLevel(), MachineType.PRECISION_ASSEMBLER, bearing),
                "Fixture recipe must initially be ambiguous");
        MachineBlockEntity m = assembler(helper, new BlockPos(1, 1, 1));
        m.items().setStackInSlot(0, new ItemStack(item("superconducting_coil"), 16));
        m.items().setStackInSlot(1, new ItemStack(item("steel_plate"), 32));
        m.energy().setEnergy(m.energy().getMaxEnergyStored());
        MachineBlockEntity.serverTick(helper.getLevel(), m.getBlockPos(), m.getBlockState(), m);
        helper.assertTrue(m.status() == MachineBlockEntity.Status.IDLE, "Ambiguous fixture should be idle");
        try {
            manager.replaceRecipes(java.util.List.of(bearing));
            helper.assertFalse(RecipeSelection.ambiguous(helper.getLevel(), MachineType.PRECISION_ASSEMBLER, bearing),
                    "Removed recipes still prevent automatic crafting after reload");
            MachineBlockEntity.serverTick(helper.getLevel(), m.getBlockPos(), m.getBlockState(), m);
            helper.assertTrue(m.status() == MachineBlockEntity.Status.RUNNING,
                    "Unchanged inputs do not resume after recipe reload removes ambiguity");
        } finally {
            manager.replaceRecipes(original);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "recipe_reload")
    public static void selectedIndexFollowsRecipeReload(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var manager = helper.getLevel().getRecipeManager();
        var original = java.util.List.copyOf(manager.getRecipes());
        MachineBlockEntity m = assembler(helper, new BlockPos(1, 1, 1));
        int before = indexOf(helper, "quantum_computing_module");
        var selected = RecipeSelection.byIndex(helper.getLevel(), m.type(), before);
        m.selectRecipe(before);
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        var menu = new io.github.genichimaruo.singulo.machine.MachineMenu(1, player.getInventory(), m);
        helper.assertTrue(menu.value(MachineBlockEntity.D_SELECTED) == before + 1, "Initial choice not synchronized");
        try {
            manager.replaceRecipes(java.util.List.of(selected));
            helper.assertTrue(menu.value(MachineBlockEntity.D_SELECTED) == 1,
                    "Selected recipe keeps its obsolete GUI index after reload");
            helper.assertTrue(m.selectedRecipe().equals(selected.id()), "Reload changed the selected recipe ID");
        } finally {
            manager.replaceRecipes(original);
            player.discard();
        }
        helper.succeed();
    }
}
