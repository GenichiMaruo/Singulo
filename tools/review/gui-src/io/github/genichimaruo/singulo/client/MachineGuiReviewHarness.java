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
    private static int SCREENS = SELECTOR ? 6 : SIDES ? 1 : MachineType.values().length + 28;
    private static final List<String> metrics = new ArrayList<>();
    private static int language, screen;
    private static long renderedFrames, shownFrame;
    private static boolean shown;
    private static net.minecraft.client.gui.screens.Screen fixture;
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
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void rendered(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        renderedFrames++;
    }

    static void tick(Minecraft mc) throws Exception {
        if (mc.getOverlay() != null) return;
        if (!shown) {
            show(mc);
            shown = true;
            shownFrame = renderedFrames;
            fixture = mc.screen;
            return;
        }
        // Capture after actual completed renders, not an arbitrary twelve-tick delay.
        // Several client ticks can happen in one frame on a slow CI renderer.
        if (renderedFrames - shownFrame < 2) return;
        if (mc.screen != fixture) throw new IllegalStateException("GUI fixture closed/replaced before capture");
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
                if (slot.x == menu.layout.capacityX && slot.y == menu.layout.capacityY) continue;
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
            var menu = new CatalystDeviceMenu(0, mc.player.getInventory(), BlockPos.ZERO, kind, new ItemStackHandler(2), new ItemStackHandler(1), data);
            mc.setScreen(new CatalystDeviceScreen(menu, mc.player.getInventory(),
                    Component.translatable(shield ? "block.singulo.shield_tower_core" : "block.singulo.worldline_anchor_small")));
            metrics.add(LANGUAGES[language] + "	catalyst-" + extra + "	0	0	176");
        } else if (extra >= 12 && extra <= 18) {
            // スロットの少ない装置（DeviceMenu）7種類。どれも動いている状態の値
            var kind = DeviceMenu.Kind.values()[extra - 12];
            SyncedInts data = SyncedInts.client(kind.data);
            switch (kind) {
                case WORMHOLE_GENERATOR -> {
                    value(data, DeviceMenu.Gen.FORMED, 1);
                    value(data, DeviceMenu.Gen.PROGRESS, 120);
                    value(data, DeviceMenu.Gen.RECEIVED, 2_000_000);
                    value(data, DeviceMenu.Gen.REQUIRED, 5_000_000);
                }
                case WORMHOLE_MOUTH -> {
                    value(data, DeviceMenu.Mouth.SIZE, 1);
                    value(data, DeviceMenu.Mouth.TARGET, 2);
                    value(data, DeviceMenu.Mouth.PARTNER, 2);
                    value(data, DeviceMenu.Mouth.PX, 120);
                    value(data, DeviceMenu.Mouth.PY, 64);
                    value(data, DeviceMenu.Mouth.PZ, -340);
                    value(data, DeviceMenu.Mouth.CROSS, 1);
                    value(data, DeviceMenu.Mouth.TICKS_PER_MATTER, 18_000);
                    value(data, DeviceMenu.Mouth.GRACE, 1_200);
                    value(data, DeviceMenu.Mouth.PORTS, 3);
                    value(data, DeviceMenu.Mouth.PORT_LIMIT, 4);
                }
                case WORMHOLE_PORT -> {
                    value(data, DeviceMenu.Port.STATE, DeviceMenu.Port.STATE_LINKED);
                    value(data, DeviceMenu.Port.SIZE, 1);
                    value(data, DeviceMenu.Port.ENERGY, 2);
                    value(data, DeviceMenu.Port.ITEMS, 1);
                    value(data, DeviceMenu.Port.FLUIDS, 0);
                    value(data, DeviceMenu.Port.CHANNEL, 37);
                    value(data, DeviceMenu.Port.RANK, 2);
                    value(data, DeviceMenu.Port.LIMIT, 4);
                }
                case CONTAINMENT_TANK -> {
                    value(data, DeviceMenu.Tank.AMOUNT, 24_000);
                    value(data, DeviceMenu.Tank.CAPACITY, 64_000);
                    value(data, DeviceMenu.Tank.CONTAINED, 1);
                    value(data, DeviceMenu.Tank.ENERGY, 300_000);
                    value(data, DeviceMenu.Tank.ENERGY_MAX, 500_000);
                }
                case HALO_COLLECTOR -> {
                    value(data, DeviceMenu.Halo.RATE, 12);
                    value(data, DeviceMenu.Halo.AMOUNT, 600);
                    value(data, DeviceMenu.Halo.BUFFER, 1_000);
                    value(data, DeviceMenu.Halo.ENERGY, 80_000);
                    value(data, DeviceMenu.Halo.ENERGY_MAX, 200_000);
                    value(data, DeviceMenu.Halo.CORE, 1);
                    value(data, DeviceMenu.Halo.CORE_MASS, 1_250);
                }
                case MUON_COLLECTOR -> {
                    value(data, DeviceMenu.Muon.RATE, 6);
                    value(data, DeviceMenu.Muon.Y, 160);
                    value(data, DeviceMenu.Muon.SKY, 1);
                    value(data, DeviceMenu.Muon.PROGRESS, 45);
                }
                case DETECTOR -> {
                    value(data, DeviceMenu.Detector.ENERGY, 900_000);
                    value(data, DeviceMenu.Detector.ENERGY_MAX, 1_000_000);
                    value(data, DeviceMenu.Detector.OBSERVED, 1);
                    value(data, DeviceMenu.Detector.FOUND, 1);
                    value(data, DeviceMenu.Detector.DIRECTION, 3);
                    value(data, DeviceMenu.Detector.BAND, 2);
                    value(data, DeviceMenu.Detector.COST, 250_000);
                }
            }
            var menu = new DeviceMenu(0, mc.player.getInventory(), kind, BlockPos.ZERO, new ItemStackHandler(Math.max(1, kind.slots)), data, null);
            mc.setScreen(new DeviceScreen(menu, mc.player.getInventory(), Component.literal(kind.name().toLowerCase())));
            metrics.add(LANGUAGES[language] + "\tdevice-" + kind.name().toLowerCase() + "\t0\t0\t" + DeviceMenu.WIDTH);
        } else if (extra == 19) {
            // ワームホール固定化装置: 片方の喉が固定化中、もう片方は燃料・電力待ち
            SyncedInts data = SyncedInts.client(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu.COUNT);
            value(data, io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu.D_PROGRESS_0,
                    io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity.STABILIZE_TICKS * 45 / 100);
            value(data, io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu.D_ENERGY, 600_000);
            value(data, io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu.D_CAPACITY, 1_000_000);
            ItemStackHandler items = new ItemStackHandler(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity.SLOTS);
            items.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("unstable_wormhole_mouth"))));
            var menu = new io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu(0, mc.player.getInventory(), BlockPos.ZERO, items, data, null);
            mc.setScreen(new WormholeStabilizerScreen(menu, mc.player.getInventory(), Component.translatable("block.singulo.wormhole_stabilizer")));
            metrics.add(LANGUAGES[language] + "\tstabilizer\t0\t0\t212");
        } else if (extra == 20) {
            // ペンローズ・リアクター: 発電モードで稼働中
            SyncedInts data = SyncedInts.client(io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.COUNT);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_STATE,
                    io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.State.RUNNING.ordinal());
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_MASS_X10, 3_125);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_SPIN_X1000, 912);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_ETA_X10000, 4_210);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_OUT_LO, 250_000_000);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_BUFFER_MFE, 12_300);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_TARGET, 2);
            value(data, io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.D_HARVEST, 1);
            var menu = new io.github.genichimaruo.singulo.reactor.PenroseReactorMenu(0, mc.player.getInventory(), BlockPos.ZERO,
                    new ItemStackHandler(io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.SLOTS), data, null);
            mc.setScreen(new PenroseReactorScreen(menu, mc.player.getInventory(), Component.literal("penrose_reactor")));
            metrics.add(LANGUAGES[language] + "\tpenrose\t0\t0\t212");
        } else if (extra == 21) {
            // 創世炉の核（核のブロックがないので未形成の見た目）
            var menu = new io.github.genichimaruo.singulo.genesis.GenesisCoreMenu(0, mc.player.getInventory(), BlockPos.ZERO,
                    new net.minecraft.world.SimpleContainer(1), new net.minecraft.world.SimpleContainer(1));
            mc.setScreen(new GenesisCoreScreen(menu, mc.player.getInventory(), Component.translatable("block.singulo.genesis_core")));
            metrics.add(LANGUAGES[language] + "\tgenesis\t0\t0\t248");
        } else if (extra >= 22 && extra <= 24) {
            // Empty, fully configured, and a translated mode tooltip in every language.
            var entries = new net.minecraft.world.SimpleContainer(io.github.genichimaruo.singulo.android.work.SupplyPortBlockEntity.ENTRIES);
            var data = new net.minecraft.world.inventory.SimpleContainerData(entries.getContainerSize() * 2);
            if (extra > 22) {
                var samples = new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.IRON_INGOT,
                        net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.DIAMOND,
                        net.minecraft.world.item.Items.COPPER_INGOT, net.minecraft.world.item.Items.REDSTONE,
                        net.minecraft.world.item.Items.LAPIS_LAZULI, net.minecraft.world.item.Items.EMERALD,
                        net.minecraft.world.item.Items.NETHERITE_INGOT};
                for (int i = 0; i < samples.length; i++) {
                    entries.setItem(i, new net.minecraft.world.item.ItemStack(samples[i]));
                    data.set(i, i == 7 ? 9 : i + 1);
                    data.set(samples.length + i, i % 3);
                }
            }
            var menu = new io.github.genichimaruo.singulo.android.work.SupplyPortMenu(0, mc.player.getInventory(), BlockPos.ZERO, entries, data);
            mc.setScreen(new SupplyPortScreen(menu, mc.player.getInventory(), Component.translatable("block.singulo.android_supply_port")));
            if (extra == 24) hover(mc, menu.WIDTH, menu.HEIGHT, menu.entryX(7) + 25, menu.entryY(7) + 7);
            for (var slot : menu.slots) {
                if (slot.x < 1 || slot.x + 16 >= menu.WIDTH || slot.y < 16 || slot.y + 16 > menu.HEIGHT - 4)
                    throw new IllegalStateException("Supply port slot outside panel");
            }
            metrics.add(LANGUAGES[language] + "\tsupply-" + (extra - 22) + "\t0\t0\t176");
        } else if (extra >= 25) {
            var status = new CatalystDeviceBlockEntity.Status[] {CatalystDeviceBlockEntity.Status.WAITING,
                    CatalystDeviceBlockEntity.Status.OUTPUT_FULL, CatalystDeviceBlockEntity.Status.NEEDS_UPGRADE}[extra - 25];
            SyncedInts data = SyncedInts.client(CatalystDeviceBlockEntity.COUNT);
            value(data, CatalystDeviceBlockEntity.D_STATUS, status.ordinal());
            value(data, CatalystDeviceBlockEntity.D_EXTRA, 25);
            value(data, CatalystDeviceBlockEntity.D_VALUE, 300);
            value(data, CatalystDeviceBlockEntity.D_ENABLED, 1);
            value(data, CatalystDeviceBlockEntity.D_ENERGY, 50_000);
            value(data, CatalystDeviceBlockEntity.D_CAPACITY, 100_000);
            var menu = new CatalystDeviceMenu(0, mc.player.getInventory(), BlockPos.ZERO,
                    CatalystDeviceBlockEntity.Kind.PROBE_STATION, new ItemStackHandler(1), new ItemStackHandler(1), data);
            mc.setScreen(new CatalystDeviceScreen(menu, mc.player.getInventory(), Component.translatable("block.singulo.probe_station")));
            hover(mc, 176, CatalystDeviceMenu.HEIGHT, 146, 9);
            metrics.add(LANGUAGES[language] + "\tprobe-" + status.name().toLowerCase() + "\t0\t0\t176");
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
        hover(mc, width, MachineLayout.HEIGHT, x, y);
    }

    private static void hover(Minecraft mc, int width, int height, int x, int y) {
        double left = (mc.getWindow().getGuiScaledWidth() - width) / 2;
        double top = (mc.getWindow().getGuiScaledHeight() - height) / 2;
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
        } else if (event.getScreen() instanceof SupplyPortScreen screen) {
            screen.render(event.getGuiGraphics(), hoverX, hoverY, 0);
        } else if (event.getScreen() instanceof GeneratorSlotsScreen screen) {
            screen.render(event.getGuiGraphics(), hoverX, hoverY, 0);
        } else if (event.getScreen() instanceof CatalystDeviceScreen screen) {
            screen.render(event.getGuiGraphics(), hoverX, hoverY, 0);
        }
    }

}
