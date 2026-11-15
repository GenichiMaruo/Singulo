package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

/** Additive jet geometry, with depth testing but without writing depth. */
final class JetForgeRenderTypes {
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Singulo.id("pipeline/jet_forge_glow"))
            .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false).withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();
    private static final RenderPipeline BEAM_PIPELINE = RenderPipeline.builder(
            RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Singulo.id("pipeline/jet_forge_beam"))
            .withVertexShader(Singulo.id("core/jet_beam")).withFragmentShader(Singulo.id("core/jet_beam"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false).withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS).build();
    static final RenderType GLOW = RenderType.create("singulo_jet_forge_glow",
            RenderSetup.builder(GLOW_PIPELINE).bufferSize(4096).createRenderSetup());
    static final RenderType BEAM_TYPE = RenderType.create("singulo_jet_forge_beam",
            RenderSetup.builder(BEAM_PIPELINE).bufferSize(16384).createRenderSetup());
    private JetForgeRenderTypes() {}
    @Nullable static RenderType beam() {
        return GravitationalLensing.shaderPackInUse() ? null : BEAM_TYPE;
    }
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GLOW_PIPELINE);
        event.registerPipeline(BEAM_PIPELINE);
    }
}