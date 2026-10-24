package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;

/** A Sodium chunk-build snapshot, never a live-world/light-engine lookup. */
public interface SodiumRayleighView {
    RayleighSky.LightSnapshot singulo$sunlight();
    int singulo$lightEpoch();
}
