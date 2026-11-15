package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Extracts custom mesh data while world access is safe; submission retains no live block entity. */
abstract class SnapshotBlockRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, SnapshotBlockRenderer.State> {
    static final class State extends BlockEntityRenderState {
        List<Batch> geometry = List.of();
        List<ModelSubmissions.Draw> models = List.of();
    }
    record Vertex(float x, float y, float z, int color, float u, float v,
                          int overlay, int light, float nx, float ny, float nz, float width) {}
    record Batch(RenderType type, List<Vertex> vertices) {}

    @Override public final State createRenderState() { return new State(); }
    @Override public final void extractRenderState(T entity, State state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, camera, breaking);
        Capture capture = new Capture();
        GlowDepth.beginExtract();
        try { state.models = ModelSubmissions.capture(() -> render(entity, partialTick, new PoseStack(), capture, state.lightCoords, OverlayTexture.NO_OVERLAY)); }
        finally { GlowDepth.endExtract(); }
        state.geometry = capture.finish();
    }
    protected abstract void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay);
    @Override public final void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        submitGeometry(state.geometry, pose, collector);
        ModelSubmissions.submit(state.models, pose, collector);
    }
    static void submitGeometry(List<Batch> geometry, PoseStack pose, SubmitNodeCollector collector) {
        for (Batch batch : geometry) {
            if (GlowDepth.submitDeferred(batch,pose)) continue;
            collector.submitCustomGeometry(pose, batch.type(), (transform, output) -> {
                for (Vertex v : batch.vertices()) {
                    output.addVertex(transform, v.x(), v.y(), v.z()).setColor(v.color()).setUv(v.u(), v.v())
                            .setOverlay(v.overlay()).setLight(v.light()).setNormal(transform, v.nx(), v.ny(), v.nz())
                            .setLineWidth(v.width());
                }
            });
        }
    }
    static final class Capture implements MultiBufferSource {
        private final Map<RenderType, RecordingConsumer> buffers = new LinkedHashMap<>();
        @Override public VertexConsumer getBuffer(RenderType type) { return buffers.computeIfAbsent(type, ignored -> new RecordingConsumer()); }
        List<Batch> finish() {
            List<Batch> result = new ArrayList<>();
            buffers.forEach((type, buffer) -> { buffer.finishVertex(); result.add(new Batch(type, List.copyOf(buffer.vertices))); });
            return List.copyOf(result);
        }
    }
    private static final class RecordingConsumer implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private boolean pending;
        private float x,y,z,u,v,nx,ny,nz,width = 1;
        private int color = -1, overlay = OverlayTexture.NO_OVERLAY, light;
        void finishVertex() { if (pending) { vertices.add(new Vertex(x,y,z,color,u,v,overlay,light,nx,ny,nz,width)); pending=false; } }
        @Override public VertexConsumer addVertex(float x,float y,float z) { finishVertex();this.x=x;this.y=y;this.z=z;pending=true;return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { color=(a<<24)|(r<<16)|(g<<8)|b;return this; }
        @Override public VertexConsumer setColor(int value) { color=value;return this; }
        @Override public VertexConsumer setUv(float u,float v) { this.u=u;this.v=v;return this; }
        @Override public VertexConsumer setUv1(int u,int v) { overlay=(v<<16)|(u&65535);return this; }
        @Override public VertexConsumer setUv2(int u,int v) { light=(v<<16)|(u&65535);return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { nx=x;ny=y;nz=z;return this; }
        @Override public VertexConsumer setLineWidth(float width) { this.width=width;return this; }
    }
}