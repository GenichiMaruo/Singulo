package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import io.github.genichimaruo.singulo.Singulo;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * ワールドの光（entityTranslucentEmissive の白い絵）の描き方を2つ直す。どちらもブロックエンティティをすべて描き終えた時点にまとめて描く。
 * <ul>
 *   <li>奥から順に描く: 装置を描く順のままだと、あとに描かれた奥の光や装置が手前の光の上に重なる。全部の光を1つにまとめ、面ごとに
 *       遠い順に並べ替えて、固体をすべて描いたあとで描く</li>
 *   <li>深度も書く: 光は深度を書かないので、あとから描かれる水・窓・雲が光の手前に割り込む。光と同じ形を、明るい所（不透明度 0.6 以上）
 *       だけ深度に書く。ぼんやりした光の縁は書かないので、後ろの景色は隠さない</li>
 * </ul>
 * 鍛造炉のジェットとビームだけは、ワールドを描き終えたあとで足す（{@link #additive}）。
 * シェーダーパック使用時は深度の扱いが違うので何もしない。
 */
final class GlowDepth {
    private static final RenderPipeline DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Singulo.id("pipeline/glow_depth")).withVertexShader(Singulo.id("core/glow_depth"))
            .withFragmentShader(Singulo.id("core/glow_depth"))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE))
            .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build();
    private static final RenderPipeline GLAZED_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Singulo.id("pipeline/glazed"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("PER_FACE_LIGHTING").withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(true).build();
    private static final java.util.function.Function<Identifier, RenderType> GLAZED = net.minecraft.util.Util.memoize(texture ->
            RenderType.create("singulo_glazed", RenderSetup.builder(GLAZED_PIPELINE).withTexture("Sampler0", texture)
                    .useLightmap().useOverlay().sortOnUpload().createRenderSetup()));
    private static final RenderPipeline COLOR_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Singulo.id("pipeline/glow_sorted")).withSampler("Sampler1")
            .withShaderDefine("ALPHA_CUTOUT",0.1F).withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,false)).withCull(false).build();
    private static RenderPipeline fluidPipeline(boolean depth) {
        return RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET).withLocation(Singulo.id("pipeline/pipe_fluid_"+depth))
                .withSampler("Sampler1").withShaderDefine("ALPHA_CUTOUT",0.1F)
                .withColorTargetState(depth ? new ColorTargetState(Optional.empty(),ColorTargetState.WRITE_NONE) : new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,depth)).withCull(true).build();
    }
    private static final RenderPipeline FLUID_COLOR_PIPELINE=fluidPipeline(false), FLUID_DEPTH_PIPELINE=fluidPipeline(true);
    private static final RenderType TYPE=RenderType.create("singulo_glow_depth",RenderSetup.builder(DEPTH_PIPELINE).createRenderSetup());
    private static final RenderType COLOR=RenderType.create("singulo_glow_sorted",RenderSetup.builder(COLOR_PIPELINE)
            .withTexture("Sampler0",FxDraw.WHITE).useOverlay().sortOnUpload().createRenderSetup());
    private static RenderType fluidType(String name,RenderPipeline pipeline,boolean sort) {
        var setup=RenderSetup.builder(pipeline).withTexture("Sampler0",net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).useLightmap().useOverlay();
        if(sort)setup.sortOnUpload();
        return RenderType.create(name,setup.createRenderSetup());
    }
    private static final RenderType FLUID_COLOR=fluidType("singulo_pipe_fluid",FLUID_COLOR_PIPELINE,true);
    private static final RenderType FLUID_DEPTH=fluidType("singulo_pipe_fluid_depth",FLUID_DEPTH_PIPELINE,false);

    private static final MultiBufferSource.BufferSource FLUID_SOURCE = fluidSource();

    private static MultiBufferSource.BufferSource fluidSource() {
        java.util.SequencedMap<RenderType, ByteBufferBuilder> buffers = new java.util.LinkedHashMap<>();
        buffers.put(FLUID_COLOR, new ByteBufferBuilder(1 << 18));
        buffers.put(FLUID_DEPTH, new ByteBufferBuilder(1 << 18));
        return MultiBufferSource.immediateWithBuffers(buffers, new ByteBufferBuilder(1 << 10));
    }

    /**
     * 管の中の液体の入れ物。ワールドの中では色と深度の2つに同じ面を書き、ブロックエンティティを描き終えた時点で、色（遠い順）→
     * 深度の順にまとめて描く。ワールドの外やシェーダーパック使用時は、これまでどおりの描き方（{@link FluidLook#buffer}）。
     */
    static VertexConsumer fluid(MultiBufferSource buffers) {
        if (!isActive() || GravitationalLensing.shaderPackInUse()) {
            return FluidLook.buffer(buffers);
        }
        return VertexMultiConsumer.create(route(buffers, FLUID_SOURCE, FLUID_COLOR), route(buffers, FLUID_SOURCE, FLUID_DEPTH));
    }

    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private static final MultiBufferSource.BufferSource COLOR_SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
    /** 鍛造炉のジェットとビーム（加算の光）。種類ごとに入れ物を分けて、水や雲まで描き終えてからまとめて描く。 */
    private static final MultiBufferSource.BufferSource JET_SOURCE = jetSource();
    /** このフレームに描くジェットがあるか。 */
    private static boolean jetsPending;
    /** 固体と白い光だけの奥行き（ジェットはこれと比べる）と、ジェットを描く間に取っておく今の奥行き。 */
    @Nullable
    private static TextureTarget solidDepth;
    @Nullable
    private static TextureTarget laterDepth;

    private static MultiBufferSource.BufferSource jetSource() {
        return jetSource(1 << 20);
    }

    /** size は最初に取る大きさ（足りなければ伸びる）。 */
    private static MultiBufferSource.BufferSource jetSource(int size) {
        java.util.SequencedMap<RenderType, ByteBufferBuilder> buffers = new java.util.LinkedHashMap<>();
        buffers.put(JetForgeRenderTypes.GLOW, new ByteBufferBuilder(size));
        buffers.put(JetForgeRenderTypes.BEAM_TYPE, new ByteBufferBuilder(size));
        return MultiBufferSource.immediateWithBuffers(buffers, new ByteBufferBuilder(1 << 10));
    }

    /**
     * 鍛造炉のジェットとビームの入れ物。ワールドを描き終えたあと、固体だけの奥行きと比べて足す。重力レンズとの順は
     * {@link GravitationalLensing} が決める（奥のレンズ → ジェット → 手前のレンズ）。
     * 深度は書かない（書くと重力レンズがジェットを手前の物と見て、歪みが切れる）。
     * 加算の光なので、半透明の物やほかの光との前後はほとんど見た目に出ない。
     */
    static MultiBufferSource additive(MultiBufferSource buffers) {
        return type -> {
            if (!isActive() || GravitationalLensing.shaderPackInUse()
                    || type != JetForgeRenderTypes.GLOW && type != JetForgeRenderTypes.BEAM_TYPE) {
                return buffers.getBuffer(type);
            }
            jetsPending = true;
            return route(buffers, JET_SOURCE, type);
        };
    }

    /**
     * 鍛造炉1基ぶんのジェットの光。炉心のブラックホール（重力レンズ）より奥の光（far）と手前の光（near）を分けて持ち、
     * {@link GravitationalLensing} が遠い炉から順に「奥の光 → その炉のレンズ → 手前の光」と描く。
     * 手前の光をレンズより先に描くと、ブラックホールの影と円盤が、カメラとのあいだにあるジェットの上に出てしまう。
     */
    static final class JetGroup {
        final Vec3 core;
        private final MultiBufferSource.BufferSource far;
        private final MultiBufferSource.BufferSource near;
        private boolean farPending;
        private boolean nearPending;

        private JetGroup(Vec3 core, MultiBufferSource.BufferSource far, MultiBufferSource.BufferSource near) {
            this.core = core;
            this.far = far;
            this.near = near;
        }
    }

    private static final List<JetGroup> GROUPS = new ArrayList<>();
    /** 炉ごとの入れ物の使い回し（フレームごとに作らない）。 */
    private static final List<MultiBufferSource.BufferSource> POOL = new ArrayList<>();
    private static int pooled;

    private static MultiBufferSource.BufferSource pooledSource() {
        if (pooled == POOL.size()) {
            POOL.add(jetSource(1 << 16));
        }
        return POOL.get(pooled++);
    }

    /** 炉心 core の炉のジェットの組を、このフレームに作る。ワールドの外やシェーダーパック使用時は null（ふつうの入れ物に描く）。 */
    @Nullable
    static JetGroup group(Vec3 core) {
        if (!isActive() || GravitationalLensing.shaderPackInUse()) {
            return null;
        }
        JetGroup g = new JetGroup(core, pooledSource(), pooledSource());
        GROUPS.add(g);
        return g;
    }

    /** 組 group の、炉心より奥（near が false）か手前（true）の光の入れ物。group が null ならふつうの {@link #additive}。 */
    static MultiBufferSource additive(MultiBufferSource buffers, @Nullable JetGroup group, boolean near) {
        if (group == null) {
            return additive(buffers);
        }
        return type -> {
            if (type != JetForgeRenderTypes.GLOW && type != JetForgeRenderTypes.BEAM_TYPE) {
                return buffers.getBuffer(type);
            }
            if (near) {
                group.nearPending = true;
                return route(buffers, group.near, type);
            }
            group.farPending = true;
            return route(buffers, group.far, type);
        };
    }

    /** このフレームの炉ごとのジェットの組。 */
    static List<JetGroup> jetGroups() {
        return GROUPS;
    }

    /**
     * 重力レンズを掛け終えたあとで足す光（アンドロイドの妖精の羽など、ブラックホールの近くでも歪みの手前に見せたい物）。
     * ジェットと同じく深度は書かず、固体の奥行きと比べて足す。
     */
    private static final MultiBufferSource.BufferSource LATE_SOURCE = jetSource(1 << 16);
    private static boolean latePending;

    /** 重力レンズのあとで足す光の入れ物（ワールドの外やシェーダーパック使用時は、ふつうの入れ物）。 */
    static MultiBufferSource afterLensing(MultiBufferSource buffers) {
        return type -> {
            if (!isActive() || GravitationalLensing.shaderPackInUse()
                    || type != JetForgeRenderTypes.GLOW && type != JetForgeRenderTypes.BEAM_TYPE) {
                return buffers.getBuffer(type);
            }
            latePending = true;
            return route(buffers, LATE_SOURCE, type);
        };
    }

    private static boolean anyPending() {
        if (jetsPending || latePending) {
            return true;
        }
        for (JetGroup g : GROUPS) {
            if (g.farPending || g.nearPending) {
                return true;
            }
        }
        return false;
    }

    /** ジェットの通り道: 出している炉心と両端。 */
    private record JetPath(Vec3 core, Vec3 from, Vec3 to) {}

    private static final List<JetPath> JET_PATHS = new ArrayList<>();
    /** ジェットの太さのめやす（ブロック）。これより離れて見える所は重ならない。 */
    private static final double JET_REACH = 3;

    /** このフレームのジェットの通り道を覚える（レンズとの前後を決めるのに使う）。 */
    static void jetPath(Vec3 core, Vec3 from, Vec3 to) {
        if (isActive() && !GravitationalLensing.shaderPackInUse()) {
            JET_PATHS.add(new JetPath(core, from, to));
        }
    }

    /**
     * pos にあるレンズが、カメラから見てジェットの奥にあるか。半径 region のレンズと画面で重なる所のジェットが、レンズより手前なら奥。
     * ジェットを出している本人のレンズは奥に数えない（ジェットごと歪めて見せる）。
     */
    static boolean behindJet(Vec3 camera, Vec3 pos, float region) {
        Vec3 toLens = pos.subtract(camera);
        double distance = toLens.length();
        if (distance < 1.0E-3) {
            return false;
        }
        Vec3 view = toLens.scale(1 / distance);
        double reach = (region + JET_REACH) / distance;
        for (JetPath jet : JET_PATHS) {
            if (jet.core.distanceToSqr(pos) < 1) {
                continue;
            }
            for (int i = 0; i <= 32; i++) {
                Vec3 toPoint = jet.from.lerp(jet.to, i / 32.0).subtract(camera);
                double along = toPoint.dot(view);
                if (along > 0.5 && along < distance && toPoint.subtract(view.scale(along)).length() / along <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ワールドの中で光を描いている間だけ true（手に持った物や画面の絵は対象にしない）。 */
    private static boolean active;

    static boolean inWorld() {
        return isActive();
    }

    private GlowDepth() {}
    private static int extractionDepth;
    static void beginExtract() { extractionDepth++; }
    static void endExtract() { extractionDepth--; }
    private static boolean isActive() { return active || extractionDepth > 0; }
    private record Route(MultiBufferSource.BufferSource source, RenderType actual) {}
    private static final java.util.Map<RenderType,Route> ROUTES = new java.util.IdentityHashMap<>();
    static VertexConsumer route(MultiBufferSource buffers, MultiBufferSource.BufferSource source, RenderType actual) {
        if (extractionDepth == 0) return source.getBuffer(actual);
        for (var e : ROUTES.entrySet()) if (e.getValue().source == source && e.getValue().actual == actual) return buffers.getBuffer(e.getKey());
        RenderType marker=RenderType.create("singulo_deferred_"+ROUTES.size(),RenderSetup.builder(actual.pipeline()).createRenderSetup());
        ROUTES.put(marker,new Route(source,actual));
        return buffers.getBuffer(marker);
    }
    static boolean submitDeferred(SnapshotBlockRenderer.Batch batch, PoseStack pose) {
        Route route=ROUTES.get(batch.type());
        if(route==null)return false;
        AndroidWeaponLight.noteDeferred(route.source);
        VertexConsumer output=route.source.getBuffer(route.actual);
        var transform=pose.last();
        for(var v:batch.vertices()) output.addVertex(transform,v.x(),v.y(),v.z()).setColor(v.color()).setUv(v.u(),v.v())
                .setOverlay(v.overlay()).setLight(v.light()).setNormal(transform,v.nx(),v.ny(),v.nz()).setLineWidth(v.width());
        return true;
    }

    /** 光を描き終えてから描く、半透明の固体（最後の記録者の翼の骨）。 */
    private static final MultiBufferSource.BufferSource GLAZED_SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));

    /**
     * 少し透ける固体の入れ物（外向きの面だけ・陰影つき）。光の色と深度を書き終えたあとで描くので、光との前後は深度で決まる:
     * 手前の光（羽根）は上に重なり、奥の光は透けて見える。光と一緒に並べ替えると、長い面と小さな面の前後が視点で入れ替わってちらつく。
     */
    static VertexConsumer glazed(MultiBufferSource buffers, Identifier texture) {
        RenderType type = GLAZED.apply(texture);
        if (!isActive() || GravitationalLensing.shaderPackInUse()) {
            return buffers.getBuffer(type);
        }
        return route(buffers, GLAZED_SOURCE, type);
    }

    /** 光の入れ物。白い絵の光だけまとめて描く（模様のある絵は透ける所まで深度に書いてしまうので、これまでどおり）。 */
    static VertexConsumer of(MultiBufferSource buffers, Identifier texture) {
        if (!isActive() || !FxDraw.WHITE.equals(texture) || GravitationalLensing.shaderPackInUse()) {
            return buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture));
        }
        return VertexMultiConsumer.create(route(buffers, SOURCE, TYPE), route(buffers, COLOR_SOURCE, COLOR));
    }

    static void registerShaders(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(DEPTH_PIPELINE);event.registerPipeline(COLOR_PIPELINE);event.registerPipeline(GLAZED_PIPELINE);
        event.registerPipeline(FLUID_COLOR_PIPELINE);event.registerPipeline(FLUID_DEPTH_PIPELINE);
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event instanceof RenderLevelStageEvent.AfterSky) {
            active = true;
        } else if (event instanceof RenderLevelStageEvent.AfterTranslucentFeatures) {
            active = false;
            // 固体をすべて描いてから、光を奥から順に描き、最後に深度を書く
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            // 管の中の液体: 色を遠い順に重ねてから、深度を書く
            Matrix4fStack modelView=RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.set(event.getModelViewMatrix());
                AndroidWeaponLight.afterBlockEntities(event.getModelViewMatrix());
                FLUID_SOURCE.endBatch(FLUID_COLOR);
                FLUID_SOURCE.endBatch(FLUID_DEPTH);
                COLOR_SOURCE.endBatch();
                SOURCE.endBatch();
                GLAZED_SOURCE.endBatch();
            } finally { modelView.popMatrix(); }
            if (anyPending()) {
                RenderTarget main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
                solidDepth = sized(solidDepth, main);
                copyDepth(main, solidDepth);
            }
        }
    }

    /** 奥行きを固体だけに戻してジェットを足し、元へ戻す（重力レンズはジェットのない奥行きを読む）。このフレームのジェットがなければ何もしない。 */
    static void drawJets(RenderLevelStageEvent event) {
        if (jetsPending) {
            jetsPending = false;
            flush(event, JET_SOURCE);
        }
    }

    /** 組 g の奥（near が false）か手前（true）の光を足す。 */
    static void drawJets(RenderLevelStageEvent event, JetGroup g, boolean near) {
        if (near ? g.nearPending : g.farPending) {
            if (near) {
                g.nearPending = false;
            } else {
                g.farPending = false;
            }
            flush(event, near ? g.near : g.far);
        }
    }

    /** このフレームの残りのジェットを全部足し、組を片付ける（フレームの終わりに必ず呼ぶ）。 */
    static void finishJets(RenderLevelStageEvent event) {
        drawJets(event);
        for (JetGroup g : GROUPS) {
            drawJets(event, g, false);
            drawJets(event, g, true);
        }
        ROUTES.clear();
        GROUPS.clear();
        pooled = 0;
        JET_PATHS.clear();
        if (latePending) {
            latePending = false;
            flush(event, LATE_SOURCE);
        }
    }

    private static void flush(RenderLevelStageEvent event, MultiBufferSource.BufferSource source) {
        RenderTarget main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (solidDepth == null) {
            source.endBatch();
            return;
        }
        laterDepth = sized(laterDepth, main);
        copyDepth(main, laterDepth);
        copyDepth(solidDepth, main);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix());
            source.endBatch();
        } finally {
            modelView.popMatrix();
            copyDepth(laterDepth, main);
        }
    }

    /** main と同じ大きさ・形式の深度の置き場。 */
    private static TextureTarget sized(@Nullable TextureTarget target, RenderTarget main) {
        if(target!=null && target.width==main.width && target.height==main.height && target.useStencil==main.useStencil)return target;
        if(target!=null)target.destroyBuffers();
        return new TextureTarget("Singulo deferred depth",main.width,main.height,true,main.useStencil);
    }
    private static void copyDepth(RenderTarget from, RenderTarget to) { to.copyDepthFrom(from); }
}
