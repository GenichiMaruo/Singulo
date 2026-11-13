package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.dye.RgbDyeStationBlockEntity;
import io.github.genichimaruo.singulo.dye.RgbDyeStationMenu;
import io.github.genichimaruo.singulo.food.FoodPrinterMenu;
import io.github.genichimaruo.singulo.network.LegacyMenuButtonPayload;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyMenuPacketGameTests {
    @GameTest(template = "empty")
    public static void legacyMenuPacketPreservesFullWidthActions(GameTestHelper h) {
        for (int action : new int[] {RgbDyeStationMenu.SET_COLOR | 0xA1B2C3,
                RgbDyeStationMenu.SET_COLOR | 0xFFFFFF, RgbDyeStationMenu.APPLY,
                FoodPrinterMenu.BUTTON_AUTO, FoodPrinterMenu.BUTTON_POWER}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var original = new LegacyMenuButtonPayload(173, action);
                LegacyMenuButtonPayload.encode(original, buffer);
                h.assertTrue(original.equals(LegacyMenuButtonPayload.decode(buffer)), "Action was truncated: " + action);
                h.assertTrue(buffer.readableBytes() == 0, "Unread packet bytes");
            } finally {
                buffer.release();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyMenuPacketValidatesActiveMenu(GameTestHelper h) {
        var at = new BlockPos(2, 1, 2);
        h.setBlock(at, SinguloBlocks.RGB_DYE_STATION.get());
        RgbDyeStationBlockEntity station = (RgbDyeStationBlockEntity) h.getBlockEntity(at);
        var player = TestScope.player(h);
        var menu = new RgbDyeStationMenu(73, player.getInventory(), h.absolutePos(at), station.items(), station.data());
        player.containerMenu = menu;
        int before = menu.color();
        h.assertFalse(LegacyMenuButtonPayload.apply(player,
                new LegacyMenuButtonPayload(74, RgbDyeStationMenu.SET_COLOR | 0xA1B2C3)), "Wrong container accepted");
        h.assertTrue(menu.color() == before, "Rejected action changed color");
        h.assertTrue(LegacyMenuButtonPayload.apply(player,
                new LegacyMenuButtonPayload(73, RgbDyeStationMenu.SET_COLOR | 0xA1B2C3)), "Valid RGB action rejected");
        h.assertTrue(menu.color() == 0xA1B2C3, "RGB bits lost on server");
        player.containerMenu = player.inventoryMenu;
        h.assertFalse(LegacyMenuButtonPayload.apply(player,
                new LegacyMenuButtonPayload(player.inventoryMenu.containerId, RgbDyeStationMenu.SET_COLOR | 0xFFFFFF)),
                "Unrelated menu accepted action");
        h.assertFalse(LegacyMenuButtonPayload.apply(null, new LegacyMenuButtonPayload(73, 0)), "Missing sender accepted");
        h.succeed();
    }
}