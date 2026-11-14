package io.github.genichimaruo.singulo.api.android;

/**
 * Optional marker for an addon {@code IItemHandler} that exposes a storage shared by several blocks (for example
 * a whole AE2 ME network seen through any of its interfaces), 0.19.0.
 *
 * <p>When Supply Ports and Work Consoles collect neighbouring storages, handlers with the same
 * {@link #sharedStorageKey()} are counted and used only once per collection, even when several such blocks touch
 * the same console's ports (Mekanism QIO dashboards are de-duplicated per frequency the same way).
 * Return the same key for the same backing storage while it exists, and different keys for different storages.
 */
public interface AndroidSharedStorage {
    long sharedStorageKey();
}
