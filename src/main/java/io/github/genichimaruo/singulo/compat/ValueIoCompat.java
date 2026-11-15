package io.github.genichimaruo.singulo.compat;

import com.mojang.serialization.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Preserves existing root-level save keys while adapting to contextual value I/O. */
public final class ValueIoCompat {
    private ValueIoCompat() {}
    public static CompoundTag read(ValueInput input) {
        return input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
    }
    private static final MapCodec<BiConsumer<CompoundTag, HolderLookup.Provider>> WRITER = new MapCodec<>() {
        public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
        public <T> DataResult<BiConsumer<CompoundTag, HolderLookup.Provider>> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.error(() -> "Write-only legacy save bridge");
        }
        public <T> RecordBuilder<T> encode(BiConsumer<CompoundTag, HolderLookup.Provider> writer, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (!(ops instanceof RegistryOps<?> registryOps) || !(registryOps.lookupProvider instanceof RegistryOps.HolderLookupAdapter adapter)) {
                throw new IllegalStateException("Singulo persistence requires a registry-aware value output");
            }
            CompoundTag tag = new CompoundTag();
            writer.accept(tag, adapter.lookupProvider);
            for (var entry : tag.entrySet()) prefix.add(entry.getKey(), NbtOps.INSTANCE.convertTo(ops, entry.getValue()));
            return prefix;
        }
    };
    public static void write(ValueOutput output, BiConsumer<CompoundTag, HolderLookup.Provider> writer) {
        output.store(WRITER, writer);
    }
}
