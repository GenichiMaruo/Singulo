package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
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
                    case "singulo$lightEpoch" -> 1;
                    case "getShade" -> 1F;
                    default -> throw new AssertionError("Unexpected live view access: " + method.getName());
                });
        for (boolean selfEmissive : new boolean[] {false, true}) {
            var cache = new me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess() {
                { world = view; }
                @Override public int get(int x, int y, int z) {
                    return packAO(1) | packEM(selfEmissive || !pos.equals(new net.minecraft.core.BlockPos(x, y, z)));
                }
            };
            var original = new me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline(cache);
            var pipeline = new SodiumRayleighPipeline(cache, me.jellysquid.mods.sodium.client.model.light.LightMode.SMOOTH, original);
            var quad = new me.jellysquid.mods.sodium.client.model.quad.ModelQuad();
            quad.setFlags(me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags.IS_ALIGNED);
            var expected = new me.jellysquid.mods.sodium.client.model.light.data.QuadLightData();
            var actual = new me.jellysquid.mods.sodium.client.model.light.data.QuadLightData();
            original.calculate(quad, pos, expected, null, net.minecraft.core.Direction.UP, true);
            pipeline.calculate(quad, pos, actual, null, net.minecraft.core.Direction.UP, true);
            for (int i = 0; i < 4; i++) {
                if (actual.lm[i] != expected.lm[i]) throw new AssertionError("Emissive geometry invented sunlight or changed native lighting");
            }
        }
        Singulo.LOGGER.info("SINGULO_EMBEDDIUM_NOON_EMISSIVE_COMPLETE");
    }

    public static void verify() {
        var vertex = new ChunkVertexEncoder.Vertex();
        vertex.x = 1; vertex.y = 2; vertex.z = 3;
        vertex.u = .25F; vertex.v = .75F; vertex.color = -1;
        long ptr = MemoryUtil.nmemAlloc(32);
        try {
            for (boolean compact : new boolean[]{true, false}) {
                var type = compact ? new CompactChunkVertex() : new VanillaLikeChunkVertex();
                var encoder = type.getEncoder();
                int stride = type.getVertexFormat().getStride();
                if (stride != (compact ? 20 : 32)) throw new AssertionError("Wrong vertex stride: " + stride);
                vertex.light = 0x00C00070;
                encoder.write(ptr, DefaultMaterials.SOLID, vertex, 173);
                byte[] geometry = new byte[compact ? 16 : 24];
                for (int i = 0; i < geometry.length; i++) geometry[i] = MemoryUtil.memGetByte(ptr + i);
                for (int sun = 0; sun <= 240; sun++) {
                    vertex.light = ModelQuadUtil.mergeBakedLight(0x00C00030, (sun << 24) | 0x00A00070);
                    if (encoder.write(ptr, DefaultMaterials.SOLID, vertex, 173) != ptr + stride) throw new AssertionError("Wrong encoder advance");
                    int decoded = compact ? MemoryUtil.memGetInt(ptr + 16) >>> 24 : MemoryUtil.memGetInt(ptr + 28);
                    if (decoded != sun) throw new AssertionError("Noon coordinate lost: " + sun);
                    for (int i = 0; i < geometry.length; i++) if (geometry[i] != MemoryUtil.memGetByte(ptr + i)) throw new AssertionError("Geometry/color/material changed");
                    int packed = MemoryUtil.memGetInt(ptr + (compact ? 16 : 24));
                    if (compact ? (packed & 0x00FFFFFF) != 0x00C00070 : packed != (0xC0700000 | (173 << 8) | DefaultMaterials.SOLID.bits()))
                        throw new AssertionError("Native light/draw data changed");
                }
            }
        } finally { MemoryUtil.nmemFree(ptr); }
        var shader = me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader.loadShader(
                me.jellysquid.mods.sodium.client.gl.shader.ShaderType.VERTEX,
                new net.minecraft.resources.ResourceLocation("sodium", "blocks/block_layer_opaque.vsh"),
                me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants.builder().build());
        shader.delete();
        Singulo.LOGGER.info("SINGULO_EMBEDDIUM_NOON_VERTEX_COMPLETE 241");
    }
}
