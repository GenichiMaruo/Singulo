package io.github.genichimaruo.singulo.genesis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
 * 質量生成の表（data/&lt;ns&gt;/mass_synthesis/*.json）。創世炉が電力から作れる物と、その質量・希少度。
 * <p>
 * 1個を作る電力 = 質量 × genesisBaseCost（既定 1 GFE）× 10^希少度。希少度は 0（ありふれた物）〜 4（伝説の物）。
 * 質量1あたりの電力は、Pリアクターが質量1から取り出せる電力（最大で約 0.5 GFE）より必ず多いので、
 * 生成した物を炉に戻しても電力は増えない。表にない物は作れない（mod の圧縮ブロックや質量ペレットなどは載せない）。
 * アイテム単位の指定がタグより優先し、タグ同士は後に読んだものが優先する。
 */
public final class MassSynthesis extends SimpleJsonResourceReloadListener {
    public static final MassSynthesis INSTANCE = new MassSynthesis();
    public static final int MAX_TIER = 4;

    /** 表の1行: 質量と希少度。 */
    public record Entry(double mass, int tier) {}

    private record TagEntry(TagKey<Item> tag, Entry entry) {}

    private Map<Item, Entry> items = Map.of();
    private List<TagEntry> tags = List.of();

    private MassSynthesis() {
        super(new Gson(), "mass_synthesis");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, Entry> newItems = new HashMap<>();
        List<TagEntry> newTags = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
                for (JsonElement e : file.getValue().getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject o = e.getAsJsonObject();
                    Entry entry = new Entry(o.get("mass").getAsDouble(),
                            Math.max(0, Math.min(MAX_TIER, o.get("tier").getAsInt())));
                    if (o.has("item")) {
                        ResourceLocation id = ResourceLocation.parse(o.get("item").getAsString());
                        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> newItems.put(item, entry));
                    } else if (o.has("tag")) {
                        newTags.add(new TagEntry(TagKey.create(Registries.ITEM, ResourceLocation.parse(o.get("tag").getAsString())), entry));
                    }
                }
            } catch (RuntimeException ex) {
                Singulo.LOGGER.error("Failed to read mass synthesis table {}", file.getKey(), ex);
            }
        });
        this.items = newItems;
        this.tags = newTags;
        Singulo.LOGGER.info("Loaded {} item and {} tag mass synthesis entries", newItems.size(), newTags.size());
    }

    /** その物の行（作れない物は null）。 */
    @Nullable
    public Entry entry(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Entry direct = items.get(stack.getItem());
        if (direct != null) {
            return direct;
        }
        for (int i = tags.size() - 1; i >= 0; i--) {
            if (stack.is(tags.get(i).tag())) {
                return tags.get(i).entry();
            }
        }
        return null;
    }

    public static double baseCost() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.GENESIS_BASE_COST.get() : 1_000_000_000L;
    }

    /** 1個を作る電力（FE）。 */
    public static double cost(Entry e) {
        return e.mass() * baseCost() * Math.pow(10, e.tier());
    }
}
