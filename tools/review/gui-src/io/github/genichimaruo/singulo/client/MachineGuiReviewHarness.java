package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Real font/resource-pack rendering of deterministic menu fixtures, in all shipped languages. Development only. */
@net.neoforged.fml.common.EventBusSubscriber(modid = Singulo.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class MachineGuiReviewHarness {
    private static final String[] LANGUAGES = {"ja_jp", "en_us", "zh_cn", "zh_tw", "ko_kr"};
    private static final boolean COMBINED = Boolean.getBoolean("singulo.sidesGuiReview")
            && Boolean.getBoolean("singulo.selectorGuiReview");
    private static boolean SIDES = !COMBINED && Boolean.getBoolean("singulo.sidesGuiReview");
    private static boolean SELECTOR = !COMBINED && Boolean.getBoolean("singulo.selectorGuiReview");
    private static int SCREENS = SELECTOR ? 6 : SIDES ? 1 : MachineType.values().length + 12;
    private static final List<String> metrics = new ArrayList<>();
    private static int language, screen, wait;
    private static boolean shown;
    private static int hoverX = -1, hoverY;

    static void start(Minecraft mc) {
        mc.player.closeContainer();
        mc.getWindow().setWindowed(854, 480);
        selectLanguage(mc);
    }

    private static void selectLanguage(Minecraft mc) {
        mc.setScreen(null);
        mc.getLanguageManager().setSelected(LANGUAGES[language]);
        mc.options.languageCode = LANGUAGES[language];
        // All fixtures use the same installed font resources. Reload translations synchronously,
        // without repeatedly destroying font atlases while JEI rebuilds its ingredient filter.
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        wait = 0;
    }

    static void tick(Minecraft mc) throws Exception {
        if (mc.getOverlay() != null || ++wait % 6 != 0) return;
        if (!shown) {
            show(mc);
            shown = true;
            return;
        }
        Path output = Path.of(System.getProperty("singulo.guiReviewOutput"));
        String name = (SELECTOR ? "selector-" : SIDES ? "sides-" : "machine-") + LANGUAGES[language] + "-" + String.format("%02d", screen);
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(output.resolve(name + ".png"));
        }
        Singulo.LOGGER.info("SINGULO_MACHINE_GUI_CAPTURE {}", name);
        shown = false;
        if (++screen < SCREENS) return;
        screen = 0;
        if (++language < LANGUAGES.length) {selectLanguage(mc); return;}
        String prefix = SELECTOR ? "selector" : SIDES ? "sides" : "machine";
        Files.write(output.getParent().resolve(prefix + "-font-metrics.tsv"), metrics);
        Singulo.LOGGER.info("SINGULO_MACHINE_GUI_COMPLETE {}", SCREENS * LANGUAGES.length);
        if (COMBINED && !SELECTOR) {
            SELECTOR = SIDES;
            SIDES = !SIDES;
            SCREENS = SELECTOR ? 6 : 1;
            language = 0;
            metrics.clear();
            selectLanguage(mc);
        } else {
            mc.stop();
        }
    }

    private static void value(SyncedInts data, int index, int value) {
        data.set(index * 2, value & 0xffff);
        data.set(index * 2 + 1, value >>> 16);
    }

    private static void show(Minecraft mc) throws Exception {
        hoverX = -1;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 20, 20);
        int extra = screen - MachineType.values().length;
        if (screen < MachineType.values().length || extra >= 4 && extra <= 6) {
            MachineType type = extra == 4 ? MachineType.CATALYTIC_REACTOR : extra == 5 ? MachineType.PARTICLE_ACCELERATOR
                    : extra == 6 ? MachineType.ENTANGLEMENT_SYNTHESIZER : MachineType.values()[screen];
            if (SELECTOR) type = screen < 3 ? MachineType.PRECISION_ASSEMBLER : MachineType.SINGULARITY_ENCAPSULATOR;
            SyncedInts data = SyncedInts.client(MachineBlockEntity.syncedCount(type));
            value(data, MachineBlockEntity.D_STATUS, (extra == 4 || extra == 5 ? MachineBlockEntity.Status.IDLE : MachineBlockEntity.Status.NO_POWER).ordinal());
            value(data, MachineBlockEntity.D_STRUCTURE, type.isMultiblock() ? (type == MachineType.PARTICLE_ACCELERATOR ? 16 : 10) : -1);
            value(data, MachineBlockEntity.D_FLAGS, MachineBlockEntity.FLAG_ENABLED);
            if (extra == 5) value(data, MachineBlockEntity.D_STRUCTURE, 8);
            ItemStackHandler items = new ItemStackHandler(type.itemSlots());
            if (extra == 4) {
                items.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_COPPER, 4));
                value(data, MachineBlockEntity.D_TANKS, net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.world.level.material.Fluids.WATER) + 1);
                value(data, MachineBlockEntity.D_TANKS + 1, 250);
            } else if (extra == 5) {
                items.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("zinc_ingot"))));
                items.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("bismuth_ingot"))));
            }
            var ctor = MachineMenu.class.getDeclaredConstructor(int.class, net.minecraft.world.entity.player.Inventory.class,
                    MachineType.class, BlockPos.class, IItemHandler.class, SyncedInts.class, MachineBlockEntity.class);
            ctor.setAccessible(true);
            BlockPos reviewPos = SIDES ? mc.player.blockPosition().above(3) : BlockPos.ZERO;
            if (SIDES) {
                mc.level.setBlock(reviewPos, io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES.get(type).get().defaultBlockState(), 2);
                for (var direction : net.minecraft.core.Direction.values())
                    mc.level.setBlock(reviewPos.relative(direction), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 2);
                value(data, MachineBlockEntity.sidesIndex(type), new io.github.genichimaruo.singulo.machine.SideConfig().packed());
            }
            MachineMenu menu = ctor.newInstance(0, mc.player.getInventory(), type, reviewPos,
                    items, data, null);
            Component title = Component.translatable((type.isMultiblock() ? io.github.genichimaruo.singulo.registry.SinguloBlocks.CONTROLLERS
                    : io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES).get(type).get().getDescriptionId());
            // Multiblock menu titles use the machine name, not the controller item name.
            MachineScreen gui = new MachineScreen(menu, mc.player.getInventory(), title);
            mc.setScreen(gui);
            if (SELECTOR) {
                var entries = io.github.genichimaruo.singulo.machine.RecipeSelection.selectable(mc.level, type);
                if (screen % 3 == 0) {
                    value(data, MachineBlockEntity.D_SELECTED, entries.size());
                    hover(mc, menu.layout.width, menu.layout.selectX + 8, menu.layout.selectY + 8);
                } else {
                    var open = MachineScreen.class.getDeclaredField("selector");
                    open.setAccessible(true);
                    ((Popup) open.get(gui)).open = true;
                    var scroll = MachineScreen.class.getDeclaredField("selectorScroll");
                    scroll.setAccessible(true);
                    scroll.setInt(gui, screen % 3 == 2 ? Math.max(0, (entries.size() + 9) / 9 - 3) : 0);
                }
            }
            if (SIDES) {
                var open = MachineScreen.class.getDeclaredField("sides");
                open.setAccessible(true);
                ((Popup) open.get(gui)).open = true;
                var layout = MachineScreen.class.getDeclaredMethod("layoutPopups");
                layout.setAccessible(true);
                layout.invoke(gui);
                var quads = MachineScreen.class.getDeclaredMethod("cubeQuads");
                quads.setAccessible(true);
                @SuppressWarnings("unchecked") var faces = (java.util.List<SideCube.Quad>) quads.invoke(gui);
                var face = faces.getLast();
                hoverX = (int) face.cx(); hoverY = (int) face.cy();
                Singulo.LOGGER.info("SINGULO_SIDES_GUI {} adjacent={}", LANGUAGES[language],
                        Component.translatable("gui.singulo.sides.adjacent", net.minecraft.world.level.block.Blocks.CHEST.getName()).getString());
            }
            if (extra >= 4 && extra <= 6) hover(mc, menu.layout.width, extra == 6 ? 40 : menu.layout.width - 30, 10);
            int titleWidth = mc.font.width(title), statusWidth = mc.font.width(Component.translatable("gui.singulo.status.no_power"));
            metrics.add(LANGUAGES[language] + "\t" + type.id() + "\t" + titleWidth + "\t" + statusWidth + "\t" + menu.layout.width);
            for (var slot : menu.slots) {
                if (slot.x == menu.layout.upgradeX && slot.y == menu.layout.upgradeY) continue;   // 右レールの強化の段
                if (slot.x < 1 || slot.x + 16 >= menu.layout.width || slot.y < 16 || slot.y + 16 > MachineLayout.HEIGHT - 4)
                    throw new IllegalStateException("Slot outside panel: " + type + " " + slot.x + "," + slot.y);
            }
        } else if (extra == 8) {
            // SMES: 50M のうち 12.3M、受け取り 1.2K・送り出し 3.4K
            SyncedInts data = SyncedInts.client(SmesMenu.COUNT);
            value(data, SmesMenu.D_STORED, 12_300_000);
            value(data, SmesMenu.D_CAPACITY, 50_000_000);
            value(data, SmesMenu.D_IN, 1_200);
            value(data, SmesMenu.D_OUT, 3_400);
            value(data, SmesMenu.D_RATE, 100_000);
            mc.setScreen(new SmesScreen(new SmesMenu(0, mc.player.getInventory(), BlockPos.ZERO, data), mc.player.getInventory(),
                    Component.translatable("block.singulo.smes_cell")));
            metrics.add(LANGUAGES[language] + "	smes	0	0	176");
        } else if (extra == 9) {
            // 熱電発電機: 1300 K と 300 K、45 FE/t、液体窒素 800 mB
            SyncedInts data = SyncedInts.client(ThermoelectricGeneratorBlockEntity.COUNT);
            value(data, ThermoelectricGeneratorBlockEntity.D_HOT, 1300);
            value(data, ThermoelectricGeneratorBlockEntity.D_COLD, 300);
            value(data, ThermoelectricGeneratorBlockEntity.D_RATE_X100, 4500);
            value(data, ThermoelectricGeneratorBlockEntity.D_ENERGY, 40_000);
            value(data, ThermoelectricGeneratorBlockEntity.D_CAPACITY, 100_000);
            value(data, ThermoelectricGeneratorBlockEntity.D_FLUID, 800);
            mc.setScreen(new ThermoelectricGeneratorScreen(new ThermoelectricGeneratorMenu(0, BlockPos.ZERO, data), mc.player.getInventory(),
                    Component.translatable("block.singulo.thermoelectric_generator")));
            metrics.add(LANGUAGES[language] + "	thermo	0	0	176");
        } else if (extra == 10 || extra == 11) {
            // 触媒装置: 動いているシールド塔（半径・段階・許可証・消費・効果）と、触媒のないアンカー
            boolean shield = extra == 10;
            var kind = shield ? CatalystDeviceBlockEntity.Kind.SHIELD_TOWER : CatalystDeviceBlockEntity.Kind.WORLDLINE_ANCHOR;
            SyncedInts data = SyncedInts.client(CatalystDeviceBlockEntity.COUNT);
            value(data, CatalystDeviceBlockEntity.D_ENERGY, 640_000);
            value(data, CatalystDeviceBlockEntity.D_CAPACITY, 1_000_000);
            value(data, CatalystDeviceBlockEntity.D_STATUS, (shield ? CatalystDeviceBlockEntity.Status.RUNNING
                    : CatalystDeviceBlockEntity.Status.NO_CATALYST).ordinal());
            value(data, CatalystDeviceBlockEntity.D_VALUE, 32);
            value(data, CatalystDeviceBlockEntity.D_USAGE, 2_500);
            value(data, CatalystDeviceBlockEntity.D_SPEED_X100, 125);
            value(data, CatalystDeviceBlockEntity.D_WEAR_X100, 80);
            value(data, CatalystDeviceBlockEntity.D_EXTRA, 5);
            value(data, CatalystDeviceBlockEntity.D_ENABLED, 1);
            var menu = new CatalystDeviceMenu(0, mc.player.getInventory(), BlockPos.ZERO, kind, new ItemStackHandler(2), data);
            mc.setScreen(new CatalystDeviceScreen(menu, mc.player.getInventory(),
                    Component.translatable(shield ? "block.singulo.shield_tower_core" : "block.singulo.worldline_anchor_small")));
            metrics.add(LANGUAGES[language] + "	catalyst-" + extra + "	0	0	176");
        } else {
            int variant = extra == 7 ? 2 : extra, kind = variant / 2;
            SyncedInts data = SyncedInts.client(GeneratorSlotsMenu.COUNT);
            value(data, GeneratorSlotsMenu.D_CAPACITY, 400_000);
            value(data, GeneratorSlotsMenu.D_ENERGY, 399_999);
            value(data, GeneratorSlotsMenu.D_FLUID, kind == 0 ? 31_999 : 7_999);
            value(data, GeneratorSlotsMenu.D_FLUID_CAP, kind == 0 ? 32_000 : 8_000);
            value(data, GeneratorSlotsMenu.D_HOT, kind == 0 && variant % 2 == 0 ? 1300 : 0);
            value(data, GeneratorSlotsMenu.D_RATE_X100, variant % 2 != 0 ? 0 : kind == 0 ? 450_000 : 400_000);
            value(data, GeneratorSlotsMenu.D_EXTRA, kind == 0 ? (variant % 2 == 0 ? 90 : 0) : (variant % 2 == 0 ? 1000 : -1));
            value(data, GeneratorSlotsMenu.D_STANDBY, variant % 2);
            GeneratorSlotsMenu menu = new GeneratorSlotsMenu(0, mc.player.getInventory(), BlockPos.ZERO, kind,
                    new ItemStackHandler(2), data);
            Component title = Component.translatable("block.singulo." + (kind == 0 ? "cryogenic_turbine" : "muon_fusion_reactor"));
            mc.setScreen(new GeneratorSlotsScreen(menu, mc.player.getInventory(), title));
            if (extra == 7) hover(mc, 176, GeneratorSlotsMenu.IN_X + 8, GeneratorSlotsMenu.IN_Y + 8);
            metrics.add(LANGUAGES[language] + "\tgenerator-" + variant + "\t" + mc.font.width(title) + "\t0\t176");
        }
    }
    private static void hover(Minecraft mc, int width, int x, int y) {
        double left = (mc.getWindow().getGuiScaledWidth() - width) / 2;
        double top = (mc.getWindow().getGuiScaledHeight() - MachineLayout.HEIGHT) / 2;
        hoverX = (int) left + x;
        hoverY = (int) top + y;
    }

    /** Draw the real tooltip hit-testing path at a deterministic GUI coordinate.
     * Virtual displays need not dispatch native cursor motion, so do not depend on OS focus. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void renderTooltips(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) throws Exception {
        if (!shown || hoverX < 0) return;
        if (event.getScreen() instanceof MachineScreen screen) {
            var method = MachineScreen.class.getDeclaredMethod(SIDES ? "renderSides" : "renderOwnTooltips", net.minecraft.client.gui.GuiGraphics.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(screen, event.getGuiGraphics(), hoverX, hoverY);
        } else if (event.getScreen() instanceof GeneratorSlotsScreen screen) {
            screen.render(event.getGuiGraphics(), hoverX, hoverY, 0);
        }
    }

}
