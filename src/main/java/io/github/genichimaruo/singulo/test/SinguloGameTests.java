package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 段階1の装置の動作確認。`./gradlew runGameTestServer` で実行する。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class SinguloGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    private SinguloGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static MachineBlockEntity machine(GameTestHelper helper, MachineType type) {
        helper.setBlock(MACHINE, SinguloBlocks.MACHINES.get(type).get());
        MachineBlockEntity be = helper.getBlockEntity(MACHINE);
        be.energy().setEnergy(MachineType.ENERGY_CAPACITY);
        return be;
    }

    @GameTest(template = EMPTY)
    public static void kilnMakesSteel(GameTestHelper helper) {
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        kiln.items().setStackInSlot(0, new ItemStack(item("steel_blend")));
        helper.succeedWhen(() -> helper.assertTrue(
                kiln.items().getStackInSlot(kiln.type().outputSlot()).is(item("steel_ingot")), "鋼鉄インゴットが出ない"));
    }

    @GameTest(template = EMPTY)
    public static void kilnWithoutPowerWaits(GameTestHelper helper) {
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        kiln.energy().setEnergy(0);
        kiln.items().setStackInSlot(0, new ItemStack(item("steel_blend")));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(kiln.items().getStackInSlot(kiln.type().outputSlot()).isEmpty(), "電力なしで加工した");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void compressorMassModeMakesPellet(GameTestHelper helper) {
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        var recipes = MachineBlockEntity.massRecipes(helper.getLevel(), MachineType.COMPRESSOR);
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).value().result().is(item("mass_pellet"))) {
                index = i;
            }
        }
        helper.assertTrue(index >= 0, "質量ペレットのレシピがない");
        for (int i = 0; i <= index; i++) {
            compressor.cycleMode();
        }
        compressor.items().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 20));
        helper.succeedWhen(() -> {
            helper.assertTrue(compressor.items().getStackInSlot(compressor.type().outputSlot()).is(item("mass_pellet")),
                    "質量ペレットが出ない");
            helper.assertTrue(compressor.items().getStackInSlot(0).getCount() == 4, "丸石を16個だけ使うはず");
        });
    }

    @GameTest(template = EMPTY)
    public static void compressorMakesSteelPlate(GameTestHelper helper) {
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        compressor.items().setStackInSlot(0, new ItemStack(item("steel_ingot")));
        helper.succeedWhen(() -> helper.assertTrue(
                compressor.items().getStackInSlot(compressor.type().outputSlot()).is(item("steel_plate")), "鋼板が出ない"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void assemblerMakesQuantumModule(GameTestHelper helper) {
        MachineBlockEntity assembler = machine(helper, MachineType.PRECISION_ASSEMBLER);
        assembler.items().setStackInSlot(0, new ItemStack(item("entangled_element"), 5));
        assembler.items().setStackInSlot(5, new ItemStack(item("basic_circuit")));
        assembler.items().setStackInSlot(4, new ItemStack(item("coolant_cartridge")));
        helper.succeedWhen(() -> {
            helper.assertTrue(assembler.items().getStackInSlot(assembler.type().outputSlot()).is(item("quantum_computing_module")),
                    "量子演算モジュールが出ない");
            helper.assertTrue(assembler.items().getStackInSlot(0).getCount() == 1, "量子もつれ素子を4個だけ使うはず");
        });
    }

    @GameTest(template = EMPTY)
    public static void electrolyzerSplitsWater(GameTestHelper helper) {
        MachineBlockEntity electrolyzer = machine(helper, MachineType.ELECTROLYZER);
        electrolyzer.automationFluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            FluidStack hydrogen = electrolyzer.automationFluids().drain(
                    new FluidStack(SinguloFluids.get("hydrogen"), 1000), IFluidHandler.FluidAction.SIMULATE);
            FluidStack oxygen = electrolyzer.automationFluids().drain(
                    new FluidStack(SinguloFluids.get("oxygen"), 1000), IFluidHandler.FluidAction.SIMULATE);
            helper.assertTrue(hydrogen.getAmount() >= 200, "水素が出ない");
            helper.assertTrue(oxygen.getAmount() >= 100, "酸素が出ない");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void archiveCopiesObservationLog(GameTestHelper helper) {
        MachineBlockEntity archive = machine(helper, MachineType.ARCHIVE_TERMINAL);
        archive.items().setStackInSlot(0, new ItemStack(item("observation_log")));
        archive.items().setStackInSlot(1, new ItemStack(item("blank_data_card"), 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(archive.items().getStackInSlot(archive.type().outputSlot()).is(item("data_card_observation_log")),
                    "データカードが出ない");
            ItemStack log = archive.items().getStackInSlot(0);
            helper.assertTrue(log.is(item("observation_log")) && UsesHelper.remaining(log) == 15,
                    "観測ログの使用回数が1減っていない: " + UsesHelper.remaining(log));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void archiveRestoresControlUnit(GameTestHelper helper) {
        MachineBlockEntity archive = machine(helper, MachineType.ARCHIVE_TERMINAL);
        archive.items().setStackInSlot(0, new ItemStack(item("degraded_control_unit")));
        archive.items().setStackInSlot(1, new ItemStack(item("basic_circuit")));
        archive.items().setStackInSlot(2, new ItemStack(item("ceramic_substrate")));
        archive.items().setStackInSlot(3, new ItemStack(Items.COPPER_INGOT, 2));
        helper.succeedWhen(() -> {
            ItemStack out = archive.items().getStackInSlot(archive.type().outputSlot());
            helper.assertTrue(out.is(item("control_unit")), "制御ユニットが復元されない");
            helper.assertTrue(UsesHelper.remaining(out) == 16, "復元直後は16回使える: " + UsesHelper.remaining(out));
            helper.assertTrue(archive.items().getStackInSlot(0).isEmpty(), "劣化品が消費されていない");
        });
    }

    @GameTest(template = EMPTY)
    public static void usesWearAndRepairLimit(GameTestHelper helper) {
        ItemStack unit = new ItemStack(item("control_unit"));
        ItemStack worn = UsesHelper.consume(unit, 16);
        helper.assertTrue(worn.is(item("degraded_control_unit")), "使い切ったら劣化品に戻る");
        helper.assertTrue(UsesHelper.repairsOf(worn) == 1, "修復回数が1になる");
        ItemStack restored = UsesHelper.restore(worn, new ItemStack(item("control_unit")));
        helper.assertTrue(UsesHelper.remaining(restored) == 12, "2周目は16×0.75=12回: " + UsesHelper.remaining(restored));
        ItemStack last = worn;
        for (int i = 0; i < 3; i++) {
            last = UsesHelper.consume(UsesHelper.restore(last, new ItemStack(item("control_unit"))), 1000);
        }
        helper.assertTrue(!UsesHelper.canRestore(last), "4回目の再復元はできない（新品＋修復3回）");

        ItemStack log = new ItemStack(item("observation_log"));
        ItemStack spent = UsesHelper.consume(log, 16);
        helper.assertTrue(spent.is(item("observation_log")) && UsesHelper.canRestore(spent), "原本は使い切っても残り、修復できる");
        UsesData data = UsesHelper.restore(spent, spent).get(SinguloComponents.USES.get());
        helper.assertTrue(data != null && data.max() == 12 && data.repairs() == 1, "原本の修復で最大12回");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thermoelectricCampfireBlueIce(GameTestHelper helper) {
        helper.setBlock(MACHINE, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(MACHINE.west(), Blocks.CAMPFIRE);
        helper.setBlock(MACHINE.east(), Blocks.BLUE_ICE);
        ThermoelectricGeneratorBlockEntity gen = helper.getBlockEntity(MACHINE);
        helper.runAtTickTime(60, () -> {
            // 焚き火 800 K と青氷 243 K → ΔT 557 K、0.10 × 557 ≈ 55.7 FE/t。最初の走査まで（テスト開始のずれを含めて）最大20 tick かかる
            int energy = gen.energy().getEnergyStored();
            helper.assertTrue(energy >= 55 * 40 && energy <= 56 * 60, "発電量が設計値から外れている: " + energy);
            helper.assertBlockPresent(Blocks.BLUE_ICE, MACHINE.east());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void thermoelectricLavaMeltsIce(GameTestHelper helper) {
        helper.setBlock(MACHINE, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(MACHINE.west(), Blocks.MAGMA_BLOCK);
        helper.setBlock(MACHINE.east(), Blocks.SNOW_BLOCK);
        // マグマ 1000 K と雪 268 K → ΔT 732 K。維持できる 300 K を 432 K 超えるので約14秒で水になる
        helper.succeedWhenBlockPresent(Blocks.WATER, MACHINE.east());
    }

    @GameTest(template = EMPTY)
    public static void copperWireCarriesPower(GameTestHelper helper) {
        BlockPos gen = new BlockPos(1, 1, 1);
        helper.setBlock(gen, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(gen.west(), Blocks.CAMPFIRE);
        helper.setBlock(gen.above(), Blocks.BLUE_ICE);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), SinguloBlocks.COPPER_WIRE.get());
        }
        BlockPos kilnPos = new BlockPos(5, 1, 1);
        helper.setBlock(kilnPos, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = helper.getBlockEntity(kilnPos);
        helper.succeedWhen(() -> helper.assertTrue(kiln.energy().getEnergyStored() > 200,
                "導線の先の焼成炉に電力が届かない: " + kiln.energy().getEnergyStored()));
    }
}
