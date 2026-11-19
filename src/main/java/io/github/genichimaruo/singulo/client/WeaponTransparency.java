package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import java.util.Optional;
import java.util.OptionalInt;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Fast/Fancy equivalent of Fabulous' depth-sorted weapon and translucent terrain layers. */
final class WeaponTransparency {
    private static final RenderPipeline COMPOSITE = RenderPipeline.builder()
            .withLocation(Singulo.id("pipeline/weapon_transparency"))
            .withVertexShader(Singulo.id("core/weapon_transparency"))
            .withFragmentShader(Singulo.id("core/weapon_transparency"))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withSampler("WeaponColor").withSampler("WeaponDepth")
            .withSampler("TerrainColor").withSampler("TerrainDepth")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withDepthStencilState(Optional.empty()).withCull(false).build();

    private static TextureTarget weapons;
    private static TextureTarget terrain;

    private WeaponTransparency() {}

    static void registerShaders(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(COMPOSITE);
    }

    static void clear() {
        if (weapons != null) weapons.destroyBuffers();
        if (terrain != null) terrain.destroyBuffers();
        weapons = null;
        terrain = null;
    }

    static RenderTarget weaponTarget() { return weapons; }
    static RenderTarget terrainTarget() { return terrain; }

    static void begin(RenderTarget main, Runnable drawWeapons) {
        if (weapons == null || terrain == null || weapons.width != main.width || weapons.height != main.height
                || weapons.useStencil != main.useStencil) {
            clear();
            weapons = new TextureTarget("Singulo translucent weapons", main.width, main.height, true, main.useStencil);
            terrain = new TextureTarget("Singulo translucent terrain", main.width, main.height, true, main.useStencil);
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(weapons.getColorTexture(), 0);
        encoder.clearColorTexture(terrain.getColorTexture(), 0);
        weapons.copyDepthFrom(main);
        terrain.copyDepthFrom(main);
        drawWeapons.run();
    }

    static void finish(RenderTarget main) {
        // Subsequent particles and outlines use the vanilla terrain depth.
        main.copyDepthFrom(terrain);
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Singulo weapon and terrain composite", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(COMPOSITE);
            pass.bindTexture("WeaponColor", weapons.getColorTextureView(), sampler);
            pass.bindTexture("WeaponDepth", weapons.getDepthTextureView(), sampler);
            pass.bindTexture("TerrainColor", terrain.getColorTextureView(), sampler);
            pass.bindTexture("TerrainDepth", terrain.getDepthTextureView(), sampler);
            pass.draw(0, 3);
        }
    }
}
