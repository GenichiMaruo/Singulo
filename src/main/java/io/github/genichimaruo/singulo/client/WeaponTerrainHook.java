package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Whether AndroidWeaponTerrainMixin's terrain wrap ran in the current frame.
 * The mixin is optional (require = 0): another mod may change LevelRenderer.renderLevel so that
 * it matches nothing. MixinExtras applies the wrap after the config plugin's postApply, so this
 * is observed at runtime instead. Solid terrain is drawn through the wrap before entities, so
 * weapons rendered later in the same frame already know whether beginTerrain() will be reached.
 */
public final class WeaponTerrainHook {
    private static boolean wrappedThisFrame;
    private static boolean judgePreviousFrame;
    private static boolean reported;

    private WeaponTerrainHook() {}

    /** Called from the wrapped renderChunkLayer calls. */
    public static void onTerrainDraw() {
        wrappedThisFrame = true;
    }

    /** True when the queued weapon batch will be drawn by AndroidWeaponLight.beginTerrain(). */
    static boolean active() {
        return wrappedThisFrame;
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (judgePreviousFrame && !reported) {
            reported = true;
            if (wrappedThisFrame) {
                Singulo.LOGGER.info("AndroidWeaponTerrainMixin applied: terrain draws in LevelRenderer.renderLevel are wrapped");
            } else {
                Singulo.LOGGER.warn("AndroidWeaponTerrainMixin is not active; another mod probably changed "
                        + "LevelRenderer.renderLevel. Android weapon transparency in Fast/Fancy graphics is disabled "
                        + "and weapons are drawn normally.");
            }
        }
        // Shader packs replace the terrain pipeline and already bypass the weapon batch.
        judgePreviousFrame = !GravitationalLensing.shaderPackInUse();
        wrappedThisFrame = false;
    }
}
