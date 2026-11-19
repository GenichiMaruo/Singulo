package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.PortableAndroidPrinterItem;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class MachineResultGameTests {
    private static ItemStack stack(String path, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(path)), count);
    }

    private static MachineBlockEntity place(GameTestHelper h, MachineType type, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(type).get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }

    private static MachineBlockEntity reload(GameTestHelper h, MachineBlockEntity original, int x) {
        var registries = h.getLevel().registryAccess();
        var restored = place(h, original.type(), x);
        restored.load(original.saveWithoutMetadata());
        return restored;
    }

    private static void tick(GameTestHelper h, MachineBlockEntity machine) {
        MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }

    @GameTest(template = "empty")
    public static void laserCoolerSharesHeliumBetweenInputTanks(GameTestHelper h) {
        var machine = place(h, MachineType.LASER_COOLER, 1);
        var helium = SinguloFluids.get("liquid_helium");
        machine.tankSides(0).set(SideConfig.Face.TOP, SideConfig.INPUT, false);
        machine.tankSides(1).set(SideConfig.Face.TOP, SideConfig.NONE, false);
        machine.fluidsFor(Direction.UP).fill(new FluidStack(helium, 4), FluidAction.EXECUTE);
        machine.tankSides(0).set(SideConfig.Face.TOP, SideConfig.NONE, false);
        machine.tankSides(1).set(SideConfig.Face.TOP, SideConfig.INPUT, false);
        var second = machine.fluidsFor(Direction.UP);
        second.fill(new FluidStack(helium, 5), FluidAction.EXECUTE);
        machine.items().setStackInSlot(0, stack("stardust", 1));
        machine.items().setStackInSlot(1, new ItemStack(Items.QUARTZ));
        machine.items().setStackInSlot(2, stack("cold_atom_trap", 1));
        machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
        tick(h, machine);
        var tanks = machine.automationFluids();
        h.assertTrue(machine.report().progress() == 0 && tanks.getFluidInTank(0).getAmount() == 4
                && tanks.getFluidInTank(1).getAmount() == 5, "A short batch used helium");
        second.fill(new FluidStack(helium, 2), FluidAction.EXECUTE);
        for (int i = 0; i < 205; i++) {
            machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
            tick(h, machine);
        }
        h.assertTrue(ItemStack.matches(machine.items().getStackInSlot(machine.type().outputSlot()), stack("cold_atoms", 2))
                && tanks.getFluidInTank(0).isEmpty() && tanks.getFluidInTank(1).getAmount() == 1,
                "The cooler did not spend exactly 10 mB across both tanks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblerUpgradesLoadedPrinterWithoutLosingContents(GameTestHelper h) {
        var machine = place(h, MachineType.PRECISION_ASSEMBLER, 1);
        ItemStack mk1 = stack("portable_android_printer", 1);
        List<ItemStack> set = List.of(stack("android_memory_blade", 1), stack("android_kit", 2),
                stack("android_repair_kit", 10), stack("android_armor_plate", 1));
        PortableAndroidPrinterItem.setContents(mk1, set);
        mk1.setHoverName(Component.literal("Field printer"));
        mk1.set(SinguloComponents.ENERGY.get(), 240_000); // surplus charge after changing a gunner memory
        List<ItemStack> inputs = List.of(stack("quantum_computing_module", 1), stack("entangled_element", 2),
                mk1, stack("superconducting_coil", 2), stack("white_ceramic_composite", 4), stack("steel_plate", 2));
        io.github.genichimaruo.singulo.test.harness.AssemblyFixture.place(h, machine, "portable_android_printer_2",
                inputs.stream().map(ItemStack::copy).toArray(ItemStack[]::new));
        ItemStack mk2 = stack("portable_android_printer_2", 1);
        int out = machine.type().outputSlot();
        for (int i = 0; i < 500 && machine.items().getStackInSlot(out).isEmpty(); i++) {
            machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
            tick(h, machine);
        }
        ItemStack upgraded = machine.automationItems().extractItem(out, 1, false);
        h.assertTrue(upgraded.is(mk2.getItem()) && upgraded.getHoverName().equals(mk1.getHoverName())
                && upgraded.getOrDefault(SinguloComponents.ENERGY.get(), 0) == 240_000, "Upgrade lost printer name or stored FE");
        var slots = PortableAndroidPrinterItem.contents(upgraded);
        for (int i = 0; i < slots.size(); i++) {
            h.assertTrue(ItemStack.matches(slots.get(i), i < set.size() ? set.get(i) : ItemStack.EMPTY), "Wrong contents in slot " + i);
        }
        for (int i = 0; i < machine.assemblyGrid().getSlots(); i++) {
            h.assertTrue(machine.assemblyGrid().getStackInSlot(i).isEmpty(), "Upgrade left an ingredient behind");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void incubatorKeepsPurityAndIngredientsUntilOutputFits(GameTestHelper h) {
        var machine = place(h, MachineType.TIME_CRYSTAL_INCUBATOR, 1);
        machine.items().setStackInSlot(0, stack("time_crystal_seed", 1));
        machine.items().setStackInSlot(1, new ItemStack(Items.ECHO_SHARD, 2));
        machine.items().setStackInSlot(2, stack("entangled_element", 2));
        machine.items().setStackInSlot(3, stack("phase_sync_plate", 1));
        machine.items().setStackInSlot(machine.type().catalystSlot(), stack("bose_condensate_catalyst", 1));
        ItemStack original = stack("time_crystal_catalyst", 1);
        int out = machine.type().outputSlot();
        machine.items().setStackInSlot(out, original.copy());
        int growth = ServerConfig.TIME_CRYSTAL_GROWTH_TICKS.get();
        for (int i = 0; i < growth / 2; i++) {
            machine.energy().setEnergy(15_000);
            tick(h, machine);
        }
        machine = reload(h, machine, 2);
        for (int i = growth / 2; i < growth + 2; i++) {
            machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
            tick(h, machine);
        }
        int status = machine.syncData().getInt(MachineBlockEntity.D_STATUS);
        var echo = machine.items().getStackInSlot(1);
        var entangled = machine.items().getStackInSlot(2);
        var waiting = machine.items().getStackInSlot(out);
        h.assertTrue(status == MachineBlockEntity.Status.OUTPUT_FULL.ordinal() && echo.getCount() == 2 && entangled.getCount() == 2
                && ItemStack.matches(waiting, original), "Blocked growth: status=" + MachineBlockEntity.Status.values()[status]
                + ", echo=" + echo + ", entangled=" + entangled + ", output=" + waiting + ", progress=" + machine.syncData().getInt(MachineBlockEntity.D_PROGRESS));
        machine.items().setStackInSlot(machine.type().upgradeSlot(), stack("overclock_chip", 1));
        int stored = machine.energy().getEnergyStored();
        tick(h, machine);
        h.assertTrue(machine.syncData().getInt(MachineBlockEntity.D_MAX_PROGRESS) == growth && machine.energy().getEnergyStored() == stored,
                "Changing the speed while output was blocked changed the paid duration or consumed more energy");
        machine = reload(h, machine, 3);
        machine.automationItems().extractItem(out, 1, false);
        machine.energy().setEnergy(0);
        tick(h, machine);
        var grown = machine.items().getStackInSlot(out);
        h.assertTrue(grown.is(original.getItem()) && grown.getCount() == 1
                && Math.abs(UsesHelper.get(grown).max() - UsesHelper.get(original).max() / 2) <= 1,
                "Completed catalyst lost its low-power history or required more energy after reload");
        h.assertTrue(machine.items().getStackInSlot(1).getCount() == 1 && machine.items().getStackInSlot(2).isEmpty(),
                "Growth did not consume exactly one recipe");
        tick(h, machine);
        h.assertTrue(machine.items().getStackInSlot(out).getCount() == 1, "Growth produced twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void compressorRemembersMixedMaterialsAcrossReload(GameTestHelper h) {
        var compressor = place(h, MachineType.COMPRESSOR, 1);
        compressor.cycleMode();
        compressor.items().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
        tick(h, compressor);
        compressor.items().setStackInSlot(0, new ItemStack(Items.ANDESITE, 3));
        tick(h, compressor);
        compressor = reload(h, compressor, 2);
        compressor.items().setStackInSlot(0, new ItemStack(Items.GRANITE, 3));
        int out = compressor.type().outputSlot();
        for (int i = 0; i < 100 && compressor.items().getStackInSlot(out).isEmpty(); i++) {
            compressor.energy().setEnergy(compressor.energy().getMaxEnergyStored());
            tick(h, compressor);
        }
        var result = compressor.items().getStackInSlot(out);
        h.assertTrue(result.is(stack("compressed_block_1", 1).getItem()) && result.getOrDefault(SinguloComponents.MIXED_SOURCE.get(), false),
                "Reload discarded previously absorbed material types");
        h.succeed();
    }
}
