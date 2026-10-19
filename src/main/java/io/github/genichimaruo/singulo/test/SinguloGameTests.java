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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        MachineBlockEntity be = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, MACHINE);
        be.energy().setEnergy(MachineType.ENERGY_CAPACITY);
        return be;
    }

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void kilnMakesSteel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        kiln.items().setStackInSlot(0, new ItemStack(item("steel_blend")));
        helper.succeedWhen(() -> helper.assertTrue(
                kiln.items().getStackInSlot(kiln.type().outputSlot()).is(item("steel_ingot")), "鋼鉄インゴットが出ない"));
    }

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void kilnWithoutPowerWaits(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        kiln.energy().setEnergy(0);
        kiln.items().setStackInSlot(0, new ItemStack(item("steel_blend")));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(kiln.items().getStackInSlot(kiln.type().outputSlot()).isEmpty(), "電力なしで加工した");
            helper.succeed();
        });
    }

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void compressorMassModeMakesPellet(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void compressorMakesSteelPlate(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        compressor.items().setStackInSlot(0, new ItemStack(item("steel_ingot")));
        helper.succeedWhen(() -> helper.assertTrue(
                compressor.items().getStackInSlot(compressor.type().outputSlot()).is(item("steel_plate")), "鋼板が出ない"));
    }

    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 300)
    public static void assemblerMakesQuantumModule(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void electrolyzerSplitsWater(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    /** 圧縮機で方解石を砕くと石灰粉が2個。石灰粉はセラミックの骨粉の代わり（タグ singulo:ceramic_flux）になる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void compressorCrushesCalciteIntoLimestonePowder(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        compressor.items().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.CALCITE));
        helper.assertTrue(new ItemStack(item("limestone_powder")).is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, Singulo.id("ceramic_flux"))), "石灰粉が骨粉の代わりにならない");
        helper.succeedWhen(() -> {
            ItemStack out = compressor.items().getStackInSlot(compressor.type().outputSlot());
            helper.assertTrue(out.is(item("limestone_powder")) && out.getCount() == 2, "石灰粉が2個出ない: " + out);
        });
    }

    /** 圧縮機で木炭3個を砕くと炭素粉が2個。炭素粉は鋼の素などで石炭の代わり（タグ singulo:carbon）になる。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void compressorTurnsCharcoalIntoCarbonDust(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        compressor.items().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.CHARCOAL, 3));
        helper.assertTrue(new ItemStack(item("carbon_dust")).is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, Singulo.id("carbon"))), "炭素粉が石炭の代わりにならない");
        helper.succeedWhen(() -> {
            ItemStack out = compressor.items().getStackInSlot(compressor.type().outputSlot());
            helper.assertTrue(out.is(item("carbon_dust")) && out.getCount() == 2, "炭素粉が2個出ない: " + out);
            helper.assertTrue(compressor.items().getStackInSlot(0).isEmpty(), "木炭が3個減らない");
        });
    }

    /** 純酸素転炉: 焼成炉に鉄2・石炭1・酸素250 mB で鋼鉄インゴット2。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void kilnBlowsOxygenIntoSteel(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        helper.onEachTick(() -> kiln.energy().setEnergy(MachineType.ENERGY_CAPACITY));
        kiln.items().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
        kiln.items().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL));
        kiln.automationFluids().fill(new FluidStack(SinguloFluids.get("oxygen"), 250), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            ItemStack out = kiln.items().getStackInSlot(kiln.type().outputSlot());
            helper.assertTrue(out.is(item("steel_ingot")) && out.getCount() == 2, "鋼鉄インゴットが2個できない: " + out);
        });
    }

    /** 蛍光体: 焼成炉で未焼成の蛍光体を焼くと、グロウストーンダスト4個。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void kilnFiresPhosphorIntoGlowstone(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity kiln = machine(helper, MachineType.KILN);
        helper.onEachTick(() -> kiln.energy().setEnergy(MachineType.ENERGY_CAPACITY));
        kiln.items().setStackInSlot(0, new ItemStack(item("unfired_phosphor")));
        helper.succeedWhen(() -> {
            ItemStack out = kiln.items().getStackInSlot(kiln.type().outputSlot());
            helper.assertTrue(out.is(net.minecraft.world.item.Items.GLOWSTONE_DUST) && out.getCount() == 4, "グロウストーンダストが4個できない: " + out);
        });
    }

    /** ペルチェ冷却器: 水1,000 mB を凍らせて氷1個。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400)
    public static void peltierCoolerFreezesWater(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity cooler = machine(helper, MachineType.PELTIER_COOLER);
        helper.onEachTick(() -> cooler.energy().setEnergy(MachineType.ENERGY_CAPACITY));
        cooler.automationFluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> helper.assertTrue(
                cooler.items().getStackInSlot(cooler.type().outputSlot()).is(net.minecraft.world.item.Items.ICE), "氷ができない"));
    }

    /** 圧縮機で鉄のチェストプレート（インゴット8個）を砕くと、鉄の塊48個（インゴット約5個ぶん）。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void compressorRecyclesIronArmor(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity compressor = machine(helper, MachineType.COMPRESSOR);
        helper.onEachTick(() -> compressor.energy().setEnergy(MachineType.ENERGY_CAPACITY));
        ItemStack worn = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
        worn.setDamageValue(100);
        compressor.items().setStackInSlot(0, worn);
        helper.succeedWhen(() -> {
            ItemStack out = compressor.items().getStackInSlot(compressor.type().outputSlot());
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_NUGGET) && out.getCount() == 48, "鉄の塊が48個出ない: " + out);
        });
    }

    /**
     * 単極子を入れた電解槽でも、水素1 mB あたりの電力は変わらない（8 FE）。速くなったぶん毎tickの電力が増える。
     * 水素燃料電池（1 mB で 4.8 FE）と組んでも電力は増えない。
     */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void electrolyzerHydrogenCostIgnoresMonopole(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity electrolyzer = machine(helper, MachineType.ELECTROLYZER);
        electrolyzer.items().setStackInSlot(MachineType.ELECTROLYZER.upgradeSlot(), new ItemStack(item("monopole_upgrade")));
        helper.onEachTick(() -> electrolyzer.energy().setEnergy(MachineType.ENERGY_CAPACITY));
        electrolyzer.automationFluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            var data = electrolyzer.syncData();
            int usage = data.getInt(MachineBlockEntity.D_USAGE);
            int ticks = data.getInt(MachineBlockEntity.D_MAX_PROGRESS);
            helper.assertTrue(usage > 0, "電解が始まらない");
            double perMb = usage * (double) ticks / 200.0;
            helper.assertTrue(perMb >= 7.9, "単極子で水素1 mB あたりの電力が下がる: " + perMb + " FE/mB");
            helper.assertTrue(perMb > io.github.genichimaruo.singulo.machine.HydrogenFuelCellBlockEntity.fePerMb(),
                    "燃料電池で取り戻せる電力のほうが多い");
        });
    }

    /** 待機の理由:空・作れない材料・液体不足（何がいくつ足りないか）を見分ける。 */
    @GameTest(setupTicks = 20, template = EMPTY)
    public static void idleMachineExplainsWhy(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        var level = helper.getLevel();
        var none = java.util.List.<FluidStack>of(FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY);
        var empty = io.github.genichimaruo.singulo.machine.MachineDiagnosis.idle(level, MachineType.ELECTROLYZER, java.util.List.of(), none, -1);
        helper.assertTrue(empty.kind() == io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.EMPTY, "空なのに " + empty.kind());
        var dirt = io.github.genichimaruo.singulo.machine.MachineDiagnosis.idle(level, MachineType.KILN,
                java.util.List.of(new ItemStack(net.minecraft.world.item.Items.DIRT)), java.util.List.of(), -1);
        helper.assertTrue(dirt.kind() == io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.NO_MATCH, "土で焼成炉が " + dirt.kind());
        var water = io.github.genichimaruo.singulo.machine.MachineDiagnosis.idle(level, MachineType.ELECTROLYZER, java.util.List.of(),
                java.util.List.of(new FluidStack(Fluids.WATER, 50), FluidStack.EMPTY, FluidStack.EMPTY), -1);
        helper.assertTrue(water.kind() == io.github.genichimaruo.singulo.machine.MachineDiagnosis.Kind.MISSING
                && water.lines().stream().anyMatch(c -> c.getString().contains("150")), "水50 mBで、あと150 mBと言わない: " + water.lines());
        helper.succeed();
    }

    /** 液体キャニスター: 水を注いで電解槽を動かし、空いたキャニスターで水素を汲む。重ねたままでは出し入れしない。 */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 200)
    public static void fluidCanisterCarriesAnyFluid(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        MachineBlockEntity electrolyzer = machine(helper, MachineType.ELECTROLYZER);
        ItemStack canister = new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_CANISTER.get());
        helper.assertTrue(io.github.genichimaruo.singulo.item.FluidCanisterItem.handler(canister.copyWithCount(2))
                .fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "重ねたまま詰まってしまう");
        ItemStack water = io.github.genichimaruo.singulo.item.FluidCanisterItem.filled(canister, new FluidStack(Fluids.WATER, 1000));
        var poured = net.minecraftforge.fluids.FluidUtil.tryEmptyContainer(water, electrolyzer.automationFluids(), 1000, null, true);
        helper.assertTrue(poured.isSuccess() && io.github.genichimaruo.singulo.item.FluidCanisterItem.fluid(poured.getResult()).isEmpty()
                && poured.getResult().isStackable(), "水を注いだあと空にならない: " + poured.getResult());
        helper.succeedWhen(() -> {
            var got = net.minecraftforge.fluids.FluidUtil.tryFillContainer(canister.copy(), electrolyzer.automationFluids(), 1000, null, true);
            helper.assertTrue(got.isSuccess(), "水素を汲めない");
            FluidStack h = io.github.genichimaruo.singulo.item.FluidCanisterItem.fluid(got.getResult());
            helper.assertTrue(h.getFluid().isSame(SinguloFluids.get("hydrogen")) && h.getAmount() >= 200, "水素が入らない: " + h.getAmount());
            var mixed = io.github.genichimaruo.singulo.item.FluidCanisterItem.handler(got.getResult());
            helper.assertTrue(mixed.fill(new FluidStack(SinguloFluids.get("oxygen"), 100), IFluidHandler.FluidAction.SIMULATE) == 0,
                    "水素の入ったキャニスターに酸素が混ざる");
        });
    }

    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 500)
    public static void archiveCopiesObservationLog(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 500)
    public static void archiveRestoresControlUnit(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void usesWearAndRepairLimit(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
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
        UsesData data = SinguloComponents.get(UsesHelper.restore(spent, spent), SinguloComponents.USES.get());
        helper.assertTrue(data != null && data.max() == 12 && data.repairs() == 1, "原本の修復で最大12回");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void thermoelectricCampfireBlueIce(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(MACHINE, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(MACHINE.west(), Blocks.CAMPFIRE);
        helper.setBlock(MACHINE.east(), Blocks.BLUE_ICE);
        ThermoelectricGeneratorBlockEntity gen = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, MACHINE);
        helper.runAtTickTime(60, () -> {
            // 焚き火 800 K と青氷 243 K → ΔT 557 K、0.10 × 557 ≈ 55.7 FE/t。最初の走査まで（テスト開始のずれを含めて）最大20 tick かかる
            int energy = gen.energy().getEnergyStored();
            helper.assertTrue(energy >= 55 * 40 && energy <= 56 * 60, "発電量が設計値から外れている: " + energy);
            helper.assertBlockPresent(Blocks.BLUE_ICE, MACHINE.east());
            helper.succeed();
        });
    }

    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 400)
    public static void thermoelectricLavaMeltsIce(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(MACHINE, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(MACHINE.west(), Blocks.MAGMA_BLOCK);
        helper.setBlock(MACHINE.east(), Blocks.SNOW_BLOCK);
        // マグマ 1000 K と雪 268 K → ΔT 732 K。維持できる 300 K を 432 K 超えるので約14秒で水になる
        helper.succeedWhenBlockPresent(Blocks.WATER, MACHINE.east());
    }

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void copperWireCarriesPower(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos gen = new BlockPos(1, 1, 1);
        helper.setBlock(gen, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(gen.west(), Blocks.CAMPFIRE);
        helper.setBlock(gen.above(), Blocks.BLUE_ICE);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), SinguloBlocks.COPPER_WIRE.get());
        }
        BlockPos kilnPos = new BlockPos(5, 1, 1);
        helper.setBlock(kilnPos, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, kilnPos);
        helper.succeedWhen(() -> helper.assertTrue(kiln.energy().getEnergyStored() > 200,
                "導線の先の焼成炉に電力が届かない: " + kiln.energy().getEnergyStored()));
    }
    /** Odd hydrogen amounts must not create credit for gas that was never removed from the tank. */
    @GameTest(setupTicks = 20, template = EMPTY, timeoutTicks = 30)
    public static void fuelCellKeepsUnpairedHydrogenUntilOxygenPairIsComplete(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.HYDROGEN_FUEL_CELL.get());
        io.github.genichimaruo.singulo.machine.HydrogenFuelCellBlockEntity cell = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        cell.fluids().fill(new FluidStack(SinguloFluids.get("hydrogen"), 3), IFluidHandler.FluidAction.EXECUTE);
        cell.fluids().fill(new FluidStack(SinguloFluids.get("oxygen"), 2), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(cell.energy().getEnergyStored() == 9,
                    "Unpaired hydrogen created energy credit: " + cell.energy().getEnergyStored());
            helper.assertTrue(cell.hydrogen().getFluidAmount() == 1 && cell.oxygen().getFluidAmount() == 1,
                    "The incomplete hydrogen/oxygen pair was consumed");
            cell.fluids().fill(new FluidStack(SinguloFluids.get("hydrogen"), 1), IFluidHandler.FluidAction.EXECUTE);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(cell.energy().getEnergyStored() == 18, "The completed second gas pair did not generate power");
            helper.assertTrue(cell.hydrogen().isEmpty() && cell.oxygen().isEmpty(), "Complete gas pairs were not consumed exactly");
            helper.succeed();
        });
    }

}
