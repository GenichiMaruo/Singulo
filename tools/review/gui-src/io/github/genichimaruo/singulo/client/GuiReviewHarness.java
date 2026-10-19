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
    private static int lookupChapter, lookupPage;
    private static String bootScreen = "";
    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(name)));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 180) {
            capture(mc, "diagnostic-timeout");
            throw new IllegalStateException("GUI review timed out: phase=" + phase + " screen=" + mc.screen + " overlay=" + mc.getOverlay());
        }
        if (phase == 0 && mc.getOverlay() == null && mc.screen != null) {
            String screen = mc.screen.getClass().getName();
            if (!screen.equals(bootScreen)) {
                bootScreen = screen;
                Singulo.LOGGER.info("SINGULO_GUI_BOOT {} {}", screen, mc.screen.getTitle().getString());
            }
            // The disposable GameTest save enables experimental feature packs.
            // Forge asks for confirmation; accept only this specific review prompt.
            if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                    && mc.screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title
                    && "selectWorld.backupQuestion.experimental".equals(title.getKey())) {
                for (var child : mc.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                            && "selectWorld.backupJoinSkipButton".equals(label.getKey()) && button.active) {
                        button.onPress();
                        return;
                    }
                }
            }
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (phase == 29 && Boolean.getBoolean("singulo.machineGuiReview")) {
            MachineGuiReviewHarness.tick(mc);
            return;
        }
        // Opening menus and resizing can move the real cursor back onto a tooltip.
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 20, 20);
        if (phase == 0) {
            phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc));
            return;
        }
        if (!prepared || mc.getOverlay() != null || ++ready % 30 != 0) {
            return;
        }
        switch (phase) {
            case 1 -> {
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 20, 20);
                openAndroid(mc);
                phase++;
            }
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
                verifySearch(mc);
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
                int x = (mc.getWindow().getGuiScaledWidth() - WorkConsoleMenu.WIDTH) / 2;
                int y = (mc.getWindow().getGuiScaledHeight() - WorkConsoleMenu.HEIGHT) / 2;
                mc.screen.mouseClicked(x + 110, y + 42, 0);
                if (!(Boolean) field(mc.screen, "fresh")) throw new IllegalStateException("New order toggle did not enable");
                mc.screen.mouseClicked(x + 110, y + 42, 0);
                if ((Boolean) field(mc.screen, "fresh")) throw new IllegalStateException("New order toggle did not disable");
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
                mc.getSingleplayerServer().execute(() -> detailFixture(mc));
                openBlock(mc, CONSOLE);
                phase++;
            }
            case 13 -> {
                require(mc, WorkConsoleScreen.class);
                setField(mc.screen, "tab", 1);
                setField(mc.screen, "detail", 999);
                phase++;
            }
            case 14 -> {
                capture(mc, "console-detail");
                int left = (mc.getWindow().getGuiScaledWidth() - WorkConsoleMenu.WIDTH) / 2;
                int top = (mc.getWindow().getGuiScaledHeight() - WorkConsoleMenu.HEIGHT) / 2;
                mc.screen.mouseScrolled(left + 20, top + 50, 0, -1);
                mc.screen.mouseScrolled(left + 200, top + 50, 0, -1);
                phase++;
            }
            case 15 -> {
                capture(mc, "console-detail-scrolled");
                if ((Integer) field(mc.screen, "detailScroll") != 1 || (Integer) field(mc.screen, "workerScroll") != 1) {
                    throw new IllegalStateException("Detail columns did not scroll independently");
                }
                openBlock(mc, STATION);
                phase++;
            }
            case 16 -> {
                require(mc, AndroidStationScreen.class);
                mc.getWindow().setWindowed(854, 480);
                phase++;
            }
            case 17 -> {
                capture(mc, "station-ja-854x480");
                openAndroid(mc);
                phase = 19;
            }
            case 19 -> {
                require(mc, AndroidScreen.class);
                ((AndroidMenu) mc.player.containerMenu).setPage(AndroidMenu.PAGE_LOG);
                phase++;
            }
            case 20 -> {
                capture(mc, "android-log-ja-854x480");
                openBlock(mc, STATION);
                phase++;
            }
            case 21 -> {
                require(mc, AndroidStationScreen.class);
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, AndroidStationBlockEntity.BUTTON_MODE_PATROL);
                phase++;
            }
            case 22 -> {
                if (!((AndroidStationMenu) mc.player.containerMenu).patrolling()) {
                    throw new IllegalStateException("Patrol button did not reach server");
                }
                capture(mc, "station-patrol-ja-854x480");
                mc.screen.mouseClicked((mc.getWindow().getGuiScaledWidth() - AndroidStationMenu.WIDTH) / 2 + 190,
                        (mc.getWindow().getGuiScaledHeight() - AndroidStationMenu.HEIGHT) / 2 + 8, 0);
                phase++;
            }
            case 23 -> {
                if (!((AndroidStationMenu) mc.player.containerMenu).ruleView()) {
                    throw new IllegalStateException("Route page button did not open route slots");
                }
                capture(mc, "station-rules-ja-854x480");
                ((AndroidStationMenu) mc.player.containerMenu).setRuleView(false);
                phase = 99;
                mc.getLanguageManager().setSelected("en_us");
                mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                    ready = 0;
                    phase = 18;
                }));
            }
            case 18 -> {
                require(mc, AndroidStationScreen.class);
                capture(mc, "station-patrol-en-854x480");
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, AndroidStationBlockEntity.BUTTON_MODE_GUARD);
                phase = 26;
            }
            case 26 -> {
                if (((AndroidStationMenu) mc.player.containerMenu).patrolling()) {
                    throw new IllegalStateException("Return from patrol did not reach server");
                }
                capture(mc, "station-en-854x480");
                openAndroid(mc);
                phase = 24;
            }
            case 24 -> {
                require(mc, AndroidScreen.class);
                ((AndroidMenu) mc.player.containerMenu).setPage(AndroidMenu.PAGE_LOG);
                phase++;
            }
            case 25 -> {
                capture(mc, "android-log-en-854x480");
                if (!HandbookScreen.lookup(item("android_backpack_upgrade"))) {
                    throw new IllegalStateException("Handbook item lookup did not open");
                }
                phase = 27;
            }
            case 27 -> {
                require(mc, HandbookScreen.class);
                lookupChapter = (Integer) field(mc.screen, "chapter");
                lookupPage = (Integer) field(mc.screen, "page");
                if (lookupChapter == 0 && lookupPage == 0) {
                    throw new IllegalStateException("Item lookup stayed on the default page");
                }
                capture(mc, "handbook-item-lookup-en-854x480");
                mc.getWindow().setWindowed(1280, 800);
                phase = 28;
            }
            case 28 -> {
                require(mc, HandbookScreen.class);
                if ((Integer) field(mc.screen, "chapter") != lookupChapter || (Integer) field(mc.screen, "page") != lookupPage) {
                    throw new IllegalStateException("Handbook item lookup was lost after window resize");
                }
                var box = (net.minecraft.client.gui.components.EditBox) field(mc.screen, "searchBox");
                box.setValue("zzzz_singulo_no_matching_page");
                if (!((List<?>) field(mc.screen, "results")).isEmpty()) {
                    throw new IllegalStateException("Handbook no-results search failed");
                }
                box.setValue(item("android_backpack_upgrade").getHoverName().getString());
                if (((List<?>) field(mc.screen, "results")).isEmpty()) {
                    throw new IllegalStateException("Handbook did not find the backpack page");
                }
                box.setFocused(true);
                mc.screen.setFocused(box);
                mc.screen.keyPressed(262, 0, 0);
                if ((Integer) field(mc.screen, "page") != lookupPage) {
                    throw new IllegalStateException("Typing in handbook search turned the page");
                }
                capture(mc, "handbook-search-en-1280x800");
                Singulo.LOGGER.info("SINGULO_HANDBOOK_SEARCH_COMPLETE");
                Singulo.LOGGER.info("SINGULO_GUI_COMPLETE 22");
                phase = 29;
                if (Boolean.getBoolean("singulo.machineGuiReview")) MachineGuiReviewHarness.start(mc);
                else mc.stop();
            }
            default -> {}
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** Exercise the real search widget, inventory hotkey guard and sort buttons on both loaders. */
    private static void verifySearch(Minecraft mc) throws Exception {
        WorkConsoleScreen screen = (WorkConsoleScreen) mc.screen;
        var box = (net.minecraft.client.gui.components.EditBox) field(screen, "searchBox");
        var entries = WorkConsoleScreen.class.getDeclaredMethod("visibleEntries");
        entries.setAccessible(true);
        box.setValue("");
        int initial = ((List<?>) entries.invoke(screen)).size();
        if (initial == 0) throw new IllegalStateException("Search fixture has no entries");
        box.setValue("singulo_review_no_matching_item_9823");
        if (!((List<?>) entries.invoke(screen)).isEmpty()) throw new IllegalStateException("Search did not filter entries");
        box.setValue("@minecraft");
        if (((List<?>) entries.invoke(screen)).isEmpty()) throw new IllegalStateException("Mod search removed vanilla stock");
        box.setFocused(true);
        screen.keyPressed(69, 0, 0);
        if (mc.screen != screen) throw new IllegalStateException("Inventory hotkey closed a focused search");
        box.setFocused(false);
        box.setValue("");
        int left = (mc.getWindow().getGuiScaledWidth() - WorkConsoleMenu.WIDTH) / 2;
        int top = (mc.getWindow().getGuiScaledHeight() - WorkConsoleMenu.HEIGHT) / 2;
        for (int i = 0; i < 5; i++) {
            screen.mouseClicked(left + 130, top + 128, 0);
            if (((List<?>) entries.invoke(screen)).size() != initial) throw new IllegalStateException("Sort lost entries");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
            String previous = io.github.genichimaruo.singulo.compat.jei.JeiSearch.get();
            if (previous == null) throw new IllegalStateException("JEI runtime unavailable in search review");
            boolean sync = io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_JEI_SYNC.get();
            try {
                io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_JEI_SYNC.set(true);
                box.setValue("@minecraft oak");
                if (!box.getValue().equals(io.github.genichimaruo.singulo.compat.jei.JeiSearch.get())) {
                    throw new IllegalStateException("Console search did not update JEI");
                }
                io.github.genichimaruo.singulo.compat.jei.JeiSearch.set("minecraft:oak_log");
                screen.containerTick();
                if (!"minecraft:oak_log".equals(box.getValue())) throw new IllegalStateException("JEI search did not update console");
            } finally {
                io.github.genichimaruo.singulo.generated.ClientConfig.WORK_ORDER_JEI_SYNC.set(sync);
                io.github.genichimaruo.singulo.compat.jei.JeiSearch.set(previous);
                box.setValue("");
            }
            Singulo.LOGGER.info("SINGULO_JEI_SYNC_COMPLETE");
        }
        Singulo.LOGGER.info("SINGULO_SEARCH_COMPLETE");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Controlled UI fixture: six steps and six worker rows, including unloaded workers. */
    private static void detailFixture(Minecraft mc) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = player.serverLevel();
        var t = new net.minecraft.nbt.CompoundTag();
        t.putInt("id", 999);
        t.put("target", new ItemStack(Items.OAK_PLANKS).save(level.registryAccess()));
        t.putLong("count", 24);
        var steps = new net.minecraft.nbt.ListTag();
        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_LOG));
        var recipe = new RecipeData(grid, new ItemStack(Items.OAK_PLANKS, 4));
        for (int i = 0; i < 6; i++) {
            var step = new net.minecraft.nbt.CompoundTag();
            step.put("recipe", recipe.save(level.registryAccess()));
            step.put("location", net.minecraft.nbt.NbtUtils.writeBlockPos(CONSOLE));
            step.putString("block", "minecraft:crafting_table");
            step.putLong("total", i + 1);
            step.putLong("done", i);
            steps.add(step);
        }
        t.put("steps", steps);
        var order = WorkOrder.load(t, level.registryAccess());
        order.workers.add(androidId);
        for (int i = 0; i < 5; i++) {
            order.workers.add(new UUID(0, i + 1));
        }
        ((WorkConsoleBlockEntity) level.getBlockEntity(CONSOLE)).orders().add(order);
    }

    private static void require(Minecraft mc, Class<?> type) {
        if (!type.isInstance(mc.screen)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + ", got " + mc.screen);
        }
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        if (mc.getOverlay() != null) {
            throw new IllegalStateException("Capture obscured by loading overlay: " + name);
        }
        if (mc.screen instanceof AndroidStationScreen
                && (AndroidStationMenu.WIDTH > mc.getWindow().getGuiScaledWidth()
                || AndroidStationMenu.HEIGHT > mc.getWindow().getGuiScaledHeight())) {
            throw new IllegalStateException("Station exceeds GUI viewport: " + name);
        }
        Path dir = Path.of(System.getProperty("singulo.guiReviewOutput"));
        Files.createDirectories(dir);
        org.lwjgl.opengl.GL11.glFinish();
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(dir.resolve(name + ".png"));
            if (name.startsWith("android-")) {
                var slot = mc.player.containerMenu.getSlot(RuinAndroid.INVENTORY_SIZE);
                double scale = mc.getWindow().getGuiScale();
                int left = (mc.getWindow().getGuiScaledWidth() - AndroidMenu.WIDTH) / 2;
                int top = (mc.getWindow().getGuiScaledHeight() - AndroidMenu.HEIGHT) / 2;
                if (slot.x != AndroidMenu.INV_X || slot.y != AndroidMenu.INV_Y) {
                    throw new IllegalStateException("Unexpected first inventory slot: " + slot.x + "," + slot.y);
                }
                int pixel = screenshot.getPixelRGBA((int) ((left + AndroidMenu.INV_X + 8) * scale),
                        (int) ((top + AndroidMenu.INV_Y + 8) * scale));
                for (int i = RuinAndroid.INVENTORY_SIZE; i < mc.player.containerMenu.slots.size(); i++) {
                    if (!mc.player.containerMenu.getSlot(i).isActive()) {
                        throw new IllegalStateException("Inactive player inventory slot in " + name);
                    }
                }
                long active = mc.player.containerMenu.slots.stream().filter(net.minecraft.world.inventory.Slot::isActive).count();
                Singulo.LOGGER.info("SINGULO_GUI_INVENTORY {} active={} pixel={}", name, active, Integer.toHexString(pixel));
                if (pixel != 0xFF988F8B) {
                    throw new IllegalStateException("Player inventory background missing in " + name);
                }
            }
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
        try {
            var patrolField = RuinAndroid.class.getDeclaredField("patrol");
            patrolField.setAccessible(true);
            Object patrol = patrolField.get(android);
            var onKill = patrol.getClass().getDeclaredMethod("onKill", net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.world.entity.LivingEntity.class);
            onKill.setAccessible(true);
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.setPos(android.position());
            onKill.invoke(patrol, level, zombie);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not populate review patrol log", e);
        }
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
