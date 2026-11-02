package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AssemblerPowerGameTests {
    private static MachineBlockEntity assembler(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        return h.getBlockEntity(pos);
    }

    private static void craft(GameTestHelper h, String path, boolean overclock) {
        var machine = assembler(h);
        var id = Singulo.id("precision_assembler/" + path);
        int index = RecipeSelection.indexOf(h.getLevel(), MachineType.PRECISION_ASSEMBLER, id);
        h.assertTrue(index >= 0, "Missing real assembler recipe: " + id);
        var recipe = RecipeSelection.byIndex(h.getLevel(), MachineType.PRECISION_ASSEMBLER, index).value();
        if (recipe.energy() * (overclock ? 4 : 1) > machine.type().energyCapacity())
            machine.items().setStackInSlot(machine.type().capacitySlot(), io.github.genichimaruo.singulo.machine.CapacityUpgrades.item(1));
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var input = recipe.ingredients().get(i);
            machine.items().setStackInSlot(i, input.ingredient().getItems()[0].copyWithCount(input.count()));
        }
        if (overclock) machine.items().setStackInSlot(machine.type().upgradeSlot(),
                new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("overclock_chip"))));
        machine.selectRecipe(index);
        var input = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, machine.getBlockPos(), Direction.NORTH);
        h.assertTrue(input != null, "Assembler has no energy input capability");
        int demand = recipe.energy() * (overclock ? 4 : 1);
        h.assertTrue(input.receiveEnergy(demand, true) == demand && machine.energy().getEnergyStored() == 0,
                "Input cannot receive one processing tick, or simulation changed stored energy");
        int supplied = 0;
        int ticks = 0;
        while (machine.items().getStackInSlot(machine.type().outputSlot()).isEmpty() && ticks < recipe.time()) {
            supplied += input.receiveEnergy(demand, false);
            MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
            h.assertTrue(machine.status() == MachineBlockEntity.Status.RUNNING,
                    "Adequately supplied assembler interrupted processing: " + machine.status());
            ticks++;
        }
        var output = machine.items().getStackInSlot(machine.type().outputSlot());
        h.assertTrue(ItemStack.matches(output, recipe.result()), "Assembler did not finish the real recipe");
        int expectedTicks = Math.max(1, (int) Math.round(recipe.time() / (overclock ? 3.0 : 1.0)));
        h.assertTrue(ticks == expectedTicks && supplied == ticks * demand && machine.energy().getEnergyStored() == 0,
                "Processing changed recipe duration or created/ignored energy");
        for (int i = 0; i < machine.type().inputSlots(); i++)
            h.assertTrue(machine.items().getStackInSlot(i).isEmpty(), "Recipe did not consume exact input counts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerMk2RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "scanner_module_2", false);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerMk3RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "scanner_module_3", false);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerOverclockedMk3RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "scanner_module_3", true);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerPowerReloadPreservesOldAndExpandedBuffers(GameTestHelper h) {
        var machine = assembler(h);
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        for (int stored : new int[]{73_421, 20_000_000}) {
            saved.putInt("energy", stored);
            var restored = new MachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
            restored.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(restored.energy().getEnergyStored() == stored, "Reload changed stored energy");
            int free = restored.energy().getMaxEnergyStored() - stored;
            int accepted = restored.energy().receiveEnergy(Integer.MAX_VALUE, false);
            h.assertTrue(accepted == Math.max(0, Math.min(free, machine.type().energyReceiveLimit()))
                            && restored.energy().getEnergyStored() == stored + accepted,
                    "Reloaded input overflowed or created energy");
        }
        machine.energy().setEnergy(0);
        h.assertTrue(!machine.energy().consume(200_000), "Empty assembler bypassed its energy cost");
        h.succeed();
    }
}
