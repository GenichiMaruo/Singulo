package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.*;
import io.github.genichimaruo.singulo.android.work.*;
import io.github.genichimaruo.singulo.registry.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only GUI smoke harness. Included only through tools/review/gui.gradle. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class GuiReviewHarness {
    private static final BlockPos STATION = new BlockPos(0, 80, 0), CONSOLE = new BlockPos(3, 80, 0),
            RACK = new BlockPos(4, 80, 0), DELIVERY = new BlockPos(5, 80, 0), CHEST = new BlockPos(3, 80, 1);
    private static int ticks, phase;
    private static UUID androidId;
    private static volatile boolean prepared;
    private static int ready;
    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(name)));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 180) {
            throw new IllegalStateException("GUI review timed out");
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (phase == 0) {
            phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc));
            return;
        }
        if (!prepared || ++ready % 30 != 0) {
            return;
        }
        switch (phase) {
            case 1 -> { openAndroid(mc); phase++; }
            case 2 -> {
                require(mc, AndroidScreen.class);
                capture(mc, "android-status");
                ((AndroidMenu) mc.player.containerMenu).setPage(AndroidMenu.PAGE_GEAR);
                phase++;
            }
            case 3 -> {
                capture(mc, "android-gear");
                ((AndroidMenu) mc.player.containerMenu).setPage(AndroidMenu.PAGE_DECOR);
                phase++;
            }
            case 4 -> { capture(mc, "android-decor"); openBlock(mc, STATION); phase++; }
            case 5 -> {
                require(mc, AndroidStationScreen.class);
                capture(mc, "station");
                openBlock(mc, CONSOLE);
                phase++;
            }
            case 6 -> {
                require(mc, WorkConsoleScreen.class);
                capture(mc, "console-orders");
                ((WorkConsoleScreen) mc.screen).showRecordTab();
                phase++;
            }
            case 7 -> {
                capture(mc, "console-record-crafting");
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, WorkConsoleMenu.BUTTON_MODE);
                phase++;
            }
            case 8 -> {
                capture(mc, "console-record-processing");
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, WorkConsoleMenu.BUTTON_MODE);
                phase++;
            }
            case 9 -> {
                capture(mc, "console-record-procedure");
                var method = WorkConsoleScreen.class.getDeclaredMethod("openPicking", ItemStack.class);
                method.setAccessible(true);
                method.invoke(mc.screen, new ItemStack(Items.OAK_PLANKS));
                phase++;
            }
            case 10 -> { capture(mc, "console-order-confirmation"); openBlock(mc, RACK); phase++; }
            case 11 -> {
                require(mc, RecipeRackScreen.class);
                capture(mc, "recipe-rack");
                openBlock(mc, DELIVERY);
                phase++;
            }
            case 12 -> {
                require(mc, DeliveryPortScreen.class);
                capture(mc, "delivery-port");
                Singulo.LOGGER.info("SINGULO_GUI_COMPLETE 11");
                phase++;
                mc.stop();
            }
            default -> {}
        }
    }

    private static void require(Minecraft mc, Class<?> type) {
        if (!type.isInstance(mc.screen)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + ", got " + mc.screen);
        }
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        Path dir = Path.of(System.getProperty("singulo.guiReviewOutput"));
        Files.createDirectories(dir);
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(dir.resolve(name + ".png"));
        }
        Singulo.LOGGER.info("SINGULO_GUI_CAPTURE {}", name);
    }

    private static void openAndroid(Minecraft mc) {
        UUID playerId = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            RuinAndroid android = (RuinAndroid) player.serverLevel().getEntity(androidId);
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AndroidMenu(id, inv, android),
                    android.nickname()), buf -> buf.writeVarInt(android.getId()));
        });
    }

    private static void openBlock(Minecraft mc, BlockPos pos) {
        UUID id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            var be = player.serverLevel().getBlockEntity(pos);
            ((io.github.genichimaruo.singulo.machine.AbstractMachineBlock.MenuOpener) be).openMenu(player);
        });
    }

    private static void prepare(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = player.serverLevel();
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(2.5, 80, 2.5);
        for (BlockPos p : BlockPos.betweenClosed(-2, 79, -2, 8, 79, 5)) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(STATION, SinguloBlocks.ANDROID_STATION.get().defaultBlockState());
        level.setBlockAndUpdate(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get().defaultBlockState());
        level.setBlockAndUpdate(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get().defaultBlockState());
        level.setBlockAndUpdate(DELIVERY, SinguloBlocks.ANDROID_DELIVERY_PORT.get().defaultBlockState());
        level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        AndroidStationBlockEntity station = (AndroidStationBlockEntity) level.getBlockEntity(STATION);
        station.setOwner(player.getUUID());
        station.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.BLADE.memoryId()));
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PRODUCTION, item("android_production_upgrade"));
        BladeAndroid android = SinguloEntities.BLADE_ANDROID.get().create(level);
        android.makeGuard(player.getUUID(), STATION, RuinAndroid.MODE_GUARD, 30);
        android.setNoAi(true);
        android.moveTo(1.5, 80, 1.5);
        android.inventory().setItem(RuinAndroid.SLOT_BATTERY, item("android_battery_upgrade").copyWithCount(2));
        android.inventory().setItem(RuinAndroid.SLOT_BACKPACK, item("android_backpack_upgrade"));
        android.inventory().setItem(RuinAndroid.SLOT_MINING, item("android_mining_upgrade"));
        android.inventory().setItem(RuinAndroid.SLOT_FELLING, item("android_felling_upgrade"));
        android.inventory().setItem(RuinAndroid.SLOT_TANK, item("android_tank_upgrade"));
        android.inventory().setItem(RuinAndroid.SLOT_DECOR, item("android_ribbon"));
        android.inventory().setItem(RuinAndroid.SLOT_DECOR + 1, item("android_cape"));
        android.inventory().setItem(RuinAndroid.SLOT_DECOR + 2, item("android_book"));
        android.fillTanks(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 24000), false);
        android.fillTanks(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 16000), false);
        level.addFreshEntity(android);
        androidId = android.getUUID();
        var console = (WorkConsoleBlockEntity) level.getBlockEntity(CONSOLE);
        console.setOwner(player.getUUID());
        ItemStack link = item("work_location_card");
        WorkLocationCardItem.record(link, level, CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        ((WorkLinkedBlockEntity) level.getBlockEntity(RACK)).useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        ((WorkLinkedBlockEntity) level.getBlockEntity(DELIVERY)).useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_LOG));
        ItemStack card = item("recipe_card");
        new RecipeData(grid, new ItemStack(Items.OAK_PLANKS, 4)).write(card, level.registryAccess());
        ItemStack table = item("work_location_card");
        WorkLocationCardItem.record(table, level, new BlockPos(6, 80, 0), Blocks.CRAFTING_TABLE);
        level.setBlockAndUpdate(new BlockPos(6, 80, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
        var rack = (RecipeRackBlockEntity) level.getBlockEntity(RACK);
        rack.items().setStackInSlot(0, table);
        rack.items().setStackInSlot(1, card);
        console.setGrid(grid);
        console.encoder().setItem(WorkConsoleBlockEntity.STOCK, item("recipe_card").copyWithCount(4));
        prepared = true;
    }
}
