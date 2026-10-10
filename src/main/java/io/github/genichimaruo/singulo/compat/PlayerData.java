package io.github.genichimaruo.singulo.compat;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Saved player state, including Forge's persisted data copied on death. */
public final class PlayerData {
    private static CompoundTag tag(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
    public static <T> T get(Player player, String key, Codec<T> codec, T fallback) {
        var data = tag(player);
        return data.contains(key) ? codec.parse(NbtOps.INSTANCE, data.get(key)).result().orElse(fallback) : fallback;
    }
    public static <T> void set(Player player, String key, Codec<T> codec, T value) {
        tag(player).put(key, codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false,
                message -> { throw new IllegalArgumentException(message); }));
    }
    public static void clone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, tag(event.getOriginal()).copy());
    }
    private PlayerData() {}
}
