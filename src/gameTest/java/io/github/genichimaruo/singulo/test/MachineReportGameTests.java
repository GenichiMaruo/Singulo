package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineReport;
import io.github.genichimaruo.singulo.api.machine.MachineReports;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 装置の様子の報告（0.21.0。Jade・WTHIT・工場モニターが使う）: 画面と同じ状態と理由を出し、組み上がっていないマルチブロックには
 * 足りない部品を付け、NBT で送っても変わらない。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class MachineReportGameTests {
    private static final class AddonMachine extends net.minecraft.world.level.block.entity.ChestBlockEntity {
        AddonMachine(BlockPos pos) { super(pos, Blocks.CHEST.defaultBlockState()); }
    }

    private static void registerMachineReport() {
        var event = new io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent();
        event.registerBlockEntity(MachineReports.CAPABILITY, net.minecraft.world.level.block.entity.BlockEntityType.CHEST,
                (chest, ignored) -> chest instanceof AddonMachine ? new MachineReport.Source() {
                    public MachineReport report() {
                        return new MachineReport(MachineReport.State.NO_POWER, net.minecraft.network.chat.Component.literal("Cooling"),
                                java.util.List.of(), 5, 20, 7, 80, 0, 0, chest.getItem(0), 1, java.util.List.of(), 0);
                    }
                    public int remainingTicks() { return 15; }
                } : null);
    }

    @GameTest(template = "empty")
    public static void addonCapabilityFeedsTheMonitorOverlayAndAndroidTimer(GameTestHelper h) {
        var at = new BlockPos(2, 1, 2);
        h.setBlock(at, Blocks.CHEST);
        var level = h.getLevel();
        var pos = h.absolutePos(at);
        registerMachineReport();
        var be = new AddonMachine(pos);
        level.setBlockEntity(be);
        be.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 2));
        var report = MachineReports.read(level, pos).orElseThrow();
        report.catalyst().shrink(1);
        h.assertTrue(report.catalyst().getCount() == 2 && be.getItem(0).getCount() == 2, "Reading a report changed its source");
        h.assertTrue(MachineReport.reports(be.getBlockState(), be) && MachineReport.of(level, pos).energy() == 7
                && io.github.genichimaruo.singulo.android.work.MachineWatch.eta(level, pos) == 15, "Addon diagnostics did not reach the HUD or scheduler");
        var monitor = io.github.genichimaruo.singulo.monitor.FactoryMonitor.scan(level, pos);
        h.assertTrue(monitor.stalled().stream().anyMatch(s -> s.pos().equals(pos) && s.reason().getString().equals("Cooling")),
                "Addon machine was omitted from the factory monitor");
        var far = pos.offset(40000, 0, 40000);
        h.assertTrue(!level.isLoaded(far) && MachineReports.read(level, far).isEmpty() && !level.isLoaded(far), "Report read loaded a chunk");
        h.succeed();
    }
    private static String key(net.minecraft.network.chat.Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reportsExplainIdleMachinesAndMissingParts(GameTestHelper h) {
        var level = h.getLevel();
        // 焼成炉に土: 作れない材料で待っている
        BlockPos kilnAt = new BlockPos(1, 1, 1);
        h.setBlock(kilnAt, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, kilnAt);
        kiln.items().setStackInSlot(MachineType.KILN.recipeSlot(0), new ItemStack(Items.DIRT));
        h.assertTrue(MachineReport.reports(kiln.getBlockState(), kiln), "A kiln does not report");
        h.assertTrue(kiln.getCapability(MachineReports.CAPABILITY).orElse(null) == kiln, "Singulo machine did not expose the public capability");
        h.assertFalse(MachineReport.reports(Blocks.STONE.defaultBlockState(), null), "Stone reports");

        // 組み上がっていない縮退圧縮炉: コントローラーだけで、ほかの部品がみな足りない
        BlockPos controllerAt = new BlockPos(5, 1, 5);
        h.setBlock(controllerAt, SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get());
        MachineReport broken = MachineReport.of(level, h.absolutePos(controllerAt));
        h.assertTrue(broken != null && broken.state() == MachineReport.State.BROKEN, "An unformed controller is not broken: " + broken);
        int need = 0;
        for (var e : Blueprints.partCounts(Blueprints.Kind.DEGENERATE_COMPACTOR).entrySet()) {
            if (e.getKey() != SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get()) {
                need += e.getValue();
            }
        }
        int missing = broken.missing().stream().mapToInt(ItemStack::getCount).sum();
        h.assertTrue(missing == need, "Missing parts " + missing + " instead of " + need);
        h.assertTrue(broken.lines(true).stream().anyMatch(l -> l.getString().contains("×")), "No missing part lines");

        h.succeedWhen(() -> {
            MachineReport r = MachineReport.of(level, h.absolutePos(kilnAt));
            h.assertTrue(r != null && r.state() == MachineReport.State.WARN, "The kiln does not warn: " + r);
            h.assertTrue(key(r.label()).equals("gui.singulo.reason.label.no_match"), "Wrong reason: " + key(r.label()));
            h.assertTrue(r.capacity() > 0, "No energy in the report");
            // 送っても同じ行になる
            MachineReport back = MachineReport.load(r.save(level.registryAccess()), level.registryAccess());
            h.assertTrue(back != null && back.lines(true).stream().map(c -> c.getString()).toList()
                    .equals(r.lines(true).stream().map(c -> c.getString()).toList()), "The report changes when sent");
            MachineReport brokenBack = MachineReport.load(broken.save(level.registryAccess()), level.registryAccess());
            h.assertTrue(brokenBack != null && brokenBack.missing().size() == broken.missing().size(), "Missing parts change when sent");
        });
    }
}
