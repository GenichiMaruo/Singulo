package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.render.frapi.helper.ColorHelper;
import org.lwjgl.system.MemoryUtil;

/** Exercises the real mixed-in Sodium encoder and model-light merger, not a duplicate codec. */
public final class SodiumNoonReviewHarness {
    public static void verifyEmissive(io.github.genichimaruo.singulo.ruin.lab.RayleighSky.LightSnapshot sunlight) {
        var pos = new net.minecraft.core.BlockPos(10, 241, 10);
        if (!sunlight.near(pos) || sunlight.light(pos.getX(), pos.getY(), pos.getZ()) != 0) {
            throw new AssertionError("Emissive test must be near a panel but outside its sunlight field");
        }
        var view = (net.minecraft.world.level.BlockAndTintGetter) java.lang.reflect.Proxy.newProxyInstance(
                SodiumNoonReviewHarness.class.getClassLoader(),
                new Class<?>[] {net.minecraft.world.level.BlockAndTintGetter.class, SodiumRayleighView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "singulo$sunlight" -> sunlight;
                    case "singulo$lightEpoch" -> 1L;
                    case "getShade" -> 1F;
                    default -> throw new AssertionError("Unexpected live view access: " + method.getName());
                });
        for (boolean selfEmissive : new boolean[] {false, true}) {
            var cache = new net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess() {
                { level = view; }
                @Override public int get(int x, int y, int z) {
                    return packAO(1) | packEM(selfEmissive || !pos.equals(new net.minecraft.core.BlockPos(x, y, z)));
                }
            };
            var original = new net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline(cache);
            var pipeline = new SodiumRayleighPipeline(cache, net.caffeinemc.mods.sodium.client.model.light.LightMode.SMOOTH, original);
            var quad = new net.caffeinemc.mods.sodium.client.model.quad.ModelQuad();
            quad.setFlags(net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags.IS_ALIGNED);
            var expected = new net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData();
            var actual = new net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData();
            original.calculate(quad, pos, expected, null, net.minecraft.core.Direction.UP, true, false);
            pipeline.calculate(quad, pos, actual, null, net.minecraft.core.Direction.UP, true, false);
            for (int i = 0; i < 4; i++) {
                if (actual.lm[i] != expected.lm[i]) throw new AssertionError("Emissive geometry invented sunlight or changed native lighting");
            }
        }
        Singulo.LOGGER.info("SINGULO_SODIUM_NOON_EMISSIVE_COMPLETE");
    }

    public static void verify() {
        var vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        var encoder = new CompactChunkVertex().getEncoder();
        long ptr = MemoryUtil.nmemAlloc(80);
        try {
            for (int i = 0; i < 4; i++) {
                vertices[i].x = 1 + i;
                vertices[i].y = 2 + i;
                vertices[i].z = 3 + i;
                vertices[i].ao = 1;
                vertices[i].color = -1;
            }
            long[] base = new long[4];
            for (var vertex : vertices) vertex.light = 0x00A00070;
            encoder.write(ptr, 5, vertices, 173);
            for (int i = 0; i < 4; i++) base[i] = MemoryUtil.memGetLong(ptr + i * 20L);
            for (int sun = 0; sun <= 240; sun++) {
                for (var vertex : vertices) vertex.light = ColorHelper.maxBrightness(0x00C00030, (sun << 24) | 0x00A00070);
                encoder.write(ptr, 5, vertices, 173);
                for (int i = 0; i < 4; i++) {
                    long at = ptr + i * 20L;
                    int hi = MemoryUtil.memGetInt(at), lo = MemoryUtil.memGetInt(at + 4), data = MemoryUtil.memGetInt(at + 16);
                    int decoded = (hi >>> 30) | (lo >>> 30) << 2 | ((data >>> 20) & 15) << 4;
                    if (decoded != sun || (hi & 0x3FFFFFFF) != ((int) base[i] & 0x3FFFFFFF)
                            || (lo & 0x3FFFFFFF) != ((int) (base[i] >>> 32) & 0x3FFFFFFF)
                            || (data & 0xFF0FFFFF) != 0xAD05C070) {
                        throw new AssertionError("Sodium noon vertex roundtrip/preservation failed at " + sun);
                    }
                }
            }
        } finally { MemoryUtil.nmemFree(ptr); }
        Singulo.LOGGER.info("SINGULO_SODIUM_NOON_VERTEX_COMPLETE 241");
    }
}
