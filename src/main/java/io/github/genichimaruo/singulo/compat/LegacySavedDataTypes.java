package io.github.genichimaruo.singulo.compat;

import java.nio.file.Files;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

/** Keeps the existing root NBT schema and imports old flat save files without deleting their backup. */
public final class LegacySavedDataTypes {
    private LegacySavedDataTypes() { }
    @FunctionalInterface public interface Writer<T> { CompoundTag save(T value, CompoundTag tag, HolderLookup.Provider lookup); }
    public static <T extends SavedData> SavedDataType<T> create(String oldName, Supplier<T> empty,
            BiFunction<CompoundTag, HolderLookup.Provider, T> read, Writer<T> write) {
        var id = Identifier.fromNamespaceAndPath("singulo", oldName);
        return new SavedDataType<>(id, level -> migrateOrCreate(level, id, oldName, empty, read),
            level -> CompoundTag.CODEC.xmap(tag -> read.apply(tag, level.registryAccess()),
                value -> write.save(value, new CompoundTag(), level.registryAccess())));
    }
    private static <T extends SavedData> T migrateOrCreate(ServerLevel level, Identifier id, String oldName,
            Supplier<T> empty, BiFunction<CompoundTag, HolderLookup.Provider, T> read) {
        var data = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        var current = id.withSuffix(".dat").resolveAgainst(data);
        // Factory is invoked after failed reads too. Never reset custody or replay stale legacy state in that case.
        if (Files.exists(current)) throw new IllegalStateException("Cannot decode existing Singulo saved data: " + id + "; restore a verified backup instead of resetting it");
        var legacy = data.resolve(oldName + ".dat");
        if (!Files.exists(legacy)) return empty.get();
        try {
            var root = NbtIo.readCompressed(legacy, NbtAccounter.unlimitedHeap());
            var tag = root.getCompound("data").orElseThrow(() -> new IllegalStateException("Missing legacy saved-data root: " + oldName));
            var value = read.apply(tag, level.registryAccess());
            value.setDirty(); // New namespaced file is written by normal world saving. Legacy file stays intact.
            return value;
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot safely migrate Singulo saved data " + oldName, failure);
        }
    }
}
