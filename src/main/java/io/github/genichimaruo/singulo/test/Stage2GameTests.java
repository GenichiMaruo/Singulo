package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.CosmicMuonCollectorBlockEntity;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 段階2（極低温）の装置の動作確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Stage2GameTests {
    private static final String EMPTY = "empty";
    private static final String TALL = "tall";

    private Stage2GameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static Fluid fluid(String id) {
        return SinguloFluids.get(id);
    }

    private static int drainable(IFluidHandler handler, String id) {
        return handler.drain(new FluidStack(fluid(id), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE).getAmount();
    }

    // ------------------------------------------------------------------ 極低温冷却塔

    private static final BlockPos TOWER_CONTROLLER = new BlockPos(2, 1, 0);
    private static BlockPos towerPort;

    /** 高さ height の冷却塔を (0..4, 0.., 0..4) に建て、手前の面の下から2段目の左の外装板を搬入出ポートにする。 */
    private static MachineBlockEntity buildTower(GameTestHelper helper, int height) {
        TestBuild.build(helper, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.COOLING_TOWER, TOWER_CONTROLLER,
                net.minecraft.core.Direction.SOUTH, height);
        towerPort = TestBuild.port(helper, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.COOLING_TOWER, TOWER_CONTROLLER,
                net.minecraft.core.Direction.SOUTH, height, 1, 1, 0);
        MachineBlockEntity controller = helper.getBlockEntity(TOWER_CONTROLLER);
        controller.energy().setEnergy(controller.energy().getMaxEnergyStored());
        return controller;
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void coolingTowerMakesLiquidNitrogen(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 7);
        BlockPos port = towerPort;
        // 高さ7では液体ヘリウムは作れない（高さ10以上が必要）
        tower.automationFluids().fill(new FluidStack(fluid("helium"), 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.structureSize() == 7, "高さ7の塔として形成されない: " + tower.structureSize());
            helper.assertTrue(drainable(tower.automationFluids(), "liquid_nitrogen") >= 200, "液体窒素が出ない");
            helper.assertTrue(drainable(tower.automationFluids(), "liquid_helium") == 0, "高さ7で液体ヘリウムができた");
            IFluidHandler viaPort = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    port, null);
            helper.assertTrue(viaPort != null && drainable(viaPort, "liquid_nitrogen") > 0, "搬入出口から液体窒素を取れない");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 300)
    public static void tallCoolingTowerLiquefiesHelium(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 10);
        tower.automationFluids().fill(new FluidStack(fluid("helium"), 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.structureSize() == 10, "高さ10の塔として形成されない: " + tower.structureSize());
            helper.assertTrue(drainable(tower.automationFluids(), "liquid_helium") >= 100, "液体ヘリウムが出ない");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 700)
    public static void coolingTowerMakesSuperconductingCoil(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 7);
        // 30秒×400 FE/t は内部の蓄電を超えるので、電源の代わりに毎tick満たす
        helper.onEachTick(() -> tower.energy().setEnergy(tower.energy().getMaxEnergyStored()));
        tower.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 8));
        tower.items().setStackInSlot(1, new ItemStack(Items.REDSTONE, 4));
        tower.items().setStackInSlot(2, new ItemStack(item("steel_plate"), 2));
        tower.items().setStackInSlot(3, new ItemStack(item("ceramic_substrate"), 2));
        tower.items().setStackInSlot(4, new ItemStack(item("control_unit")));
        // 材料の液体窒素は、自分で作った液体窒素（出力タンク）から取る
        helper.succeedWhen(() -> {
            ItemStack out = tower.items().getStackInSlot(tower.type().outputSlot());
            helper.assertTrue(out.is(item("superconducting_coil")) && out.getCount() == 4, "超伝導コイルが4個出ない");
            helper.assertTrue(UsesHelper.remaining(tower.items().getStackInSlot(4)) == 15, "制御ユニットの使用回数が1減らない");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void coolingTowerBreaksWhenCasingRemoved(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 7);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(tower.structureSize() == 7, "形成されない");
            helper.setBlock(new BlockPos(3, 3, 1), Blocks.AIR);   // くびれの角の外壁
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(tower.structureSize() == 0, "外壁を外しても形成されたまま");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 粒子加速器

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void acceleratorMakesMuonBundles(GameTestHelper helper) {
        int side = 8;
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                boolean edge = x == 0 || z == 0 || x == side - 1 || z == side - 1;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == 0 || x == side - 1) && (z == 0 || z == side - 1);
                boolean extraMagnet = (x == 3 && z == 0) || (x == 0 && z == 4) || (x == side - 1 && z == 3);
                helper.setBlock(new BlockPos(x, 1, z), corner || extraMagnet
                        ? SinguloBlocks.FOCUSING_MAGNET.get() : SinguloBlocks.ACCELERATOR_TUBE.get());
            }
        }
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, SinguloBlocks.CONTROLLERS.get(MachineType.PARTICLE_ACCELERATOR).get());
        MachineBlockEntity accelerator = helper.getBlockEntity(controllerPos);
        accelerator.energy().setEnergy(accelerator.energy().getMaxEnergyStored());
        accelerator.automationFluids().fill(new FluidStack(fluid("hydrogen"), 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            helper.assertTrue(accelerator.structureSize() == side, "一辺8のリングとして形成されない: " + accelerator.structureSize());
            helper.assertTrue(accelerator.items().getStackInSlot(accelerator.type().outputSlot()).is(item("muon_bundle")),
                    "ミュオン束が出ない");
        });
    }

    // ------------------------------------------------------------------ 触媒反応器と触媒

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void catalyticReactorMakesMuonCatalyst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.CATALYTIC_REACTOR).get());
        MachineBlockEntity reactor = helper.getBlockEntity(pos);
        reactor.energy().setEnergy(reactor.energy().getMaxEnergyStored());
        helper.onEachTick(() -> reactor.energy().setEnergy(reactor.energy().getMaxEnergyStored()));
        reactor.items().setStackInSlot(0, new ItemStack(item("muon_bundle"), 2));
        reactor.items().setStackInSlot(1, new ItemStack(item("ceramic_substrate")));
        reactor.items().setStackInSlot(2, new ItemStack(Items.COPPER_INGOT, 2));
        reactor.items().setStackInSlot(3, new ItemStack(Items.REDSTONE));
        reactor.items().setStackInSlot(4, new ItemStack(item("data_card_observation_log")));
        reactor.automationFluids().fill(new FluidStack(fluid("liquid_nitrogen"), 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> helper.assertTrue(
                reactor.items().getStackInSlot(reactor.type().outputSlot()).is(item("muon_catalyst")), "ミュオン触媒が出ない"));
    }

    @GameTest(template = EMPTY)
    public static void catalystTierRules(GameTestHelper helper) {
        ItemStack muon = new ItemStack(item("muon_catalyst"));
        ItemStack bose = new ItemStack(item("bose_condensate_catalyst"));
        CatalystHelper.Effect upper = CatalystHelper.effect(2, bose);
        helper.assertTrue(upper.usable() && upper.speed() == 1.25 && Math.abs(upper.consumption() - 0.8) < 1e-9,
                "1段上の触媒は速度+25%・消費−20%");
        CatalystHelper.Effect lower = CatalystHelper.effect(3, muon);
        helper.assertTrue(lower.usable() && lower.speed() == 0.5 && lower.consumption() == 2.0, "1段下は速度×0.5・消費×2");
        helper.assertTrue(!CatalystHelper.effect(4, muon).usable(), "2段下の触媒は使えない");
        helper.assertTrue(CatalystHelper.powerMultiplier(muon) == 1.0, "新品の触媒は電力×1.0");
        int life = UsesHelper.get(muon).max();
        helper.assertTrue(life == 20 * 60 * 20, "ミュオン触媒の寿命は20分: " + life);
        ItemStack half = UsesHelper.consume(muon, life / 2);
        helper.assertTrue(Math.abs(CatalystHelper.powerMultiplier(half) - 1.25) < 1e-9, "残り50%で電力×1.25");
        ItemStack spent = UsesHelper.consume(muon, life);
        helper.assertTrue(spent.is(item("spent_muon_catalyst")), "使い切ると失活触媒になる");
        helper.assertTrue(spent.is(TagKey.create(Registries.ITEM, Singulo.id("muon_catalysts"))),
                "失活触媒もボース凝縮触媒の材料に使える（タグ）");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 発電・蓄電・送電

    @GameTest(template = EMPTY)
    public static void cryogenicTurbineRunsOnLavaAndNitrogen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.CRYOGENIC_TURBINE.get());
        helper.setBlock(pos.west(), Blocks.LAVA);
        CryogenicTurbineBlockEntity turbine = helper.getBlockEntity(pos);
        turbine.tank().fill(new FluidStack(fluid("liquid_nitrogen"), 4000), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(40, () -> {
            // 溶岩なら 5 kFE/t。最初の走査まで最大10 tick
            int energy = turbine.energy().getEnergyStored();
            helper.assertTrue(energy >= 5000 * 28 && energy <= 5000 * 41, "出力が設計値から外れている: " + energy);
            int left = turbine.tank().getFluidInTank(0).getAmount();
            helper.assertTrue(left < 4000 && left >= 4000 - 2 * 41, "液体窒素の消費が毎tick 2 mB でない: " + left);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void smesFeedsKilnThroughSuperconductingCable(GameTestHelper helper) {
        BlockPos smesPos = new BlockPos(1, 1, 4);
        helper.setBlock(smesPos, SinguloBlocks.SMES_CELL.get());
        SmesCellBlockEntity smes = helper.getBlockEntity(smesPos);
        smes.energy().setEnergy(1_000_000);
        // SMES の正面（北）から超伝導ケーブル3本を経て焼成炉へ
        for (int z = 3; z >= 1; z--) {
            helper.setBlock(new BlockPos(1, 1, z), SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        }
        BlockPos kilnPos = new BlockPos(1, 1, 0);
        helper.setBlock(kilnPos, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = helper.getBlockEntity(kilnPos);
        helper.succeedWhen(() -> helper.assertTrue(kiln.energy().getEnergyStored() == kiln.energy().getMaxEnergyStored(),
                "焼成炉が満タンにならない: " + kiln.energy().getEnergyStored()));
    }

    // ------------------------------------------------------------------ 特異点技術・収集

    @GameTest(template = EMPTY)
    public static void worldlineAnchorLoadsChunksWithCatalyst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
        WorldlineAnchorBlockEntity anchor = helper.getBlockEntity(pos);
        anchor.energy().setEnergy(50_000);
        anchor.catalystSlot().insertItem(0, new ItemStack(item("muon_catalyst")), false);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(anchor.loadedRadius() == 1, "ミュオン触媒で半径1にならない: " + anchor.loadedRadius());
            int used = 50_000 - anchor.energy().getEnergyStored();
            helper.assertTrue(used >= 9 * 20 * 18 && used <= 9 * 20 * 21, "9チャンク×20 FE/t になっていない: " + used);
            ItemStack catalyst = anchor.catalystSlot().getStackInSlot(0);
            int worn = UsesHelper.get(catalyst).used();
            helper.assertTrue(worn >= 18 && worn <= 21, "触媒が1 tickに1ずつ減っていない: " + worn);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void worldlineAnchorStopsWithoutPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
        WorldlineAnchorBlockEntity anchor = helper.getBlockEntity(pos);
        anchor.catalystSlot().insertItem(0, new ItemStack(item("muon_catalyst")), false);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(anchor.loadedRadius() < 0, "電力なしでチャンクを固定した");
            helper.assertTrue(UsesHelper.get(anchor.catalystSlot().getStackInSlot(0)).used() == 0, "止まっているのに触媒が減った");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void cosmicMuonCollectorNeedsAltitude(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.COSMIC_MUON_COLLECTOR.get());
        // テスト場所は Y=100 より低いので収集しない
        helper.assertTrue(CosmicMuonCollectorBlockEntity.rateAt(helper.getLevel(), helper.absolutePos(pos)) == 0,
                "低い場所で収集している");
        helper.assertTrue(CosmicMuonCollectorBlockEntity.rateAt(helper.getLevel(), new BlockPos(0, 250, 0)) == 1.0,
                "Y=250 の空の下で最大にならない");
        helper.succeed();
    }
}
