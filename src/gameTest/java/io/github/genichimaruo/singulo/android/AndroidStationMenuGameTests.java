package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidStationMenuGameTests {
    @GameTest(template = "empty")
    public static void stationMenuShowsCurrentPrintCost(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.ANDROID_STATION.get());
        var station = (AndroidStationBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, pos);
        var player = TestScope.player(helper);
        var server = station.createMenu(1, player.getInventory(), player);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBlockPos(station.getBlockPos());
            var client = AndroidStationMenu.client(1, player.getInventory(), buffer);
            server.addSlotListener(new ContainerListener() {
                @Override
                public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}

                @Override
                public void dataChanged(AbstractContainerMenu menu, int slot, int value) {
                    buffer.clear();
                    new ClientboundContainerSetDataPacket(menu.containerId, slot, value).write(buffer);
                    var packet = new ClientboundContainerSetDataPacket(buffer);
                    client.setData(packet.getId(), packet.getValue());
                }
            });

            station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.BLADE.memoryId()));
            assertPrintCost(helper, server, client, 40_000);
            station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.GUNNER.memoryId()));
            assertPrintCost(helper, server, client, 120_000);
            station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PRINT_UNIT, item("android_factory_print_unit"));
            assertPrintCost(helper, server, client, 60_000);
            station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.BLADE.memoryId()));
            assertPrintCost(helper, server, client, 20_000);
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    private static void assertPrintCost(GameTestHelper helper, AbstractContainerMenu server, AndroidStationMenu client, int expected) {
        server.broadcastChanges();
        int actual = client.value(AndroidStationBlockEntity.D_PRINT_FE);
        helper.assertTrue(actual == expected, "Expected " + expected + " FE on the client, got " + actual);
    }
}
