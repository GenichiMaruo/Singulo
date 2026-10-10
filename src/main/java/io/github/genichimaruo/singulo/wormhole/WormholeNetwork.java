package io.github.genichimaruo.singulo.wormhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 設置されたワームホールの口の場所（サーバー全体、ワールドに保存）。同じ対（pair）の口どうしを結ぶ。
 * 向こう側のチャンクが読み込まれていないときも、場所だけはここでわかる。
 */
public class WormholeNetwork extends SavedData {
    static final String NAME = "singulo_wormholes";

    private final Map<Long, List<GlobalPos>> mouths = new HashMap<>();

    public static WormholeNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WormholeNetwork::load, WormholeNetwork::new, NAME);
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

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        mouths.forEach((pair, positions) -> {
            for (GlobalPos p : positions) {
                CompoundTag e = new CompoundTag();
                e.putLong("pair", pair);
                e.putString("dim", p.dimension().location().toString());
                e.putLong("pos", p.pos().asLong());
                list.add(e);
            }
        });
        tag.put("mouths", list);
        return tag;
    }

    static WormholeNetwork load(CompoundTag tag) {
        WormholeNetwork net = new WormholeNetwork();
        for (Tag t : tag.getList("mouths", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dim"));
            if (dim != null) {
                net.mouths.computeIfAbsent(e.getLong("pair"), p -> new ArrayList<>())
                        .add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("pos"))));
            }
        }
        return net;
    }
}
