package io.github.genichimaruo.singulo.android;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.food.FoodPaste;
import io.github.genichimaruo.singulo.food.PrintedFood;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Item/fluid types and facility construction. Batch quantities, time and power are outside this test. */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidRecipeReachabilityGameTests {
    private record Route(String name, List<Set<Object>> inputs, Set<Object> products) {}
    private record Facility(Blueprints.Kind kind, int size) {}

    @GameTest(template = "empty")
    public static void allLoadedSinguloRecipesHaveAnAcquisitionPath(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        Set<Object> known = new HashSet<>();
        // Survival supplies, including witnesses for vanilla/commons tags. Creative-only items cannot seed a route.
        String vanilla = """
                amethyst_shard black_dye blast_furnace blaze_powder blaze_rod blue_dye blue_ice bone bone_meal book bucket calcite
                chain charcoal chest clay_ball clock coal coal_block cobblestone compass copper_block copper_ingot crafting_table
                dandelion deepslate diamond diamond_axe diamond_hoe diamond_pickaxe echo_shard egg ender_eye ender_pearl fishing_rod
                flint glass glass_bottle glass_pane glowstone_dust gold_ingot gold_nugget golden_axe golden_boots golden_chestplate
                golden_helmet golden_hoe golden_leggings golden_pickaxe golden_shovel golden_sword gravel green_dye gunpowder honeycomb
                hopper ice iron_axe iron_bars iron_block iron_boots iron_chestplate iron_helmet iron_hoe iron_ingot iron_leggings
                iron_pickaxe iron_shovel iron_sword iron_trapdoor lapis_lazuli leather lightning_rod moss_block nether_star netherite_ingot
                oak_log oak_planks obsidian orange_dye packed_ice paper phantom_membrane piston purple_dye quartz raw_copper red_dye
                redstone sand sandstone sculk sculk_sensor slime_ball snow_block stick stone string torch tuff wheat white_dye white_wool""";
        for (String id : vanilla.split("\\s+")) known.add(BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(id)).orElseThrow());
        for (String id : List.of("alloy_infused", "basic_control_circuit", "basic_chemical_tank", "ingot_osmium"))
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("mekanism", id)).ifPresent(known::add);
        known.addAll(List.of(Fluids.WATER, Fluids.LAVA));
        explorationLoot(level, known);
        // Lightning, boss chapter awards and memory recovery are implemented in world/entity code.
        for (String name : ("fulgurite fulgurite_block echo_chapter gravity_chapter horizon_chapter android_memory_blade "
                + "android_memory_axe android_memory_whip android_memory_bit android_memory_saber android_memory_gunner "
                + "drone_memory drone_memory_jet drone_memory_gravity").split(" ")) known.add(item(name));

        List<Route> routes = new ArrayList<>();
        var stations = new HashMap<String, Block>();
        for (MachineType t : MachineType.values()) stations.put(t.id(), t.isMultiblock()
                ? SinguloBlocks.CONTROLLERS.get(t).get() : SinguloBlocks.MACHINES.get(t).get());
        stations.put("accretion_jet_forge", SinguloBlocks.JET_FORGE_CORE.get());
        stations.put("tidal_spindle", SinguloBlocks.TIDAL_SPINDLE.get());
        Set<Facility> facilities = new HashSet<>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(Singulo.MODID)) continue;
            var recipe = holder.value();
            List<Set<Object>> inputs = new ArrayList<>();
            Set<Object> products = new HashSet<>();
            if (recipe instanceof MachineRecipe base) {
                var machine = RecipeTweaks.adjust(holder.id(), base);
                if (machine == null) continue;
                var block = Objects.requireNonNull(stations.get(machine.station()), machine.station());
                var kind = Blueprints.kindOf(block);
                inputs.add(Set.of(kind == null ? block.asItem() : facility(kind, machine.minSize(), facilities)));
                for (var input : machine.ingredients()) {
                    if (input.uses() <= 0) inputs.add(choices(input.ingredient()));
                    else {
                        Set<Object> tools = new HashSet<>();
                        for (Item item : BuiltInRegistries.ITEM) if (input.usesFrom(item.getDefaultInstance()) > 0) tools.add(item);
                        inputs.add(tools);
                    }
                }
                for (var input : machine.fluidIngredients()) inputs.add(Set.of(input.fluid()));
                machine.restore().ifPresent(target -> inputs.add(choices(target)));
                machine.mass().ifPresent(m -> inputs.add(Set.of(m.metalOnly() ? Items.IRON_BLOCK : Items.COBBLESTONE)));
                if (!machine.result().isEmpty()) products.add(machine.result().getItem());
                if (!machine.byproduct().isEmpty()) products.add(machine.byproduct().getItem());
                machine.fluidResults().forEach(fluid -> products.add(fluid.getFluid()));
            } else {
                h.assertTrue(recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe || recipe instanceof StonecutterRecipe,
                        "Unmodeled Singulo recipe type " + holder.id());
                for (var ingredient : recipe.getIngredients()) {
                    if (ingredient != Ingredient.EMPTY) inputs.add(choices(ingredient));
                }
                var result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty()) products.add(result.getItem());
            }
            h.assertTrue(!products.isEmpty(), "Recipe has no product " + holder.id());
            routes.add(new Route(holder.id().toString(), inputs, products));
        }
        h.assertTrue(!routes.isEmpty(), "No Singulo recipes were loaded");
        // These products come from dedicated world/machine operations rather than recipe-manager entries.
        var ring = facility(Blueprints.Kind.PARTICLE_ACCELERATOR, 24, facilities);
        routes.add(route("accelerator magnetic monopole", item("magnetic_monopole"),
                facility(Blueprints.Kind.PARTICLE_ACCELERATOR, 0, facilities), item("muon_bundle")));
        routes.add(route("strangelet collection", item("strange_matter"), ring, item("muon_bundle"), item("magnetic_bottle")));
        routes.add(route("void collection", item("void_dust"), item("void_collector")));
        routes.add(route("halo collection", BuiltInRegistries.FLUID.get(Singulo.id("dark_matter")), item("halo_collector"),
                facility(Blueprints.Kind.PENROSE_REACTOR, 0, facilities), item("singularity_seed")));
        routes.add(route("wormhole pair generation", item("unstable_wormhole_mouth"), facility(Blueprints.Kind.WORMHOLE_GENERATOR, 0, facilities)));
        routes.add(route("wormhole stabilization", item("wormhole_mouth"), item("unstable_wormhole_mouth"), item("exotic_matter"),
                item("wormhole_mouth_casing"), item("wormhole_stabilizer")));
        for (String catalyst : List.of("muon_catalyst", "time_crystal_catalyst", "bose_condensate_catalyst", "singularity_core"))
            routes.add(route("use up " + catalyst, item("spent_" + catalyst), item(catalyst)));
        for (var food : PrintedFood.values()) {
            List<Set<Object>> needs = new ArrayList<>(List.of(Set.of(item("food_printer")), Set.of(item("food_cartridge"))));
            for (int i = 0; i < FoodPaste.KINDS; i++) {
                if (food.cost(i) > 0) needs.add(choices(Ingredient.of(FoodPaste.TAGS[i])));
            }
            routes.add(new Route("print " + food.id(), needs, Set.of(item(food.id()))));
        }
        for (var facility : facilities) {
            var parts = new HashSet<Item>();
            for (var state : Blueprints.layout(facility.kind(), BlockPos.ZERO, Direction.NORTH, facility.size()).values()) {
                if (!state.isAir()) parts.add(state.getBlock().asItem());
            }
            routes.add(route(facility.toString(), facility, parts.toArray()));
        }

        List<Route> pending = new ArrayList<>(routes);
        // Evaluate each recipe even if its result can also be looted or another recipe makes the same item.
        while (pending.removeIf(route -> {
            if (route.inputs().stream().anyMatch(options -> Collections.disjoint(options, known))) return false;
            known.addAll(route.products());
            return true;
        })) {}
        h.assertTrue(pending.isEmpty(), "Unreachable recipes/production steps: " + pending.stream().map(route ->
                route.name() + " needs " + route.inputs().stream().filter(options -> Collections.disjoint(options, known)).toList()).toList());
        h.succeed();
    }

    private static Set<Object> choices(Ingredient ingredient) {
        Set<Object> items = new HashSet<>();
        for (ItemStack choice : ingredient.getItems()) if (!choice.isEmpty() && ingredient.test(choice)) items.add(choice.getItem());
        return items;
    }

    private static Facility facility(Blueprints.Kind kind, int minSize, Set<Facility> facilities) {
        var facility = new Facility(kind, Math.max(minSize, kind.defaultSize()));
        if (!kind.validSize(facility.size())) throw new IllegalStateException("Invalid facility size " + facility);
        facilities.add(facility);
        return facility;
    }

    private static Route route(String name, Object product, Object... required) {
        return new Route(name, Arrays.stream(required).map(Set::of).toList(), Set.of(product));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Singulo.id(id)).orElseThrow(() -> new IllegalArgumentException(id));
    }

    private static void explorationLoot(ServerLevel level, Set<Object> known) throws IOException {
        var naturalBlocks = Set.of("meteorite_core", "meteorite_crust", "meteoric_iron_chunk", "stardust_cluster",
                "factory_conveyor", "guardian_core", "lab_door", "lab_reactor_core", "lab_terminal", "pressure_hull",
                "ruin_android_fabricator", "ruin_cache", "ruin_guard_dock", "sealed_record");
        var resources = level.getServer().getResourceManager().listResources("loot_table", id ->
                id.getNamespace().equals(Singulo.MODID) && id.getPath().endsWith(".json"));
        for (var entry : resources.entrySet()) {
            String path = entry.getKey().getPath().substring("loot_table/".length()).replace(".json", "");
            if (!(path.startsWith("ruins/") || path.startsWith("entities/") || path.startsWith("sealed/")
                    || path.startsWith("chests/") && !path.endsWith("lab_test_hut")
                    || path.startsWith("blocks/") && naturalBlocks.contains(path.substring("blocks/".length())))) continue;
            try (var reader = entry.getValue().openAsReader()) {
                lootItems(JsonParser.parseReader(reader), known);
            }
        }
    }

    private static void lootItems(JsonElement value, Set<Object> known) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> lootItems(child, known));
        if (!value.isJsonObject()) return;
        var object = value.getAsJsonObject();
        if (object.has("type") && object.get("type").isJsonPrimitive() && object.get("type").getAsString().equals("minecraft:item"))
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(object.get("name").getAsString())).ifPresent(known::add);
        object.asMap().values().forEach(child -> lootItems(child, known));
    }
}
