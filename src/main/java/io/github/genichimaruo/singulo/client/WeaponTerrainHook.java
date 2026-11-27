package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;

/**
 * Whether AndroidWeaponTerrainMixin's terrain wrap is running.
 * The mixin is optional (require = 0): its target is a compiler-generated lambda in
 * LevelRenderer.addMainPass, which another mod or a game update can change. MixinExtras applies the
 * wrap after a config plugin's postApply, so this is observed at runtime instead.
 * Entities, including weapons, are extracted before the frame's main pass runs, so the decision
 * uses the previous frame: the first frame, and any frame after the wrap stopped, keep vanilla buffers.
 */
public final class WeaponTerrainHook {
    private static boolean wrappedThisFrame;
    private static boolean wrappedLastFrame;
    private static boolean reported;

    private WeaponTerrainHook() {}

    /** Called from the wrapped ChunkSectionsToRender.renderGroup calls. */
    public static void onTerrainDraw() {
        wrappedThisFrame = true;
    }

    /** True when a weapon batch queued now will be drawn by AndroidWeaponLight.beginTerrain(). */
    static boolean active() {
        return wrappedLastFrame;
    }

    /** End of the frame's terrain. AfterSky is skipped in lava, powder snow and skyless dimensions. */
    static void afterTranslucentBlocks() {
        // Shader packs replace the terrain pipeline and already bypass the weapon batch.
        if (!reported && !GravitationalLensing.shaderPackInUse()) {
            reported = true;
            if (wrappedThisFrame) {
                Singulo.LOGGER.info("AndroidWeaponTerrainMixin applied: terrain draws in LevelRenderer.addMainPass are wrapped");
            } else {
                Singulo.LOGGER.warn("AndroidWeaponTerrainMixin is not active; another mod probably changed "
                        + "LevelRenderer.addMainPass. Android weapon transparency in Fast/Fancy graphics is disabled "
                        + "and weapons are drawn normally.");
            }
        }
        wrappedLastFrame = wrappedThisFrame;
        wrappedThisFrame = false;
    }
}
