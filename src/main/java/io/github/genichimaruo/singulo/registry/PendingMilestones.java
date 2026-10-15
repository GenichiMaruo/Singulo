package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** ログアウト中に完了した印刷・納品は、次のログイン時に通知する。ディメンションに依存しない。 */
@EventBusSubscriber(modid = Singulo.MODID)
public final class PendingMilestones extends SavedData {
    private final Map<UUID, Set<String>> pending = new HashMap<>();
    private static final Set<String> ALLOWED = Set.of("android_printed", "android_order_complete");

    public static PendingMilestones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PendingMilestones::new, PendingMilestones::load, null), "singulo_pending_milestones");
    }

    public static void award(MinecraftServer server, UUID owner, String milestone) {
        if (!ALLOWED.contains(milestone)) {
            throw new IllegalArgumentException("Unsupported deferred milestone: " + milestone);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            SinguloTriggers.milestone(player, milestone);
        } else {
            PendingMilestones data = get(server);
            if (data.pending.computeIfAbsent(owner, key -> new HashSet<>()).add(milestone)) {
                data.setDirty();
            }
        }
    }

    /** ログイン時とテストから共通の経路で、重複せず通知する。 */
    public void deliver(ServerPlayer player) {
        Set<String> milestones = pending.remove(player.getUUID());
        if (milestones != null) {
            for (String id : milestones) {
                SinguloTriggers.milestone(player, id);
            }
            setDirty();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player.server).deliver(player);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pending.forEach((owner, ids) -> ids.forEach(id -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.putString("id", id);
            list.add(entry);
        }));
        tag.put("pending", list);
        return tag;
    }

    public static PendingMilestones load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingMilestones data = new PendingMilestones();
        for (Tag element : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            String id = entry.getString("id");
            if (entry.hasUUID("owner") && ALLOWED.contains(id)) {
                data.pending.computeIfAbsent(entry.getUUID("owner"), key -> new HashSet<>()).add(id);
            }
        }
        return data;
    }
}
