package io.github.genichimaruo.singulo.recipe;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.HolderLookup;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class RecipePatchGameTests {
    private static final Identifier STEEL = Singulo.id("kiln/steel_ingot_from_oxygen");
    private static final Identifier FRAME = Singulo.id("precision_assembler/casimir_cavity_frame");
    private static final Identifier UPGRADE = Singulo.id("crafting/assembly_stock_upgrade");

    /** Exercise the normal preparation mixin and registry-aware recipe codecs on an isolated manager. */
    private static final class Manager extends RecipeManager {
        Manager(HolderLookup.Provider registries) {
            super(registries);
            injectContext(ICondition.IContext.EMPTY, registries);
        }
        void load(PackResources... packs) {
            try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(packs))) {
                var profiler = InactiveProfiler.INSTANCE;
                apply(prepare(resources, profiler), resources, profiler);
            }
        }
        MachineRecipe machine(Identifier id) { return (MachineRecipe) byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id)).orElseThrow().value(); }
    }

    private static Path path(Path pack, Identifier id) throws IOException {
        var file = pack.resolve("data").resolve(id.getNamespace()).resolve("recipe").resolve(id.getPath() + ".json");
        Files.createDirectories(file.getParent());
        return file;
    }
    private static PackResources pack(Path path) {
        String name = path.getFileName().toString();
        return new PathPackResources(new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty()), path);
    }

    @GameTest(template = "empty")
    public static void examplePackUpdatesVanillaFluidAndAssemblyRecipes(GameTestHelper h) throws IOException, CommandSyntaxException {
        Path tmp = Files.createTempDirectory("singulo-recipe-pack-");
        try {
            var base = tmp.resolve("base");
            var example = tmp.resolve("example");
            var serverResources = h.getLevel().getServer().getResourceManager();
            for (var id : List.of(STEEL, FRAME, UPGRADE)) {
                Files.writeString(path(base, id), RecipePatches.read(serverResources, id).toString());
                String file = "/fixtures/recipe-pack/data/singulo/recipe/" + id.getPath() + ".json";
                try (var input = RecipePatchGameTests.class.getResourceAsStream(file)) {
                    Files.copy(java.util.Objects.requireNonNull(input, file), path(example, id));
                }
            }
            var manager = new Manager(h.getLevel().registryAccess());
            manager.load(pack(base), pack(example));
            var steel = manager.machine(STEEL);
            h.assertTrue(steel.time() == 60 && steel.energy() == 60 && steel.result().getCount() == 3
                    && steel.fluidIngredients().getFirst().amount() == 125 && steel.ingredients().getFirst().count() == 2,
                    "Example patch lost a field or did not reach the machine recipe");
            h.assertTrue(manager.machine(FRAME).shape().equals(List.of("     ", "  B  ", " ADA ", "  A  ", "     ")),
                    "Precision assembly row override failed");
            var crafting = (ShapedRecipe) manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, UPGRADE)).orElseThrow().value();
            h.assertTrue(crafting.getIngredients().get(4).orElseThrow().test(Items.DIAMOND.getDefaultInstance()), "Vanilla crafting key was not patched");
            manager.load(pack(base));
            h.assertTrue(manager.machine(STEEL).fluidIngredients().getFirst().amount() == 250 && manager.machine(STEEL).result().getCount() == 2,
                    "Removing the example pack did not restore the original on reload");
            var message = new AtomicReference<Component>();
            var server = h.getLevel().getServer();
            var source = server.createCommandSourceStack().withSource(new CommandSource() {
                public void sendSystemMessage(Component component) { message.set(component); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            });
            server.getCommands().getDispatcher().execute("singulo recipe show " + STEEL, source);
            var file = message.get().getSiblings().getFirst();
            var copy = file.getStyle().getClickEvent();
            h.assertTrue(file.getString().equals("data/singulo/recipe/" + STEEL.getPath() + ".json")
                    && copy instanceof ClickEvent.CopyToClipboard clipboard
                    && com.google.gson.JsonParser.parseString(clipboard.value()).equals(RecipePatches.read(serverResources, STEEL)),
                    "Recipe inspection did not provide the path and copyable source");
            h.succeed();
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tmp.toFile());
        }
    }

    @GameTest(template = "empty")
    public static void configPacksDiscoverFoldersAndZipsAndRespectWorldSelection(GameTestHelper h) throws IOException {
        Path tmp = Files.createTempDirectory("singulo-instance-packs-");
        try {
            Path folder = tmp.resolve("datapacks");
            var repository = new PackRepository(consumer -> ModpackRecipePacks.discover(folder, consumer));
            repository.reload();
            h.assertTrue(repository.getAvailableIds().isEmpty() && !Files.exists(folder), "Empty instances should have no additional data pack");
            Path example = folder.resolve("example");
            String file = "data/singulo/recipe/" + STEEL.getPath() + ".json";
            Files.writeString(path(example, STEEL), "{\"singulo:patch\":{\"time\":30}}");
            try (var meta = RecipePatchGameTests.class.getResourceAsStream("/fixtures/recipe-pack/pack.mcmeta")) {
                Files.copy(java.util.Objects.requireNonNull(meta), example.resolve("pack.mcmeta"));
            }
            try (var zip = new ZipOutputStream(Files.newOutputStream(folder.resolve("example.zip")))) {
                for (String name : List.of("pack.mcmeta", file)) {
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(example.resolve(name), zip);
                    zip.closeEntry();
                }
            }
            repository.reload();
            var ids = List.of("singulo/example", "singulo/example.zip");
            h.assertTrue(repository.getAvailableIds().containsAll(ids), "Config recipe folder or ZIP missing");
            var base = tmp.resolve("base");
            Files.writeString(path(base, STEEL), RecipePatches.read(h.getLevel().getServer().getResourceManager(), STEEL).toString());
            var manager = new Manager(h.getLevel().registryAccess());
            for (String id : ids) {
                Pack pack = repository.getPack(id);
                h.assertTrue(pack != null && pack.getPackSource().shouldAddAutomatically() && pack.getDefaultPosition() == Pack.Position.TOP
                        && !pack.isRequired(), "Modpack recipes must be enabled by default and overridable per world");
                repository.setSelected(List.of(id));
                manager.load(pack(base), repository.openAllSelected().getFirst());
                h.assertTrue(manager.machine(STEEL).time() == 30, "Config pack did not apply to the underlying recipe");
            }
            repository.setSelected(List.of());
            h.assertTrue(repository.getSelectedIds().isEmpty(), "Config pack was forced on after disabling it");
            h.succeed();
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tmp.toFile());
        }
    }

    @GameTest(template = "empty")
    public static void patchesFollowPackPriorityAndKeepResourceConditions(GameTestHelper h) throws IOException {
        Path tmp = Files.createTempDirectory("singulo-pack-order-");
        try {
            var base = tmp.resolve("base");
            var lower = tmp.resolve("lower");
            var upper = tmp.resolve("upper");
            var resources = h.getLevel().getServer().getResourceManager();
            JsonObject original = RecipePatches.read(resources, STEEL);
            Files.writeString(path(base, STEEL), original.toString());
            Files.writeString(path(lower, STEEL), """
                    {"singulo:patch":{"time":50,"energy":30,"fluid_ingredients":null}}
                    """);
            Files.writeString(path(upper, STEEL), """
                    {"singulo:patch":{"time":15,"ingredients":{"0":{"ingredient":"minecraft:diamond"}}}}
                    """);
            var manager = new Manager(h.getLevel().registryAccess());
            manager.load(pack(base), pack(lower), pack(upper));
            var steel = manager.machine(STEEL);
            h.assertTrue(steel.time() == 15 && steel.energy() == 30 && steel.fluidIngredients().isEmpty()
                    && steel.ingredients().getFirst().ingredient().test(Items.DIAMOND.getDefaultInstance()), "Patch priority, removal or ingredient replacement failed");
            // A normal higher-priority file replaces both the recipe and the earlier patch.
            Files.writeString(path(upper, STEEL), original.toString());
            manager.load(pack(base), pack(lower), pack(upper));
            h.assertTrue(manager.machine(STEEL).time() == 80 && !manager.machine(STEEL).fluidIngredients().isEmpty(), "A lower patch overrode a full replacement");
            Files.writeString(path(upper, STEEL), """
                    {"singulo:patch":{"neoforge:conditions":[{"type":"neoforge:false"}]}}
                    """);
            manager.load(pack(base), pack(upper));
            h.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, STEEL)).isEmpty(), "A disabled recipe bypassed NeoForge conditions");
            Files.writeString(path(upper, STEEL), """
                    {"singulo:patch":{"ingredients":{"99":{"count":3}}}}
                    """);
            try (var invalid = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack(base), pack(upper)))) {
                try {
                    RecipePatches.read(invalid, STEEL);
                    h.fail("Invalid index accepted");
                } catch (IllegalArgumentException ex) {
                    h.assertTrue(ex.getMessage().contains("upper") && ex.getMessage().contains("/ingredients/99"), "Unclear patch error");
                }
            }
            Files.writeString(path(base, UPGRADE), RecipePatches.read(resources, UPGRADE).toString());
            manager.load(pack(base), pack(upper));
            h.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, STEEL)).isEmpty() && manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, UPGRADE)).isPresent(), "A bad patch affected another recipe");
            Files.writeString(path(upper, STEEL), "{\"singulo:patch\":{\"time\":10}}");
            try (var missing = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack(upper)))) {
                try {
                    RecipePatches.read(missing, STEEL);
                    h.fail("Patch accepted without a base recipe");
                } catch (IllegalArgumentException ex) {
                    h.assertTrue(ex.getMessage().contains("upper") && ex.getMessage().contains("no lower-priority base recipe"), "Missing base was not explained");
                }
            }
            var growth = Singulo.id("time_crystal_incubator/time_crystal_catalyst");
            var shortRecipe = RecipePatches.read(resources, growth);
            shortRecipe.addProperty("time", 7);
            Files.writeString(path(base, growth), shortRecipe.toString());
            manager.load(pack(base));
            h.assertTrue(RecipeTweaks.adjust(growth, manager.machine(growth)).time() == 7, "Default config overrode the pack's growth time");
            h.succeed();
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tmp.toFile());
        }
    }
}
