package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.generated.GeneratedContent.BlockDef;
import io.github.genichimaruo.singulo.generated.GeneratedContent.ItemDef;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.item.MetricDriveItem;
import io.github.genichimaruo.singulo.item.SinguloBlockItem;
import io.github.genichimaruo.singulo.item.SinguloItem;
import io.github.genichimaruo.singulo.item.UsesItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class SinguloItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, Singulo.MODID);

    /** 手持ちの道具（生成表ではなく専用クラスで作る）。 */
    public static final RegistryObject<GravityGauntletItem> INERTIAL_CONTROL_GAUNTLET = ITEMS.register(
            "inertial_control_gauntlet", () -> new GravityGauntletItem(new Item.Properties().rarity(Rarity.UNCOMMON), 3));
    public static final RegistryObject<GravitonManipulatorItem> GRAVITON_MANIPULATOR = ITEMS.register(
            "graviton_manipulator", () -> new GravitonManipulatorItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final RegistryObject<MetricDriveItem> METRIC_DRIVE = ITEMS.register(
            "metric_drive", () -> new MetricDriveItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final RegistryObject<io.github.genichimaruo.singulo.wormhole.UnstableMouthItem> UNSTABLE_WORMHOLE_MOUTH = ITEMS.register(
            "unstable_wormhole_mouth", () -> new io.github.genichimaruo.singulo.wormhole.UnstableMouthItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.BlackHoleBombItem> BLACK_HOLE_BOMB = ITEMS.register(
            "black_hole_bomb", () -> new io.github.genichimaruo.singulo.item.BlackHoleBombItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.SettingsCardItem> SETTINGS_CARD = ITEMS.register(
            "settings_card", () -> new io.github.genichimaruo.singulo.item.SettingsCardItem(new Item.Properties(), 1));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.ShieldPermitItem> SHIELD_PERMIT = ITEMS.register(
            "shield_permit", () -> new io.github.genichimaruo.singulo.item.ShieldPermitItem(new Item.Properties().rarity(Rarity.RARE), 5));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.HoloProjectorItem> HOLO_PROJECTOR = ITEMS.register(
            "holo_projector", () -> new io.github.genichimaruo.singulo.item.HoloProjectorItem(new Item.Properties(), 1));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.HandbookItem> HANDBOOK = ITEMS.register(
            "handbook", () -> new io.github.genichimaruo.singulo.item.HandbookItem(new Item.Properties(), 1));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.DecodedRecordItem> DECODED_RECORD = ITEMS.register(
            "decoded_record", () -> new io.github.genichimaruo.singulo.item.DecodedRecordItem(new Item.Properties(), 1));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.MagneticBottleItem> MAGNETIC_BOTTLE = ITEMS.register(
            "magnetic_bottle", () -> new io.github.genichimaruo.singulo.item.MagneticBottleItem(new Item.Properties().rarity(Rarity.RARE), 4));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.NeutrinoScannerItem> NEUTRINO_SCANNER = ITEMS.register(
            "neutrino_scanner", () -> new io.github.genichimaruo.singulo.item.NeutrinoScannerItem(new Item.Properties().rarity(Rarity.RARE), 3));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.ExplorerCompassItem> EXPLORER_COMPASS = ITEMS.register(
            "explorer_compass", () -> new io.github.genichimaruo.singulo.item.ExplorerCompassItem(new Item.Properties(), 1));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.CreativeCatalystItem> CREATIVE_CATALYST = ITEMS.register(
            "creative_catalyst", () -> new io.github.genichimaruo.singulo.item.CreativeCatalystItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.BuilderWandItem> BUILDER_WAND = ITEMS.register(
            "builder_wand", () -> new io.github.genichimaruo.singulo.item.BuilderWandItem(new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> SECURITY_DRONE_SPAWN_EGG = ITEMS.register("security_drone_spawn_egg",
            () -> new ForgeSpawnEggItem(SinguloEntities.SECURITY_DRONE, 0xECEEF0, 0x78D2F0, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> HORIZON_WARDEN_SPAWN_EGG = ITEMS.register("horizon_warden_spawn_egg",
            () -> new ForgeSpawnEggItem(SinguloEntities.HORIZON_WARDEN, 0xECEEF0, 0xE86060, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> ECHO_SENTINEL_SPAWN_EGG = ITEMS.register("echo_sentinel_spawn_egg",
            () -> new ForgeSpawnEggItem(SinguloEntities.ECHO_SENTINEL, 0xBFEFFF, 0x3A8FB0, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> GRAVITY_REMNANT_SPAWN_EGG = ITEMS.register("gravity_remnant_spawn_egg",
            () -> new ForgeSpawnEggItem(SinguloEntities.GRAVITY_REMNANT, 0x16121E, 0x9A6CFF, new Item.Properties()));
    /** 封印コンテナからしか出ない道具。 */
    public static final RegistryObject<io.github.genichimaruo.singulo.item.GravityBootsItem> GRAVITY_BOOTS = ITEMS.register(
            "gravity_boots", () -> new io.github.genichimaruo.singulo.item.GravityBootsItem(new Item.Properties().rarity(Rarity.RARE), 3));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.CatalystStabilizerItem> CATALYST_STABILIZER = ITEMS.register(
            "catalyst_stabilizer", () -> new io.github.genichimaruo.singulo.item.CatalystStabilizerItem(new Item.Properties().rarity(Rarity.RARE), 4));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.DimensionalPocketItem> DIMENSIONAL_POCKET = ITEMS.register(
            "dimensional_pocket", () -> new io.github.genichimaruo.singulo.item.DimensionalPocketItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.SealedRecordItem> SEALED_RECORD = ITEMS.register(
            "sealed_record", () -> new io.github.genichimaruo.singulo.item.SealedRecordItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    /** ニュートリノ感度モジュール（スキャナーと観測所の感度を上げる）。 */
    public static final RegistryObject<io.github.genichimaruo.singulo.item.ScannerModuleItem> SCANNER_MODULE_2 = ITEMS.register(
            "scanner_module_2", () -> new io.github.genichimaruo.singulo.item.ScannerModuleItem(new Item.Properties().rarity(Rarity.RARE), 4, 2));
    public static final RegistryObject<io.github.genichimaruo.singulo.item.ScannerModuleItem> SCANNER_MODULE_3 = ITEMS.register(
            "scanner_module_3", () -> new io.github.genichimaruo.singulo.item.ScannerModuleItem(new Item.Properties().rarity(Rarity.EPIC), 5, 3));
    private static final Map<String, Integer> TOOL_STAGES = Map.ofEntries(
            Map.entry("scanner_module_2", 4),
            Map.entry("scanner_module_3", 5),
            Map.entry("gravity_boots", 3),
            Map.entry("catalyst_stabilizer", 4),
            Map.entry("dimensional_pocket", 5),
            Map.entry("sealed_record", 5),
            Map.entry("inertial_control_gauntlet", 3),
            Map.entry("graviton_manipulator", 5),
            Map.entry("shield_permit", 5),
            Map.entry("black_hole_bomb", 5),
            Map.entry("settings_card", 1),
            Map.entry("metric_drive", 5),
            Map.entry("unstable_wormhole_mouth", 5),
            Map.entry("holo_projector", 1),
            Map.entry("handbook", 1),
            Map.entry("decoded_record", 1),
            Map.entry("magnetic_bottle", 4),
            Map.entry("neutrino_scanner", 3),
            Map.entry("explorer_compass", 1),
            Map.entry("creative_catalyst", 6),
            Map.entry("builder_wand", 6),
            Map.entry("creative_energy_source", 6));

    /** クリエイティブタブの並び順（用途ごと、その中は段階順）。 */
    public static final List<RegistryObject<? extends Item>> TAB_ORDER = new ArrayList<>();

    static {
        // 装置ブロック
        for (var entry : SinguloBlocks.MACHINES.entrySet()) {
            block(entry.getValue(), entry.getKey().stage());
        }
        for (var entry : SinguloBlocks.CONTROLLERS.entrySet()) {
            block(entry.getValue(), entry.getKey().stage());
        }
        TAB_ORDER.add(HANDBOOK);
        TAB_ORDER.add(SETTINGS_CARD);
        TAB_ORDER.add(HOLO_PROJECTOR);
        TAB_ORDER.add(EXPLORER_COMPASS);
        TAB_ORDER.add(DECODED_RECORD);
        block(SinguloBlocks.THERMOELECTRIC_GENERATOR, 1);
        block(SinguloBlocks.COPPER_WIRE, 1);
        block(SinguloBlocks.MULTIBLOCK_PORT, 2);
        block(SinguloBlocks.COOLING_TOWER_BASE, 2);
        block(SinguloBlocks.COOLING_TOWER_CASING, 2);
        block(SinguloBlocks.COOLING_TOWER_GLASS, 2);
        block(SinguloBlocks.COOLING_TOWER_COOLANT_BAND, 2);
        block(SinguloBlocks.COOLING_TOWER_RIM, 2);
        block(SinguloBlocks.COOLING_TOWER_GRATE, 2);
        block(SinguloBlocks.HEAT_EXCHANGE_CORE, 2);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_FRAME, 4);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_PLATE, 4);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_RAM, 4);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_ANVIL, 4);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_VENT, 4);
        block(SinguloBlocks.DEGENERATE_COMPACTOR_WINDOW, 4);
        block(SinguloBlocks.CASIMIR_CAVITY_FRAME, 4);
        block(SinguloBlocks.CASIMIR_CAVITY_PUMP, 4);
        block(SinguloBlocks.MIRROR_PLATE, 4);
        block(SinguloBlocks.CASIMIR_CAVITY_WALL, 4);
        block(SinguloBlocks.CASIMIR_CAVITY_SHIELD, 4);
        block(SinguloBlocks.CASIMIR_CAVITY_WINDOW, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_FRAME, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_SHELL, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_PISTON, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_FIN, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_TUBE, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_WINDOW, 4);
        block(SinguloBlocks.SHIELD_TOWER_PLINTH, 5);
        block(SinguloBlocks.SHIELD_TOWER_COIL, 5);
        block(SinguloBlocks.SHIELD_TOWER_BODY, 5);
        block(SinguloBlocks.SHIELD_TOWER_WAVEGUIDE, 5);
        block(SinguloBlocks.SHIELD_TOWER_CROWN, 5);
        block(SinguloBlocks.TIPLER_FRAME, 5);
        block(SinguloBlocks.TIPLER_HOUSING, 5);
        block(SinguloBlocks.TIPLER_BEARING, 5);
        block(SinguloBlocks.TIPLER_WINDOW, 5);
        block(SinguloBlocks.TIPLER_HOLDER, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_SHELL, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_COIL, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_FOCUSER, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_WINDOW, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_PORT, 5);
        block(SinguloBlocks.ACCELERATOR_TUBE, 2);
        block(SinguloBlocks.FOCUSING_MAGNET, 2);
        block(SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER, 4);
        block(SinguloBlocks.PROBE_STATION, 4);
        block(SinguloBlocks.SMES_MODULE, 4);
        block(SinguloBlocks.TOPOLOGICAL_WIRE, 4);
        block(SinguloBlocks.REACTOR_SHELL, 5);
        block(SinguloBlocks.GYRO_DRIVE, 5);
        block(SinguloBlocks.EXTRACTION_PORT, 5);
        block(SinguloBlocks.REACTOR_STABILIZER, 5);
        block(SinguloBlocks.REACTOR_MASS_ALARM, 5);
        block(SinguloBlocks.CORE_CONTROLLER, 5);
        block(SinguloBlocks.HORIZON_BUS, 5);
        block(SinguloBlocks.WORLDLINE_ANCHOR_ADVANCED, 5);
        block(SinguloBlocks.SHIELD_TOWER_CORE, 5);
        TAB_ORDER.add(SHIELD_PERMIT);
        TAB_ORDER.add(BLACK_HOLE_BOMB);
        block(SinguloBlocks.TIPLER_CORE, 5);
        TAB_ORDER.add(GRAVITON_MANIPULATOR);
        TAB_ORDER.add(METRIC_DRIVE);
        block(SinguloBlocks.HALO_COLLECTOR, 5);
        block(SinguloBlocks.CONTAINMENT_TANK, 5);
        block(SinguloBlocks.WORMHOLE_GENERATOR_CORE, 5);
        TAB_ORDER.add(UNSTABLE_WORMHOLE_MOUTH);
        block(SinguloBlocks.WORMHOLE_STABILIZER, 5);
        TAB_ORDER.add(ITEMS.register("wormhole_mouth", () -> new SinguloBlockItem(SinguloBlocks.WORMHOLE_MOUTH.get(),
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 5)));
        block(SinguloBlocks.WORMHOLE_PORT, 5);
        block(SinguloBlocks.CRYOGENIC_TURBINE, 2);
        block(SinguloBlocks.SMES_CELL, 2);
        block(SinguloBlocks.COSMIC_MUON_COLLECTOR, 2);
        block(SinguloBlocks.WORLDLINE_ANCHOR_SMALL, 2);
        block(SinguloBlocks.SUPERCONDUCTING_CABLE, 2);
        block(SinguloBlocks.QUANTUM_HEAT_ENGINE, 3);
        block(SinguloBlocks.INERTIAL_STABILIZER, 3);
        block(SinguloBlocks.LOW_GRAVITY_PANEL, 3);
        block(SinguloBlocks.HIGH_GRAVITY_PANEL, 3);
        block(SinguloBlocks.GRAVITY_PANEL_RECEIVER, 3);
        block(SinguloBlocks.IMPACT_GENERATOR, 3);
        block(SinguloBlocks.KERAUNOS_TOWER, 3);
        block(SinguloBlocks.DEEP_SEA_COLLECTOR, 3);
        block(SinguloBlocks.VOID_COLLECTOR, 4);
        block(SinguloBlocks.METEORITE_CRUST, 2);
        block(SinguloBlocks.METEORIC_IRON_CHUNK, 2);
        block(SinguloBlocks.STARDUST_CLUSTER, 2);
        block(SinguloBlocks.FULGURITE_BLOCK, 2);
        TAB_ORDER.add(INERTIAL_CONTROL_GAUNTLET);
        TAB_ORDER.add(NEUTRINO_SCANNER);
        TAB_ORDER.add(SCANNER_MODULE_2);
        TAB_ORDER.add(SCANNER_MODULE_3);
        TAB_ORDER.add(MAGNETIC_BOTTLE);
        block(SinguloBlocks.GRAVITATIONAL_WAVE_DETECTOR, 3);
        block(SinguloBlocks.NEUTRINO_OBSERVATORY, 3);
        for (var ruin : java.util.List.of(SinguloBlocks.RUIN_PANEL, SinguloBlocks.CRACKED_RUIN_PANEL, SinguloBlocks.MOSSY_RUIN_PANEL,
                SinguloBlocks.RUIN_LAMP)) {
            block(ruin, 1);
        }
        for (var ruin : java.util.List.of(SinguloBlocks.TILED_RUIN_PANEL, SinguloBlocks.VENTED_RUIN_PANEL,
                SinguloBlocks.STRIPED_RUIN_PANEL, SinguloBlocks.SCORCHED_RUIN_PANEL, SinguloBlocks.PRISTINE_RUIN_PANEL,
                SinguloBlocks.PRISTINE_RUIN_TILES, SinguloBlocks.PRISTINE_RUIN_PILLAR, SinguloBlocks.PRISTINE_RUIN_LIGHT)) {
            block(ruin, 1);
        }
        block(SinguloBlocks.WHITE_PANEL, 1);
        block(SinguloBlocks.WHITE_LIGHT_PANEL, 1);
        block(SinguloBlocks.BLACK_REINFORCED_PANEL, 1);
        block(SinguloBlocks.WHITE_GLASS_PANEL, 1);
        block(SinguloBlocks.WHITE_STAR_GLASS_PANEL, 1);
        block(SinguloBlocks.BLACK_REINFORCED_GLASS, 1);
        block(SinguloBlocks.BLACK_STAR_GLASS, 1);
        block(SinguloBlocks.RUIN_GLASS, 1);
        block(SinguloBlocks.INTACT_RUIN_GLASS, 1);
        block(SinguloBlocks.RUIN_CACHE, 1);
        block(SinguloBlocks.RUIN_GUARD_DOCK, 1);
        block(SinguloBlocks.SEAL_CONSOLE, 1);
        block(SinguloBlocks.GUARDIAN_CORE, 1);
        block(SinguloBlocks.ECHO_PROJECTOR, 1);
        TAB_ORDER.add(SECURITY_DRONE_SPAWN_EGG);
        for (var lamp : java.util.List.of(SinguloBlocks.RUIN_LAMP_AMBER, SinguloBlocks.RUIN_LAMP_VERDANT, SinguloBlocks.RUIN_LAMP_VIOLET,
                SinguloBlocks.RUIN_LAMP_CRIMSON)) {
            block(lamp, 1);
        }
        for (int i = 0; i < SinguloBlocks.SEALED_CONTAINERS.size(); i++) {
            block(SinguloBlocks.SEALED_CONTAINERS.get(i), i + 2);
        }
        TAB_ORDER.add(GRAVITY_BOOTS);
        TAB_ORDER.add(CATALYST_STABILIZER);
        TAB_ORDER.add(DIMENSIONAL_POCKET);
        TAB_ORDER.add(SEALED_RECORD);
        // クリエイティブ専用（並びの最後）
        block(SinguloBlocks.CREATIVE_ENERGY_SOURCE, 6);
        TAB_ORDER.add(CREATIVE_CATALYST);
        TAB_ORDER.add(BUILDER_WAND);
        TAB_ORDER.add(HORIZON_WARDEN_SPAWN_EGG);
        TAB_ORDER.add(ECHO_SENTINEL_SPAWN_EGG);
        TAB_ORDER.add(GRAVITY_REMNANT_SPAWN_EGG);
        for (BlockDef def : GeneratedContent.BLOCKS) {
            block(SinguloBlocks.SIMPLE.get(def.id()), def.stage());
        }
        // 素材・部品・回収物（生成表から）
        for (ItemDef def : GeneratedContent.ITEMS) {
            TAB_ORDER.add(ITEMS.register(def.id(), () -> create(def)));
        }
        // 用途ごとのまとまり（GeneratedContent.TAB_ORDER の順）。そこにないものは最後に段階順で
        TAB_ORDER.sort(java.util.Comparator.comparingInt((RegistryObject<? extends Item> item) -> tabIndex(item))
                .thenComparingInt(SinguloItems::stageOf));
    }

    private static Item create(ItemDef def) {
        Item.Properties props = new Item.Properties();
        if (def.stage() >= 5) {
            props.rarity(Rarity.EPIC);
        } else if (def.stage() >= 4) {
            props.rarity(Rarity.RARE);
        }
        return switch (def.kind()) {
            case "uses" -> new UsesItem(props, def.stage(), def.maxUses(), false);
            case "catalyst" -> new UsesItem(props, def.stage(), def.maxUses(), true);
            case "planned" -> new SinguloItem(props, def.stage(), true);
            default -> new SinguloItem(props, def.stage(), false);
        };
    }

    private static void block(RegistryObject<? extends Block> block, int stage) {
        TAB_ORDER.add(ITEMS.register(block.getId().getPath(),
                () -> new SinguloBlockItem(block.get(), new Item.Properties(), stage)));
    }

    private static int tabIndex(RegistryObject<? extends Item> item) {
        int i = GeneratedContent.TAB_ORDER.indexOf(item.getId().getPath());
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static int stageOf(RegistryObject<? extends Item> item) {
        String id = item.getId().getPath();
        for (ItemDef def : GeneratedContent.ITEMS) {
            if (def.id().equals(id)) {
                return def.stage();
            }
        }
        for (BlockDef def : GeneratedContent.BLOCKS) {
            if (def.id().equals(id)) {
                return def.stage();
            }
        }
        if (TOOL_STAGES.containsKey(id)) {
            return TOOL_STAGES.get(id);
        }
        return GeneratedContent.BLOCK_STAGES.getOrDefault(id, 1);
    }

    private SinguloItems() {}
}
