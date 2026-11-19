package io.github.genichimaruo.singulo.api.android;

import java.util.Map;

/** Optional chemical storage for integrations. Keys are registered Mekanism chemical IDs.
 * contents reports accessible stock. Transfers return the accepted/extracted quantity.
 * Simulation must leave the store untouched. No Mekanism classes are required to implement this API. */
public interface AndroidChemicalStorage {
    long amount(String id);
    long insert(String id, long amount, boolean simulate);
    long extract(String id, long amount, boolean simulate);
    Map<String, Long> contents();
}
