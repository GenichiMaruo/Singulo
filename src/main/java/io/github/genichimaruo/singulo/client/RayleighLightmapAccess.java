package io.github.genichimaruo.singulo.client;

/** Complete the synthetic sunlight bank even when a mod replaces vanilla lightmap updates. */
public interface RayleighLightmapAccess {
    boolean singulo$isNoonPass();
    void singulo$ensureNoon(float partialTick);
}
