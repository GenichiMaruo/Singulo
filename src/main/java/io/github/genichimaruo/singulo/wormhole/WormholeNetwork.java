package io.github.genichimaruo.singulo.wormhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 設置されたワームホールの口の場所（サーバー全体、ワールドに保存）。同じ対（pair）の口どうしを結ぶ。
 * 向こう側のチャンクが読み込まれていないときも、場所だけはここでわかる。
 */
public class WormholeNetwork extends SavedData {
    private static final net.minecraft.world.level.saveddata.SavedDataType<WormholeNetwork> TYPE =
        io.github.genichimaruo.singulo.compat.LegacySavedDataTypes.create("singulo_wormholes", WormholeNetwork::new, WormholeNetwork::load, WormholeNetwork::save);

    static final String NAME = "singulo_wormholes";

    private final Map<Long, List<GlobalPos>> mouths = new HashMap<>();

    public static WormholeNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TYPE);
    }

    public void register(long pair, GlobalPos pos) {
        List<GlobalPos> list = mouths.computeIfAbsent(pair, p -> new ArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos);
            setDirty();
        }
    }

    public void unregister(long pair, GlobalPos pos) {
        List<GlobalPos> list = mouths.get(pair);
        if (list != null && list.remove(pos)) {
            if (list.isEmpty()) {
                mouths.remove(pair);
            }
            setDirty();
        }
    }

    /** 同じ対のもう片方の口の場所。なければ null。 */
    @Nullable
    public GlobalPos partner(long pair, GlobalPos self) {
        List<GlobalPos> list = mouths.get(pair);
        if (list != null) {
            for (GlobalPos p : list) {
                if (!p.equals(self)) {
                    return p;
                }
            }
        }
        return null;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        mouths.forEach((pair, positions) -> {
            for (GlobalPos p : positions) {
                CompoundTag e = new CompoundTag();
                e.putLong("pair", pair);
                e.putString("dim", p.dimension().identifier().toString());
                e.putLong("pos", p.pos().asLong());
                list.add(e);
            }
        });
        tag.put("mouths", list);
        return tag;
    }

    static WormholeNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        WormholeNetwork net = new WormholeNetwork();
        for (Tag t : tag.getListOrEmpty("mouths")) {
            CompoundTag e = (CompoundTag) t;
            Identifier dim = Identifier.tryParse(e.getStringOr("dim", ""));
            if (dim != null) {
                net.mouths.computeIfAbsent(e.getLongOr("pair", 0L), p -> new ArrayList<>())
                        .add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLongOr("pos", 0L))));
            }
        }
        return net;
    }
}
