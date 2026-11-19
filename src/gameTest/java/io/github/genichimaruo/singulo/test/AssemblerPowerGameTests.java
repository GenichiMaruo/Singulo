package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.api.item.CatalystItem;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="singulo")
public final class AssemblerPowerGameTests {
    private static final net.minecraft.resources.ResourceLocation ADDON_CATALYST =
            net.minecraft.resources.ResourceLocation.parse("addon_fixture:muon_catalyst");
    private static final net.minecraft.resources.ResourceLocation ADDON_SPENT =
            net.minecraft.resources.ResourceLocation.parse("addon_fixture:tarnished_crystal");

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void registerAddonItems(net.minecraftforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.ITEM, helper -> {
            var shell = new CatalystItem.Spent(new net.minecraft.world.item.Item.Properties());
            helper.register(ADDON_SPENT, shell);
            helper.register(ADDON_CATALYST, new CatalystItem(new net.minecraft.world.item.Item.Properties(), 4, 12000, () -> shell));
        });
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void addonCatalystPaysTierCostAndReturnsItsOwnShell(GameTestHelper h) {
        var machine = assembler(h);
        var recipe = recipe(h, "casimir_cavity_frame");
        fillBoard(machine, recipe);
        var tool = recipe.ingredients().stream().filter(in -> in.uses() > 0).findFirst().orElseThrow();
        var fresh = new ItemStack(BuiltInRegistries.ITEM.get(ADDON_CATALYST));
        int pay = tool.usesFrom(fresh);
        h.assertTrue(io.github.genichimaruo.singulo.item.CatalystHelper.tierOf(fresh) == 4 && pay > 0, "Addon tier was inferred from its item path");
        var lastUse = UsesHelper.consume(fresh, UsesHelper.remaining(fresh) - pay);
        h.assertTrue(lastUse.getMaxStackSize() == 1 && fresh.getMaxStackSize() == 16, "Addon catalyst wear did not affect stacking");
        machine.items().setStackInSlot(machine.type().catalystSlot(), lastUse);
        machine.energy().setEnergy(machine.type().energyCapacity());
        var registries = h.getLevel().registryAccess();
        machine.load(machine.saveWithoutMetadata());
        tick(h, machine, recipe.time());
        var shell = machine.automationItems().extractItem(machine.type().spentCatalystSlot(), 64, false);
        h.assertTrue(ItemStack.matches(machine.items().getStackInSlot(machine.type().outputSlot()), recipe.result())
                && shell.is(BuiltInRegistries.ITEM.get(ADDON_SPENT)) && shell.getCount() == 1 && UsesHelper.isSpentCatalyst(shell),
                "Assembling with an addon catalyst lost its product or custom shell");
        h.succeed();
    }
    private static MachineBlockEntity assembler(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.PRECISION_ASSEMBLER).get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }

    private static MachineRecipe recipe(GameTestHelper h, String path) {
        int index = RecipeSelection.indexOf(h.getLevel(), MachineType.PRECISION_ASSEMBLER, Singulo.id("precision_assembler/" + path));
        return RecipeSelection.byIndex(h.getLevel(), MachineType.PRECISION_ASSEMBLER, index).value();
    }

    private static void tick(GameTestHelper h, MachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) MachineBlockEntity.serverTick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }

    private static int fillBoard(MachineBlockEntity machine, MachineRecipe recipe) {
        int lastFilled = -1;
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++) {
            var input = recipe.assemblyInput(i);
            if (input != null) {
                machine.assemblyGrid().setStackInSlot(i, input.ingredient().getItems()[0].copyWithCount(1));
                lastFilled = i;
            }
        }
        return lastFilled;
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerExternalFacesRejectAllInputsAndExposeProducts(GameTestHelper h) {
        var machine = assembler(h);
        var recipe = recipe(h, "toroidal_magnetic_coil");
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        for (var side : Arrays.asList(null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            var handler = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), side);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                for (var input : List.of(catalyst, new ItemStack(Items.IRON_INGOT))) {
                    h.assertTrue(!handler.isItemValid(slot, input) && ItemStack.matches(handler.insertItem(slot, input.copy(), true), input)
                            && ItemStack.matches(handler.insertItem(slot, input.copy(), false), input), "An external face accepted an input");
                }
            }
        }
        int cell = fillBoard(machine, recipe);
        tick(h, machine, 1);
        var sync = machine.syncData();
        int preview = MachineBlockEntity.assemblyPreviewIndex(machine.type());
        int expected = RecipeSelection.indexOf(h.getLevel(), machine.type(), Singulo.id("precision_assembler/toroidal_magnetic_coil")) + 1;
        h.assertTrue(machine.status() == MachineBlockEntity.Status.NO_POWER && sync.getInt(preview) == expected
                && machine.items().getStackInSlot(machine.type().outputSlot()).isEmpty(), "Unpowered board lost its preview or produced an item");
        var part = machine.assemblyGrid().extractItem(cell, 1, false);
        h.assertTrue(sync.getInt(preview) == 0, "Incomplete board retained a preview");
        machine.assemblyGrid().insertItem(cell, part, false);
        machine.energy().setEnergy(machine.type().energyCapacity());
        tick(h, machine, recipe.time());
        var output = machine.automationItems().extractItem(machine.type().outputSlot(), 64, false);
        h.assertTrue(ItemStack.matches(output, recipe.result()), "Could not extract the manual product");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerWaitsForSpentCatalystSpaceWithoutLosingLifespan(GameTestHelper h) {
        var machine = assembler(h);
        var recipe = recipe(h, "casimir_cavity_frame");
        fillBoard(machine, recipe);
        var ingredient = recipe.ingredients().stream().filter(in -> in.uses() > 0).findFirst().orElseThrow();
        var fresh = ingredient.ingredient().getItems()[0].copyWithCount(1);
        int cost = ingredient.usesFrom(fresh);
        var catalyst = UsesHelper.consume(fresh, UsesHelper.remaining(fresh) - cost);
        var shell = UsesHelper.consume(catalyst, cost);
        int spentSlot = machine.type().spentCatalystSlot();
        machine.items().setStackInSlot(machine.type().catalystSlot(), catalyst);
        machine.items().setStackInSlot(spentSlot, shell.copyWithCount(shell.getMaxStackSize()));
        machine.energy().setEnergy(machine.type().energyCapacity());
        tick(h, machine, 3);
        h.assertTrue(machine.status() == MachineBlockEntity.Status.OUTPUT_FULL && machine.energy().getEnergyStored() == machine.type().energyCapacity()
                && UsesHelper.remaining(machine.magazine().loaded()) == cost,
                "Full spent output consumed energy or catalyst lifespan");
        var registry = h.getLevel().registryAccess();
        machine.load(machine.saveWithoutMetadata());
        h.assertTrue(ItemStack.matches(machine.automationItems().extractItem(spentSlot, 1, false), shell), "Spent shell could not be extracted");
        tick(h, machine, recipe.time());
        h.assertTrue(ItemStack.matches(machine.items().getStackInSlot(machine.type().outputSlot()), recipe.result())
                && ItemStack.matches(machine.items().getStackInSlot(spentSlot), shell.copyWithCount(shell.getMaxStackSize()))
                && machine.magazine().contents().isEmpty(), "Wrong product, lost shell, or duplicated catalyst after reload and retry");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerRecoversLegacyInputsAndDropsAllRemainingContents(GameTestHelper h) {
        var machine = assembler(h);
        var registry = h.getLevel().registryAccess();
        var legacy = machine.saveWithoutMetadata();
        legacy.remove("slot_layout");
        legacy.remove("assembly_grid");
        legacy.putInt("energy", machine.type().energyCapacity());
        legacy.putInt("progress", 7);
        legacy.putString("recipe", "singulo:precision_assembler/toroidal_magnetic_coil");
        var old = new ItemStackHandler(10);
        var oldRecipe = recipe(h, "toroidal_magnetic_coil");
        for (int i = 0; i < oldRecipe.ingredients().size(); i++) {
            var part = oldRecipe.ingredients().get(i);
            old.setStackInSlot(i, part.ingredient().getItems()[0].copyWithCount(part.count()));
        }
        legacy.put("items", old.serializeNBT());
        machine.load(legacy);
        tick(h, machine, 2);
        h.assertTrue(machine.status() == MachineBlockEntity.Status.IDLE && machine.report().progress() == 0
                && machine.energy().getEnergyStored() == machine.type().energyCapacity(), "A complete legacy bulk recipe still ran");

        old = new ItemStackHandler(10);
        old.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        old.setStackInSlot(5, new ItemStack(Items.GOLD_INGOT, 7));
        old.setStackInSlot(6, new ItemStack(Items.EMERALD, 3));
        var catalyst = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst")));
        old.setStackInSlot(7, catalyst.copy());
        old.setStackInSlot(8, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("overclock_chip"))));
        legacy.put("items", old.serializeNBT());
        machine.load(legacy);
        h.assertTrue(machine.report().progress() == 0 && machine.items().getStackInSlot(machine.type().outputSlot()).getCount() == 3
                && ItemStack.matches(machine.items().getStackInSlot(machine.type().catalystSlot()), catalyst) && machine.hasOverclock(),
                "Legacy progress survived or a physical slot shifted");
        var player = TestScope.player(h);
        var inventory = player.getInventory();
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 63));
        machine.createMenu(1, inventory, player);
        h.assertTrue(inventory.getItem(0).getCount() == 64, "Opening did not recover the one legacy item that fit");
        machine.load(machine.saveWithoutMetadata());
        inventory.setItem(1, ItemStack.EMPTY);
        machine.createMenu(2, inventory, player);
        machine.createMenu(3, inventory, player);
        h.assertTrue(inventory.getItem(1).is(Items.DIAMOND) && inventory.getItem(1).getCount() == 4,
                "Reload or repeated opening lost or duplicated remaining legacy diamonds");
        machine.assemblyGrid().setStackInSlot(20, new ItemStack(Items.IRON_INGOT));
        var shell = UsesHelper.consume(catalyst, UsesHelper.remaining(catalyst));
        machine.items().setStackInSlot(machine.type().spentCatalystSlot(), shell.copyWithCount(2));
        var pos = machine.getBlockPos();
        h.getLevel().destroyBlock(pos, false);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        for (var expected : List.of(new ItemStack(Items.GOLD_INGOT, 7), new ItemStack(Items.IRON_INGOT), shell.copyWithCount(2))) {
            int found = drops.stream().map(ItemEntity::getItem).filter(s -> ItemStack.isSameItemSameTags(s, expected)).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(found == expected.getCount(), "Breaking the machine lost or duplicated " + expected);
        }
        h.succeed();
    }

    private static void craft(GameTestHelper h, String path, boolean overclock) {
        var machine = assembler(h);
        var id = Singulo.id("precision_assembler/" + path);
        int index = RecipeSelection.indexOf(h.getLevel(), MachineType.PRECISION_ASSEMBLER, id);
        h.assertTrue(index >= 0, "Missing real assembler recipe: " + id);
        var recipe = RecipeSelection.byIndex(h.getLevel(), MachineType.PRECISION_ASSEMBLER, index).value();
        if (recipe.energy() * (overclock ? 4 : 1) > machine.type().energyCapacity())
            machine.items().setStackInSlot(machine.type().capacitySlot(), io.github.genichimaruo.singulo.machine.CapacityUpgrades.item(1));
        fillBoard(machine, recipe);
        for (var input : recipe.ingredients()) if (input.uses() > 0)
            machine.items().setStackInSlot(machine.type().catalystSlot(), input.ingredient().getItems()[0].copyWithCount(1));
        if (overclock) machine.items().setStackInSlot(machine.type().upgradeSlot(),
                new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("overclock_chip"))));
        var input = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK, machine.getBlockPos(), Direction.NORTH);
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
        for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++)
            h.assertTrue(machine.assemblyGrid().getStackInSlot(i).isEmpty(), "Recipe did not consume exact input counts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerMk2RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "neutrino_scanner_module_2", false);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerMk3RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "neutrino_scanner_module_3", false);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerOverclockedMk3RunsContinuouslyWithExternalPower(GameTestHelper h) {
        craft(h, "neutrino_scanner_module_3", true);
    }

    @GameTest(template = "empty", batch = "assembler_power")
    public static void assemblerPowerReloadPreservesOldAndExpandedBuffers(GameTestHelper h) {
        var machine = assembler(h);
        var saved = machine.saveWithoutMetadata();
        for (int stored : new int[]{73_421, 20_000_000}) {
            saved.putInt("energy", stored);
            var restored = new MachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
            restored.load(saved);
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
