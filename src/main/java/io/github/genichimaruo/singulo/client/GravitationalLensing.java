package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 重力レンズの画面効果。ブラックホール（リアクターの炉心）とワームホールの口の描画がフレームごとに「レンズ」を登録し、
 * ワールドを描き終えたあとで画面をコピーして、点質量レンズの式で背景を中心から外へ押し広げて描き直す。
 * 縁にはアインシュタインリングが光る。1フレームに効かせるのはカメラに近い4つまで。
 * クライアント設定 gravitationalLensing で切れる。Iris 系のシェーダーパックが入っていて lensingWithShaderPacks が
 * false のときは掛けない（歪みなしの表示に戻る）。
 */
public final class GravitationalLensing {
    private record Source(Vec3 pos, float radius, float strength) {}

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final int MAX_LENSES = 4;
    private static ShaderInstance shader;
    private static TextureTarget copy;

    private GravitationalLensing() {}

    /** このフレームのレンズを足す。radius はアインシュタイン半径（ブロック）、strength は 0〜1。 */
    public static void add(Vec3 pos, float radius, float strength) {
        if (SOURCES.size() < 32) {
            SOURCES.add(new Source(pos, radius, strength));
        }
    }

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Singulo.id("lensing"),
                    DefaultVertexFormat.POSITION_TEX), s -> shader = s);
        } catch (IOException e) {
            // 読めなければレンズなし（歪みなしの表示）で続ける
            Singulo.LOGGER.error("重力レンズのシェーダーを読めなかった", e);
        }
    }

    static boolean enabled() {
        if (ClientConfig.SPEC.isLoaded() && !ClientConfig.GRAVITATIONAL_LENSING.get()) {
            return false;
        }
        boolean shaderPack = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
        return !shaderPack || !ClientConfig.SPEC.isLoaded() || ClientConfig.LENSING_WITH_SHADER_PACKS.get();
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        try {
            if (!SOURCES.isEmpty() && shader != null && enabled()) {
                apply(event);
            }
        } finally {
            SOURCES.clear();
        }
    }

    private static void apply(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f proj = event.getProjectionMatrix();
        Matrix4f view = new Matrix4f().rotation(event.getCamera().rotation().conjugate(new Quaternionf()));
        float[][] lenses = new float[MAX_LENSES][];
        int n = 0;
        SOURCES.sort(Comparator.comparingDouble(s -> s.pos.distanceToSqr(cam)));
        for (Source s : SOURCES) {
            if (n >= MAX_LENSES) {
                break;
            }
            Vector4f v = new Vector4f((float) (s.pos.x - cam.x), (float) (s.pos.y - cam.y), (float) (s.pos.z - cam.z), 1);
            view.transform(v);
            proj.transform(v);
            if (v.w <= 0.05F) {
                continue;                                    // カメラの後ろ
            }
            float x = v.x / v.w * 0.5F + 0.5F;
            float y = v.y / v.w * 0.5F + 0.5F;
            float r = proj.m11() * s.radius / v.w * 0.5F;   // 縦方向の UV での半径
            if (x < -0.5F || x > 1.5F || y < -0.5F || y > 1.5F || r < 0.002F) {
                continue;
            }
            lenses[n++] = new float[]{x, y, Math.min(r, 0.5F), s.strength};
        }
        if (n == 0) {
            return;
        }
        if (copy == null || copy.width != w || copy.height != h) {
            if (copy != null) {
                copy.destroyBuffers();
            }
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        }
        // 今の画面をコピーし、それを歪めて元の画面に描き直す
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        for (int i = 0; i < MAX_LENSES; i++) {
            float[] l = i < n ? lenses[i] : new float[]{0, 0, 0, 0};
            shader.safeGetUniform("Lens" + i).set(l[0], l[1], l[2], l[3]);
        }
        shader.safeGetUniform("Aspect").set((float) w / h);
        shader.setSampler("Sampler0", copy.getColorTextureId());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-1, -1, 0).setUv(0, 0);
        b.addVertex(1, -1, 0).setUv(1, 0);
        b.addVertex(1, 1, 0).setUv(1, 1);
        b.addVertex(-1, 1, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
