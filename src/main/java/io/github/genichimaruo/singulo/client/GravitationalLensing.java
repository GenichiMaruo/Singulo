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
    /**
     * region は歪みが及ぶ球の半径（ブロック）。この球を通る光だけが曲がり、球より手前のものは歪まない。
     * horizon が false なら黒い中心とアインシュタインリングを出さない（景色を歪めるだけ。手に持った杖の先など）。
     */
    private record Source(Vec3 pos, float radius, float strength, float region, boolean horizon) {}

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final int MAX_LENSES = 4;
    /** アインシュタイン半径 ÷ 事象の地平線の半径。大きいほど背景が大きく歪む（lensing.fsh の HORIZON と逆数の関係）。 */
    public static final float EINSTEIN_PER_HORIZON = 2.2F;
    private static ShaderInstance shader;
    private static TextureTarget copy;

    private GravitationalLensing() {}

    /** このフレームのレンズを足す。radius はアインシュタイン半径（ブロック）、strength は 0〜1。 */
    public static void add(Vec3 pos, float radius, float strength) {
        add(pos, radius, strength, radius * 2.5F);
    }

    /** region: 歪みが及ぶ球の半径（ブロック）。リアクターなら内側の空洞に収める。 */
    public static void add(Vec3 pos, float radius, float strength, float region) {
        if (SOURCES.size() < 32) {
            SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), true));
        }
    }

    /** 景色を少し歪めるだけのレンズ（黒い中心・リングなし）。 */
    public static void addDistortion(Vec3 pos, float radius, float strength, float region) {
        if (SOURCES.size() < 32) {
            SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), false));
        }
    }

    /** 杖の先の球の見かけの半径に対する、歪みの大きさと範囲。 */
    private static final float STAFF_LENS_PER_ORB = 0.8F;
    private static final float STAFF_REGION_PER_ORB = 2.4F;
    /** 一人称の歪みを置く奥行き（ブロック）。これより奥の景色が歪む。 */
    private static final float STAFF_DEPTH = 1.0F;

    /** グラビトン・マニピュレーターを持っているプレイヤーの杖の先に、小さな歪みを置く。 */
    private static void addHeldStaffs(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                if (!(p.getItemInHand(hand).getItem() instanceof io.github.genichimaruo.singulo.item.GravitonManipulatorItem)) {
                    continue;
                }
                boolean right = (hand == net.minecraft.world.InteractionHand.MAIN_HAND)
                        == (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
                Vec3 tip;
                if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                    // 画面上の位置から、今の視野角で同じ所に写るワールドの点を逆算する
                    var cam = event.getCamera();
                    Matrix4f proj = event.getProjectionMatrix();
                    Vec3 look = new Vec3(cam.getLookVector());
                    Vec3 up = new Vec3(cam.getUpVector());
                    Vec3 left = new Vec3(cam.getLeftVector());
                    // 手の揺れ・振り・持ち替えに合わせるため、直前のフレームで実際に杖の球が描かれた位置を使う。
                    // まだ描かれていない（持ち替えた直後など）なら歪みも出さない
                    float[] seen = StaffTipTracker.recent(right);
                    if (seen == null) {
                        continue;
                    }
                    float sx = seen[0];
                    float sy = seen[1];
                    float lensUv = seen[2] * STAFF_LENS_PER_ORB;
                    float regionUv = seen[2] * STAFF_REGION_PER_ORB;
                    float d = STAFF_DEPTH;
                    tip = cam.getPosition().add(look.scale(d))
                            .add(left.scale(-sx * d / proj.m00())).add(up.scale(sy * d / proj.m11()));
                    // 縦の UV での大きさ → ブロック（r_uv = m11 × 半径 ÷ 奥行き ÷ 2 の逆）
                    float radius = lensUv * 2 * d / proj.m11();
                    float region = regionUv * 2 * d / proj.m11();
                    addDistortion(tip, radius, 0.8F, region);
                    continue;
                } else {
                    float yaw = net.minecraft.util.Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
                    Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
                    Vec3 side = new Vec3(-net.minecraft.util.Mth.cos(yaw), 0, -net.minecraft.util.Mth.sin(yaw));
                    tip = p.getPosition(pt).add(0, p.getBbHeight() * 0.62 + 0.55, 0)
                            .add(side.scale(right ? 0.38 : -0.38)).add(forward.scale(0.35));
                }
                addDistortion(tip, 0.1F, 0.8F, 0.32F);
            }
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
            StaffTipTracker.nextFrame();
            if (shader != null && enabled()) {
                addHeldStaffs(event);
            }
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
        float[][] spheres = new float[MAX_LENSES][];
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
            // 歪みは球（region）の中だけ。その範囲が画面に少しでもかかれば効かせる
            float reach = r * s.region / s.radius;
            float aspect = (float) w / h;
            if (x < -reach / aspect || x > 1 + reach / aspect || y < -reach || y > 1 + reach || r < 0.002F) {
                continue;
            }
            // 球の情報: (中心までの奥行き, アインシュタイン半径, 球の半径) ブロック単位
            spheres[n] = new float[]{v.w, s.radius, s.region, s.horizon ? 1 : 0};
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
            float[] sp = i < n ? spheres[i] : new float[]{0, 1, 1, 0};
            shader.safeGetUniform("Sphere" + i).set(sp[0], sp[1], sp[2], sp[3]);
        }
        shader.safeGetUniform("DepthParams").set(proj.m22(), proj.m32());
        shader.safeGetUniform("Aspect").set((float) w / h);
        // drawWithShader は描く直前に SamplerN を RenderSystem のシェーダーテクスチャで上書きするので、
        // setSampler ではなくそちらに画面のコピーを渡す（でないと別のテクスチャを読んで画面が真っ黒になる）
        int previous = RenderSystem.getShaderTexture(0);
        int previousDepth = RenderSystem.getShaderTexture(1);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        // 深度: 球より手前のものを歪めないのに使う（深度は書き込まないので、描いている画面のものをそのまま読む）
        RenderSystem.setShaderTexture(1, main.getDepthTextureId());

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
        RenderSystem.setShaderTexture(0, previous);
        RenderSystem.setShaderTexture(1, previousDepth);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
