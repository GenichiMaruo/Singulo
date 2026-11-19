package io.github.genichimaruo.singulo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(modid = "singulo", value = Dist.CLIENT)
public final class VanillaCatalogExport extends Screen {
    private static final int COLUMNS = 32;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<Identifier> items;

    private VanillaCatalogExport() {
        super(Component.literal("Vanilla item catalogue"));
        items = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("minecraft") && BuiltInRegistries.ITEM.get(id) != Items.AIR)
                .sorted().toList();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.getOverlay() == null && mc.screen == null) {
            mc.setScreen(new VanillaCatalogExport());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = 16 * COLUMNS, h = 16 * Math.ceilDiv(items.size(), COLUMNS);
        if (width < w || height < h || minecraft.getWindow().getGuiScale() != 1
                || !minecraft.getLanguageManager().getSelected().equals("ja_jp")) {
            throw new IllegalStateException("Expected Japanese and a full-size, scale-1 catalogue surface");
        }
        graphics.flush();
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        var names = new TreeMap<String, String>();
        for (int i = 0; i < items.size(); i++) {
            var id = items.get(i);
            var stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            String label = stack.getItem().getDescription().getString();
            String path = id.getPath();
            String detail = "item.minecraft." + path + ".desc";
            if (path.endsWith("_armor_trim_smithing_template"))
                detail = "trim_pattern.minecraft." + path.replace("_armor_trim_smithing_template", "");
            else if (path.equals("netherite_upgrade_smithing_template"))
                detail = "upgrade.minecraft.netherite_upgrade";
            if (I18n.exists(detail)) label += "（" + I18n.get(detail) + "）";
            names.put(id.toString(), label);
            graphics.renderFakeItem(stack, 16 * (i % COLUMNS), 16 * (i / COLUMNS));
        }
        graphics.flush();
        var buffer = minecraft.getMainRenderTarget();
        var output = Path.of(System.getProperty("singulo.catalogOutput"));
        try (var full = new NativeImage(buffer.width, buffer.height, false);
             var sheet = new NativeImage(w, h, false)) {
            Files.createDirectories(output);
            buffer.bindRead();
            full.downloadTexture(0, false);
            full.flipY();
            full.copyRect(sheet, 0, 0, 0, 0, w, h, false, false);
            sheet.writeToFile(output.resolve("icons.png"));
            Files.writeString(output.resolve("items.json"), JSON.toJson(names) + "\n");
            exportTags(output);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        minecraft.stop();
    }

    private static void collectTags(JsonElement node, Set<String> tags) {
        if (node.isJsonArray()) node.getAsJsonArray().forEach(value -> collectTags(value, tags));
        if (node.isJsonObject()) node.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals("tag") && entry.getValue().isJsonPrimitive()) tags.add(entry.getValue().getAsString());
            else collectTags(entry.getValue(), tags);
        });
    }

    private static void exportTags(Path output) throws IOException {
        var used = new TreeSet<String>();
        try (var paths = Files.walk(Path.of(System.getProperty("singulo.catalogRecipes")))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList())
                collectTags(JsonParser.parseString(Files.readString(path)), used);
        }
        var tags = new TreeMap<String, List<String>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            var id = pair.getFirst().location().toString();
            if (used.contains(id)) tags.put(id, pair.getSecond().stream()
                    .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).sorted().toList());
        });
        Files.writeString(output.resolve("ingredient_tags.json"), JSON.toJson(tags) + "\n");
    }
}
