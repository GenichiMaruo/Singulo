package io.github.genichimaruo.singulo.test.harness;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.AndroidAssembly;
import io.github.genichimaruo.singulo.android.work.MachineWatch;
import io.github.genichimaruo.singulo.android.work.RecipeData;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkJob;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Run the actual worker's machine visit, including inventory payments and collection, without pathfinding. */
public record AndroidProcessingFixture(RuinAndroid worker, Object goal, Method method, ServerLevel level, WorkConsoleBlockEntity console,
                              WorkJob job, WorkJob.Batch batch) {
    public void visit() {
        try {
            for (int i = 0; i < 10; i++) method.invoke(goal, level, console, job, batch);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Android processing visit failed", ex);
        }
    }

    public static AndroidProcessingFixture at(GameTestHelper h, WorkConsoleBlockEntity c, RecipeData card, BlockPos machinePos) throws ReflectiveOperationException {
        var a = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        a.makeGuard(null, machinePos, RuinAndroid.MODE_GUARD, 30);
        var type = Class.forName("io.github.genichimaruo.singulo.android.AndroidWorkGoal");
        var constructor = type.getDeclaredConstructor(RuinAndroid.class);
        constructor.setAccessible(true);
        var goal = constructor.newInstance(a);
        var method = type.getDeclaredMethod("process", ServerLevel.class, WorkConsoleBlockEntity.class, WorkJob.class, WorkJob.Batch.class);
        method.setAccessible(true);
        var batch = new WorkJob.Batch(0, 1, machinePos, card);
        var job = new WorkJob(WorkJob.Type.CRAFT, 0, 1, 0, List.of(), List.of(batch), true);
        return new AndroidProcessingFixture(a, goal, method, h.getLevel(), c, job, batch);
    }

    public static AndroidProcessingFixture prepareAssembly(GameTestHelper h, WorkConsoleBlockEntity console, MachineBlockEntity machine,
                                      RecipeData card, MachineRecipe recipe) {
        AndroidProcessingFixture work;
        try {
            work = at(h, console, card, machine.getBlockPos());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        var worker = work.worker();
        var assembly = AndroidAssembly.find(h.getLevel(), card);
        if (assembly == null) throw new IllegalStateException("No matching board for card " + card.grid() + " -> " + card.output());
        if (!MachineWatch.handlesOutput(h.getLevel(), machine.getBlockPos(), card)) throw new IllegalStateException("No output face");
        worker.inventory().setItem(RuinAndroid.SLOT_BACKPACK, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_backpack_upgrade"))));
        for (var input : card.inputs()) {
            h.assertTrue(worker.addToCarry(input.item.copyWithCount(Math.toIntExact(input.count))).isEmpty(), "Worker could not carry " + input.item);
        }
        for (var tool : card.tools()) {
            var sample = card.grid().get(tool.slot());
            int life = io.github.genichimaruo.singulo.item.UsesHelper.remaining(sample);
            h.assertTrue(worker.addToCarry(sample.copyWithCount(Math.max(1, (tool.uses() + life - 1) / life))).isEmpty(), "Worker could not carry tool");
        }
        for (int visit = 0; visit < 64; visit++) {
            work.visit();
            if (recipe.assemblyPlan(machine.assemblyInputs()) != null) return work;
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        }
        throw new IllegalStateException("Worker failed to lay out " + recipe.result() + ", board=" + machine.assemblyInputs()
                + ", carry=" + Arrays.stream(worker.carrySlots()).mapToObj(worker.inventory()::getItem).filter(s -> !s.isEmpty()).toList()
                + ", charge=" + worker.charge() + ", missingTool=" + MachineWatch.missingTool(h.getLevel(), machine.getBlockPos(), card));
    }
}
