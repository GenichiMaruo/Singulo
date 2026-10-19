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
        // 高い塔は速いぶん毎tickの電力も多い（1回 100,000 FE）ので、電源の代わりに毎tick満たす
        helper.onEachTick(() -> tower.energy().setEnergy(tower.energy().getMaxEnergyStored()));
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

    /** 一辺16のリングで、亜鉛とビスマスの原子核をぶつけるとニホニウムができる（一辺8では小さすぎて作れない）。 */
    @GameTest(template = "huge", timeoutTicks = 600)
    public static void acceleratorFusesNihonium(GameTestHelper helper) {
        int side = 16;
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                boolean edge = x == 0 || z == 0 || x == side - 1 || z == side - 1;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == 0 || x == side - 1) && (z == 0 || z == side - 1);
                helper.setBlock(new BlockPos(x, 1, z), corner || (x + z) % 3 == 0
                        ? SinguloBlocks.FOCUSING_MAGNET.get() : SinguloBlocks.ACCELERATOR_TUBE.get());
            }
        }
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, SinguloBlocks.CONTROLLERS.get(MachineType.PARTICLE_ACCELERATOR).get());
        MachineBlockEntity accelerator = helper.getBlockEntity(controllerPos);
        accelerator.items().setStackInSlot(0, new ItemStack(item("zinc_ingot")));
        accelerator.items().setStackInSlot(1, new ItemStack(item("bismuth_ingot")));
        helper.onEachTick(() -> accelerator.energy().setEnergy(accelerator.energy().getMaxEnergyStored()));
        helper.succeedWhen(() -> {
            helper.assertTrue(accelerator.structureSize() == side, "一辺16のリングとして形成されない: " + accelerator.structureSize());
            helper.assertTrue(accelerator.items().getStackInSlot(accelerator.type().outputSlot()).is(item("nihonium")), "ニホニウムができない");
        });
    }

    /** 一辺8のリングでも、ビスマスと炭素粉を入れると金インゴットができる（炭素の原子核で陽子をはぎ取る核変換）。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void acceleratorTransmutesBismuthIntoGold(GameTestHelper helper) {
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
        accelerator.items().setStackInSlot(0, new ItemStack(item("bismuth_ingot")));
        accelerator.items().setStackInSlot(1, new ItemStack(item("carbon_dust")));
        helper.onEachTick(() -> accelerator.energy().setEnergy(accelerator.energy().getMaxEnergyStored()));
        helper.succeedWhen(() -> helper.assertTrue(
                accelerator.items().getStackInSlot(accelerator.type().outputSlot()).is(Items.GOLD_INGOT), "金インゴットができない"));
    }

    /** 古いデータ（スロットの並びを書いていない）の加速器と触媒反応器は、出力と単極子のスロットを新しい位置へ写して読む。 */
    @GameTest(template = EMPTY)
    public static void oldMachinesKeepTheirItemsAfterSlotChanges(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.CATALYTIC_REACTOR).get());
        MachineBlockEntity reactor = helper.getBlockEntity(pos);
        net.neoforged.neoforge.items.ItemStackHandler old = new net.neoforged.neoforge.items.ItemStackHandler(7);
        old.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
        old.setStackInSlot(5, new ItemStack(item("muon_catalyst"), 2));
        old.setStackInSlot(6, new ItemStack(item("monopole_upgrade")));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("items", old.serializeNBT(registries));
        reactor.loadCustomOnly(tag, registries);
        helper.assertTrue(reactor.items().getStackInSlot(0).getCount() == 3, "入力が残らない");
        helper.assertTrue(reactor.items().getStackInSlot(5).is(item("muon_catalyst")), "出力が1つめの出口に残らない");
        helper.assertTrue(reactor.items().getStackInSlot(6).isEmpty(), "2つめの出口に別の物が入る: " + reactor.items().getStackInSlot(6));
        helper.assertTrue(reactor.items().getStackInSlot(MachineType.CATALYTIC_REACTOR.upgradeSlot()).is(item("monopole_upgrade")),
                "単極子アップグレードが新しいスロットへ移らない");
        // 保存し直したデータは、新しい並びのまま読める
        reactor.loadCustomOnly(reactor.saveCustomOnly(registries), registries);
        helper.assertTrue(reactor.items().getStackInSlot(MachineType.CATALYTIC_REACTOR.upgradeSlot()).is(item("monopole_upgrade")),
                "保存し直すと単極子アップグレードがずれる");
        helper.succeed();
    }

    /** 触媒反応器で粗銅を精錬すると、銅インゴット6個と、副産物のビスマスインゴットが2つめの出口に出る。 */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void catalyticReactorRefinesCopperWithBismuth(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.CATALYTIC_REACTOR).get());
        MachineBlockEntity reactor = helper.getBlockEntity(pos);
        helper.onEachTick(() -> reactor.energy().setEnergy(reactor.energy().getMaxEnergyStored()));
        reactor.items().setStackInSlot(0, new ItemStack(Items.RAW_COPPER, 8));
        reactor.automationFluids().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        int out = MachineType.CATALYTIC_REACTOR.outputSlot();
        helper.succeedWhen(() -> {
            helper.assertTrue(reactor.items().getStackInSlot(out).is(Items.COPPER_INGOT) && reactor.items().getStackInSlot(out).getCount() == 6,
                    "銅インゴットが6個出ない: " + reactor.items().getStackInSlot(out));
            helper.assertTrue(reactor.items().getStackInSlot(out + 1).is(item("bismuth_ingot")), "ビスマスが2つめの出口に出ない");
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

    /**
     * 極低温タービン（液体窒素の蓄電）: 溶岩の横で、液体窒素に詰めた電力（1 mBに250 FE）の9割を取り戻す。
     * 出力の上限 4.5 kFE/t に見合う 20 mB/t を使う。取り戻す電力は、冷却塔で詰めた電力より少ない（電力は増えない）。
     */
    @GameTest(template = EMPTY)
    public static void cryogenicTurbineRunsOnLavaAndNitrogen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.CRYOGENIC_TURBINE.get());
        helper.setBlock(pos.west(), Blocks.LAVA);
        CryogenicTurbineBlockEntity turbine = helper.getBlockEntity(pos);
        turbine.tank().fill(new FluidStack(fluid("liquid_nitrogen"), 4000), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(40, () -> {
            // 最初の走査まで最大10 tick
            int energy = turbine.energy().getEnergyStored();
            helper.assertTrue(energy >= 4500 * 28 && energy <= 4500 * 43, "出力が設計値から外れている: " + energy);
            int used = 4000 - turbine.tank().getFluidInTank(0).getAmount();
            helper.assertTrue(used >= 20 * 28 && used <= 20 * 43, "液体窒素の消費が毎tick 20 mB でない: " + used);
            double perMb = energy / (double) used;
            helper.assertTrue(perMb <= 250 * 0.9 + 1e-6, "液体窒素1 mBから、詰めた電力の9割より多く取り戻した: " + perMb);
            helper.succeed();
        });
    }

    /** 0.10.0 より前のタービン（スロットを書いていない）だけが、最初に画面を開いた人へ一度だけ知らせる。 */
    @GameTest(template = EMPTY)
    public static void oldCryogenicTurbineNoticesOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.CRYOGENIC_TURBINE.get());
        CryogenicTurbineBlockEntity turbine = helper.getBlockEntity(pos);
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(!turbine.legacyNotice(), "新しく置いたタービンが知らせようとする");
        net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
        old.putInt("energy", 0);
        old.put("tank", new net.minecraft.nbt.CompoundTag());
        turbine.loadCustomOnly(old, registries);
        helper.assertTrue(turbine.legacyNotice(), "前の版のタービンが知らせない");
        turbine.loadCustomOnly(turbine.saveCustomOnly(registries), registries);
        helper.assertTrue(turbine.legacyNotice(), "保存し直すと知らせを忘れる");
        turbine.noticeIfLegacy(helper.makeMockServerPlayerInLevel());
        helper.assertTrue(!turbine.legacyNotice(), "画面を開いても知らせが残る");
        turbine.loadCustomOnly(turbine.saveCustomOnly(registries), registries);
        helper.assertTrue(!turbine.legacyNotice(), "知らせたあと保存し直すと、また知らせる");
        helper.succeed();
    }

    /** 極低温タービンの入口の冷媒カートリッジは、タンクに空けて、空のカートリッジが出口に出る。 */
    @GameTest(template = EMPTY)
    public static void cryogenicTurbineEmptiesCartridges(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.CRYOGENIC_TURBINE.get());
        CryogenicTurbineBlockEntity turbine = helper.getBlockEntity(pos);
        turbine.items().setStackInSlot(0, new ItemStack(item("coolant_cartridge"), 3));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(turbine.tank().getFluidInTank(0).getAmount() == 300, "冷媒カートリッジ3個で、液体窒素が300 mB入らない: "
                    + turbine.tank().getFluidInTank(0).getAmount());
            helper.assertTrue(turbine.items().getStackInSlot(0).isEmpty(), "入口に冷媒カートリッジが残っている");
            ItemStack out = turbine.items().getStackInSlot(1);
            helper.assertTrue(out.is(item("empty_coolant_cartridge")) && out.getCount() == 3, "出口に空のカートリッジが3個出ない: " + out);
            helper.succeed();
        });
    }

    /** 冷媒カートリッジは液体窒素100 mBの容器: 満タンから空へ、空から満タンへ（半端な量では出し入れしない）。 */
    @GameTest(template = EMPTY)
    public static void coolantCartridgeIsAFluidContainer(GameTestHelper helper) {
        var full = new io.github.genichimaruo.singulo.machine.CoolantCartridges.Handler(new ItemStack(item("coolant_cartridge")));
        helper.assertTrue(full.drain(50, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "50 mBだけ汲み出せてしまう");
        FluidStack out = full.drain(100, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(out.getAmount() == 100 && out.getFluid().isSame(fluid("liquid_nitrogen")), "100 mBの液体窒素が出ない");
        helper.assertTrue(full.getContainer().is(item("empty_coolant_cartridge")), "空けたのに空のカートリッジにならない");
        var empty = new io.github.genichimaruo.singulo.machine.CoolantCartridges.Handler(new ItemStack(item("empty_coolant_cartridge")));
        helper.assertTrue(empty.fill(new FluidStack(fluid("liquid_nitrogen"), 60), IFluidHandler.FluidAction.EXECUTE) == 0, "60 mBで詰まってしまう");
        helper.assertTrue(empty.fill(new FluidStack(fluid("liquid_nitrogen"), 500), IFluidHandler.FluidAction.EXECUTE) == 100, "100 mB詰まらない");
        helper.assertTrue(empty.getContainer().is(item("coolant_cartridge")), "詰めたのに冷媒カートリッジにならない");
        helper.succeed();
    }

    /** 冷却塔の液体窒素は、塔の高さによらず 100 mB に 25,000 FE（高い塔は速いぶん、毎tickの電力が多い）。 */
    /** 冷却塔: 溶岩を自分で作った液体窒素で急に冷やすと黒曜石になる。 */
    @GameTest(template = TALL, timeoutTicks = 300)
    public static void coolingTowerQuenchesLavaIntoObsidian(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 10);
        tower.automationFluids().fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            tower.energy().setEnergy(tower.energy().getMaxEnergyStored());
            helper.assertTrue(tower.items().getStackInSlot(tower.type().outputSlot()).is(Items.OBSIDIAN), "黒曜石ができない");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void coolingTowerNitrogenCostIsFixedPerCraft(GameTestHelper helper) {
        MachineBlockEntity tower = buildTower(helper, 10);
        int start = tower.energy().getEnergyStored();
        helper.succeedWhen(() -> {
            helper.assertTrue(drainable(tower.automationFluids(), "liquid_nitrogen") >= 100, "液体窒素がまだできない");
            int used = start - tower.energy().getEnergyStored();
            helper.assertTrue(used >= 24_900 && used <= 25_100, "高さ10の塔で、液体窒素100 mBの電力が25,000 FEでない: " + used);
        });
    }

    /**
     * ミュオン触媒核融合炉: 触媒を炉へ入れ、水を4 tickに1 mB使って 4 kFE/t。触媒が尽きると、失活したミュオン触媒が出口に出る。
     */
    @GameTest(template = EMPTY)
    public static void muonFusionReactorBurnsCatalystAndWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MUON_FUSION_REACTOR.get());
        io.github.genichimaruo.singulo.machine.MuonFusionReactorBlockEntity reactor = helper.getBlockEntity(pos);
        reactor.tank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        reactor.items().setStackInSlot(0, new ItemStack(item("muon_catalyst"), 1));
        helper.runAtTickTime(21, () -> {
            helper.assertTrue(reactor.items().getStackInSlot(0).isEmpty(), "触媒が炉へ入らない");
            int energy = reactor.energy().getEnergyStored();
            helper.assertTrue(energy >= 4000 * 19 && energy <= 4000 * 21, "出力が 4 kFE/t でない: " + energy);
            int used = 1000 - reactor.tank().getFluidInTank(0).getAmount();
            helper.assertTrue(used >= 4 && used <= 6, "水が4 tickに1 mB減らない: " + used);
            reactor.setBurnForTest(3);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(reactor.burn() == 0, "触媒が尽きない");
            helper.assertTrue(reactor.items().getStackInSlot(1).is(item("spent_muon_catalyst")), "失活したミュオン触媒が出口に出ない");
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
