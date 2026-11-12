package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.dye.RgbDyeStationBlockEntity;
import io.github.genichimaruo.singulo.dye.RgbDyeStationMenu;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class DyeStationGameTests {
    private static final BlockPos AT = new BlockPos(2, 1, 2);

    /** 使うインク: 純色は色みだけ、灰色は白と黒だけ、白は白だけ。各色 4 目盛りまで。 */
    @GameTest(template = "empty")
    public static void dyeStationCostFollowsTheColor(GameTestHelper h) {
        h.assertTrue(Arrays.equals(RgbDyeStationBlockEntity.cost(0xFF0000), new int[] {4, 0, 0, 0, 0}), "pure red " + Arrays.toString(RgbDyeStationBlockEntity.cost(0xFF0000)));
        h.assertTrue(Arrays.equals(RgbDyeStationBlockEntity.cost(0xFFFF00), new int[] {4, 4, 0, 0, 0}), "yellow");
        h.assertTrue(Arrays.equals(RgbDyeStationBlockEntity.cost(0xFFFFFF), new int[] {0, 0, 0, 4, 0}), "white");
        h.assertTrue(Arrays.equals(RgbDyeStationBlockEntity.cost(0x000000), new int[] {0, 0, 0, 0, 4}), "black");
        h.assertTrue(Arrays.equals(RgbDyeStationBlockEntity.cost(0x808080), new int[] {0, 0, 0, 2, 2}), "gray " + Arrays.toString(RgbDyeStationBlockEntity.cost(0x808080)));
        h.succeed();
    }

    /** 染料はインクになり、選んだ色に染めるとインクが減って色が付く。足りなければ染めない。壊すとインクの分の染料が戻る。 */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dyeStationTurnsDyesIntoInkAndDyes(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.RGB_DYE_STATION.get());
        RgbDyeStationBlockEntity station = h.getBlockEntity(AT);
        var items = station.items();
        h.assertFalse(RgbDyeStationBlockEntity.accepts(RgbDyeStationBlockEntity.RED, new ItemStack(Items.BLUE_DYE)), "Red slot accepted blue dye");
        h.assertFalse(RgbDyeStationBlockEntity.accepts(RgbDyeStationBlockEntity.TARGET, new ItemStack(Items.STONE)), "Target accepted stone");
        items.setItem(RgbDyeStationBlockEntity.RED, new ItemStack(Items.RED_DYE, 2));
        items.setItem(RgbDyeStationBlockEntity.TARGET, new ItemStack(Items.LEATHER_HELMET));
        h.runAtTickTime(5, () -> {
            h.assertTrue(station.ink(RgbDyeStationBlockEntity.RED) == 16 && items.getItem(RgbDyeStationBlockEntity.RED).isEmpty(),
                    "Red dye did not become ink: " + station.ink(RgbDyeStationBlockEntity.RED));
            var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
            var menu = new RgbDyeStationMenu(1, player.getInventory(), h.absolutePos(AT), items, station.data());
            // 黄色は緑のインクがないので染められない
            menu.clickMenuButton(player, RgbDyeStationMenu.SET_COLOR | 0xFFFF00);
            h.assertFalse(menu.clickMenuButton(player, RgbDyeStationMenu.APPLY), "Dyed without green ink");
            menu.clickMenuButton(player, RgbDyeStationMenu.SET_COLOR | 0xFF0000);
            h.assertTrue(menu.color() == 0xFF0000, "Menu does not show the chosen color");
            h.assertTrue(menu.clickMenuButton(player, RgbDyeStationMenu.APPLY), "Could not dye red");
            var dyed = items.getItem(RgbDyeStationBlockEntity.TARGET).get(DataComponents.DYED_COLOR);
            h.assertTrue(dyed != null && dyed.rgb() == 0xFF0000, "Helmet not dyed red");
            h.assertTrue(station.ink(RgbDyeStationBlockEntity.RED) == 12, "Red ink not used: " + station.ink(RgbDyeStationBlockEntity.RED));
            h.getLevel().destroyBlock(h.absolutePos(AT), false);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(AT)).inflate(2));
            int red = drops.stream().filter(e -> e.getItem().is(Items.RED_DYE)).mapToInt(e -> e.getItem().getCount()).sum();
            boolean helmet = drops.stream().anyMatch(e -> e.getItem().is(Items.LEATHER_HELMET));
            h.assertTrue(red == 1 && helmet, "Breaking returned " + red + " red dye, helmet " + helmet);
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            h.succeed();
        });
    }
}
