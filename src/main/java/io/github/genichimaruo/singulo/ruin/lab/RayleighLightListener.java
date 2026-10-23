package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/** Preserve the native engine's storage/updates; combine sunlight only at the public query boundary. */
public record RayleighLightListener(Level level, LayerLightEventListener nativeLight) implements LayerLightEventListener {
    @Override public int getLightValue(BlockPos pos) { return Math.max(nativeLight.getLightValue(pos), RayleighSky.light(level, pos)); }
    // Send only native storage over light packets. Each side derives artificial light from synced blocks;
    // baking it into packets would leave ghost sunlight after a distant panel is switched off.
    @Override public DataLayer getDataLayerData(SectionPos pos) { return nativeLight.getDataLayerData(pos); }
    @Override public void checkBlock(BlockPos pos) { nativeLight.checkBlock(pos); }
    @Override public boolean hasLightWork() { return nativeLight.hasLightWork(); }
    @Override public int runLightUpdates() { return nativeLight.runLightUpdates(); }
    @Override public void updateSectionStatus(SectionPos pos, boolean empty) { nativeLight.updateSectionStatus(pos, empty); }
    @Override public void setLightEnabled(ChunkPos pos, boolean enabled) { nativeLight.setLightEnabled(pos, enabled); }
    @Override public void propagateLightSources(ChunkPos pos) { nativeLight.propagateLightSources(pos); }
}
