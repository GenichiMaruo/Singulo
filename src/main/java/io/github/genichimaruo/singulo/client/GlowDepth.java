package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * ワールドの光（entityTranslucentEmissive の白い絵）の描き方を2つ直す。どちらもブロックエンティティをすべて描き終えた時点にまとめて描く。
 * <ul>
 *   <li>奥から順に描く: 装置を描く順のままだと、あとに描かれた奥の光や装置が手前の光の上に重なる。全部の光を1つにまとめ、面ごとに
 *       遠い順に並べ替えて、固体をすべて描いたあとで描く</li>
 *   <li>深度も書く: 光は深度を書かないので、あとから描かれる水・窓・雲が光の手前に割り込む。光と同じ形を、明るい所（不透明度 0.6 以上）
 *       だけ深度に書く。ぼんやりした光の縁は書かないので、後ろの景色は隠さない</li>
 * </ul>
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

    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private static final MultiBufferSource.BufferSource COLOR_SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
    @Nullable
    private static ShaderInstance colorShader;
    /** ジェットの加算の光は薄い所まで光って見えるので、白い光（0.6）より低い所まで深度に書く。 */
    private static final float JET_ALPHA_CUT = 0.2F;

    private static RenderType depthType(String name, VertexFormat format, java.util.function.Supplier<ShaderInstance> program) {
        return create(name, format, VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
                .setShaderState(new ShaderStateShard(program))
                .setWriteMaskState(DEPTH_WRITE)
                .setCullState(NO_CULL)
                .createCompositeState(false));
    }

    private static final RenderType JET_DEPTH = depthType("singulo_jet_glow_depth", DefaultVertexFormat.POSITION_COLOR, () -> colorShader);

    /** 鍛造炉のジェットとビーム（加算の光）。種類ごとに入れ物を分けて、描く順に関係なく最後にまとめて描く。 */
    private static final MultiBufferSource.BufferSource JET_SOURCE = jetSource();

    private static MultiBufferSource.BufferSource jetSource() {
        java.util.SequencedMap<RenderType, ByteBufferBuilder> buffers = new java.util.LinkedHashMap<>();
        buffers.put(JetForgeRenderTypes.GLOW, new ByteBufferBuilder(1 << 20));
        buffers.put(JetForgeRenderTypes.BEAM_TYPE, new ByteBufferBuilder(1 << 20));
        buffers.put(JET_DEPTH, new ByteBufferBuilder(1 << 18));
        buffers.put(JetForgeRenderTypes.BEAM_DEPTH, new ByteBufferBuilder(1 << 18));
        return MultiBufferSource.immediateWithBuffers(buffers, new ByteBufferBuilder(1 << 10));
    }

    /**
     * 鍛造炉のジェットとビームの入れ物。固体と白い光のあとに描き、明るい所は深度にも書く。
     * 加算の光なので、ほかの光との前後はほとんど見た目に出ない（並べ替えなくてよい）。
     */
    static MultiBufferSource additive(MultiBufferSource buffers) {
        return type -> {
            if (!active || shader == null || colorShader == null || !JetForgeRenderTypes.beamDepthReady() || GravitationalLensing.shaderPackInUse()) {
                return buffers.getBuffer(type);
            }
            RenderType depth = type == JetForgeRenderTypes.GLOW ? JET_DEPTH : type == JetForgeRenderTypes.BEAM_TYPE ? JetForgeRenderTypes.BEAM_DEPTH : null;
            return depth == null ? buffers.getBuffer(type) : VertexMultiConsumer.create(JET_SOURCE.getBuffer(depth), JET_SOURCE.getBuffer(type));
        };
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
            // ジェットの光の頂点の形の分（同じシェーダー）
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Singulo.id("glow_depth_color"), DefaultVertexFormat.POSITION_COLOR),
                    s -> {
                        colorShader = s;
                        s.safeGetUniform("AlphaCut").set(JET_ALPHA_CUT);
                    });
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
            // 固体をすべて描いてから、光を奥から順に描き、ジェットを足して、最後に深度を書く
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            COLOR_SOURCE.endBatch();
            JET_SOURCE.endBatch();
            SOURCE.endBatch();
        }
    }
}
