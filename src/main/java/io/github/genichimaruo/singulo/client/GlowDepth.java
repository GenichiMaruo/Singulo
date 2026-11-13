package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
final class GlowDepth extends RenderType {
    @Nullable
    private static ShaderInstance shader;

    private static final RenderType TYPE = create("singulo_glow_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setWriteMaskState(DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 光そのもの（entityTranslucentEmissive と同じ描き方で、面を遠い順に並べ替える）。 */
    private static final RenderType COLOR = create("singulo_glow_sorted", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(FxDraw.WHITE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /**
     * 管の中の液体（液体パイプ）の色。深度は書かず、面を遠い順に並べて重ねる。深度を書くと、並べ替えの順がずれた所で手前の面が
     * 奥の面をはじいて、面が抜けて見える（中心の箱・腕・流れの帯が重なり合うため）。
     */
    private static final RenderType FLUID_COLOR = create("singulo_pipe_fluid", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new TextureStateShard(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** 管の中の液体の深度（色のあとに、同じ形を深度だけ書く。あとから描く窓のガラスや水が、液体の手前に割り込まないように）。 */
    private static final RenderType FLUID_DEPTH = create("singulo_pipe_fluid_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new TextureStateShard(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, false, false))
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(DEPTH_WRITE)
                    .createCompositeState(false));

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
        if (!active || GravitationalLensing.shaderPackInUse()) {
            return FluidLook.buffer(buffers);
        }
        return VertexMultiConsumer.create(FLUID_SOURCE.getBuffer(FLUID_COLOR), FLUID_SOURCE.getBuffer(FLUID_DEPTH));
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
            if (!active || GravitationalLensing.shaderPackInUse()
                    || type != JetForgeRenderTypes.GLOW && type != JetForgeRenderTypes.BEAM_TYPE) {
                return buffers.getBuffer(type);
            }
            jetsPending = true;
            return JET_SOURCE.getBuffer(type);
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
        if (!active || GravitationalLensing.shaderPackInUse()) {
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
                return group.near.getBuffer(type);
            }
            group.farPending = true;
            return group.far.getBuffer(type);
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
            if (!active || GravitationalLensing.shaderPackInUse()
                    || type != JetForgeRenderTypes.GLOW && type != JetForgeRenderTypes.BEAM_TYPE) {
                return buffers.getBuffer(type);
            }
            latePending = true;
            return LATE_SOURCE.getBuffer(type);
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
        if (active && !GravitationalLensing.shaderPackInUse()) {
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

    private GlowDepth(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** 光の入れ物。白い絵の光だけまとめて描く（模様のある絵は透ける所まで深度に書いてしまうので、これまでどおり）。 */
    static VertexConsumer of(MultiBufferSource buffers, ResourceLocation texture) {
        if (!active || shader == null || !FxDraw.WHITE.equals(texture) || GravitationalLensing.shaderPackInUse()) {
            return buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        }
        return VertexMultiConsumer.create(SOURCE.getBuffer(TYPE), COLOR_SOURCE.getBuffer(COLOR));
    }

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Singulo.id("glow_depth"), DefaultVertexFormat.NEW_ENTITY),
                    s -> shader = s);
        } catch (IOException e) {
            // 読めなければ深度を書かない（これまでどおりの見た目）で続ける
            Singulo.LOGGER.error("光の深度のシェーダーを読めなかった", e);
        }
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            active = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            active = false;
            // 固体をすべて描いてから、光を奥から順に描き、最後に深度を書く
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            // 管の中の液体: 色を遠い順に重ねてから、深度を書く
            FLUID_SOURCE.endBatch(FLUID_COLOR);
            FLUID_SOURCE.endBatch(FLUID_DEPTH);
            COLOR_SOURCE.endBatch();
            SOURCE.endBatch();
            if (anyPending()) {
                RenderTarget main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
                solidDepth = sized(solidDepth, main);
                copyDepth(main, solidDepth);
                main.bindWrite(false);
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
        main.bindWrite(false);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        source.endBatch();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        copyDepth(laterDepth, main);
        main.bindWrite(false);
    }

    /** main と同じ大きさ・形式の深度の置き場。 */
    private static TextureTarget sized(@Nullable TextureTarget target, RenderTarget main) {
        if (target != null && target.width == main.width && target.height == main.height
                && target.isStencilEnabled() == main.isStencilEnabled()) {
            return target;
        }
        if (target != null) {
            target.destroyBuffers();
        }
        TextureTarget created = new TextureTarget(main.width, main.height, true, net.minecraft.client.Minecraft.ON_OSX);
        if (main.isStencilEnabled()) {
            created.enableStencil();
        }
        return created;
    }

    private static void copyDepth(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }
}
