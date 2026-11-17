package io.github.genichimaruo.singulo.genesis;

import io.github.genichimaruo.singulo.network.GalaxyPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 創世炉が生んだ銀河（ワールドに保存）。オーバーワールドの夜空に昇る。銀河ごとに種と性質（{@link GalaxyTraits}。0.21.0）を持つ。
 * 性質は種と同じ並びの別の配列に保存する（それまでの版の保存には性質がないので、みな 0 = 渦巻・色は種まかせとして読む）。
 */
public class GenesisGalaxies extends SavedData {
    static final String NAME = "singulo_galaxies";
    /** 夜空に並べる数の上限（古いものから消える）。 */
    public static final int MAX = 8;

    private final List<Long> seeds = new ArrayList<>();
    private final List<Integer> traits = new ArrayList<>();

    public static GenesisGalaxies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GenesisGalaxies::new, GenesisGalaxies::load, null), NAME);
    }

    public List<Long> seeds() {
        return List.copyOf(seeds);
    }

    public List<Integer> traits() {
        return List.copyOf(traits);
    }

    /** 銀河を加え、全員に知らせる（新しい銀河は夜空にゆっくり現れる）。 */
    public static void add(MinecraftServer server, long seed, int galaxyTraits) {
        GenesisGalaxies data = get(server);
        if (!data.seeds.contains(seed)) {
            data.seeds.add(seed);
            data.traits.add(galaxyTraits);
            while (data.seeds.size() > MAX) {
                data.seeds.remove(0);
                data.traits.remove(0);
            }
            data.setDirty();
        }
        PacketDistributor.sendToAllPlayers(data.payload(true));
    }

    private GalaxyPayload payload(boolean fresh) {
        return new GalaxyPayload(seeds(), traits(), fresh);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null
                && player.connection != null && player.connection.hasChannel(GalaxyPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, get(player.getServer()).payload(false));
        }
    }

    private static GenesisGalaxies load(CompoundTag tag, HolderLookup.Provider registries) {
        GenesisGalaxies data = new GenesisGalaxies();
        long[] s = tag.getLongArray("seeds");
        int[] t = tag.getIntArray("traits");
        for (int i = 0; i < s.length; i++) {
            data.seeds.add(s[i]);
            data.traits.add(i < t.length ? t[i] : 0);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("seeds", new LongArrayTag(seeds));
        tag.put("traits", new IntArrayTag(traits));
        return tag;
    }
}
