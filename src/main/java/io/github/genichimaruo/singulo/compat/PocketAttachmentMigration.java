package io.github.genichimaruo.singulo.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Lossless shape-only migration: item IDs, counts and components are never decoded or discarded. */
public final class PocketAttachmentMigration {
    public static final String KEY = "singulo:dimensional_pocket";
    private PocketAttachmentMigration() {}
    private static final java.util.Map<String,String> LEGACY_LISTS = java.util.Map.of(
        KEY, "items", "singulo:records", "entries", "singulo:prototype_memories", "entries", "singulo:discovered_ruins", "entries");
    public static CompoundTag migrate(CompoundTag attachments) {
        CompoundTag result = attachments;
        for (var entry : LEGACY_LISTS.entrySet()) {
            if (!(attachments.get(entry.getKey()) instanceof ListTag legacy)) continue;
            if (result == attachments) result = attachments.copy();
            CompoundTag wrapped = new CompoundTag();
            wrapped.put(entry.getValue(), legacy.copy());
            result.put(entry.getKey(), wrapped);
        }
        return result;
    }
}
