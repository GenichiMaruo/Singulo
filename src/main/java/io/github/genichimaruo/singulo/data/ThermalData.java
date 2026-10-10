package io.github.genichimaruo.singulo.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.Singulo;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 熱電発電機の高温源・低温源（data/&lt;ns&gt;/thermal/*.json）。
 * 液体（水・溶岩）は水源ブロックだけを数える。
 */
public final class ThermalData extends SimpleJsonResourceReloadListener {
    public static final ThermalData INSTANCE = new ThermalData();

    public record Hot(int temperature, boolean requiresLit) {}

    /** tolerance: 溶けずに維持できる温度差。becomes: 超えたときに変わるブロック。 */
    public record Cold(int temperature, int tolerance, Block becomes) {}

    private Map<Block, Hot> hot = Map.of();
    private Map<Block, Cold> cold = Map.of();

    private ThermalData() {
        super(new Gson(), "thermal");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, Hot> newHot = new HashMap<>();
        Map<Block, Cold> newCold = new HashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
                JsonObject root = file.getValue().getAsJsonObject();
                if (root.has("hot")) {
                    for (JsonElement e : root.getAsJsonArray("hot")) {
                        JsonObject o = e.getAsJsonObject();
                        block(o).ifPresent(b -> newHot.put(b, new Hot(o.get("temperature").getAsInt(),
                                o.has("requires_lit") && o.get("requires_lit").getAsBoolean())));
                    }
                }
                if (root.has("cold")) {
                    for (JsonElement e : root.getAsJsonArray("cold")) {
                        JsonObject o = e.getAsJsonObject();
                        Block becomes = BuiltInRegistries.BLOCK.get(new ResourceLocation(o.get("becomes").getAsString()));
                        block(o).ifPresent(b -> newCold.put(b, new Cold(o.get("temperature").getAsInt(),
                                o.get("tolerance").getAsInt(), becomes)));
                    }
                }
            } catch (RuntimeException ex) {
                Singulo.LOGGER.error("Failed to read thermal data {}", file.getKey(), ex);
            }
        });
        this.hot = newHot;
        this.cold = newCold;
    }

    private static java.util.Optional<Block> block(JsonObject o) {
        return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(o.get("block").getAsString()));
    }

    @Nullable
    public Hot hot(BlockState state) {
        Hot h = hot.get(state.getBlock());
        if (h == null || !isSourceOrSolid(state)) {
            return null;
        }
        if (h.requiresLit() && state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        return h;
    }

    @Nullable
    public Cold cold(BlockState state) {
        Cold c = cold.get(state.getBlock());
        return c != null && isSourceOrSolid(state) ? c : null;
    }

    private static boolean isSourceOrSolid(BlockState state) {
        return state.getFluidState().isEmpty() || state.getFluidState().isSource();
    }
}
