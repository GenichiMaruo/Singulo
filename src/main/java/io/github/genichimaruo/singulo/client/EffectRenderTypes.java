package io.github.genichimaruo.singulo.client;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import io.github.genichimaruo.singulo.Singulo;
import java.util.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Shared finite pipeline variants for procedural mob and conduit effects. */
final class EffectRenderTypes {
    private static final Map<String,RenderPipeline> COLORS=new LinkedHashMap<>();
    private static final RenderPipeline EMISSIVE_DEPTH=emissive("emissive_depth",true);
    private static final RenderPipeline EMISSIVE_XRAY=emissive("emissive_xray",false);
    private static final RenderPipeline XRAY_LINES=RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).withLocation(Singulo.id("pipeline/xray_lines")).withDepthStencilState(Optional.empty()).build();
    static RenderType xrayLines(){return RenderType.create("singulo_xray_lines",RenderSetup.builder(XRAY_LINES).createRenderSetup());}
    static {
        for(String blend:List.of("add","alpha","opaque")) for(boolean depth:new boolean[]{false,true}) for(boolean cull:new boolean[]{false,true}) {
            String key=blend+"_"+depth+"_"+cull;
            ColorTargetState color=blend.equals("opaque") ? ColorTargetState.DEFAULT : new ColorTargetState(blend.equals("add")?BlendFunction.LIGHTNING:BlendFunction.TRANSLUCENT);
            COLORS.put(key,RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Singulo.id("pipeline/effect_"+key)).withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS)
                    .withColorTargetState(color).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,depth)).withCull(cull).build());
        }
    }
    private static RenderPipeline emissive(String name,boolean depth) {
        var b=RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET).withLocation(Singulo.id("pipeline/"+name))
                .withShaderDefine("ALPHA_CUTOUT",0.1F).withShaderDefine("PER_FACE_LIGHTING").withSampler("Sampler1")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(false);
        if(depth)b.withDepthStencilState(DepthStencilState.DEFAULT);else b.withDepthStencilState(Optional.empty());
        return b.build();
    }
    static RenderType color(String name,String blend,boolean depth,boolean cull,boolean sort,OutputTarget target) {
        var setup=RenderSetup.builder(COLORS.get(blend+"_"+depth+"_"+cull)).bufferSize(1536).setOutputTarget(target);
        if(sort)setup.sortOnUpload();
        return RenderType.create(name,setup.createRenderSetup());
    }
    static RenderType emissive(String name,Identifier texture,boolean depth) {
        return RenderType.create(name,RenderSetup.builder(depth?EMISSIVE_DEPTH:EMISSIVE_XRAY).withTexture("Sampler0",texture).useOverlay().sortOnUpload().affectsCrumbling().createRenderSetup());
    }
    static void register(RegisterRenderPipelinesEvent event){COLORS.values().forEach(event::registerPipeline);event.registerPipeline(EMISSIVE_DEPTH);event.registerPipeline(EMISSIVE_XRAY);event.registerPipeline(XRAY_LINES);}
}