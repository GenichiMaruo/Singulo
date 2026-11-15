package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import java.util.OptionalInt;
import org.joml.Vector3f;

/** GPU equivalent of the old CPU natural/noon lightmap banks. */
public final class RayleighLightmapBanks implements AutoCloseable {
    private static boolean renderingNoon;
    private static final RenderPipeline COMBINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("singulo", "pipeline/rayleigh_banks"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("singulo", "core/rayleigh_banks"))
            .withSampler("NaturalLight").withSampler("NoonLight")
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).build();
    private final Lightmap noon = new Lightmap();
    private final GpuTexture texture = RenderSystem.getDevice().createTexture("Singulo lightmap banks", 13, TextureFormat.RGBA8, 16, 512, 1, 1);
    private final GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
    public static boolean isNoonPass() { return renderingNoon; }
    public static void registerShaders(RegisterRenderPipelinesEvent event) { event.registerPipeline(COMBINE); }
    public GpuTextureView view() { return view; }
    public void update(GpuTextureView natural, LightmapRenderState state) {
        LightmapRenderState sun = new LightmapRenderState();
        sun.needsUpdate = true;
        sun.skyFactor = 1.0F;
        sun.skyLightColor = new Vector3f(1, 1, 1);
        // Vanilla 26.1.2 overworld.json supplies #0a0a0a ambient light even at clear noon.
        // Zero here darkens the entire ramp (including sky=0) relative to the real reference.
        // Artificial sunlight is canonical clear-overworld noon, independent of current dimension/weather.
        sun.ambientColor = new Vector3f(10.0F / 255.0F);
        sun.blockFactor = state.blockFactor;
        sun.blockLightTint = state.blockLightTint;
        sun.brightness = state.brightness;
        sun.darknessEffectScale = state.darknessEffectScale;
        sun.nightVisionEffectIntensity = state.nightVisionEffectIntensity;
        sun.nightVisionColor = state.nightVisionColor;
        sun.bossOverlayWorldDarkening = state.bossOverlayWorldDarkening;
        renderingNoon = true;
        try { noon.render(sun); } finally { renderingNoon = false; }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "Singulo natural/noon banks", view, OptionalInt.empty())) {
            pass.setPipeline(COMBINE);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.bindTexture("NaturalLight", natural, sampler);
            pass.bindTexture("NoonLight", noon.getTextureView(), sampler);
            pass.draw(0, 3);
        }
    }
    @Override public void close() { view.close(); texture.close(); noon.close(); }
}
