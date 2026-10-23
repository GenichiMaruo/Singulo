package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.ruin.lab.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Separate lightmap banks combine natural lighting with vanilla clear-noon sunlight without baking time/weather into meshes. */
public final class RayleighLighting {
    /** Four additional bits carry artificial sky separately from natural sky through vertex lighting. */
    public static final int NOON = 0x0F000000;
    private RayleighLighting() {}

    public static Level level(BlockAndTintGetter view) {
        return view.getLightEngine() instanceof RayleighLightAccess access ? access.singulo$level() : null;
    }
    /** Convert a public combined SKY query back to its two independent render contributions. */
    public static int pack(BlockAndTintGetter view, BlockPos pos, int packed) {
        Level level = level(view);
        if (level == null) return packed;
        int sunlight = RayleighSky.light(level, pos);
        if (sunlight == 0) return packed;
        var listener = view.getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.SKY);
        int natural = listener instanceof RayleighLightListener rayleigh ? rayleigh.nativeLight().getLightValue(pos) : 0;
        return (packed & 0xFFFF) | natural << 20 | sunlight << 24;
    }
    /** Preserve an interpolated natural-light coordinate and add the face's artificial contribution. */
    public static int tag(BlockAndTintGetter view, BlockPos pos, int packed) {
        Level level = level(view);
        return level == null ? packed : (packed & ~NOON) | RayleighSky.light(level, pos) << 24;
    }
    public static int blend(int a, int b, int c, int d, float wa, float wb, float wc, float wd) {
        int block = (int) ((a & 255) * wa + (b & 255) * wb + (c & 255) * wc + (d & 255) * wd);
        int natural = (int) ((a >>> 16 & 255) * wa + (b >>> 16 & 255) * wb + (c >>> 16 & 255) * wc + (d >>> 16 & 255) * wd);
        int artificial = (int) ((a >>> 24 & 15) * wa + (b >>> 24 & 15) * wb + (c >>> 24 & 15) * wc + (d >>> 24 & 15) * wd);
        return (block & 255) | (natural & 255) << 16 | (artificial & 15) << 24;
    }
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (long packed : RayleighSky.takeChangedSections(mc.level)) {
            mc.levelRenderer.setSectionDirtyWithNeighbors(SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed));
        }
    }
}
