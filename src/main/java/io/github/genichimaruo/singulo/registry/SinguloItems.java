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
    public static final DeferredItem<io.github.genichimaruo.singulo.item.BlackHoleBombItem> BLACK_HOLE_BOMB = ITEMS.register(
            "black_hole_bomb", () -> new io.github.genichimaruo.singulo.item.BlackHoleBombItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.SettingsCardItem> SETTINGS_CARD = ITEMS.register(
            "settings_card", () -> new io.github.genichimaruo.singulo.item.SettingsCardItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.ShieldPermitItem> SHIELD_PERMIT = ITEMS.register(
            "shield_permit", () -> new io.github.genichimaruo.singulo.item.ShieldPermitItem(new Item.Properties().rarity(Rarity.RARE), 5));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.HoloProjectorItem> HOLO_PROJECTOR = ITEMS.register(
            "holo_projector", () -> new io.github.genichimaruo.singulo.item.HoloProjectorItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.HandbookItem> HANDBOOK = ITEMS.register(
            "handbook", () -> new io.github.genichimaruo.singulo.item.HandbookItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.DecodedRecordItem> DECODED_RECORD = ITEMS.register(
            "decoded_record", () -> new io.github.genichimaruo.singulo.item.DecodedRecordItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.FluidCanisterItem> FLUID_CANISTER = ITEMS.register(
            "fluid_canister", () -> new io.github.genichimaruo.singulo.item.FluidCanisterItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.MagneticBottleItem> MAGNETIC_BOTTLE = ITEMS.register(
            "magnetic_bottle", () -> new io.github.genichimaruo.singulo.item.MagneticBottleItem(new Item.Properties().rarity(Rarity.RARE), 4));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.NeutrinoScannerItem> NEUTRINO_SCANNER = ITEMS.register(
            "neutrino_scanner", () -> new io.github.genichimaruo.singulo.item.NeutrinoScannerItem(new Item.Properties().rarity(Rarity.RARE), 3));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.ExplorerCompassItem> EXPLORER_COMPASS = ITEMS.register(
            "explorer_compass", () -> new io.github.genichimaruo.singulo.item.ExplorerCompassItem(new Item.Properties(), 1));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.MeteorCompassItem> METEOR_COMPASS = ITEMS.register(
            "meteor_compass", () -> new io.github.genichimaruo.singulo.item.MeteorCompassItem(new Item.Properties(), 2));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.CreativeCatalystItem> CREATIVE_CATALYST = ITEMS.register(
            "creative_catalyst", () -> new io.github.genichimaruo.singulo.item.CreativeCatalystItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.BuilderWandItem> BUILDER_WAND = ITEMS.register(
            "builder_wand", () -> new io.github.genichimaruo.singulo.item.BuilderWandItem(new Item.Properties()));
    /** アンドロイド用の無線充電器。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidChargerItem> ANDROID_CHARGER = ITEMS.register(
            "android_charger", () -> new io.github.genichimaruo.singulo.android.AndroidChargerItem(new Item.Properties()));
    /** アンドロイド用装甲板（護衛のアンドロイドに積む防具）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidArmorPlateItem> ANDROID_ARMOR_PLATE = ITEMS.register(
            "android_armor_plate", () -> new io.github.genichimaruo.singulo.android.AndroidArmorPlateItem(new Item.Properties()));
    /** 採掘アップグレード（ツルハシのエンチャントを付けられる）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidMiningUpgradeItem> ANDROID_MINING_UPGRADE = ITEMS.register(
            "android_mining_upgrade", () -> new io.github.genichimaruo.singulo.android.AndroidMiningUpgradeItem(new Item.Properties()));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidFellingUpgradeItem> ANDROID_FELLING_UPGRADE = ITEMS.register(
            "android_felling_upgrade", () -> new io.github.genichimaruo.singulo.android.AndroidFellingUpgradeItem(new Item.Properties()));
    /** 作業位置カードとレシピカード（アンドロイドの作業）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.WorkLocationCardItem> WORK_LOCATION_CARD = ITEMS.register(
            "work_location_card", () -> new io.github.genichimaruo.singulo.android.work.WorkLocationCardItem(new Item.Properties()));
    /** 範囲カード（2つの角で囲った範囲）と、記録した場所を見せる測量メガネ。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.AreaCardItem> AREA_CARD = ITEMS.register(
            "area_card", () -> new io.github.genichimaruo.singulo.android.work.AreaCardItem(new Item.Properties()));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.SurveyGlassesItem> SURVEY_GLASSES = ITEMS.register(
            "survey_glasses", () -> new io.github.genichimaruo.singulo.android.work.SurveyGlassesItem(new Item.Properties()));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.RecipeCardItem> RECIPE_CARD = ITEMS.register(
            "recipe_card", () -> new io.github.genichimaruo.singulo.android.work.RecipeCardItem(new Item.Properties()));
    /** アンドロイドの装飾（頭・背中/腰・手）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_RIBBON = ITEMS.register(
            "android_ribbon", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD, "ribbon", 0xE0607E));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_HAIR_FLOWER = ITEMS.register(
            "android_hair_flower", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD, "hair_flower", 0xF2C94C));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_BERET = ITEMS.register(
            "android_beret", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD, "beret", 0x34406E));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_HEADDRESS = ITEMS.register(
            "android_headdress", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD, "headdress", 0xF5F5F5));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_CAPE = ITEMS.register(
            "android_cape", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.BACK, "cape", 0x8E2F3F));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_SCARF = ITEMS.register(
            "android_scarf", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.BACK, "scarf", 0xC84E4E));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_WAIST_RIBBON = ITEMS.register(
            "android_waist_ribbon", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.BACK, "waist_ribbon", 0x5A8ED6));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_BOOK = ITEMS.register(
            "android_book", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND, "book", 0x6B4A3A));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_BOUQUET = ITEMS.register(
            "android_bouquet", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND, "bouquet", 0xF0E2C0));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_TEACUP = ITEMS.register(
            "android_teacup", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND, "teacup", 0xFFFFFF));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem> ANDROID_UMBRELLA = ITEMS.register(
            "android_umbrella", () -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem(new Item.Properties(),
                    io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HAND, "umbrella", 0x4A78C8));
    /** アンドロイドの装飾の一覧（染めた色の付け方・描き方で使う）。 */
    public static final java.util.List<DeferredItem<io.github.genichimaruo.singulo.android.AndroidAccessoryItem>> ACCESSORIES = java.util.List.of(
            ANDROID_RIBBON, ANDROID_HAIR_FLOWER, ANDROID_BERET, ANDROID_HEADDRESS, ANDROID_CAPE, ANDROID_SCARF, ANDROID_WAIST_RIBBON, ANDROID_BOOK, ANDROID_BOUQUET, ANDROID_TEACUP, ANDROID_UMBRELLA);
    /** 流体の見本（作業の自動化の内部の目印。手に入れる物ではない）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.FluidSampleItem> FLUID_SAMPLE = ITEMS.register(
            "fluid_sample", () -> new io.github.genichimaruo.singulo.android.work.FluidSampleItem(new Item.Properties()));
    /** 化学物質の見本（Mekanism の化学物質。作業の自動化の内部の目印。手に入れる物ではない）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.ChemicalSampleItem> CHEMICAL_SAMPLE = ITEMS.register(
            "chemical_sample", () -> new io.github.genichimaruo.singulo.android.work.ChemicalSampleItem(new Item.Properties()));
    public static final DeferredItem<io.github.genichimaruo.singulo.android.work.ProcedureCardItem> PROCEDURE_CARD = ITEMS.register(
            "procedure_card", () -> new io.github.genichimaruo.singulo.android.work.ProcedureCardItem(new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SECURITY_DRONE_SPAWN_EGG = ITEMS.register("security_drone_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.SECURITY_DRONE, 0xECEEF0, 0x78D2F0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BLADE_ANDROID_SPAWN_EGG = ITEMS.register("blade_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.BLADE_ANDROID, 0xF0F2F6, 0x78E1FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> WHIP_ANDROID_SPAWN_EGG = ITEMS.register("whip_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.WHIP_ANDROID, 0x2A2A36, 0xFF6AC4, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SABER_ANDROID_SPAWN_EGG = ITEMS.register("saber_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.SABER_ANDROID, 0xDCE0E8, 0xFFC45A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> AXE_ANDROID_SPAWN_EGG = ITEMS.register("axe_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.AXE_ANDROID, 0xB86A48, 0xFF6E40, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BIT_ANDROID_SPAWN_EGG = ITEMS.register("bit_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.BIT_ANDROID, 0x2C345C, 0xB080FF, new Item.Properties()));
    /** 射撃型のスポーンエッグ（まだクリエイティブでだけ。クリエイティブタブにも JEI にも出さない。/give で取る）。 */
    public static final DeferredItem<DeferredSpawnEggItem> GUNNER_ANDROID_SPAWN_EGG = ITEMS.register("gunner_android_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.GUNNER_ANDROID, 0xC8E2F6, 0x6EF0D2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HORIZON_WARDEN_SPAWN_EGG = ITEMS.register("horizon_warden_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.HORIZON_WARDEN, 0xECEEF0, 0xE86060, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ECHO_SENTINEL_SPAWN_EGG = ITEMS.register("echo_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.ECHO_SENTINEL, 0xBFEFFF, 0x3A8FB0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GRAVITY_REMNANT_SPAWN_EGG = ITEMS.register("gravity_remnant_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.GRAVITY_REMNANT, 0x16121E, 0x9A6CFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ERGOSPHERE_LEVIATHAN_SPAWN_EGG = ITEMS.register("ergosphere_leviathan_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.ERGOSPHERE_LEVIATHAN, 0x1A1424, 0x8A5CFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LAST_ARCHIVIST_SPAWN_EGG = ITEMS.register("last_archivist_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.LAST_ARCHIVIST, 0xECE4D2, 0xFFD27A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FOUNDRY_OVERSEER_SPAWN_EGG = ITEMS.register("foundry_overseer_spawn_egg",
            () -> new DeferredSpawnEggItem(SinguloEntities.FOUNDRY_OVERSEER, 0x2E333D, 0xF08C3C, new Item.Properties()));
    /** 封印コンテナからしか出ない道具。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.item.GravityBootsItem> GRAVITY_BOOTS = ITEMS.register(
            "gravity_boots", () -> new io.github.genichimaruo.singulo.item.GravityBootsItem(new Item.Properties().rarity(Rarity.RARE), 3));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.CatalystStabilizerItem> CATALYST_STABILIZER = ITEMS.register(
            "catalyst_stabilizer", () -> new io.github.genichimaruo.singulo.item.CatalystStabilizerItem(new Item.Properties().rarity(Rarity.RARE), 4));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.DimensionalPocketItem> DIMENSIONAL_POCKET = ITEMS.register(
            "dimensional_pocket", () -> new io.github.genichimaruo.singulo.item.DimensionalPocketItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    /** 残光の翼（最後の記録者が落とす。クリエイティブのタブにも JEI にも出さない）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.item.AfterglowWingsItem> AFTERGLOW_WINGS = ITEMS.register(
            "afterglow_wings", () -> new io.github.genichimaruo.singulo.item.AfterglowWingsItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.SealedRecordItem> SEALED_RECORD = ITEMS.register(
            "sealed_record", () -> new io.github.genichimaruo.singulo.item.SealedRecordItem(new Item.Properties().rarity(Rarity.EPIC), 5));
    /** ニュートリノ感度モジュール（スキャナーと観測所の感度を上げる）。 */
    public static final DeferredItem<io.github.genichimaruo.singulo.item.ScannerModuleItem> SCANNER_MODULE_2 = ITEMS.register(
            "scanner_module_2", () -> new io.github.genichimaruo.singulo.item.ScannerModuleItem(new Item.Properties().rarity(Rarity.RARE), 4, 2));
    public static final DeferredItem<io.github.genichimaruo.singulo.item.ScannerModuleItem> SCANNER_MODULE_3 = ITEMS.register(
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
            Map.entry("android_charger", 2),
            Map.entry("android_armor_plate", 2),
            Map.entry("android_mining_upgrade", 2),
            Map.entry("android_felling_upgrade", 2),
            Map.entry("android_ribbon", 1),
            Map.entry("android_hair_flower", 1),
            Map.entry("android_beret", 1),
            Map.entry("android_headdress", 1),
            Map.entry("android_cape", 1),
            Map.entry("android_scarf", 1),
            Map.entry("android_waist_ribbon", 1),
            Map.entry("android_book", 1),
            Map.entry("android_bouquet", 1),
            Map.entry("android_teacup", 1),
            Map.entry("android_umbrella", 1),
            Map.entry("work_location_card", 2),
            Map.entry("area_card", 2),
            Map.entry("survey_glasses", 2),
            Map.entry("recipe_card", 2),
            Map.entry("procedure_card", 2),
            Map.entry("explorer_compass", 1),
            Map.entry("creative_catalyst", 6),
            Map.entry("builder_wand", 6),
            Map.entry("creative_energy_source", 6));

    /** クリエイティブタブの並び順（用途ごと、その中は段階順）。 */
    public static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();
    /** まだ表に出さないアイテム（クリエイティブタブにも JEI にも出さない）: 射撃型のスポーンエッグと設計メモリ。 */
    public static final java.util.Set<String> HIDDEN = java.util.Set.of("gunner_android_spawn_egg", "android_memory_gunner");
    /** 段階に関係なく、名前を紫（エピック）にするアイテム（とても珍しい物）。 */
    private static final java.util.Set<String> EPIC = java.util.Set.of("android_memory_gunner");

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
        TAB_ORDER.add(METEOR_COMPASS);
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
        block(SinguloBlocks.ANDROID_STATION, 2);
        block(SinguloBlocks.ANDROID_WORK_CONSOLE, 2);
        block(SinguloBlocks.ANDROID_SUPPLY_PORT, 2);
        block(SinguloBlocks.ANDROID_DELIVERY_PORT, 2);
        block(SinguloBlocks.ANDROID_RECIPE_RACK, 2);
        block(SinguloBlocks.ANDROID_WORKBENCH, 2);
        TAB_ORDER.add(ANDROID_CHARGER);
        TAB_ORDER.add(ANDROID_ARMOR_PLATE);
        TAB_ORDER.add(ANDROID_MINING_UPGRADE);
        TAB_ORDER.add(ANDROID_FELLING_UPGRADE);
        TAB_ORDER.add(WORK_LOCATION_CARD);
        TAB_ORDER.add(AREA_CARD);
        TAB_ORDER.add(SURVEY_GLASSES);
        TAB_ORDER.add(RECIPE_CARD);
        TAB_ORDER.add(PROCEDURE_CARD);
        TAB_ORDER.addAll(ACCESSORIES);
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
        block(SinguloBlocks.GENESIS_CORE, 5);
        block(SinguloBlocks.CRYOGENIC_TURBINE, 2);
        block(SinguloBlocks.MUON_FUSION_REACTOR, 2);
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
        block(SinguloBlocks.METEORITE_CORE, 2);
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
        block(SinguloBlocks.ANDROID_BARRIER_GLASS, 2);
        block(SinguloBlocks.ANDROID_BARRIER_GLASS_PATTERNED, 2);
        block(SinguloBlocks.ANDROID_GATE_GLASS, 2);
        block(SinguloBlocks.ANDROID_GATE_GLASS_PATTERNED, 2);
        block(SinguloBlocks.RUIN_GLASS, 1);
        block(SinguloBlocks.INTACT_RUIN_GLASS, 1);
        block(SinguloBlocks.REINFORCED_RUIN_GLASS, 5);
        block(SinguloBlocks.RUIN_CACHE, 1);
        block(SinguloBlocks.RUIN_GUARD_DOCK, 1);
        block(SinguloBlocks.RUIN_ANDROID_FABRICATOR, 1);
        block(SinguloBlocks.SEAL_CONSOLE, 1);
        block(SinguloBlocks.GUARDIAN_CORE, 1);
        block(SinguloBlocks.ECHO_PROJECTOR, 1);
        block(SinguloBlocks.FACTORY_LINE, 1);
        block(SinguloBlocks.FACTORY_CONVEYOR, 1);
        block(SinguloBlocks.LAB_PANEL, 1);
        block(SinguloBlocks.LAB_PANEL_LINED, 1);
        block(SinguloBlocks.LAB_TILES, 1);
        block(SinguloBlocks.LAB_FRAME, 1);
        block(SinguloBlocks.LAB_GLASS, 1);
        block(SinguloBlocks.FACILITY_LIGHT, 1);
        block(SinguloBlocks.EMERGENCY_LIGHT, 1);
        block(SinguloBlocks.REACTOR_CORE, 1);
        block(SinguloBlocks.POWER_CONDUIT, 1);
        block(SinguloBlocks.PRESSURE_HULL, 1);
        block(SinguloBlocks.BULKHEAD, 1);
        block(SinguloBlocks.REACTOR_CONSOLE, 1);
        block(SinguloBlocks.SERVER_RACK, 1);
        block(SinguloBlocks.LAB_TERMINAL, 1);
        block(SinguloBlocks.ANDROID_CAPSULE, 1);
        block(SinguloBlocks.DESK_MONITOR, 1);
        block(SinguloBlocks.COFFEE_MAKER, 1);
        block(SinguloBlocks.VENDING_MACHINE, 1);
        block(SinguloBlocks.PAPER_STACK, 1);
        block(SinguloBlocks.TRASH_BIN, 1);
        TAB_ORDER.add(SECURITY_DRONE_SPAWN_EGG);
        TAB_ORDER.add(BLADE_ANDROID_SPAWN_EGG);
        TAB_ORDER.add(WHIP_ANDROID_SPAWN_EGG);
        TAB_ORDER.add(SABER_ANDROID_SPAWN_EGG);
        TAB_ORDER.add(AXE_ANDROID_SPAWN_EGG);
        TAB_ORDER.add(BIT_ANDROID_SPAWN_EGG);
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
            DeferredItem<Item> item = ITEMS.register(def.id(), () -> create(def));
            if (!HIDDEN.contains(def.id())) {
                TAB_ORDER.add(item);
            }
        }
        // 用途ごとのまとまり（GeneratedContent.TAB_ORDER の順）。そこにないものは最後に段階順で
        TAB_ORDER.sort(java.util.Comparator.comparingInt((DeferredItem<? extends Item> item) -> tabIndex(item))
                .thenComparingInt(SinguloItems::stageOf));
    }

    private static Item create(ItemDef def) {
        Item.Properties props = new Item.Properties();
        if (EPIC.contains(def.id())) {
            props.rarity(Rarity.EPIC);
        } else if (def.stage() >= 5) {
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

    private static int tabIndex(DeferredItem<? extends Item> item) {
        int i = GeneratedContent.TAB_ORDER.indexOf(item.getId().getPath());
        return i < 0 ? Integer.MAX_VALUE : i;
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
