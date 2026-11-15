package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** World overlays retain only extracted geometry/model states, never world objects. */
final class WorldGeometry {
    private record Frame(List<SnapshotBlockRenderer.Batch> geometry,List<ModelSubmissions.Draw> models) {}
    private static final ContextKey<Frame> KEY=new ContextKey<>(io.github.genichimaruo.singulo.Singulo.id("world_geometry"));
    static void extract(ExtractLevelRenderStateEvent event) {
        var capture=new SnapshotBlockRenderer.Capture();
        var models=ModelSubmissions.capture(()->{
            GlowDepth.beginExtract();
            try {
            HologramRenderer.extract(event,new PoseStack(),capture);
            NeutrinoOverlay.extract(event,new PoseStack(),capture);
            PipeSurveyRenderer.extract(event,new PoseStack(),capture);
            SurveyRenderer.extract(event,new PoseStack(),capture);
            } finally { GlowDepth.endExtract(); }
        });
        event.getRenderState().setRenderData(KEY,new Frame(capture.finish(),models));
    }
    static void submit(SubmitCustomGeometryEvent event) {
        Frame frame=event.getLevelRenderState().getRenderData(KEY);
        if(frame==null)return;
        SnapshotBlockRenderer.submitGeometry(frame.geometry(),event.getPoseStack(),event.getSubmitNodeCollector());
        ModelSubmissions.submit(frame.models(),event.getPoseStack(),event.getSubmitNodeCollector());
    }
}
