package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class CapacityUpgradeGameTests {
    private static MachineBlockEntity machine(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        return h.getBlockEntity(new BlockPos(1, 1, 1));
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacityTiersNeverChargeIdleBaseBeyondOriginalBuffer(GameTestHelper h) {
        var machine = machine(h);
        h.assertTrue(machine.energy().getMaxEnergyStored() == 100_000, "Base assembler buffer not restored");
        h.assertTrue(machine.energy().receiveEnergy(40_000_000, false) == 100_000,
                "Idle base assembler drained power into an enlarged buffer");
        for (int tier = 1; tier <= 3; tier++) {
            machine.items().setStackInSlot(machine.type().capacitySlot(), CapacityUpgrades.item(tier));
            machine.energy().setEnergy(0);
            int expected = CapacityUpgrades.capacity(tier);
            h.assertTrue(machine.energy().getMaxEnergyStored() == expected, "Wrong capacity for tier " + tier);
            int accepted = machine.energy().receiveEnergy(Integer.MAX_VALUE, true);
            h.assertTrue(accepted == Math.min(expected, 4_000_000) && machine.energy().getEnergyStored() == 0,
                    "Upgrade changed input throughput or simulation charged buffer");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacityRemovalAndDowngradePreserveSurplusWithoutNegativeReception(GameTestHelper h) {
        var machine = machine(h);
        int slot = machine.type().capacitySlot();
        machine.items().setStackInSlot(slot, CapacityUpgrades.item(3));
        machine.energy().setEnergy(20_000_000);
        machine.items().setStackInSlot(slot, CapacityUpgrades.item(1));
        h.assertTrue(machine.energy().getEnergyStored() == 20_000_000
                && machine.energy().receiveEnergy(Integer.MAX_VALUE, false) == 0, "Downgrade lost energy or accepted surplus");
        machine.items().setStackInSlot(slot, ItemStack.EMPTY);
        var tag = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new MachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(restored.energy().getEnergyStored() == 20_000_000 && restored.energy().getMaxEnergyStored() == 100_000
                && restored.energy().receiveEnergy(Integer.MAX_VALUE, true) == 0, "Reload lost surplus or bypassed capacity");
        h.assertTrue(restored.energy().consume(19_950_000) && restored.energy().receiveEnergy(100_000, false) == 50_000
                && restored.energy().getEnergyStored() == 100_000, "Reception did not resume at base capacity");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacitySlotShiftClickCoexistsWithOverclockAndPreservesLegacyIndices(GameTestHelper h) {
        var machine = machine(h);
        var player = TestScope.player(h);
        var menu = new MachineMenu(1, player.getInventory(), machine);
        int inventory = machine.type().itemSlots();
        var overclock = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("overclock_chip")));
        machine.items().setStackInSlot(machine.type().upgradeSlot(), overclock.copy());
        menu.getSlot(inventory).set(CapacityUpgrades.item(2).copyWithCount(3));
        menu.quickMoveStack(player, inventory);
        h.assertTrue(CapacityUpgrades.tier(machine.items().getStackInSlot(machine.type().capacitySlot())) == 2
                && machine.items().getStackInSlot(machine.type().capacitySlot()).getCount() == 1
                && menu.getSlot(inventory).getItem().getCount() == 2 && machine.hasOverclock(), "Shift-click moved wrong slot or multiple upgrades");
        var tag = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new MachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(restored.hasOverclock() && restored.energy().getMaxEnergyStored() == 4_000_000
                && CapacityUpgrades.tier(restored.items().getStackInSlot(restored.type().capacitySlot())) == 2,
                "Reload changed separate upgrade slots");
        // Old inventory lacks only the appended capacity slot; speed/catalyst indices stay intact.
        var old = new net.neoforged.neoforge.items.ItemStackHandler(machine.type().capacitySlot());
        old.setStackInSlot(machine.type().upgradeSlot(), overclock.copy());
        old.setStackInSlot(machine.type().catalystSlot(), new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst"))));
        old.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        tag.put("items", old.serializeNBT(h.getLevel().registryAccess()));
        restored.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(restored.hasOverclock() && restored.items().getStackInSlot(0).getCount() == 7
                && restored.items().getStackInSlot(restored.type().catalystSlot()).getCount() == 1
                && restored.items().getStackInSlot(restored.type().capacitySlot()).isEmpty(), "Old inventory migration shifted or duplicated slots");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacityUpgradeRecipesRequireExactlyThePreviousTier(GameTestHelper h) {
        for (int tier = 2; tier <= 3; tier++) {
            int wantedTier = tier;
            var holder = RecipeSelection.selectable(h.getLevel(), MachineType.PRECISION_ASSEMBLER).stream()
                    .filter(r -> r.value().result().is(CapacityUpgrades.item(wantedTier).getItem())).findFirst().orElseThrow();
            int previous = tier - 1;
            h.assertTrue(holder.value().ingredients().stream().anyMatch(i -> i.count() == 1
                    && i.ingredient().test(CapacityUpgrades.item(previous))), "Upgrade skips or omits previous tier");
            h.assertTrue(holder.value().energy() <= MachineType.PRECISION_ASSEMBLER.energyCapacity(),
                    "Capacity upgrade cannot be crafted before it is installed");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacityInsufficientStopsMk3WithoutConsumingMaterialsOrEnergy(GameTestHelper h) {
        var machine = machine(h);
        int index = RecipeSelection.indexOf(h.getLevel(), machine.type(), Singulo.id("precision_assembler/scanner_module_3"));
        var recipe = RecipeSelection.byIndex(h.getLevel(), machine.type(), index).value();
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var input = recipe.ingredients().get(i);
            machine.items().setStackInSlot(i, input.ingredient().getItems()[0].copyWithCount(input.count()));
        }
        machine.selectRecipe(index);
        machine.energy().receiveEnergy(Integer.MAX_VALUE, false);
        MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        h.assertTrue(machine.status() == MachineBlockEntity.Status.NO_POWER && machine.energy().getEnergyStored() == 100_000
                && machine.items().getStackInSlot(machine.type().outputSlot()).isEmpty(), "Undersized buffer bypassed recipe energy demand");
        for (int i = 0; i < recipe.ingredients().size(); i++)
            h.assertTrue(machine.items().getStackInSlot(i).getCount() == recipe.ingredients().get(i).count(),
                    "Insufficient capacity consumed materials");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "capacity_upgrade")
    public static void capacityUpgradeDoesNotReduceLargeMachinesOrAcceptWrongItems(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), SinguloBlocks.MACHINES.get(MachineType.SINGULARITY_ENCAPSULATOR).get());
        MachineBlockEntity machine = h.getBlockEntity(new BlockPos(1, 1, 1));
        machine.items().setStackInSlot(machine.type().capacitySlot(), CapacityUpgrades.item(1));
        h.assertTrue(machine.energy().getMaxEnergyStored() == machine.type().energyCapacity(), "Low-tier upgrade shrank a larger base buffer");
        h.assertTrue(!machine.items().insertItem(machine.type().capacitySlot(), new ItemStack(Items.DIRT), true).isEmpty()
                && machine.items().getSlotLimit(machine.type().capacitySlot()) == 1,
                "Dedicated slot accepted ordinary items or multiple modules");
        h.succeed();
    }
}
