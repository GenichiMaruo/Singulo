package io.github.genichimaruo.singulo.registry;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.wormhole.WormholeData;
import java.util.function.Supplier;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** Typed item state stored in stack NBT on Minecraft 1.20.1. */
public final class SinguloComponents {
    public record Key<T>(String name, Codec<T> codec) {}
    private static <T> Supplier<Key<T>> key(String name, Codec<T> codec) {
        Key<T> key = new Key<>("singulo:" + name, codec);
        return () -> key;
    }
    public static final Supplier<Key<UsesData>> USES = key("uses", UsesData.CODEC);
    public static final Supplier<Key<WormholeData>> WORMHOLE = key("wormhole", WormholeData.CODEC);
    public static final Supplier<Key<Integer>> ENERGY = key("energy", Codec.INT);
    public static final Supplier<Key<Integer>> GRAVITY_MODE = key("gravity_mode", Codec.INT);
    public static final Supplier<Key<Integer>> EXOTIC_CHARGE = key("exotic_charge", Codec.INT);
    public static final Supplier<Key<Integer>> DARK_MATTER = key("dark_matter", Codec.INT);
    public static final Supplier<Key<Integer>> HOLO_SIZE = key("holo_size", Codec.INT);
    public static final Supplier<Key<Integer>> COMPASS_LEVEL = key("compass_level", Codec.INT);
    public static final Supplier<Key<Integer>> SCANNER_TIER = key("scanner_tier", Codec.INT);
    public static final Supplier<Key<Integer>> CATALYST_TIER = key("catalyst_tier", Codec.INT);
    public static final Supplier<Key<Boolean>> MIXED_SOURCE = key("mixed_source", Codec.BOOL);
    public static final Supplier<Key<Boolean>> CONE = key("cone", Codec.BOOL);
    public static final Supplier<Key<Boolean>> STABILIZED = key("stabilized", Codec.BOOL);
    public static <T> T get(ItemStack stack, Key<T> key) {
        if (stack.getTag() == null || !stack.getTag().contains(key.name())) return null;
        return key.codec().parse(NbtOps.INSTANCE, stack.getTag().get(key.name())).result().orElse(null);
    }
    public static <T> T getOrDefault(ItemStack stack, Key<T> key, T fallback) {
        T value = get(stack, key);
        return value == null ? fallback : value;
    }
    public static <T> void set(ItemStack stack, Key<T> key, T value) {
        if (value == null) { remove(stack, key); return; }
        stack.getOrCreateTag().put(key.name(), key.codec().encodeStart(NbtOps.INSTANCE, value)
                .getOrThrow(false, message -> { throw new IllegalArgumentException(message); }));
    }
    public static void remove(ItemStack stack, Key<?> key) {
        if (stack.getTag() != null) stack.getTag().remove(key.name());
    }
    private SinguloComponents() {}
}
