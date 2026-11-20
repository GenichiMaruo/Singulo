package io.github.genichimaruo.singulo.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

/** Expand an explicitly marked recipe over the same recipe in lower-priority data packs.
 * The vanilla recipe manager still handles conditions, decoding, registration and client sync. */
public final class RecipePatches {
    private static final String MARKER = "singulo:patch";
    private static final FileToIdConverter FILES = FileToIdConverter.json("recipe");

    private RecipePatches() {}

    private static boolean isPatch(JsonElement json) {
        return json.isJsonObject() && json.getAsJsonObject().has(MARKER);
    }

    public static Map<Identifier, JsonElement> expand(Map<Identifier, JsonElement> recipes, ResourceManager resources) {
        var expanded = recipes;
        for (var entry : recipes.entrySet()) {
            if (!isPatch(entry.getValue())) continue;
            if (expanded == recipes) expanded = new LinkedHashMap<>(recipes);
            var id = entry.getKey();
            try {
                expanded.put(id, resolve(resources.getResourceStack(FILES.idToFile(id)), entry.getValue()));
            } catch (IOException | IllegalArgumentException | JsonParseException ex) {
                Singulo.LOGGER.error("Cannot load recipe {}: {}", id, ex.getMessage());
                expanded.remove(id);
            }
        }
        return expanded;
    }

    private record Layer(String pack, JsonObject changes) {}

    /** The merged source before recipe codecs and server balance settings. */
    public static JsonObject read(ResourceManager resources, Identifier id) throws IOException {
        var file = FILES.idToFile(id);
        var resource = resources.getResourceOrThrow(file);
        JsonObject json = readObject(resource);
        return isPatch(json) ? resolve(resources.getResourceStack(file), json) : json;
    }

    private static JsonObject readObject(Resource resource) throws IOException {
        try (var reader = resource.openAsReader()) {
            return GsonHelper.parse(reader);
        } catch (IOException | JsonParseException ex) {
            throw new IOException(resource.sourcePackId() + ": " + ex.getMessage(), ex);
        }
    }

    private static JsonObject resolve(List<Resource> stack, JsonElement visible) throws IOException {
        List<Layer> patches = new ArrayList<>();
        // ResourceManager orders these from lowest to highest and already applies pack filters.
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            JsonElement json = i == stack.size() - 1 ? visible : readObject(resource);
            if (isPatch(json)) {
                JsonObject wrapper = json.getAsJsonObject();
                if (wrapper.size() != 1 || !wrapper.get(MARKER).isJsonObject())
                    throw new IllegalArgumentException(resource.sourcePackId() + ": expected an object containing only " + MARKER);
                patches.add(new Layer(resource.sourcePackId(), wrapper.getAsJsonObject(MARKER)));
                continue;
            }
            // This full recipe was freshly parsed. Complete replacements hide everything below them.
            var result = json.getAsJsonObject();
            for (Layer patch : patches.reversed()) {
                try {
                    merge(result, patch.changes(), "");
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(patch.pack() + ": " + ex.getMessage(), ex);
                }
            }
            return result;
        }
        throw new IllegalArgumentException("no lower-priority base recipe for patch in "
                + (patches.isEmpty() ? "resource stack" : patches.getFirst().pack()));
    }

    private static JsonElement merge(JsonElement base, JsonElement change, String path) {
        if (!change.isJsonObject()) return change.deepCopy();
        if (base.isJsonArray()) {
            var array = base.getAsJsonArray();
            for (var entry : change.getAsJsonObject().entrySet()) {
                String slot = entry.getKey();
                int index;
                try {
                    index = Integer.parseInt(slot);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("expected an array index at " + path + "/" + slot);
                }
                if (index < 0 || index >= array.size() || !Integer.toString(index).equals(slot))
                    throw new IllegalArgumentException("invalid array index at " + path + "/" + slot + " (size " + array.size() + ")");
                if (entry.getValue().isJsonNull()) throw new IllegalArgumentException("replace the array to remove an entry at " + path + "/" + slot);
                array.set(index, merge(array.get(index), entry.getValue(), path + "/" + slot));
            }
            return array;
        }
        JsonObject object = base.isJsonObject() ? base.getAsJsonObject() : new JsonObject();
        for (var entry : change.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (entry.getValue().isJsonNull()) object.remove(key);
            else object.add(key, merge(object.has(key) ? object.get(key) : JsonNull.INSTANCE, entry.getValue(), path + "/" + key));
        }
        return object;
    }
}
