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
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Singulo.MODID);

    /** 手持ちの道具（生成表ではなく専用クラスで作る）。 */
    public static final DeferredItem<GravityGauntletItem> INERTIAL_CONTROL_GAUNTLET = ITEMS.register(
            "inertial_control_gauntlet", () -> new GravityGauntletItem(new Item.Properties().rarity(Rarity.UNCOMMON), 3));
    public static final DeferredItem<GravitonManipulatorItem> GRAVITON_MANIPULATOR = ITEMS.register(
            "graviton_manipulator", () -> new GravitonManipulatorItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final DeferredItem<MetricDriveItem> METRIC_DRIVE = ITEMS.register(
            "metric_drive", () -> new MetricDriveItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final DeferredItem<io.github.genichimaruo.singulo.wormhole.UnstableMouthItem> UNSTABLE_WORMHOLE_MOUTH = ITEMS.register(
            "unstable_wormhole_mouth", () -> new io.github.genichimaruo.singulo.wormhole.UnstableMouthItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.HoloProjectorItem> HOLO_PROJECTOR = ITEMS.register(
            "holo_projector", () -> new io.github.genichimaruo.singulo.item.HoloProjectorItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.HandbookItem> HANDBOOK = ITEMS.register(
            "handbook", () -> new io.github.genichimaruo.singulo.item.HandbookItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.DecodedRecordItem> DECODED_RECORD = ITEMS.register(
            "decoded_record", () -> new io.github.genichimaruo.singulo.item.DecodedRecordItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.MagneticBottleItem> MAGNETIC_BOTTLE = ITEMS.register(
            "magnetic_bottle", () -> new io.github.genichimaruo.singulo.item.MagneticBottleItem(new Item.Properties().rarity(Rarity.RARE), 4));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.NeutrinoScannerItem> NEUTRINO_SCANNER = ITEMS.register(
            "neutrino_scanner", () -> new io.github.genichimaruo.singulo.item.NeutrinoScannerItem(new Item.Properties().rarity(Rarity.RARE), 3));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.ExplorerCompassItem> EXPLORER_COMPASS = ITEMS.register(
            "explorer_compass", () -> new io.github.genichimaruo.singulo.item.ExplorerCompassItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.CreativeCatalystItem> CREATIVE_CATALYST = ITEMS.register(
            "creative_catalyst", () -> new io.github.genichimaruo.singulo.item.CreativeCatalystItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.BuilderWandItem> BUILDER_WAND = ITEMS.register(
            "builder_wand", () -> new io.github.genichimaruo.singulo.item.BuilderWandItem(new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SECURITY_DRONE_SPAWN_EGG = ITEMS.register("security_drone_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.SECURITY_DRONE, 0xECEEF0, 0x78D2F0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HORIZON_WARDEN_SPAWN_EGG = ITEMS.register("horizon_warden_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.HORIZON_WARDEN, 0xECEEF0, 0xE86060, new Item.Properties()));
    private static final Map<String, Integer> TOOL_STAGES = Map.ofEntries(
            Map.entry("inertial_control_gauntlet", 3),
            Map.entry("graviton_manipulator", 5),
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

    /** クリエイティブタブの並び順（段階順）。 */
    public static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    static {
        // 装置ブロック
        for (var entry : SinguloBlocks.MACHINES.entrySet()) {
            block(entry.getValue(), entry.getKey().stage());
        }
        for (var entry : SinguloBlocks.CONTROLLERS.entrySet()) {
            block(entry.getValue(), entry.getKey().stage());
        }
        TAB_ORDER.add(HANDBOOK);
        TAB_ORDER.add(HOLO_PROJECTOR);
        TAB_ORDER.add(EXPLORER_COMPASS);
        TAB_ORDER.add(DECODED_RECORD);
        block(SinguloBlocks.THERMOELECTRIC_GENERATOR, 1);
        block(SinguloBlocks.COPPER_WIRE, 1);
        block(SinguloBlocks.COOLING_TOWER_CASING, 2);
        block(SinguloBlocks.COOLING_TOWER_GLASS, 2);
        block(SinguloBlocks.HEAT_EXCHANGE_CORE, 2);
        block(SinguloBlocks.COOLING_TOWER_PORT, 2);
        block(SinguloBlocks.ACCELERATOR_TUBE, 2);
        block(SinguloBlocks.FOCUSING_MAGNET, 2);
        block(SinguloBlocks.DEGENERATE_CASING, 4);
        block(SinguloBlocks.MIRROR_PLATE, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_PISTON, 4);
        block(SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER, 4);
        block(SinguloBlocks.PROBE_STATION, 4);
        block(SinguloBlocks.SMES_MODULE, 4);
        block(SinguloBlocks.TOPOLOGICAL_WIRE, 4);
        block(SinguloBlocks.REACTOR_SHELL, 5);
        block(SinguloBlocks.GYRO_DRIVE, 5);
        block(SinguloBlocks.EXTRACTION_PORT, 5);
        block(SinguloBlocks.CORE_CONTROLLER, 5);
        block(SinguloBlocks.HORIZON_BUS, 5);
        block(SinguloBlocks.WORLDLINE_ANCHOR_ADVANCED, 5);
        block(SinguloBlocks.SHIELD_TOWER_CORE, 5);
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
        TAB_ORDER.add(INERTIAL_CONTROL_GAUNTLET);
        TAB_ORDER.add(NEUTRINO_SCANNER);
        TAB_ORDER.add(MAGNETIC_BOTTLE);
        block(SinguloBlocks.GRAVITATIONAL_WAVE_DETECTOR, 3);
        for (var ruin : java.util.List.of(SinguloBlocks.RUIN_PANEL, SinguloBlocks.CRACKED_RUIN_PANEL, SinguloBlocks.MOSSY_RUIN_PANEL,
                SinguloBlocks.RUIN_LAMP)) {
            block(ruin, 1);
        }
        block(SinguloBlocks.RUIN_GLASS, 1);
        block(SinguloBlocks.RUIN_CACHE, 1);
        block(SinguloBlocks.RUIN_GUARD_DOCK, 1);
        block(SinguloBlocks.SEAL_CONSOLE, 1);
        TAB_ORDER.add(SECURITY_DRONE_SPAWN_EGG);
        // クリエイティブ専用（並びの最後）
        block(SinguloBlocks.CREATIVE_ENERGY_SOURCE, 6);
        TAB_ORDER.add(CREATIVE_CATALYST);
        TAB_ORDER.add(BUILDER_WAND);
        TAB_ORDER.add(HORIZON_WARDEN_SPAWN_EGG);
        for (BlockDef def : GeneratedContent.BLOCKS) {
            block(SinguloBlocks.SIMPLE.get(def.id()), def.stage());
        }
        // 素材・部品・回収物（生成表から）
        for (ItemDef def : GeneratedContent.ITEMS) {
            TAB_ORDER.add(ITEMS.register(def.id(), () -> create(def)));
        }
        TAB_ORDER.sort((a, b) -> Integer.compare(stageOf(a), stageOf(b)));
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

    private static void block(DeferredBlock<? extends Block> block, int stage) {
        TAB_ORDER.add(ITEMS.register(block.getId().getPath(),
                () -> new SinguloBlockItem(block.get(), new Item.Properties(), stage)));
    }

    private static int stageOf(DeferredItem<? extends Item> item) {
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
