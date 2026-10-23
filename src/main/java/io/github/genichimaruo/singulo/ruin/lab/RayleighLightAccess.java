package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.world.level.Level;

/** Bridges a render region's light engine back to its owning level without retaining another world. */
public interface RayleighLightAccess {
    Level singulo$level();
}
