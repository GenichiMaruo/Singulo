package io.github.genichimaruo.singulo.genesis;

import io.github.genichimaruo.singulo.network.GalaxyPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 創世炉が生んだ銀河（ワールドに保存）。オーバーワールドの夜空に昇る。 */
public class GenesisGalaxies extends SavedData {
    static final String NAME = "singulo_galaxies";
    /** 夜空に並べる数の上限（古いものから消える）。 */
    public static final int MAX = 8;

    private final List<Long> seeds = new ArrayList<>();

    public static GenesisGalaxies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GenesisGalaxies::new, GenesisGalaxies::load, null), NAME);
    }

    public List<Long> seeds() {
        return List.copyOf(seeds);
    }

    /** 銀河を加え、全員に知らせる（新しい銀河は夜空にゆっくり現れる）。 */
    public static void add(MinecraftServer server, long seed) {
        GenesisGalaxies data = get(server);
        if (!data.seeds.contains(seed)) {
            data.seeds.add(seed);
            while (data.seeds.size() > MAX) {
                data.seeds.remove(0);
            }
            data.setDirty();
        }
        PacketDistributor.sendToAllPlayers(new GalaxyPayload(data.seeds(), true));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null
                && player.connection != null && player.connection.hasChannel(GalaxyPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new GalaxyPayload(get(player.getServer()).seeds(), false));
        }
    }

    private static GenesisGalaxies load(CompoundTag tag, HolderLookup.Provider registries) {
        GenesisGalaxies data = new GenesisGalaxies();
        for (long s : tag.getLongArray("seeds")) {
            data.seeds.add(s);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("seeds", new LongArrayTag(seeds));
        return tag;
    }
}
