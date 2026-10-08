package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.DegenerateFurnaceBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.ProbeStationBlockEntity;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 段階4（時間）の装置の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Stage4GameTests {
    private static final String EMPTY = "empty";
    private static final String LARGE = "large";

    private Stage4GameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static void keepPowered(GameTestHelper helper, MachineBlockEntity m) {
        helper.onEachTick(() -> m.energy().setEnergy(m.energy().getMaxEnergyStored()));
    }

    private static int drainable(IFluidHandler handler, String id) {
        return handler.drain(new FluidStack(SinguloFluids.get(id), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE).getAmount();
    }

    // ------------------------------------------------------------------ 混成ボーナス

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void mixedSourcesMakeMixedLv1AndEightMakeLv2(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        MachineBlockEntity c = helper.getBlockEntity(pos);
        keepPowered(helper, c);
        c.cycleMode();                                       // 圧縮ブロックLv1（質量9）
        c.items().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
        helper.runAtTickTime(3, () -> c.items().setStackInSlot(0, new ItemStack(Items.ANDESITE, 3)));
        helper.runAtTickTime(6, () -> c.items().setStackInSlot(0, new ItemStack(Items.GRANITE, 3)));
        helper.runAtTickTime(40, () -> {
            ItemStack lv1 = c.items().getStackInSlot(c.type().outputSlot());
            helper.assertTrue(lv1.is(item("compressed_block_1")), "Lv1 ができない");
            helper.assertTrue(Boolean.TRUE.equals(lv1.get(SinguloComponents.MIXED_SOURCE.get())), "3種類から作ったのに混成にならない");
            // 通常モードに戻し、混成の Lv1 を8個入れると Lv2 になる
            c.cycleMode();
            c.cycleMode();
            c.cycleMode();
            c.items().setStackInSlot(c.type().outputSlot(), ItemStack.EMPTY);
            c.items().setStackInSlot(0, lv1.copyWithCount(8));
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(c.items().getStackInSlot(c.type().outputSlot()).is(item("compressed_block_2")),
                    "混成の Lv1 8個で Lv2 にならない");
            helper.assertTrue(c.items().getStackInSlot(0).isEmpty(), "混成の Lv1 を8個だけ使うはず");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void plainLv1NeedsNine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.COMPRESSOR).get());
        MachineBlockEntity c = helper.getBlockEntity(pos);
        keepPowered(helper, c);
        c.items().setStackInSlot(0, new ItemStack(item("compressed_block_1"), 8));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(c.items().getStackInSlot(c.type().outputSlot()).isEmpty(), "混成でない Lv1 8個で Lv2 ができた");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 縮退圧縮炉・カシミール空洞

    @GameTest(template = EMPTY, timeoutTicks = 1400)
    public static void compactorMakesDegenerateShell(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 2, 3);
        BlockPos controllerPos = center.north();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!p.equals(center)) {
                helper.setBlock(p, SinguloBlocks.DEGENERATE_CASING.get());
            }
        }
        helper.setBlock(controllerPos, SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get());
        MachineBlockEntity m = helper.getBlockEntity(controllerPos);
        keepPowered(helper, m);
        m.items().setStackInSlot(0, new ItemStack(item("compressed_block_3"), 8));
        m.items().setStackInSlot(1, new ItemStack(item("compressed_metal_block_2"), 2));
        m.items().setStackInSlot(2, new ItemStack(item("entangled_element")));
        m.automationFluids().fill(new FluidStack(SinguloFluids.get("liquid_nitrogen"), 6000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.structureSize() == 3, "3×3×3 として形成されない");
            helper.assertTrue(m.items().getStackInSlot(m.type().outputSlot()).is(item("degenerate_matter_shell")),
                    "縮退物質殻ができない");
            helper.assertTrue(drainable(m.automationFluids(), "liquid_nitrogen") == 0
                    && m.automationFluids().getFluidInTank(0).isEmpty(), "圧縮熱の除去に液体窒素6000 mBを使っていない");
        });
    }

    /** 5×5×5 のカシミール空洞を (1,1,1) から建てる。コントローラは間の段の側面。 */
    private static MachineBlockEntity buildCavity(GameTestHelper helper) {
        BlockPos o = new BlockPos(1, 1, 1);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(o.offset(x, 0, z), SinguloBlocks.MIRROR_PLATE.get());
                helper.setBlock(o.offset(x, 4, z), SinguloBlocks.MIRROR_PLATE.get());
                boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                if (corner) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(o.offset(x, y, z), SinguloBlocks.DEGENERATE_CASING.get());
                    }
                }
            }
        }
        BlockPos controllerPos = o.offset(2, 2, 0);
        helper.setBlock(controllerPos, SinguloBlocks.CONTROLLERS.get(MachineType.CASIMIR_CAVITY).get());
        MachineBlockEntity m = helper.getBlockEntity(controllerPos);
        keepPowered(helper, m);
        return m;
    }

    @GameTest(template = EMPTY, timeoutTicks = 3000)
    public static void casimirCavityMakesAxionThenExoticMatter(GameTestHelper helper) {
        MachineBlockEntity m = buildCavity(helper);
        m.items().setStackInSlot(0, new ItemStack(Items.NETHER_STAR));
        m.items().setStackInSlot(1, new ItemStack(Items.ENDER_PEARL, 8));
        m.items().setStackInSlot(2, new ItemStack(item("quantum_computing_module")));
        m.items().setStackInSlot(3, new ItemStack(item("optical_lattice_substrate"), 4));
        m.items().setStackInSlot(4, new ItemStack(item("data_card_culture_data")));
        m.items().setStackInSlot(5, new ItemStack(item("time_crystal_catalyst")));
        helper.succeedWhen(() -> {
            helper.assertTrue(m.structureSize() == 5, "5×5×5 として形成されない");
            ItemStack out = m.items().getStackInSlot(m.type().outputSlot());
            helper.assertTrue(out.is(item("exotic_matter")) && out.getCount() == 8, "エキゾチック物質が8個できない");
            ItemStack catalyst = m.items().getStackInSlot(5);
            helper.assertTrue(UsesHelper.get(catalyst).used() == 2 * 60 * 20,
                    "時間結晶触媒が2分ぶんだけ減っていない: " + UsesHelper.get(catalyst).used());
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void cavityNeedsVacuumBetweenMirrors(GameTestHelper helper) {
        MachineBlockEntity m = buildCavity(helper);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);   // 鏡面の間に物を置くと真空でなくなる
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(m.structureSize() == 0, "鏡面の間がふさがっているのに形成された");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 時間結晶

    @GameTest(template = EMPTY, timeoutTicks = 25_000)
    public static void incubatorPurityHalvesLifeWhenHalfUnderpowered(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.TIME_CRYSTAL_INCUBATOR).get());
        MachineBlockEntity m = helper.getBlockEntity(pos);
        m.items().setStackInSlot(0, new ItemStack(item("time_crystal_seed")));
        m.items().setStackInSlot(1, new ItemStack(Items.ECHO_SHARD, 2));
        m.items().setStackInSlot(2, new ItemStack(item("entangled_element"), 2));
        m.items().setStackInSlot(3, new ItemStack(item("optical_lattice_substrate"), 2));
        m.items().setStackInSlot(4, new ItemStack(item("bose_condensate_catalyst")));
        int[] tick = {0};
        // 育成の前半は要求（30 kFE/t）の半分しか渡さず、後半は満たす
        helper.onEachTick(() -> m.energy().setEnergy(tick[0]++ < 12_000 ? 15_000 : m.energy().getMaxEnergyStored()));
        helper.succeedWhen(() -> {
            ItemStack out = m.items().getStackInSlot(m.type().outputSlot());
            helper.assertTrue(out.is(item("time_crystal_catalyst")), "時間結晶触媒が育たない");
            int life = UsesHelper.get(out).max();
            int full = 45 * 60 * 20;
            helper.assertTrue(Math.abs(life - full / 2) <= full / 20, "純度約50%で寿命が半分にならない: " + life + " / " + full);
        });
    }

    // ------------------------------------------------------------------ 縮退熱炉

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void degenerateFurnaceBurnsLv2With20MFE(GameTestHelper helper) {
        BlockPos o = new BlockPos(1, 1, 0);
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean piston = (x == 3 && (z == 2 || z == 4)) || (z == 3 && (x == 2 || x == 4));
                Block face = piston ? SinguloBlocks.DEGENERATE_FURNACE_PISTON.get() : SinguloBlocks.DEGENERATE_CASING.get();
                helper.setBlock(o.offset(x, 0, z), face);
                helper.setBlock(o.offset(x, 8, z), face);
                if ((x == 0 || x == 6) && (z == 0 || z == 6)) {
                    for (int y = 1; y <= 7; y++) {
                        helper.setBlock(o.offset(x, y, z), SinguloBlocks.DEGENERATE_CASING.get());
                    }
                }
            }
        }
        BlockPos controllerPos = o.offset(0, 3, 0);
        helper.setBlock(controllerPos, SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER.get());
        DegenerateFurnaceBlockEntity f = helper.getBlockEntity(controllerPos);
        f.catalystSlot().insertItem(0, new ItemStack(item("time_crystal_catalyst")), false);
        f.fuel().insertItem(0, new ItemStack(item("compressed_block_2"), 2), false);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(f.structureSize() == 9, "7×7×9 として形成されない");
            long energy = f.energy().getEnergyStored();
            helper.assertTrue(energy >= 20_000_000L * 4, "20 MFE/t で発電していない: " + energy);
            helper.assertTrue(f.fuel().getStackInSlot(0).getCount() == 1, "燃料を1個ずつ燃やしていない");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 送電・蓄電

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void cableNetworkIsLimitedByThinnestCable(GameTestHelper helper) {
        // SMESモジュール → トポロジカル導線 → 銅導線 → トポロジカル導線 → SMESモジュール。銅導線（2 kFE/t）で頭打ちになる
        BlockPos from = new BlockPos(1, 1, 5);
        BlockPos to = new BlockPos(1, 1, 1);
        helper.setBlock(from, SinguloBlocks.SMES_MODULE.get());
        helper.setBlock(new BlockPos(1, 1, 4), SinguloBlocks.TOPOLOGICAL_WIRE.get());
        helper.setBlock(new BlockPos(1, 1, 3), SinguloBlocks.COPPER_WIRE.get());
        helper.setBlock(new BlockPos(1, 1, 2), SinguloBlocks.TOPOLOGICAL_WIRE.get());
        helper.setBlock(to, SinguloBlocks.SMES_MODULE.get());
        SmesCellBlockEntity source = helper.getBlockEntity(from);
        SmesCellBlockEntity sink = helper.getBlockEntity(to);
        source.energy().setEnergy(1_000_000_000);
        helper.runAtTickTime(21, () -> {
            int got = sink.energy().getEnergyStored();
            helper.assertTrue(got > 0 && got <= 2_000 * 21, "銅導線が混じると 2 kFE/t で頭打ちになるはず: " + got);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void topologicalWireCarries100MFE(GameTestHelper helper) {
        BlockPos from = new BlockPos(1, 1, 4);
        BlockPos to = new BlockPos(1, 1, 1);
        helper.setBlock(from, SinguloBlocks.SMES_MODULE.get());
        helper.setBlock(new BlockPos(1, 1, 3), SinguloBlocks.TOPOLOGICAL_WIRE.get());
        helper.setBlock(new BlockPos(1, 1, 2), SinguloBlocks.TOPOLOGICAL_WIRE.get());
        helper.setBlock(to, SinguloBlocks.SMES_MODULE.get());
        SmesCellBlockEntity source = helper.getBlockEntity(from);
        SmesCellBlockEntity sink = helper.getBlockEntity(to);
        source.energy().setEnergy(1_000_000_000);
        helper.runAtTickTime(5, () -> {
            int got = sink.energy().getEnergyStored();
            helper.assertTrue(got >= 100_000_000 * 3, "トポロジカル導線で 100 MFE/t 届かない: " + got);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 自動探査機

    @GameTest(template = EMPTY)
    public static void probeStationFetchesFromDiscoveredObservationPost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            RuinDiscovery.record(player, "observation_post", new BlockPos(100, 64, 100));
            RuinDiscovery.record(player, "culture_facility", new BlockPos(200, -40, 200));   // 常に手動
            BlockPos pos = new BlockPos(2, 1, 2);
            helper.setBlock(pos, SinguloBlocks.PROBE_STATION.get());
            ProbeStationBlockEntity station = helper.getBlockEntity(pos);
            helper.assertTrue(station.registerFrom(player) == 1, "地表観測拠点だけが登録されるはず");
            long now = 1000;
            station.completeMission(helper.getLevel(), now);
            int logs = count(station, item("observation_log"));
            int units = count(station, item("degraded_control_unit"));
            helper.assertTrue(logs >= 1 && logs <= 3 && units >= 3 && units <= 5,
                    "持ち帰る量が手動遠征の半分にならない: ログ" + logs + " 制御ユニット" + units);
            station.completeMission(helper.getLevel(), now + 1);
            helper.assertTrue(count(station, item("observation_log")) == logs, "再生前の遺構にもう一度行った");
            station.completeMission(helper.getLevel(), now + 7 * 24_000);
            helper.assertTrue(count(station, item("observation_log")) > logs, "7日たっても行かない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static int count(ProbeStationBlockEntity station, Item item) {
        int n = 0;
        for (int i = 0; i < station.output().getSlots(); i++) {
            if (station.output().getStackInSlot(i).is(item)) {
                n += station.output().getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    private static final class Blocks {
        static final net.minecraft.world.level.block.Block STONE = net.minecraft.world.level.block.Blocks.STONE;
    }
}
