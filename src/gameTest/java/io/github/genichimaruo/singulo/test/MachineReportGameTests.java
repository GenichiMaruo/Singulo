package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineReport;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 装置の様子の報告（0.21.0。Jade・WTHIT・工場モニターが使う）: 画面と同じ状態と理由を出し、組み上がっていないマルチブロックには
 * 足りない部品を付け、NBT で送っても変わらない。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class MachineReportGameTests {
    private static String key(net.minecraft.network.chat.Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reportsExplainIdleMachinesAndMissingParts(GameTestHelper h) {
        var level = h.getLevel();
        // 焼成炉に土: 作れない材料で待っている
        BlockPos kilnAt = new BlockPos(1, 1, 1);
        h.setBlock(kilnAt, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = h.getBlockEntity(kilnAt);
        kiln.items().setStackInSlot(MachineType.KILN.recipeSlot(0), new ItemStack(Items.DIRT));
        h.assertTrue(MachineReport.reports(kiln.getBlockState(), kiln), "A kiln does not report");
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
