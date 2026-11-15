package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

/** World-dependent effect geometry is extracted once, then submitted without retaining an entity. */
abstract class SnapshotEntityRenderer<T extends Entity> extends EntityRenderer<T, SnapshotEntityRenderer.State> {
    static final class State extends EntityRenderState {
        List<SnapshotBlockRenderer.Batch> geometry = List.of();
        List<ModelSubmissions.Draw> models = List.of();
    }
    protected SnapshotEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public final State createRenderState() { return new State(); }
    @Override public final void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        SnapshotBlockRenderer.Capture capture = new SnapshotBlockRenderer.Capture();
        GlowDepth.beginExtract();
        try { state.models = ModelSubmissions.capture(() -> render(entity, entity.getYRot(), partialTick, new PoseStack(), capture, getPackedLightCoords(entity, partialTick))); }
        finally { GlowDepth.endExtract(); }
        state.geometry = capture.finish();
    }
    protected abstract void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light);
    @Override public final void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        SnapshotBlockRenderer.submitGeometry(state.geometry, pose, collector, state.outlineColor);
        ModelSubmissions.submit(state.models, pose, collector);
    }
}