package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.Singulo;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 版: NeoForge のデータの付け足し（AttachmentType と entity.getData / setData）の代わり。
 * 中身は実体の Forge の永続データ（getPersistentData の "singulo_attachments"）へ、登録の名前ごとに codec で書く。
 * 読むたびに解かないよう、実体ごとに解いた中身を覚えておく。
 */
@Mod.EventBusSubscriber(modid = Singulo.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AttachmentType<T> {
    private static final String ROOT = "singulo_attachments";
    private static final Map<Entity, Map<AttachmentType<?>, Object>> CACHE = new WeakHashMap<>();
    private static final Map<AttachmentType<?>, String> NAMES = new HashMap<>();

    private final Supplier<T> defaultValue;
    @Nullable
    private final Codec<T> codec;
    private final boolean copyOnDeath;

    private AttachmentType(Supplier<T> defaultValue, @Nullable Codec<T> codec, boolean copyOnDeath) {
        this.defaultValue = defaultValue;
        this.codec = codec;
        this.copyOnDeath = copyOnDeath;
    }

    /** NeoForge の DeferredRegister.create(ATTACHMENT_TYPES, modid) の代わり。 */
    public static DeferredRegister<AttachmentType<?>> registry(String modid) {
        return DeferredRegister.local(modid);
    }

    public static <T> Builder<T> builder(Supplier<T> defaultValue) {
        return new Builder<>(defaultValue);
    }

    public static final class Builder<T> {
        private final Supplier<T> defaultValue;
        @Nullable
        private Codec<T> codec;
        private boolean copyOnDeath;

        private Builder(Supplier<T> defaultValue) {
            this.defaultValue = defaultValue;
        }

        public Builder<T> serialize(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(defaultValue, codec, copyOnDeath);
        }
    }

    private static String name(Supplier<? extends AttachmentType<?>> holder) {
        String name = holder instanceof DeferredHolder<?, ?> h ? h.getId().toString() : String.valueOf(holder);
        synchronized (NAMES) {
            NAMES.putIfAbsent(holder.get(), name);
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Entity entity, Supplier<? extends AttachmentType<T>> holder) {
        AttachmentType<T> type = holder.get();
        synchronized (CACHE) {
            Map<AttachmentType<?>, Object> map = CACHE.computeIfAbsent(entity, e -> new HashMap<>());
            Object cached = map.get(type);
            if (cached != null) {
                return (T) cached;
            }
            T value = type.defaultValue.get();
            Tag saved = entity.getPersistentData().getCompound(ROOT).get(name(holder));
            if (saved != null && type.codec != null) {
                value = type.codec.parse(entity.registryAccess().createSerializationContext(NbtOps.INSTANCE), saved)
                        .resultOrPartial(err -> Singulo.LOGGER.warn("Bad saved data {}: {}", name(holder), err)).orElse(value);
            }
            map.put(type, value);
            return value;
        }
    }

    public static <T> void set(Entity entity, Supplier<? extends AttachmentType<T>> holder, T value) {
        AttachmentType<T> type = holder.get();
        synchronized (CACHE) {
            CACHE.computeIfAbsent(entity, e -> new HashMap<>()).put(type, value);
        }
        if (type.codec != null) {
            CompoundTag root = entity.getPersistentData().getCompound(ROOT);
            type.codec.encodeStart(entity.registryAccess().createSerializationContext(NbtOps.INSTANCE), value)
                    .resultOrPartial(err -> Singulo.LOGGER.warn("Can't save data {}: {}", name(holder), err))
                    .ifPresent(tag -> root.put(name(holder), tag));
            entity.getPersistentData().put(ROOT, root);
        }
    }

    /** 死んで生まれ直したとき（とエンドからの帰還）、付け足したデータを新しい実体へ移す。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(ROOT);
        if (!old.isEmpty()) {
            // Forge の永続データは生まれ直しで引き継がれないので、ここで写す（copyOnDeath でない物は死んだときは写さない）
            CompoundTag copy = old.copy();
            if (event.isWasDeath()) {
                // 使ったことのある型の名前の一覧から、copyOnDeath でない物を除く
                synchronized (NAMES) {
                    NAMES.forEach((type, name) -> {
                        if (!type.copyOnDeath) {
                            copy.remove(name);
                        }
                    });
                }
            }
            event.getEntity().getPersistentData().put(ROOT, copy);
        }
    }
}
