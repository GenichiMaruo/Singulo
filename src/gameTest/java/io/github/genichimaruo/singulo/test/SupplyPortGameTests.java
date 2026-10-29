package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.android.work.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class SupplyPortGameTests {
    @GameTest(template = "empty")
    public static void supplyGhostEntriesNeverCreateOrConsumePhysicalItems(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        SupplyPortBlockEntity port = h.getBlockEntity(pos);
        var player = TestScope.player(h);
        var menu = new SupplyPortMenu(1, player.getInventory(), h.absolutePos(pos), port.entries(), port.entryData());
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 64));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(port.entries().getItem(0).getCount() == 1 && menu.getCarried().getCount() == 64,
                "Ghost registration consumed or copied a physical stack");
        h.assertFalse(menu.slots.getFirst().mayPickup(player), "Ghost sample could be picked up");
        h.assertFalse(menu.canTakeItemForPickAll(new ItemStack(Items.IRON_INGOT), menu.slots.getFirst()), "Double-click could collect a ghost");
        port.shiftRank(0, 100); port.cycleMode(0);
        h.assertTrue(menu.rank(0) == 9 && menu.mode(0) == SupplyPortBlockEntity.MODE_TAKE, "Rank/mode was not synchronized");
        player.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 32));
        menu.quickMoveStack(player, SupplyPortBlockEntity.ENTRIES);
        menu.quickMoveStack(player, SupplyPortBlockEntity.ENTRIES);
        h.assertTrue(port.entries().getItem(1).is(Items.GOLD_INGOT) && port.entries().getItem(2).isEmpty()
                && player.getInventory().getItem(9).getCount() == 32, "Shift-click consumed stock or duplicated a registration");
        menu.quickMoveStack(player, 0);
        h.assertTrue(port.entries().getItem(0).isEmpty() && menu.rank(0) == 1 && menu.mode(0) == SupplyPortBlockEntity.MODE_BOTH,
                "Removing a sample retained its priority settings");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void supplyMenuRejectsInvalidButtonAndSlotIndices(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        SupplyPortBlockEntity port = h.getBlockEntity(pos);
        var player = TestScope.player(h);
        var menu = new SupplyPortMenu(1, player.getInventory(), h.absolutePos(pos), port.entries(), port.entryData());
        port.entries().setItem(0, new ItemStack(Items.IRON_INGOT));
        for (int button : new int[] {-1, -3, 24, Integer.MAX_VALUE, Integer.MIN_VALUE})
            h.assertFalse(menu.clickMenuButton(player, button), "Invalid menu button accepted");
        for (int slot : new int[] {-1, menu.slots.size(), Integer.MAX_VALUE})
            h.assertTrue(menu.quickMoveStack(player, slot).isEmpty(), "Invalid slot returned a stack");
        h.assertTrue(menu.mode(0) == SupplyPortBlockEntity.MODE_BOTH && menu.rank(0) == 1,
                "Invalid button changed a real entry");
        h.succeed();
    }
}
