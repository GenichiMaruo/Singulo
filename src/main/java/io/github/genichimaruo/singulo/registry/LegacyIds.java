package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 改名した登録ID の、古い名前から新しい名前への引き継ぎ（0.20.0 で表記揺れをそろえた）。
 * 古いワールドのチャンク・持ち物・機械の中身・ブロックエンティティに残った古い ID は、読み込むときに新しい ID として扱われる
 * （NeoForge の登録の別名。新しい名前が登録されていて、古い名前が登録されていないときに効く）。
 * 古い名前は、ほかの物の名前に使い回さないこと。
 */
public final class LegacyIds {
    private LegacyIds() {}

    /** ブロックとそのアイテム（ブロックアイテムは同じ ID）。 */
    public static final Map<String, String> BLOCKS = Map.ofEntries(
            Map.entry("ruin_glass", "broken_ruin_glass"),
            Map.entry("core_controller", "reactor_core_controller"),
            Map.entry("gyro_drive", "reactor_gyro_drive"),
            Map.entry("extraction_port", "reactor_extraction_port"),
            Map.entry("reactor_core", "lab_reactor_core"),
            Map.entry("reactor_console", "lab_reactor_console"),
            Map.entry("mirror_plate", "casimir_cavity_mirror_plate"),
            Map.entry("focusing_magnet", "accelerator_focusing_magnet"),
            Map.entry("heat_exchange_core", "cooling_tower_heat_exchange_core"),
            Map.entry("spindle_cradle", "tidal_spindle_cradle"),
            Map.entry("worldline_anchor_small", "worldline_anchor"),
            Map.entry("worldline_anchor_advanced", "advanced_worldline_anchor"));

    /** ブロックではないアイテム。 */
    public static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("record_fragment", "crystal_memory"),
            Map.entry("bh_container", "empty_black_hole_vessel"),
            Map.entry("micro_black_hole", "micro_black_hole_vessel"),
            Map.entry("scanner_module_2", "neutrino_scanner_module_2"),
            Map.entry("scanner_module_3", "neutrino_scanner_module_3"));

    /** ブロックエンティティの種類（ブロックの ID にそろえた）。 */
    public static final Map<String, String> BLOCK_ENTITIES = Map.ofEntries(
            Map.entry("penrose_reactor", "reactor_core_controller"),
            Map.entry("degenerate_furnace", "degenerate_furnace_controller"),
            Map.entry("worldline_anchor_advanced", "advanced_worldline_anchor"),
            Map.entry("shield_tower", "shield_tower_core"),
            Map.entry("tipler_cylinder", "tipler_core"),
            Map.entry("containment_tank", "gravitational_containment_tank"),
            Map.entry("wormhole_generator", "wormhole_generator_core"),
            Map.entry("jet_forge", "jet_forge_core"),
            Map.entry("creative_energy", "creative_energy_source"),
            Map.entry("guard_dock", "ruin_guard_dock"),
            Map.entry("android_fabricator", "ruin_android_fabricator"),
            Map.entry("reactor_console", "lab_reactor_console"));

    /**
     * レシピ ID（データなので登録の別名が効かない）。機械・設定カード・鍛造炉が保存した選択中のレシピを、読み込むときに置き換える。
     */
    public static final Map<String, String> RECIPES = Map.ofEntries(
            Map.entry("crafting/focusing_magnet", "crafting/accelerator_focusing_magnet"),
            Map.entry("crafting/heat_exchange_core", "crafting/cooling_tower_heat_exchange_core"),
            Map.entry("crafting/heat_exchange_core_from_packed_ice", "crafting/cooling_tower_heat_exchange_core_from_packed_ice"),
            Map.entry("crafting/ruin_glass", "crafting/broken_ruin_glass"),
            Map.entry("crafting/spindle_cradle", "crafting/tidal_spindle_cradle"),
            Map.entry("crafting/worldline_anchor_small", "crafting/worldline_anchor"),
            Map.entry("precision_assembler/bh_container", "precision_assembler/empty_black_hole_vessel"),
            Map.entry("precision_assembler/core_controller", "precision_assembler/reactor_core_controller"),
            Map.entry("precision_assembler/extraction_port", "precision_assembler/reactor_extraction_port"),
            Map.entry("precision_assembler/gyro_drive", "precision_assembler/reactor_gyro_drive"),
            Map.entry("precision_assembler/mirror_plate", "precision_assembler/casimir_cavity_mirror_plate"),
            Map.entry("precision_assembler/scanner_module_2", "precision_assembler/neutrino_scanner_module_2"),
            Map.entry("precision_assembler/scanner_module_3", "precision_assembler/neutrino_scanner_module_3"),
            Map.entry("precision_assembler/worldline_anchor_advanced", "precision_assembler/advanced_worldline_anchor"),
            Map.entry("singularity_encapsulator/micro_black_hole", "singularity_encapsulator/micro_black_hole_vessel"));

    /** 保存されていたレシピ ID を今の ID にする（改名していなければそのまま。null は null）。 */
    @javax.annotation.Nullable
    public static net.minecraft.resources.ResourceLocation recipe(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation id) {
        if (id == null || !Singulo.MODID.equals(id.getNamespace())) {
            return id;
        }
        String renamed = RECIPES.get(id.getPath());
        return renamed == null ? id : Singulo.id(renamed);
    }

    /** 各登録に別名を足す（登録の前、Singulo の初期化で呼ぶ）。 */
    public static void apply() {
        alias(SinguloBlocks.BLOCKS, BLOCKS);
        alias(SinguloItems.ITEMS, BLOCKS);
        alias(SinguloItems.ITEMS, ITEMS);
        alias(SinguloBlockEntities.REGISTER, BLOCK_ENTITIES);
    }

    private static void alias(DeferredRegister<?> register, Map<String, String> renames) {
        renames.forEach((from, to) -> register.addAlias(Singulo.id(from), Singulo.id(to)));
    }
}
