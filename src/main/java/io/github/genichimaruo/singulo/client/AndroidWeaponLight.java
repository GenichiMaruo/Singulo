package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import io.github.genichimaruo.singulo.Singulo;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

/** Translucent weapon surfaces, drawn after opaque entities and block entities. */
public final class AndroidWeaponLight {
    private AndroidWeaponLight() {}
    private static final OutputTarget LAYER = new OutputTarget("singulo_weapon", () -> Minecraft.useShaderTransparency()
            ? Minecraft.getInstance().levelRenderer.getItemEntityTarget() : WeaponTransparency.weaponTarget());
    private static final RenderPipeline BLADE_PIPELINE = blade(false);
    private static final RenderPipeline LAYER_BLADE_PIPELINE = blade(true);
    private static final RenderPipeline FABULOUS_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Singulo.id("pipeline/fabulous_weapon_glow"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader(Singulo.id("core/fabulous_weapon_glow"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.ONE, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA)))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false).build();

    static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(BLADE_PIPELINE);
        event.registerPipeline(LAYER_BLADE_PIPELINE);
        event.registerPipeline(FABULOUS_GLOW_PIPELINE);
    }

    private static RenderPipeline blade(boolean depthWrite) {
        return RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                .withLocation(Singulo.id(depthWrite ? "pipeline/weapon_blade_layer" : "pipeline/weapon_blade"))
                .withSampler("Sampler1").withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, depthWrite))
                .withCull(false).build();
    }
    static final RenderType BLADE = material("blade", true, false);
    static final RenderType CORE = material("core", false, false);
    static final RenderType GLOW = material("glow", false, false);
    private static final RenderType LAYER_BLADE = material("blade", true, true);
    private static final RenderType LAYER_CORE = material("core", false, true);
    private static final RenderType LAYER_GLOW = material("glow", false, true);
    private static final RenderType FABULOUS_GLOW = RenderType.create("singulo_fabulous_weapon_glow",
            RenderSetup.builder(FABULOUS_GLOW_PIPELINE).setOutputTarget(LAYER).sortOnUpload().bufferSize(1536).createRenderSetup());
    private static final MultiBufferSource.BufferSource BATCH = weaponBuffers();
    private static boolean queued;
    private static boolean drawingTerrain;
    private static final org.joml.Matrix4f terrainView = new org.joml.Matrix4f();

    private static MultiBufferSource.BufferSource weaponBuffers() {
        var layers = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        // Finish the textured surface and white core before blending the wider colored glow.
        for (RenderType type : java.util.List.of(LAYER_BLADE, LAYER_CORE, LAYER_GLOW, FABULOUS_GLOW))
            layers.put(type, new ByteBufferBuilder(1536));
        return MultiBufferSource.immediateWithBuffers(layers, new ByteBufferBuilder(256));
    }

    private static RenderType material(String name, boolean textured, boolean layered) {
        String id = "singulo_weapon_" + name + (layered ? "_layered" : "");
        OutputTarget target = layered ? LAYER : OutputTarget.MAIN_TARGET;
        if (!textured) return EffectRenderTypes.color(id, "add", layered, false, true, target);
        return RenderType.create(id, RenderSetup.builder(layered ? LAYER_BLADE_PIPELINE : BLADE_PIPELINE)
                .setOutputTarget(target).withTexture("Sampler0", AndroidRenderer.Variant.BLADE.texture)
                .useOverlay().sortOnUpload().bufferSize(1536).createRenderSetup());
    }

    static MultiBufferSource worldBuffers(MultiBufferSource original) {
        if (!GlowDepth.inWorld() || AndroidRenderer.inGui || GravitationalLensing.shaderPackInUse()) return original;
        return type -> {
            RenderType layer;
            if (type == BLADE) layer = LAYER_BLADE;
            else if (type == CORE) layer = LAYER_CORE;
            else if (type == GLOW) layer = Minecraft.useShaderTransparency() ? FABULOUS_GLOW : LAYER_GLOW;
            else return original.getBuffer(type);
            return GlowDepth.route(original, BATCH, layer);
        };
    }

    static void noteDeferred(MultiBufferSource.BufferSource source) {
        if (source == BATCH) queued = true;
    }

    private static void flush(org.joml.Matrix4fc view) {
        var stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.set(view);
            BATCH.endBatch();
        } finally {
            stack.popMatrix();
        }
    }

    static void afterBlockEntities(org.joml.Matrix4fc view) {
        terrainView.set(view);
        if (queued && Minecraft.useShaderTransparency()) {
            queued = false;
            flush(view);
        }
    }

    public static boolean beginTerrain() {
        if (!queued || Minecraft.useShaderTransparency()) return false;
        queued = false;
        WeaponTransparency.begin(Minecraft.getInstance().getMainRenderTarget(), () -> flush(terrainView));
        drawingTerrain = true;
        return true;
    }

    public static void finishTerrain() {
        if (drawingTerrain) {
            drawingTerrain = false;
            WeaponTransparency.finish(Minecraft.getInstance().getMainRenderTarget());
        }
    }

    public static @Nullable RenderTarget terrainTarget() {
        return drawingTerrain ? WeaponTransparency.terrainTarget() : null;
    }
}
