package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.FilterMode;
import org.lwjgl.system.MemoryStack;
import java.util.OptionalInt;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 重力レンズの画面効果。ブラックホール（リアクターの炉心）とワームホールの口の描画がフレームごとに「レンズ」を登録し、
 * ワールドを描き終えたあとで画面をコピーして、シュバルツシルト時空の光の曲がり方で背景をずらして描き直す。
 * 中心は地平線の約2.6倍の影で黒く、その縁に光子リングが光り、外側では背景がアインシュタインリング状に引き伸ばされる。
 * 歪みの範囲はミップマップから読むので、低い解像度でもブロック状のノイズにならない。
 * レンズの数に上限はない: 1つずつ、影響が及ぶ画面の範囲だけを奥のものから描くので、重さは画面に占める面積で決まる。
 * 画面で重なるレンズは手前のものが奥のものを歪めた絵を読めるよう、画面をコピーし直して次のパスで描く（{@link #MAX_PASSES}）。
 * クライアント設定 gravitationalLensing で切れる。
 * <p>
 * Iris 系のシェーダーパックを使っているとき（専用の処理）: シェーダーパックは自分の画面と深度で描き、最後に画面へ合成するので、
 * ワールドを描き終えた時点ではまだ絵ができていない。そこで、レンズの位置だけをその時点で覚えておき、合成が終わったあと
 * （GUI を描く直前）に掛ける。シェーダーパックの深度は読めないので、手前のものを除く処理は使わず、球の範囲の中だけを歪める。
 * lensingWithShaderPacks が false なら、シェーダーパック使用時は掛けない。
 */
public final class GravitationalLensing {
    private enum LensType { BLACK_HOLE, DISTORTION, REMNANT }

    /** region は歪みが及ぶ半径。REMNANT の radius はワールドに描いた黒い核の半径。 */
    private record Source(Vec3 pos, float radius, float strength, float region, LensType type, Disk disk) {}

    /**
     * シェーダーで光の経路をたどって描く降着円盤。normal はワールドでの面の向き、beaming はドップラーで明るさが変わる幅。
     * brightness は明るさの倍率（1 で従来どおり。大きいほど白く熱い）、swirl は渦の筋の時計（0 なら筋を描かない）。
     */
    private record Disk(Vec3 normal, float inner, float outer, float beaming, float brightness, float swirl) {}

    /** 1回の描画に渡すレンズ。minX〜maxY は描く画面の範囲（UV）。lens・sphere・disk はシェーダーの Lens・Sphere・Disk 系にそのまま渡す。 */
    private record Draw(float[] lens, float[] sphere, float[] disk, float minX, float minY, float maxX, float maxY) {
        boolean overlaps(Draw o) {
            return minX < o.maxX && o.minX < maxX && minY < o.maxY && o.minY < maxY;
        }
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    /**
     * 画面をコピーし直す回数の上限。1回ごとに画面全体のミップマップを作り直すので、視線に沿って並んだレンズで重くなりすぎないよう抑える。
     * 超えた分も描くが、同じコピーを読むので、重なった所だけは手前のレンズが奥のレンズを歪めず上に重なる。
     */
    private static final int MAX_PASSES = 16;
    private static final float[] NO_DISK = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
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
    private static final RenderPipeline shader = RenderPipeline.builder()
            .withLocation(Singulo.id("pipeline/lensing")).withVertexShader(Singulo.id("core/lensing"))
            .withFragmentShader(Singulo.id("core/lensing")).withSampler("Sampler0").withSampler("Sampler1")
            .withUniform("LensData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false).build();
    private static LensingCopyTarget copy;

    private GravitationalLensing() {}

    /** このフレームのレンズを足す。radius はアインシュタイン半径（ブロック）、strength は 0〜1。 */
    public static void add(Vec3 pos, float radius, float strength) {
        add(pos, radius, strength, radius * 2.5F);
    }

    /** region: 歪みが及ぶ球の半径（ブロック）。リアクターなら内側の空洞に収める。 */
    public static void add(Vec3 pos, float radius, float strength, float region) {
        SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), LensType.BLACK_HOLE, null));
    }

    /**
     * 降着円盤つきのブラックホール。シェーダーが画素ごとに光の経路をたどり、影と円盤（重力で影の上下に回り込んで見える
     * 奥側の像も含めて）を描く。このフレームにワールドでレンズを掛けない（設定で切っている・シェーダーパック使用時）なら
     * 何もせず false を返すので、呼び出し側で影と円盤をワールドに描き、{@link #add} でレンズだけを足す。
     * brightness は円盤の明るさの倍率、swirl は渦の筋の時計（{@link Disk}）。
     */
    static boolean addBlackHole(Vec3 pos, float radius, float strength, float region, Vec3 diskNormal, float inner, float outer,
                                float beaming, float brightness, float swirl) {
        if (shader == null || !enabled() || shaderPackInUse()) {
            return false;
        }
        SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), LensType.BLACK_HOLE,
                new Disk(diskNormal.normalize(), inner, outer, beaming, brightness, swirl)));
        return true;
    }

    /** 景色を少し歪めるだけのレンズ（黒い中心・リングなし）。 */
    public static void addDistortion(Vec3 pos, float radius, float strength, float region) {
        SOURCES.add(new Source(pos, radius, strength, Math.max(region, radius * 1.2F), LensType.DISTORTION, null));
    }

    static void addRemnant(Vec3 pos, float core, float strength, float region) {
        // 外部シェーダーの合成後は深度がない。手前の機体にも掛かる画面効果は避け、ワールドの核と降着環を使う。
        if (!shaderPackInUse()) {
            SOURCES.add(new Source(pos, core, strength, region, LensType.REMNANT, null));
        }
    }

    /** 杖の先の球の見かけの半径に対する、歪みの大きさと範囲。 */
    private static final float STAFF_LENS_PER_ORB = 0.8F;
    private static final float STAFF_REGION_PER_ORB = 2.4F;
    /** 一人称の歪みを置く奥行き（ブロック）。これより奥の景色が歪む。 */
    private static final float STAFF_DEPTH = 1.0F;

    /** グラビトン・マニピュレーターを持っているプレイヤーの杖の先に、小さな歪みを置く。 */
    private static void addHeldStaffs(ExtractLevelRenderStateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
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
                    Matrix4f proj = event.getRenderState().cameraRenderState.projectionMatrix;
                    Vec3 look = new Vec3(cam.forwardVector());
                    Vec3 up = new Vec3(cam.upVector());
                    Vec3 left = new Vec3(cam.leftVector());
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
                    tip = cam.position().add(look.scale(d))
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

    static void registerShaders(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(shader);
    }

    static void extract(ExtractLevelRenderStateEvent event) {
        StaffTipTracker.nextFrame();
        if (enabled()) addHeldStaffs(event);
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
        if (!(event instanceof RenderLevelStageEvent.AfterLevel)) {
            return;
        }
        try {
            if (!SOURCES.isEmpty() && shader != null && enabled()) {
                if (shaderPackInUse()) {
                    // シェーダーパックの合成のあとで掛ける
                    pending = new Pending(List.copyOf(SOURCES), event.getLevelRenderState().cameraRenderState.pos, new Matrix4f(event.getLevelRenderState().cameraRenderState.projectionMatrix),
                            new Quaternionf(event.getLevelRenderState().cameraRenderState.orientation));
                } else {
                    // ジェットより奥のレンズ → ジェット → 手前のレンズ（奥のブラックホールがジェットの上に出ないように）。
                    // 鍛造炉の炉心のレンズは、遠い炉から順に「その炉の奥側の光 → 炉心のレンズ → 手前側の光」とはさむ
                    // （カメラと炉心のあいだのジェットの上に、炉心のブラックホールが出ないように）
                    Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
                    var groups = new ArrayList<>(GlowDepth.jetGroups());
                    groups.sort(Comparator.comparingDouble((GlowDepth.JetGroup g) -> -g.core.distanceToSqr(cam)));
                    java.util.Map<GlowDepth.JetGroup, List<Source>> own = new java.util.IdentityHashMap<>();
                    List<Source> behind = new ArrayList<>();
                    List<Source> front = new ArrayList<>();
                    for (Source s : SOURCES) {
                        GlowDepth.JetGroup owner = null;
                        for (GlowDepth.JetGroup g : groups) {
                            if (g.core.distanceToSqr(s.pos) < 1) {
                                owner = g;
                                break;
                            }
                        }
                        if (owner != null) {
                            own.computeIfAbsent(owner, g -> new ArrayList<>()).add(s);
                        } else if (GlowDepth.behindJet(cam, s.pos, s.region)) {
                            behind.add(s);
                        } else {
                            front.add(s);
                        }
                    }
                    applyOnly(behind, cam, event);
                    GlowDepth.drawJets(event);
                    // 炉心より奥にあるほかのレンズ（紡糸機・リアクターなど）は、その炉心のレンズと同じ回に掛ける。
                    // 1回の中では奥から描くので、手前の炉心のレンズが奥のブラックホールもまわりの景色と一緒に歪める
                    // （炉心のレンズより後に掛けると、奥のブラックホールだけが歪まない位置に出て、景色とずれる）。
                    // ジェットが手前にかかるレンズは behind に入っているので、炉の奥側の光のあとに掛けてもよい
                    front.sort(Comparator.comparingDouble((Source s) -> -s.pos.distanceToSqr(cam)));
                    int next = 0;
                    for (GlowDepth.JetGroup g : groups) {
                        GlowDepth.drawJets(event, g, false);
                        double reach = g.core.distanceToSqr(cam);
                        List<Source> batch = new ArrayList<>(own.getOrDefault(g, List.of()));
                        while (next < front.size() && front.get(next).pos.distanceToSqr(cam) > reach) {
                            batch.add(front.get(next++));
                        }
                        applyOnly(batch, cam, event);
                        GlowDepth.drawJets(event, g, true);
                    }
                    applyOnly(front.subList(next, front.size()), cam, event);
                }
            }
        } finally {
            SOURCES.clear();
            // レンズがないフレームや、レンズを切っているときのジェット（組もここで片付ける）
            GlowDepth.finishJets(event);
        }
    }

    /** sources のレンズだけを掛ける（空なら何もしない）。 */
    private static void applyOnly(List<Source> sources, Vec3 cam, RenderLevelStageEvent event) {
        if (sources.isEmpty()) {
            return;
        }
        SOURCES.clear();
        SOURCES.addAll(sources);
        apply(cam, event.getLevelRenderState().cameraRenderState.projectionMatrix, event.getLevelRenderState().cameraRenderState.orientation, true);
    }

    /** レンズを掛ける。useDepth が false なら深度を使わない（シェーダーパック使用時）。 */
    private static void apply(Vec3 cam, Matrix4f proj, Quaternionf cameraRotation, boolean useDepth) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // Fabulous composites color back into main and clears its depth first. The
        // translucent target retains opaque terrain plus the translucent depth.
        RenderTarget depthSource = mc.levelRenderer.getTranslucentTarget();
        if (depthSource == null || !useDepth) {
            depthSource = main;
        }
        int w = main.width;
        int h = main.height;
        float aspect = (float) w / h;
        Matrix4f view = new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf()));
        // 奥のレンズから描く。同じ場所に重ねた歪みと影では、影と円盤を手前（あと）にする
        SOURCES.sort(Comparator.comparingDouble((Source s) -> -s.pos.distanceToSqr(cam)).thenComparing(s -> s.disk != null));
        List<Draw> draws = new ArrayList<>();
        for (Source s : SOURCES) {
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
            // 歪みは球（region）の中だけ
            float reach = r * s.region / s.radius;
            if (reach * h < 1) {
                continue;                                    // 1画素に満たない
            }
            float uvPerBlock = r / s.radius;
            float[] lens;
            float[] sphere;
            float[] disk = NO_DISK;
            if (s.type == LensType.BLACK_HOLE) {
                float horizon = s.radius / EINSTEIN_PER_HORIZON;
                float shadow = shadowRadius(horizon, s.region);
                // 背景までの距離の比 D_ls / D_s。背景はカメラからブラックホールまでと同じくらい（球の中から見るなら
                // 球の半径くらい）奥にあるとみなす。近くで見るほど影が大きく、遠くから見るほどアインシュタインリングが広がる
                float behind = Math.max(v.w, s.region);
                float k = behind / (v.w + behind);
                sphere = new float[]{v.w, horizon, reach, k};
                if (s.disk != null) {
                    // 光の経路をたどるときの地平線の半径は、影の大きさから逆算する（影を球の範囲に収めて小さくしたときも
                    // 影と円盤の回り込みが合うように）。中心と面の向きはカメラから見た座標で渡す
                    Vector3f nv = view.transformDirection(new Vector3f((float) s.disk.normal.x, (float) s.disk.normal.y,
                            (float) s.disk.normal.z));
                    disk = new float[]{inView.x, inView.y, inView.z, shadow / SHADOW_PER_HORIZON,
                            nv.x, nv.y, nv.z, s.disk.inner, s.disk.outer, s.disk.beaming * s.strength, s.disk.brightness, s.disk.swirl};
                }
                lens = new float[]{x, y, Math.min(shadow * uvPerBlock, 0.5F), s.strength};
            } else if (s.type == LensType.REMNANT) {
                sphere = new float[]{v.w, s.radius, reach, -1};
                lens = new float[]{x, y, r, s.strength};
            } else {
                // 歪みだけ: 手前の面はアインシュタイン半径の 0.45 倍（従来どおり）
                sphere = new float[]{v.w, s.radius * 0.4545F, reach, 0};
                lens = new float[]{x, y, Math.min(r, 0.5F), s.strength};
            }
            // 描く範囲: 歪みの球と円盤を覆う長方形（はみ出した分は切る。画面にかからないレンズはここで落ちる）
            float[] rect = {x - reach / aspect, y - reach, x + reach / aspect, y + reach};
            if (s.disk != null) {
                includeDisk(rect, inView, s.disk.outer, proj);
            }
            float minX = Math.max(rect[0] - 2F / w, 0);
            float minY = Math.max(rect[1] - 2F / h, 0);
            float maxX = Math.min(rect[2] + 2F / w, 1);
            float maxY = Math.min(rect[3] + 2F / h, 1);
            if (minX < maxX && minY < maxY) {
                draws.add(new Draw(lens, sphere, disk, minX, minY, maxX, maxY));
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        // パスを割り当てる: 画面で重なる奥のレンズより後のパスにする（重ならないレンズは同じコピーを読んで同じパスで描ける）
        int[] passes = new int[draws.size()];
        int passCount = 0;
        for (int i = 0; i < passes.length; i++) {
            for (int j = 0; j < i; j++) {
                if (draws.get(i).overlaps(draws.get(j))) {
                    passes[i] = Math.max(passes[i], passes[j] + 1);
                }
            }
            passes[i] = Math.min(passes[i], MAX_PASSES - 1);
            passCount = Math.max(passCount, passes[i] + 1);
        }
        if (copy == null || copy.width != w || copy.height != h || copy.useStencil != depthSource.useStencil || copy.getColorTexture().getFormat() != main.getColorTexture().getFormat()) {
            if (copy != null) copy.destroyBuffers();
            copy = new LensingCopyTarget(w, h, depthSource.useStencil, main.getColorTexture().getFormat());
        }
        for (int pass = 0; pass < passCount; pass++) {
            copyScreen(main, depthSource, w, h, useDepth && pass == 0);
            for (int i = 0; i < passes.length; i++) {
                if (passes[i] == pass) drawLens(draws.get(i), main, proj, aspect, useDepth);
            }
        }
    }

    /** 円盤の光をたどる範囲（シェーダーでは外側の半径の 1.25 倍の球）が画面に写る範囲を rect に足す。カメラがそばにあれば画面全体。 */
    private static void includeDisk(float[] rect, Vector3f inView, float outer, Matrix4f proj) {
        float e = outer * 1.25F + 0.1F;
        for (int i = 0; i < 8; i++) {
            Vector4f c = new Vector4f(inView.x + ((i & 1) == 0 ? -e : e), inView.y + ((i & 2) == 0 ? -e : e),
                    inView.z + ((i & 4) == 0 ? -e : e), 1);
            proj.transform(c);
            if (c.w <= 0.05F) {
                rect[0] = 0;
                rect[1] = 0;
                rect[2] = 1;
                rect[3] = 1;
                return;
            }
            float x = c.x / c.w * 0.5F + 0.5F;
            float y = c.y / c.w * 0.5F + 0.5F;
            rect[0] = Math.min(rect[0], x);
            rect[1] = Math.min(rect[1], y);
            rect[2] = Math.max(rect[2], x);
            rect[3] = Math.max(rect[3], y);
        }
    }

    /** 今の画面をコピーに写す（これを歪めて元の画面に描き直す）。 */
    private static void copyScreen(RenderTarget main, RenderTarget depthSource, int w, int h, boolean depth) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, w, h);
        if (depth) copy.copyDepthFrom(depthSource);
        copy.generateMipmaps();
    }

    private static void drawLens(Draw d, RenderTarget main, Matrix4f proj, float aspect, boolean useDepth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder data = Std140Builder.onStack(stack, 192);
            data.putMat4f(new Matrix4f(proj).invert());
            data.putVec4(proj.m22(), proj.m32(), aspect, proj.m11() * 0.5F);
            data.putVec4(useDepth ? 1F : 0F, 0F, 0F, 0F);
            data.putVec4(d.lens[0], d.lens[1], d.lens[2], d.lens[3]);
            data.putVec4(d.sphere[0], d.sphere[1], d.sphere[2], d.sphere[3]);
            data.putVec4(d.disk[0], d.disk[1], d.disk[2], d.disk[3]);
            data.putVec4(d.disk[4], d.disk[5], d.disk[6], d.disk[7]);
            data.putVec4(d.disk[8], d.disk[9], d.disk[10], d.disk[11]);
            data.putVec4(d.minX, d.minY, d.maxX, d.maxY);
            try (GpuBuffer uniforms = RenderSystem.getDevice().createBuffer(() -> "Singulo lens uniforms", GpuBuffer.USAGE_UNIFORM, data.get());
                 var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                         () -> "Singulo gravitational lens", main.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(shader);
                pass.setUniform("LensData", uniforms);
                pass.bindTexture("Sampler0", copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true));
                pass.bindTexture("Sampler1", copy.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(0, 3);
            }
        }
    }
}
