package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * 降着ジェット鍛造炉の光の描き方。どちらも加算で重ね（明るい所が重なるほど白く）、深度は書かない（光どうしで隠し合わない）。
 * <ul>
 *   <li>{@link #GLOW}: 色だけの面（輪・閃光・破片・稲妻など）</li>
 *   <li>{@link #BEAM}: ジェットとビームのシェーダー（jet_beam。軸に沿って流れる渦と波を画素ごとに描く）。読めなかったときは
 *       {@link #beam()} が null を返し、呼び出し側で GLOW の帯に切り替える</li>
 * </ul>
 */
final class JetForgeRenderTypes extends RenderType {
    @Nullable
    private static ShaderInstance beamShader;

    static final RenderType GLOW = create("singulo_jet_forge_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            4096, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    static final RenderType BEAM_TYPE = create("singulo_jet_forge_beam", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            16384, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> beamShader))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private JetForgeRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                                Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** ビームのシェーダーの描き方（読めなかった・シェーダーパックを使っているときは null）。 */
    @Nullable
    static RenderType beam() {
        return beamShader == null || GravitationalLensing.shaderPackInUse() ? null : BEAM_TYPE;
    }

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Singulo.id("jet_beam"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> beamShader = s);
        } catch (IOException e) {
            Singulo.LOGGER.error("降着ジェット鍛造炉のビームのシェーダーを読めなかった", e);
        }
    }
}
