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
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 重力レンズの画面効果。ブラックホール（リアクターの炉心）とワームホールの口の描画がフレームごとに「レンズ」を登録し、
 * ワールドを描き終えたあとで画面をコピーして、シュバルツシルト時空の光の曲がり方で背景をずらして描き直す。
 * 中心は地平線の約2.6倍の影で黒く、その縁に光子リングが光り、外側では背景がアインシュタインリング状に引き伸ばされる。
 * 歪みの範囲はミップマップから読むので、低い解像度でもブロック状のノイズにならない。1フレームに効かせるのはカメラに近い4つまで。
 * クライアント設定 gravitationalLensing で切れる。
 * <p>
 * Iris 系のシェーダーパックを使っているとき（専用の処理）: シェーダーパックは自分の画面と深度で描き、最後に画面へ合成するので、
 * ワールドを描き終えた時点ではまだ絵ができていない。そこで、レンズの位置だけをその時点で覚えておき、合成が終わったあと
 * （GUI を描く直前）に掛ける。シェーダーパックの深度は読めないので、手前のものを除く処理は使わず、球の範囲の中だけを歪める。
 * lensingWithShaderPacks が false なら、シェーダーパック使用時は掛けない。
 */
public final class GravitationalLensing {
    /**
     * region は歪みが及ぶ球の半径（ブロック）。この球を通る光だけが曲がり、球より手前のものは歪まない。
     * horizon が false なら黒い中心とアインシュタインリングを出さない（景色を歪めるだけ。手に持った杖の先など）。
     */
    private record Source(Vec3 pos, float radius, float strength, float region, boolean horizon, Disk disk) {}

    /**
     * シェーダーで光の経路をたどって描く降着円盤。normal はワールドでの面の向き、beaming はドップラーで明るさが変わる幅。
     * brightness は明るさの倍率（1 で従来どおり。大きいほど白く熱い）、swirl は渦の筋の時計（秒。0 なら筋を描かない）。
     */
    private record Disk(Vec3 normal, float inner, float outer, float beaming, float brightness, float swirl) {}

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final int MAX_LENSES = 4;
    /**
     * add に渡す大きさ ÷ 事象の地平線の半径。ブラックホールのレンズは、渡された大きさをこれで割ったものを地平線の半径とし、
     * そこからシュバルツシルト時空の曲がり方で影・光子リング・アインシュタインリングを出す。
     */
    public static final float EINSTEIN_PER_HORIZON = 2.2F;
    /** 影（黒く見える円）の半径 ÷ 地平線の半径（3√3 / 2。lensing.fsh の SHADOW_PER_HORIZON と同じ）。 */
    static final float SHADOW_PER_HORIZON = 2.598F;

    /**
     * ブラックホールの影の半径（ブロック）。地平線の 3√3/2 倍（光子球の臨界衝突パラメータ）で、歪みが球の縁で消えていく前に
     * 収まるよう、歪みが及ぶ球の半径の半分までにする。影をワールドに描くときもこの大きさに合わせる。
     */
    static float shadowRadius(float horizon, float region) {
        return Math.min(horizon * SHADOW_PER_HORIZON, region * 0.5F);
    }
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
            SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), true, null));
        }
    }

    /**
     * 降着円盤つきのブラックホール。シェーダーが画素ごとに光の経路をたどり、影と円盤（重力で影の上下に回り込んで見える
     * 奥側の像も含めて）を描く。このフレームにワールドでレンズを掛けない（設定で切っている・シェーダーパック使用時）なら
     * 何もせず false を返すので、呼び出し側で影と円盤をワールドに描き、{@link #add} でレンズだけを足す。
     */
    static boolean addBlackHole(Vec3 pos, float radius, float strength, float region, Vec3 diskNormal, float inner, float outer,
                                float beaming) {
        if (shader == null || !enabled() || shaderPackInUse() || SOURCES.size() >= 32) {
            return false;
        }
        SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), true,
                new Disk(diskNormal.normalize(), inner, outer, beaming, 1, 0)));
        return true;
    }

    /** 明るさと渦の筋を変えられる降着円盤つきのブラックホール（降着ジェット鍛造炉）。ほかは {@link #addBlackHole} と同じ。 */
    static boolean addBlackHole(Vec3 pos, float radius, float strength, float region, Vec3 diskNormal, float inner, float outer,
                                float beaming, float brightness, float swirl) {
        if (shader == null || !enabled() || shaderPackInUse() || SOURCES.size() >= 32) {
            return false;
        }
        SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), true,
                new Disk(diskNormal.normalize(), inner, outer, beaming, brightness, swirl)));
        return true;
    }

    /** 景色を少し歪めるだけのレンズ（黒い中心・リングなし）。 */
    public static void addDistortion(Vec3 pos, float radius, float strength, float region) {
        if (SOURCES.size() < 32) {
            SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), false, null));
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
                if (!StaffTipTracker.distorts(p.getItemInHand(hand))) {
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
        return !shaderPackInUse() || !ClientConfig.SPEC.isLoaded() || ClientConfig.LENSING_WITH_SHADER_PACKS.get();
    }

    /** Iris 系のシェーダーパックを使っているか（Iris の公開 API をリフレクションで呼ぶ。入っていなければ false）。 */
    private static java.lang.reflect.Method shaderPackCheck;
    private static Object irisApi;
    private static boolean irisLooked;

    static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    shaderPackCheck = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError e) {
                    Singulo.LOGGER.warn("Iris の API が見つからない（重力レンズはシェーダーパックなしの扱いにする）: {}", e.toString());
                }
            }
        }
        if (shaderPackCheck == null) {
            return false;
        }
        try {
            return (boolean) shaderPackCheck.invoke(irisApi);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** シェーダーパック使用時に、合成のあとで掛けるために覚えておくレンズ（ワールドを描き終えた時点のもの）。 */
    private record Pending(List<Source> sources, Vec3 cam, Matrix4f proj, Quaternionf rotation) {}

    private static Pending pending;

    /** GUI を描く直前（シェーダーパックの合成が終わったあと）に、覚えておいたレンズを掛ける。 */
    static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        Pending p = pending;
        pending = null;
        if (p == null || shader == null) {
            return;
        }
        SOURCES.clear();
        SOURCES.addAll(p.sources());
        try {
            apply(p.cam(), p.proj(), p.rotation(), false);
        } finally {
            SOURCES.clear();
        }
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
                if (shaderPackInUse()) {
                    // シェーダーパックの合成のあとで掛ける
                    pending = new Pending(List.copyOf(SOURCES), event.getCamera().getPosition(), new Matrix4f(event.getProjectionMatrix()),
                            new Quaternionf(event.getCamera().rotation()));
                } else {
                    apply(event.getCamera().getPosition(), event.getProjectionMatrix(), event.getCamera().rotation(), true);
                }
            }
        } finally {
            SOURCES.clear();
        }
    }

    /** レンズを掛ける。useDepth が false なら深度を使わない（シェーダーパック使用時）。 */
    private static void apply(Vec3 cam, Matrix4f proj, Quaternionf cameraRotation, boolean useDepth) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        Matrix4f view = new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf()));
        float[][] lenses = new float[MAX_LENSES][];
        float[][] spheres = new float[MAX_LENSES][];
        float[][] disks = new float[MAX_LENSES][];
        int n = 0;
        SOURCES.sort(Comparator.comparingDouble(s -> s.pos.distanceToSqr(cam)));
        for (Source s : SOURCES) {
            if (n >= MAX_LENSES) {
                break;
            }
            Vector4f v = new Vector4f((float) (s.pos.x - cam.x), (float) (s.pos.y - cam.y), (float) (s.pos.z - cam.z), 1);
            view.transform(v);
            Vector3f inView = new Vector3f(v.x, v.y, v.z);
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
            float uvPerBlock = r / s.radius;
            if (s.horizon) {
                float horizon = s.radius / EINSTEIN_PER_HORIZON;
                float shadow = shadowRadius(horizon, s.region);
                // 背景までの距離の比 D_ls / D_s。背景はカメラからブラックホールまでと同じくらい（球の中から見るなら
                // 球の半径くらい）奥にあるとみなす。近くで見るほど影が大きく、遠くから見るほどアインシュタインリングが広がる
                float behind = Math.max(v.w, s.region);
                float k = behind / (v.w + behind);
                spheres[n] = new float[]{v.w, horizon, reach, k};
                if (s.disk != null) {
                    // 光の経路をたどるときの地平線の半径は、影の大きさから逆算する（影を球の範囲に収めて小さくしたときも
                    // 影と円盤の回り込みが合うように）。中心と面の向きはカメラから見た座標で渡す
                    Vector3f nv = view.transformDirection(new Vector3f((float) s.disk.normal.x, (float) s.disk.normal.y,
                            (float) s.disk.normal.z));
                    disks[n] = new float[]{inView.x, inView.y, inView.z, shadow / SHADOW_PER_HORIZON,
                            nv.x, nv.y, nv.z, s.disk.inner, s.disk.outer, s.disk.beaming * s.strength, s.disk.brightness, s.disk.swirl};
                }
                lenses[n++] = new float[]{x, y, Math.min(shadow * uvPerBlock, 0.5F), s.strength};
            } else {
                // 歪みだけ: 手前の面はアインシュタイン半径の 0.45 倍（従来どおり）
                spheres[n] = new float[]{v.w, s.radius * 0.4545F, reach, 0};
                lenses[n++] = new float[]{x, y, Math.min(r, 0.5F), s.strength};
            }
        }
        if (n == 0) {
            return;
        }
        if (copy == null || copy.width != w || copy.height != h || copy.isStencilEnabled() != main.isStencilEnabled()) {
            if (copy != null) {
                copy.destroyBuffers();
            }
            // 深度もコピーする（描き込み先の深度をそのまま読むと、フレームによって読めたり読めなかったりして、
            // 手前のブロックの上にレンズが出たり消えたりする）。形式を合わせるため、ステンシルの有無も画面にそろえる
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) {
                copy.enableStencil();
            }
            // 歪めて拡大した所はなめらかに補間し、縮める所はミップマップで（1画素が写す範囲をならして）読む
            copy.setFilterMode(GL11.GL_LINEAR);
            GlStateManager._bindTexture(copy.getColorTextureId());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._bindTexture(0);
        }
        // 今の画面をコピーし、それを歪めて元の画面に描き直す
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h,
                useDepth ? GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._bindTexture(copy.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);
        main.bindWrite(true);

        for (int i = 0; i < MAX_LENSES; i++) {
            float[] l = i < n ? lenses[i] : new float[]{0, 0, 0, 0};
            shader.safeGetUniform("Lens" + i).set(l[0], l[1], l[2], l[3]);
            float[] sp = i < n ? spheres[i] : new float[]{0, 1, 1, 0};
            shader.safeGetUniform("Sphere" + i).set(sp[0], sp[1], sp[2], sp[3]);
            float[] dk = i < n && disks[i] != null ? disks[i] : new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
            shader.safeGetUniform("Disk" + i).set(dk[0], dk[1], dk[2], dk[3]);
            shader.safeGetUniform("DiskN" + i).set(dk[4], dk[5], dk[6], dk[7]);
            shader.safeGetUniform("DiskP" + i).set(dk[8], dk[9], dk[10], dk[11]);
        }
        shader.safeGetUniform("InvProj").set(new Matrix4f(proj).invert());
        shader.safeGetUniform("DepthParams").set(proj.m22(), proj.m32());
        shader.safeGetUniform("Aspect").set((float) w / h);
        shader.safeGetUniform("Focal").set(proj.m11() * 0.5F);
        shader.safeGetUniform("UseDepth").set(useDepth ? 1.0F : 0.0F);
        // drawWithShader は描く直前に SamplerN を RenderSystem のシェーダーテクスチャで上書きするので、
        // setSampler ではなくそちらに画面のコピーを渡す（でないと別のテクスチャを読んで画面が真っ黒になる）
        int previous = RenderSystem.getShaderTexture(0);
        int previousDepth = RenderSystem.getShaderTexture(1);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        // 深度: 球より手前のものを歪めないのに使う（コピーしたもの。シェーダーパック使用時は読まない）
        RenderSystem.setShaderTexture(1, copy.getDepthTextureId());

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
