package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.CapacityUpgrades;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import io.github.genichimaruo.singulo.test.harness.AndroidProcessingFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemHandlerHelper;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidMachineCycleGameTests {
    @GameTest(template = "forge")
    public static void recordedCardsCompleteEveryStandardMachineCycle(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 2, 10));
        var consolePos = new BlockPos(2, 1, 1);
        h.setBlock(consolePos, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, consolePos);
        console.encoder().setItem(WorkConsoleBlockEntity.CARD, new ItemStack(SinguloItems.RECIPE_CARD.get()));
        var all = RecipeTweaks.adjustAll(level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()));
        var failures = new ArrayList<String>();
        int checked = 0;
        for (var type : MachineType.values()) {
            var recipes = all.stream().filter(r -> r.value().station().equals(type.id())).toList();
            h.assertTrue(!recipes.isEmpty(), "No recipes for " + type.id());
            var block = type.isMultiblock() ? SinguloBlocks.CONTROLLERS.get(type).get() : SinguloBlocks.MACHINES.get(type).get();
            var structure = Map.of(pos, block.defaultBlockState());
            if (type.isMultiblock()) {
                var kind = Blueprints.kindOf(block);
                int size = recipes.stream().mapToInt(r -> r.value().minSize()).max().orElse(0);
                structure = Blueprints.layout(kind, pos, Direction.EAST, Math.max(kind.defaultSize(), size));
            }
            structure.forEach(level::setBlockAndUpdate);
            var machine = (MachineBlockEntity) level.getBlockEntity(pos);
            var empty = machine.saveWithoutMetadata();
            for (var recipe : recipes) {
                machine.load(empty);
                try {
                    cycle(h, console, machine, recipe);
                    checked++;
                } catch (RuntimeException failure) {
                    failures.add(recipe.id() + " " + failure.getMessage());
                }
            }
            machine.load(empty);
            for (var part : structure.keySet()) level.setBlockAndUpdate(part, Blocks.AIR.defaultBlockState());
        }
        h.assertTrue(failures.isEmpty(), checked + " cycles passed. Failed: " + String.join(", ", failures));
        Singulo.LOGGER.info("Verified {} registered recipes through complete Android machine processing cycles", checked);
        h.succeed();
    }

    private static void cycle(GameTestHelper h, WorkConsoleBlockEntity console, MachineBlockEntity machine, MachineRecipe holder) {
        var level = h.getLevel();
        var recipe = holder.value();
        var pos = machine.getBlockPos();
        if (recipe.energy() > machine.type().energyCapacity()) {
            int tier = CapacityUpgrades.requiredTier(recipe.energy());
            if (tier == 0 || !machine.items().insertItem(machine.type().capacitySlot(), CapacityUpgrades.item(tier), false).isEmpty()) {
                throw new IllegalStateException("could not install capacity upgrade for " + recipe.energy() + " FE/t");
            }
        }
        console.clearGrid();
        console.setMachineProcess(recipe, List.of());
        if (!console.encode()) throw new IllegalStateException("could not record");
        var card = RecipeData.read(console.encoder().getItem(WorkConsoleBlockEntity.CARD), level.registryAccess());
        AndroidProcessingFixture assembly = null;
        if (machine.type() == MachineType.PRECISION_ASSEMBLER) {
            assembly = AndroidProcessingFixture.prepareAssembly(h, console, machine, card, recipe);
        } else {
            machine.selectFor(card.output(), card.grid());
            for (var tool : card.tools()) {
                var stack = card.grid().get(tool.slot());
                int life = UsesHelper.remaining(stack);
                insert(level, pos, card, stack, (tool.uses() + life - 1) / life);
            }
            for (var in : card.inputs()) insert(level, pos, card, in.item, Math.toIntExact(in.count));
        }

        var sync = machine.syncData();
        var power = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
        if (power == null) throw new IllegalStateException("no energy input");
        boolean finished = false;
        for (int tick = 0; tick < recipe.time() * 4 + 128; tick++) {
            power.receiveEnergy(Integer.MAX_VALUE, false);
            MachineBlockEntity.serverTick(level, pos, machine.getBlockState(), machine);
            if (machine.status() == MachineBlockEntity.Status.RUNNING && sync.getInt(MachineBlockEntity.D_PROGRESS) == 0) {
                finished = true;
                break;
            }
            // Even the largest 64-item load into the mass slot is absorbed in eight ticks.
            if (tick > 12 && machine.status() != MachineBlockEntity.Status.RUNNING
                    && machine.status() != MachineBlockEntity.Status.NO_POWER) break;
        }
        String ran = machine.saveWithoutMetadata().getString("recipe");
        if (!finished || !holder.id().toString().equals(ran)) {
            throw new IllegalStateException("active=" + ran + ", status=" + machine.status() + ", size=" + machine.structureSize());
        }
        if (assembly != null) {
            assembly.visit();
            for (var product : card.outputs()) {
                int count = 0;
                for (int slot : assembly.worker().carrySlots()) {
                    var carried = assembly.worker().inventory().getItem(slot);
                    if (ItemStack.isSameItemSameTags(product, carried)) count += carried.getCount();
                }
                if (count != product.getCount()) throw new IllegalStateException("worker collected " + count + " of " + product);
            }
            return;
        }
        for (var product : card.outputs()) {
            var expected = FluidSamples.amount(product);
            var key = FluidSamples.key(product);
            var output = RecipeData.processing(card.grid(), product, List.of(), ItemStack.EMPTY);
            var face = MachineWatch.outputFace(level, pos, output);
            long removed = 0;
            if (face != null && FluidSamples.is(key)) {
                removed = LiquidStores.machine(level, pos, face).drain(key, expected + 1, false);
            } else if (face != null) {
                var handler = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.ItemHandler.BLOCK, pos, face);
                for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                    if (ItemStack.isSameItemSameTags(key, handler.getStackInSlot(slot))) {
                        removed += handler.extractItem(slot, Integer.MAX_VALUE, false).getCount();
                    }
                }
            }
            if (removed != expected) throw new IllegalStateException(product + " produced " + removed + ", expected " + expected);
        }
    }

    private static void insert(ServerLevel level, BlockPos pos, RecipeData card, ItemStack sample, int count) {
        var face = MachineWatch.inputFace(level, pos, card, sample);
        if (face != null) {
            if (FluidSamples.is(sample)) {
                if (LiquidStores.machine(level, pos, face).fill(sample, count, false) == count) return;
            } else {
                var handler = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.ItemHandler.BLOCK, pos, face);
                if (handler != null && ItemHandlerHelper.insertItemStacked(handler, sample.copyWithCount(count), false).isEmpty()) return;
            }
        }
        throw new IllegalStateException("input refused " + sample + " x " + count);
    }
}
