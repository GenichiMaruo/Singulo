package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class BalanceRevisionGameTests {
    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    private static ProbeStationBlockEntity station(GameTestHelper h, int x) {
        var pos = new BlockPos(x, 1, 1);
        h.setBlock(pos, SinguloBlocks.PROBE_STATION.get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }

    private static void tick(GameTestHelper h, ProbeStationBlockEntity station) {
        CatalystDeviceBlockEntity.serverTick(h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
    }

    @GameTest(template = "empty")
    public static void probeOverflowSurvivesReloadAndDrainsExactlyOnce(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        RuinDiscovery.record(player, "observation_post", new BlockPos(100, 64, 100));
        var first = station(h, 1);
        first.registerFrom(player);
        for (int i = 0; i < first.output().getSlots(); i++) first.output().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        long now = h.getLevel().getGameTime();
        first.completeMission(h.getLevel(), now);
        var saved = first.saveWithoutMetadata();
        var cargo = saved.getList("pending", Tag.TAG_COMPOUND);
        h.assertTrue(!cargo.isEmpty(), "Blocked output lost the returned loot");
        int expected = 0;
        for (var entry : cargo) expected += ItemStack.of((net.minecraft.nbt.CompoundTag) entry).getCount();
        var restored = station(h, 5);
        restored.load(saved);
        restored.completeMission(h.getLevel(), now);
        h.assertTrue(restored.saveWithoutMetadata().getList("pending", Tag.TAG_COMPOUND).equals(cargo),
                "A blocked retry rerolled or discarded the loot");
        for (int i = 0; i < restored.output().getSlots(); i++) restored.output().setStackInSlot(i, ItemStack.EMPTY);
        tick(h, restored);
        int actual = 0;
        for (int i = 0; i < restored.output().getSlots(); i++) actual += restored.output().getStackInSlot(i).getCount();
        h.assertTrue(actual == expected, "Reload did not deliver exactly the saved loot");
        tick(h, restored);
        int repeated = 0;
        for (int i = 0; i < restored.output().getSlots(); i++) repeated += restored.output().getStackInSlot(i).getCount();
        h.assertTrue(repeated == expected, "Saved loot was delivered twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeCooldownAndFullOutputSpendNoEnergyOrLife(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        RuinDiscovery.record(player, "observation_post", new BlockPos(100, 64, 100));
        var probe = station(h, 1);
        probe.registerFrom(player);
        probe.completeMission(h.getLevel(), h.getLevel().getGameTime());
        probe.catalystSlot().setStackInSlot(0, item("time_crystal_catalyst"));
        probe.energy().setEnergy(50_000);
        tick(h, probe);
        int life = UsesHelper.remaining(probe.catalyst());
        for (int i = 0; i < 25; i++) tick(h, probe);
        h.assertTrue(probe.status() == CatalystDeviceBlockEntity.Status.WAITING
                && probe.energy().getEnergyStored() == 50_000 && UsesHelper.remaining(probe.catalyst()) == life,
                "Cooldown consumed power or catalyst");
        for (int i = 0; i < probe.output().getSlots(); i++) probe.output().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        tick(h, probe);
        h.assertTrue(probe.status() == CatalystDeviceBlockEntity.Status.OUTPUT_FULL
                && probe.energy().getEnergyStored() == 50_000 && UsesHelper.remaining(probe.catalyst()) == life,
                "Full output consumed power or catalyst");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeVisitsOnlyOneTargetPerMission(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        for (int i = 0; i < 3; i++) RuinDiscovery.record(player, "observation_post", new BlockPos(100+i*20, 64, 100));
        var probe = station(h, 1);
        h.assertTrue(probe.registerFrom(player) == 3, "Missing destinations");
        long now = h.getLevel().getGameTime();
        for (int completed = 1; completed <= 3; completed++) {
            probe.completeMission(h.getLevel(), now);
            int visited = 0;
            for (var entry : probe.saveWithoutMetadata().getList("targets", Tag.TAG_COMPOUND))
                if (((net.minecraft.nbt.CompoundTag) entry).getLong("next") > now) visited++;
            h.assertTrue(visited == completed, "A mission visited multiple targets or starved another target");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeResearchRequiresCoreAndManualRuinsStayManual(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        RuinDiscovery.record(player, "research_building", new BlockPos(100, 64, 100));
        RuinDiscovery.record(player, "culture_facility", new BlockPos(200, 64, 100));
        RuinDiscovery.record(player, "final_lab", new BlockPos(300, 64, 100));
        var low = station(h, 1);
        h.assertTrue(low.registerFrom(player) == 1, "A manually explored late ruin became automatable");
        low.catalystSlot().setStackInSlot(0, item("time_crystal_catalyst"));
        low.energy().setEnergy(50_000);
        tick(h, low);
        h.assertTrue(low.status() == CatalystDeviceBlockEntity.Status.NEEDS_UPGRADE, "Time catalyst unlocked research collection");
        var high = station(h, 5);
        high.registerFrom(player);
        high.catalystSlot().setStackInSlot(0, item("singularity_core"));
        high.energy().setEnergy(50_000);
        tick(h, high);
        high.completeMission(h.getLevel(), h.getLevel().getGameTime());
        int next = high.saveWithoutMetadata().getList("targets", Tag.TAG_COMPOUND).getCompound(0).getLong("next") > 0 ? 1 : 0;
        h.assertTrue(next == 1 && high.status() == CatalystDeviceBlockEntity.Status.RUNNING, "Core did not unlock research collection");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void smesRecipesRequireTheirMeaningfulShapedLayout(GameTestHelper h) {
        var stacks = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        stacks.set(0, item("toroidal_magnetic_coil")); stacks.set(2, item("toroidal_magnetic_coil"));
        stacks.set(3, item("coolant_cartridge")); stacks.set(5, item("coolant_cartridge"));
        stacks.set(4, item("entangled_element")); stacks.set(7, item("time_crystal_module"));
        var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream().filter(r -> r.getId().equals(Singulo.id("crafting/smes_module"))).findFirst().orElseThrow();
        h.assertTrue(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe, "Module is not shaped");
        h.assertTrue(recipe.matches(io.github.genichimaruo.singulo.compat.CraftingInput.of(3, 3, stacks), h.getLevel()), "Correct layout rejected");
        java.util.Collections.swap(stacks, 4, 7);
        h.assertTrue(!recipe.matches(io.github.genichimaruo.singulo.compat.CraftingInput.of(3, 3, stacks), h.getLevel()), "Arbitrary layout accepted");
        stacks.set(4, ItemStack.EMPTY); stacks.set(7, ItemStack.EMPTY);
        h.assertTrue(!recipe.matches(io.github.genichimaruo.singulo.compat.CraftingInput.of(3, 3, stacks), h.getLevel()), "Time part can be omitted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smesUpgradePreservesChargeWithoutReturningTheConsumedCell(GameTestHelper h) {
        var stacks = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        stacks.set(0, item("entangled_element")); stacks.set(2, item("entangled_element"));
        stacks.set(1, item("toroidal_magnetic_coil"));
        stacks.set(3, item("coolant_cartridge")); stacks.set(5, item("coolant_cartridge"));
        stacks.set(4, item("smes_cell")); stacks.set(7, item("time_crystal_module"));
        var data = new net.minecraft.nbt.CompoundTag(); data.putInt("energy", 7_500_000);
        net.minecraft.world.item.BlockItem.setBlockEntityData(stacks.get(4),
                io.github.genichimaruo.singulo.registry.SinguloBlockEntities.SMES_CELL.get(), data);
        var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream().filter(r -> r.getId().equals(Singulo.id("crafting/smes_module_from_cell"))).findFirst().orElseThrow();
        var input = io.github.genichimaruo.singulo.compat.CraftingInput.of(3, 3, stacks);
        h.assertTrue(recipe instanceof io.github.genichimaruo.singulo.recipe.SmesUpgradeRecipe && recipe.matches(input, h.getLevel()),
                "Upgrade was not decoded as a shaped charge-preserving recipe");
        var result = recipe.assemble(input, h.getLevel().registryAccess());
        var tag = result.getTagElement("BlockEntityTag");
        h.assertTrue(result.is(item("smes_module").getItem()) && result.getCount() == 1 && tag.getInt("energy") == 7_500_000,
                "Upgrade lost or multiplied stored charge");
        h.assertTrue(tag.getString("id").equals("singulo:smes_module"), "Old block entity type was copied into a module");
        h.assertTrue(recipe.getRemainingItems(input).stream().noneMatch(s -> s.is(item("smes_cell").getItem())), "Consumed cell was returned");
        h.assertTrue(stacks.get(4).getTagElement("BlockEntityTag").getInt("energy") == 7_500_000,
                "Recipe preview mutated the source item");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void researchMissionCanFinishOnTheLastCoreUse(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        RuinDiscovery.record(player, "research_building", new BlockPos(100, 64, 100));
        var probe = station(h, 1);
        probe.registerFrom(player);
        var saved = probe.saveWithoutMetadata();
        saved.putInt("progress", ProbeStationBlockEntity.MISSION_TICKS - 1);
        probe.load(saved);
        var core = item("singularity_core");
        io.github.genichimaruo.singulo.registry.SinguloComponents.set(core, io.github.genichimaruo.singulo.registry.SinguloComponents.USES.get(),
                new io.github.genichimaruo.singulo.item.UsesData(1, UsesHelper.get(core).max(), 0));
        probe.catalystSlot().setStackInSlot(0, core);
        probe.energy().setEnergy(50_000);
        tick(h, probe);
        var next = probe.saveWithoutMetadata().getList("targets", Tag.TAG_COMPOUND).getCompound(0).getLong("next");
        h.assertTrue(next > h.getLevel().getGameTime(), "Last core use paid for a mission but lost the research visit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void underTierProbeKeepsFractionalProgress(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        RuinDiscovery.record(player, "observation_post", new BlockPos(100, 64, 100));
        var probe = station(h, 1);
        probe.registerFrom(player);
        probe.catalystSlot().setStackInSlot(0, item("bose_condensate_catalyst"));
        probe.energy().setEnergy(100_000);
        for (int i = 0; i < 20; i++) tick(h, probe);
        var saved = probe.saveWithoutMetadata();
        h.assertTrue(saved.getDouble("progress") == 10, "Lower-tier catalyst ignored its half-speed penalty");
        h.assertTrue(UsesHelper.remaining(probe.catalyst()) == 36_000 - 40, "Lower-tier wear was not doubled");
        var restored = station(h, 5);
        saved.putDouble("progress", 10.5);
        restored.load(saved);
        h.assertTrue(restored.saveWithoutMetadata().getDouble("progress") == 10.5,
                "Reload rounded away fractional mission progress");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reconstructedPartsAndPrimordialSeedKeepTheirUnlockInputs(GameTestHelper h) {
        var coreRecipe = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.PRECISION_ASSEMBLER).stream()
                .filter(r -> r.getId().equals(Singulo.id("precision_assembler/sentinel_core_reconstructed"))).findFirst().orElseThrow();
        var parts = new java.util.ArrayList<ItemStack>(java.util.List.of(item("quantum_computing_module"),
                item("optical_lattice_substrate").copyWithCount(2), item("meteoric_iron_ingot").copyWithCount(2)));
        h.assertTrue(coreRecipe.plan(parts) == null, "Reconstruction bypassed its BE unlock");
        parts.add(item("muon_catalyst"));
        h.assertTrue(coreRecipe.plan(parts) == null, "Muon catalyst bypassed its BE unlock");
        parts.set(3, item("bose_condensate_catalyst"));
        h.assertTrue(coreRecipe.plan(parts) != null, "Reconstruction remains blocked after BE unlock");
        var seedRecipe = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.SINGULARITY_ENCAPSULATOR).stream()
                .filter(r -> r.getId().equals(Singulo.id("singularity_encapsulator/primordial_seed"))).findFirst().orElseThrow();
        var seedInputs = new java.util.ArrayList<ItemStack>(java.util.List.of(item("ergosphere_chapter"),
                item("hawking_condensate").copyWithCount(4), item("artificial_star_core"), item("exotic_matter").copyWithCount(8)));
        h.assertTrue(seedRecipe.plan(seedInputs) == null, "Sea loot bypassed the final lab");
        var sample = item("anomaly_sample");
        int life = UsesHelper.remaining(sample);
        seedInputs.add(sample);
        var plan = seedRecipe.plan(seedInputs);
        h.assertTrue(plan != null && plan.uses()[4] == 1 && UsesHelper.remaining(sample) == life,
                "Seed does not require exactly one non-mutating sample use in its plan");
        h.succeed();
    }

}
