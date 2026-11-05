package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 液体パイプ: ほかの mod なしで、このmodの液体をすべて運べること。 */
@net.neoforged.neoforge.gametest.GameTestHolder("singulo")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class FluidPipeGameTests {
    private static final String EMPTY = "empty";

    private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos, MachineType type) {
        h.setBlock(pos, SinguloBlocks.MACHINES.get(type).get());
        return h.getBlockEntity(pos);
    }

    private static void pipes(GameTestHelper h, BlockPos from, BlockPos to) {
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            h.setBlock(p, SinguloBlocks.FLUID_PIPE.get());
        }
    }

    private static IFluidHandler handler(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), null);
    }

    private static int amount(IFluidHandler handler, Fluid fluid) {
        int sum = 0;
        for (int t = 0; t < handler.getTanks(); t++) {
            if (handler.getFluidInTank(t).getFluid() == fluid) {
                sum += handler.getFluidInTank(t).getAmount();
            }
        }
        return sum;
    }

    /** レシピが液体を使う通常の装置の種類（液体ごとの受け手を探す）。 */
    private static MachineType consumerOf(GameTestHelper h, Fluid fluid) {
        for (MachineType type : MachineType.values()) {
            if (!SinguloBlocks.MACHINES.containsKey(type) || type.fluidInputs() == 0) {
                continue;
            }
            List<net.minecraft.world.item.crafting.RecipeHolder<MachineRecipe>> recipes =
                    new ArrayList<>(MachineBlockEntity.stationRecipes(h.getLevel(), type));
            recipes.addAll(MachineBlockEntity.massRecipes(h.getLevel(), type));
            for (var holder : recipes) {
                for (MachineRecipe.FluidInput in : holder.value().fluidIngredients()) {
                    if (in.fluid() == fluid) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    private static void charge(ContainmentTankBlockEntity tank) {
        for (int i = 0; i < 5; i++) {
            tank.energy().receiveEnergy(20_000, false);
        }
    }

    /**
     * このmodで扱う液体（水・溶岩と、登録したすべての液体・ガス）は、どれも出口 → パイプ → それを使う口へ届く。
     * 受け手は通常の装置のレシピから探し、なければ液体専用の受け口（燃料電池・閉じ込めタンク）を使う。受け手のない液体があれば失敗する。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void everyFluidTravelsToWhatUsesIt(GameTestHelper h) {
        List<Fluid> fluids = new ArrayList<>(List.of(Fluids.WATER, Fluids.LAVA));
        SinguloFluids.ALL.values().forEach(e -> fluids.add(e.source().get()));
        List<Runnable> checks = new ArrayList<>();
        List<Fluid> noReceiver = new ArrayList<>();
        int z = 1;
        for (Fluid fluid : fluids) {
            BlockPos giver = new BlockPos(1, 1, z);
            BlockPos taker = new BlockPos(5, 1, z);
            MachineType type = consumerOf(h, fluid);
            if (type != null) {
                machine(h, taker, type);
            } else if (fluid == SinguloFluids.get("dark_matter")) {
                h.setBlock(taker, SinguloBlocks.CONTAINMENT_TANK.get());
                charge(h.getBlockEntity(taker));
            } else if (fluid == SinguloFluids.get("oxygen") || fluid == SinguloFluids.get("hydrogen")) {
                h.setBlock(taker, SinguloBlocks.HYDROGEN_FUEL_CELL.get());
            } else if (fluid == Fluids.LAVA) {
                // 溶岩を使うレシピはないので、大釜へ（大釜は1,000 mB単位）
                h.setBlock(taker, Blocks.CAULDRON);
            } else if (fluid == SinguloFluids.get("helium") || fluid == SinguloFluids.get("axion_condensate")) {
                continue;                                   // マルチブロックのレシピだけが使う（下のポート経由のテスト）
            } else {
                noReceiver.add(fluid);
                continue;
            }
            MachineBlockEntity from = machine(h, giver, MachineType.ELECTROLYZER);
            from.forceOutputTankForTest(new FluidStack(fluid, fluid == Fluids.LAVA ? 1_000 : 500));
            pipes(h, giver.east(), taker.west());
            // 届いた先で使われることもあるので、出口が空になったことで確かめる
            checks.add(() -> h.assertTrue(amount(from.automationFluids(), fluid) == 0, fluid + " が届かない: " + type));
            z += 2;
        }
        h.assertTrue(noReceiver.isEmpty(), "受け取る口がない液体: " + noReceiver);
        h.succeedWhen(() -> checks.forEach(Runnable::run));
    }

    /** パイプ → 搬入出ポート → マルチブロックの入力タンク。 */
    private static void deliverThroughPort(GameTestHelper h, Blueprints.Kind kind, BlockPos controller, int size, int portY, Fluid fluid) {
        TestBuild.build(h, kind, controller, Direction.SOUTH, size);
        BlockPos port = TestBuild.port(h, kind, controller, Direction.SOUTH, size, 1, portY, 0);
        BlockPos pipe = port.north();
        BlockPos giver = pipe.north();
        MachineBlockEntity target = h.getBlockEntity(controller);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(target.structureSize() == size, "形成されない"))
                .thenExecute(() -> {
                    h.getLevel().setBlockAndUpdate(giver, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get().defaultBlockState());
                    ((MachineBlockEntity) h.getLevel().getBlockEntity(giver)).forceOutputTankForTest(new FluidStack(fluid, 500));
                    h.getLevel().setBlockAndUpdate(pipe, SinguloBlocks.FLUID_PIPE.get().defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(amount(target.automationFluids(), fluid) == 500, fluid + " がポートから入らない"))
                .thenSucceed();
    }

    @GameTest(template = "tall", timeoutTicks = 200)
    public static void heliumReachesCoolingTowerThroughPort(GameTestHelper h) {
        deliverThroughPort(h, Blueprints.Kind.COOLING_TOWER, new BlockPos(2, 1, 2), 7, 1, SinguloFluids.get("helium"));
    }

    @GameTest(template = "tall", timeoutTicks = 200)
    public static void lavaReachesCoolingTowerThroughPort(GameTestHelper h) {
        deliverThroughPort(h, Blueprints.Kind.COOLING_TOWER, new BlockPos(2, 1, 2), 7, 1, Fluids.LAVA);
    }

    @GameTest(template = "huge", timeoutTicks = 200)
    public static void axionReachesCasimirCavityThroughPort(GameTestHelper h) {
        deliverThroughPort(h, Blueprints.Kind.CASIMIR_CAVITY, new BlockPos(7, 2, 3), 5, 3, SinguloFluids.get("axion_condensate"));
    }

    /** マルチブロックの出力タンク → 搬入出ポート → パイプ → 使う口。 */
    @GameTest(template = "tall", timeoutTicks = 200)
    public static void coolingTowerOutputLeavesThroughPort(GameTestHelper h) {
        BlockPos controller = new BlockPos(2, 1, 2);
        TestBuild.build(h, Blueprints.Kind.COOLING_TOWER, controller, Direction.SOUTH, 7);
        BlockPos port = TestBuild.port(h, Blueprints.Kind.COOLING_TOWER, controller, Direction.SOUTH, 7, 1, 1, 0);
        BlockPos pipe = port.north();
        BlockPos turbine = pipe.north();
        MachineBlockEntity tower = h.getBlockEntity(controller);
        Fluid nitrogen = SinguloFluids.get("liquid_nitrogen");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tower.structureSize() == 7, "形成されない"))
                .thenExecute(() -> {
                    tower.forceOutputTankForTest(new FluidStack(nitrogen, 400));
                    h.getLevel().setBlockAndUpdate(turbine, SinguloBlocks.CRYOGENIC_TURBINE.get().defaultBlockState());
                    h.getLevel().setBlockAndUpdate(pipe, SinguloBlocks.FLUID_PIPE.get().defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(amount(tower.automationFluids(), nitrogen) == 0, "塔の窒素が出ていかない"))
                .thenSucceed();
    }

    /** 液体の総量は変わらず、受け取った分だけ出口から減る。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void transferConservesFluid(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(6, 1, 1);
        MachineBlockEntity giver = machine(h, a, MachineType.ELECTROLYZER);
        MachineBlockEntity taker = machine(h, b, MachineType.ELECTROLYZER);
        giver.forceOutputTankForTest(new FluidStack(Fluids.WATER, 3_000));
        pipes(h, a.east(), b.west());
        // 流れている途中でも合計は常に同じ（パイプは液体を溜めない）
        h.onEachTick(() -> h.assertTrue(amount(giver.automationFluids(), Fluids.WATER) + amount(taker.automationFluids(), Fluids.WATER) == 3_000,
                "液体の総量が変わった"));
        h.succeedWhen(() -> h.assertTrue(amount(taker.automationFluids(), Fluids.WATER) == 3_000, "届いていない"));
    }

    /** レシピで使わない液体は、空の入力タンクにも入れない（水の枠に水素が入って詰まらない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void unusedFluidDoesNotEnterInputTank(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        MachineBlockEntity giver = machine(h, a, MachineType.ELECTROLYZER);
        MachineBlockEntity taker = machine(h, b, MachineType.ELECTROLYZER);
        giver.forceOutputTankForTest(new FluidStack(SinguloFluids.get("hydrogen"), 1_000));
        pipes(h, a.east(), b.west());
        h.runAfterDelay(40, () -> {
            h.assertTrue(amount(taker.automationFluids(), SinguloFluids.get("hydrogen")) == 0, "使わない水素が入力タンクに入った");
            h.assertTrue(amount(giver.automationFluids(), SinguloFluids.get("hydrogen")) == 1_000, "水素が消えた");
            h.succeed();
        });
    }

    /** 入力タンクの中身は抜かない（燃料を別の装置へ回さない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void inputTanksAreNotDrained(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        BlockPos turbine = new BlockPos(3, 2, 1);
        MachineBlockEntity withWater = machine(h, a, MachineType.ELECTROLYZER);
        withWater.automationFluids().fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        MachineBlockEntity empty = machine(h, b, MachineType.ELECTROLYZER);
        h.setBlock(turbine, SinguloBlocks.CRYOGENIC_TURBINE.get());
        CryogenicTurbineBlockEntity cryo = h.getBlockEntity(turbine);
        cryo.tank().fill(new FluidStack(SinguloFluids.get("liquid_nitrogen"), 800), IFluidHandler.FluidAction.EXECUTE);
        int nitrogen = amount(cryo.tank(), SinguloFluids.get("liquid_nitrogen"));
        pipes(h, a.east(), b.west());
        h.runAfterDelay(40, () -> {
            h.assertTrue(amount(empty.automationFluids(), Fluids.WATER) == 0, "入力タンクの水が抜かれた");
            h.assertTrue(amount(cryo.tank(), SinguloFluids.get("liquid_nitrogen")) <= nitrogen
                    && amount(empty.automationFluids(), SinguloFluids.get("liquid_nitrogen")) == 0, "タービンの窒素が抜かれた");
            h.succeed();
        });
    }

    /** タンクどうしでは行き来しない。受け手がいればタンクから送る。 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void storesDoNotSloshBetweenEachOther(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        h.setBlock(a, SinguloBlocks.CONTAINMENT_TANK.get());
        h.setBlock(b, SinguloBlocks.CONTAINMENT_TANK.get());
        ContainmentTankBlockEntity ta = h.getBlockEntity(a);
        ContainmentTankBlockEntity tb = h.getBlockEntity(b);
        charge(ta);
        charge(tb);
        ta.tank().fill(new FluidStack(SinguloFluids.get("dark_matter"), 3_000), IFluidHandler.FluidAction.EXECUTE);
        tb.tank().fill(new FluidStack(SinguloFluids.get("dark_matter"), 1_000), IFluidHandler.FluidAction.EXECUTE);
        pipes(h, a.east(), b.west());
        h.runAfterDelay(50, () -> {
            h.assertTrue(ta.amount() == 3_000 && tb.amount() == 1_000, "タンクどうしで液体が動いた: " + ta.amount() + " / " + tb.amount());
            h.succeed();
        });
    }

    /** 隣の水源から汲む。受け手が満ちたら汲まず、水源を減らさない。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pipeDrawsWaterSourceOnlyWhileThereIsRoom(GameTestHelper h) {
        BlockPos water = new BlockPos(1, 1, 1);
        BlockPos taker = new BlockPos(5, 1, 1);
        h.setBlock(water, Blocks.WATER);
        MachineBlockEntity machine = machine(h, taker, MachineType.ELECTROLYZER);
        pipes(h, water.east(), taker.west());
        h.succeedWhen(() -> {
            h.assertTrue(amount(machine.automationFluids(), Fluids.WATER) == 1_000, "水源から汲めていない");
            h.assertBlockNotPresent(Blocks.WATER, water);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void fullReceiverLeavesSourceInPlace(GameTestHelper h) {
        BlockPos water = new BlockPos(1, 1, 1);
        BlockPos taker = new BlockPos(5, 1, 1);
        h.setBlock(water, Blocks.WATER);
        MachineBlockEntity machine = machine(h, taker, MachineType.ELECTROLYZER);
        machine.automationFluids().fill(new FluidStack(Fluids.WATER, MachineType.TANK_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        pipes(h, water.east(), taker.west());
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(Blocks.WATER, water);
            h.succeed();
        });
    }

    /** パイプを切ると止まり、つなぎ直すと流れる。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cuttingThePipeStopsTheFlow(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        BlockPos middle = new BlockPos(3, 1, 1);
        MachineBlockEntity giver = machine(h, a, MachineType.ELECTROLYZER);
        MachineBlockEntity taker = machine(h, b, MachineType.ELECTROLYZER);
        pipes(h, a.east(), b.west());
        h.setBlock(middle, Blocks.AIR);
        giver.forceOutputTankForTest(new FluidStack(Fluids.WATER, 500));
        h.runAfterDelay(30, () -> {
            h.assertTrue(amount(taker.automationFluids(), Fluids.WATER) == 0, "切れたパイプを液体が通った");
            h.setBlock(middle, SinguloBlocks.FLUID_PIPE.get());
            h.succeedWhen(() -> h.assertTrue(amount(taker.automationFluids(), Fluids.WATER) == 500, "つなぎ直しても流れない"));
        });
    }

    /** 面の設定で出し入れしない面からは運ばず、その面にはパイプの腕も伸びない。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void machineSideSettingsAreRespected(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        BlockPos firstPipe = a.east();
        MachineBlockEntity giver = machine(h, a, MachineType.ELECTROLYZER);
        MachineBlockEntity taker = machine(h, b, MachineType.ELECTROLYZER);
        giver.forceOutputTankForTest(new FluidStack(Fluids.WATER, 500));
        for (int channel = 1; channel < giver.sideChannels(); channel++) {
            for (SideConfig.Face face : SideConfig.Face.values()) {
                giver.setSide(channel, face, SideConfig.NONE, false);
            }
        }
        pipes(h, firstPipe, b.west());
        h.runAfterDelay(30, () -> {
            h.assertTrue(amount(taker.automationFluids(), Fluids.WATER) == 0, "出さない面から液体が抜かれた");
            h.assertTrue(!h.getBlockState(firstPipe).getValue(io.github.genichimaruo.singulo.cable.CableBlock.CONNECTIONS.get(Direction.WEST)),
                    "出し入れしない面へパイプがつながった");
            h.succeed();
        });
    }
}
