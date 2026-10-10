package io.github.genichimaruo.singulo.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 質量値（data/&lt;ns&gt;/mass_values/*.json）。丸石1個を1とする。
 * アイテム単位の指定がタグより優先し、タグ同士は後に読んだものが優先する。
 */
public final class MassValues extends SimpleJsonResourceReloadListener {
    public static final MassValues INSTANCE = new MassValues();

    private record TagEntry(TagKey<Item> tag, double mass) {}

    private Map<Item, Double> items = Map.of();
    private List<TagEntry> tags = List.of();

    private MassValues() {
        super(new Gson(), "mass_values");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, Double> newItems = new HashMap<>();
        List<TagEntry> newTags = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
                for (JsonElement e : file.getValue().getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject o = e.getAsJsonObject();
                    double mass = o.get("mass").getAsDouble();
                    if (o.has("item")) {
                        ResourceLocation id = new ResourceLocation(o.get("item").getAsString());
                        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> newItems.put(item, mass));
                    } else if (o.has("tag")) {
                        newTags.add(new TagEntry(TagKey.create(Registries.ITEM,
                                new ResourceLocation(o.get("tag").getAsString())), mass));
                    }
                }
            } catch (RuntimeException ex) {
                Singulo.LOGGER.error("Failed to read mass values {}", file.getKey(), ex);
            }
        });
        this.items = newItems;
        this.tags = newTags;
        Singulo.LOGGER.info("Loaded {} item and {} tag mass values", newItems.size(), newTags.size());
    }

    /** 1個あたりの質量値。燃料にならないものは 0。 */
    public double massOf(ItemStack stack) {
        Double direct = items.get(stack.getItem());
        if (direct != null) {
            return direct;
        }
        for (int i = tags.size() - 1; i >= 0; i--) {
            if (stack.is(tags.get(i).tag())) {
                return tags.get(i).mass();
            }
        }
        return 0;
    }

    public static boolean isMetal(ItemStack stack) {
        return stack.is(SinguloTags.METAL_CORE);
    }
}
