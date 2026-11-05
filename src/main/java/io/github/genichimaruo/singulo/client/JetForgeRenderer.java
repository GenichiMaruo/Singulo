package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeLayout;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 降着ジェット鍛造炉の演出。座標はすべて制御核のブロックの角からの位置（{@link JetForgeLayout} の形の座標を変換したもの）。
 * <ul>
 *   <li>炉心: 縦向きの降着円盤つきのブラックホール（重力レンズのシェーダーで光の経路をたどる。明るさと渦の筋は段階で変わる）。
 *       拘束コイルの輪を電流の光が巡る</li>
 *   <li>ジェット: 点火で両極から太いプラズマが伸び、3つの収束リングが入るたびに絞られて、白く細いビームになって素材に届く。
 *       反対側は排熱側の杯に当たって広がり、杯の奥が赤く焼ける。リングからビームへ稲妻が走る</li>
 *   <li>素材: 照射室の中央に浮かび、赤熱 → 亀裂 → 破片と光の輪 → 白熱。仕上げの閃光と衝撃波（重力レンズの歪みの輪）のあと、
 *       完成品が冷えて回収口へ降りる</li>
 *   <li>予告: 警告灯が回り、照射室の四方に力場の膜が張る</li>
 * </ul>
 * 遠いと細部（稲妻・破片・亀裂）を省き、ジェットの分割を減らす。
 */
public class JetForgeRenderer implements BlockEntityRenderer<JetForgeBlockEntity> {
    /** 影の半径（ブロック）。地平線はこの 1/2.598、重力レンズに渡す半径（アインシュタイン半径）は地平線の 2.2 倍。 */
    static final float SHADOW = 0.85F;
    static final float DISK_OUTER = 3.6F;
    /** 収束リングの、炉心からの距離と、絞ったあとのビームの太さ。 */
    static final float[] RING_S = {JetForgeLayout.LENS_X[0] - JetForgeLayout.CORE_X, JetForgeLayout.LENS_X[1] - JetForgeLayout.CORE_X,
            JetForgeLayout.LENS_X[2] - JetForgeLayout.CORE_X};
    static final float TARGET_S = JetForgeLayout.TARGET_X - JetForgeLayout.CORE_X;
    static final float STOP_S = JetForgeLayout.STOP_X - JetForgeLayout.CORE_X - 0.5F;
    static final float SINK_S = -(JetForgeLayout.CORE_X - 0.5F);

    private static final int CYAN = 0x8CD2FF;
    private static final int WHITE = 0xFFF8F0;
    private static final int PLASMA = 0x7EA0FF;
    private static final int WARM = 0xFFC48C;
    private static final int AMBER = 0xFFA028;

    public JetForgeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(JetForgeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public AABB getRenderBoundingBox(JetForgeBlockEntity be) {
        BlockPos p = be.getBlockPos();
        int r = JetForgeLayout.W;
        return new AABB(p).inflate(r, JetForgeLayout.H, r);
    }

    /** 1回の描画で使う基準（形の向きと、カメラの位置）。 */
    /** 鍛造炉ごとの渦の筋の時計: (前に見た時刻, 時計)。 */
    private final Map<JetForgeBlockEntity, float[]> swirlClocks = new WeakHashMap<>();

    private record Frame(PoseStack.Pose pose, Vec3 origin, Vec3 axis, Vec3 up, Vec3 cam, float time, float dist, boolean far) {}

    @Override
    public void render(JetForgeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       int packedOverlay) {
        Direction back = be.back();
        if (back == null || be.getLevel() == null || !be.formed() && !be.shownCore()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = be.getBlockPos();
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        Vec3 axis = JetForgeLayout.axis(back);
        Vec3 coreW = JetForgeLayout.core(pos, back);
        Vec3 core = coreW.subtract(corner);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition().subtract(corner);
        float time = (be.getLevel().getGameTime() % 24000L + partialTick) / 20F;
        float dist = (float) cam.distanceTo(core);
        JetForgeTimeline tl = JetForgeTimeline.of(be, partialTick);
        Frame f = new Frame(pose.last(), core, axis, new Vec3(0, 1, 0), cam, time, dist, dist > 96);
        // 2種類の光と素材のアイテムを交互に描くので、頂点ごとに描き込み先を引き直す（共有の描き込み先は、別の描き方を引くと閉じられる）
        VertexConsumer glow = new Lazy(buffers, JetForgeRenderTypes.GLOW);
        RenderType beamType = JetForgeRenderTypes.beam();
        VertexConsumer beam = beamType == null ? null : new Lazy(buffers, beamType);
        boolean fancy = mc.options.graphicsMode().get() != net.minecraft.client.GraphicsStatus.FAST;

        if (be.shownCore()) {
            drawCore(be, f, tl, coreW, buffers, glow);
        }
        if (!be.formed()) {
            return;
        }
        float ignition = be.blackHole() == JetForgeBlockEntity.BH_IGNITING ? Math.max(0.001F, be.ignitionProgress()) : 0;
        // 点火の3つの段（Pリアクターと同じ。点火の音の目覚め・注入・崩壊に合わせる）
        float wake = Mth.clamp(ignition / 0.375F, 0, 1);
        float inject = Mth.clamp((ignition - 0.375F) / (0.85F - 0.375F), 0, 1);
        float ignitionPower = ignition > 0 ? 0.25F * wake + 0.75F * inject : 0;
        drawCoils(f, tl, glow, ignitionPower);
        if (ignition > 0) {
            drawIgnition(f, beam, glow, coreW, ignition, be.ignitionCharged(), back, pos, corner, buffers);
        }
        if (be.shownCore() && be.formedAge() >= 0 && be.formedAge() < FLASH_TICKS) {
            drawFormationFlash(f, glow, (be.formedAge() + partialTick) / FLASH_TICKS);
        }
        // 降着円盤へ物質を送り込む流れ（点火の注入から、加工の点火〜仕上げ）
        float feed = Math.max(ignition > 0 ? inject : 0, feedStrength(tl));
        if (feed > 0.01F) {
            drawInfall(f, beam, glow, feed, fancy);
        }
        if (tl.active()) {
            float flicker = tl.stalled() ? 0.35F + 0.3F * Mth.sin(time * 37F) * Mth.sin(time * 13F) : 1;
            drawJets(f, tl, beam, glow, flicker, fancy);
            drawCollimators(f, tl, glow, flicker);
            drawBeacons(f, tl, glow, back, pos, corner);
            drawField(f, tl, beam, glow, back, pos, corner);
            drawSinks(f, tl, glow, flicker);
            drawImpact(f, tl, glow, flicker);
            drawMaterial(be, f, tl, pose, buffers, glow, back, pos, corner, partialTick);
            drawFinish(f, tl, glow, coreW.add(axis.scale(TARGET_S)));
        }
    }

    // ------------------------------------------------------------------ 炉心

    private void drawCore(JetForgeBlockEntity be, Frame f, JetForgeTimeline tl, Vec3 coreW, MultiBufferSource buffers, VertexConsumer glow) {
        float horizon = SHADOW / GravitationalLensing.SHADOW_PER_HORIZON;
        float radius = horizon * GravitationalLensing.EINSTEIN_PER_HORIZON;
        // 待機中は暗い赤、運転中は白く熱く、仕上げの瞬間がいちばん明るい
        float bright = 0.55F + 1.4F * tl.power();
        if (tl.finish() >= 0) {
            bright += 1.2F * Math.max(0, 1 - tl.finish() / 10F);
        }
        float beaming = 0.45F;
        boolean traced = GravitationalLensing.addBlackHole(coreW, radius, 1, 5.0F, f.axis(), SHADOW * 1.1F, DISK_OUTER, beaming, bright,
                swirlClock(be, f.time(), tl.power()));
        if (!traced) {
            // 重力レンズを掛けない（設定・シェーダーパック）: 黒い球と、光る円盤をワールドに描く
            VertexConsumer solid = FxDraw.glow(buffers);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(f.pose().pose());
            ps.last().normal().set(f.pose().normal());
            ps.translate(f.origin().x, f.origin().y, f.origin().z);
            FxDraw.sphere(ps.last(), solid, SHADOW * 0.75F, 0x000000, LightTexture.FULL_BRIGHT);
            float a = Mth.clamp(0.25F + 0.5F * tl.power(), 0, 1);
            disc(f, glow, f.origin(), f.axis(), SHADOW * 1.1F, DISK_OUTER, 48, WARM, (int) (220 * a), 0, 0);
            GravitationalLensing.add(coreW, radius, 1, 5.0F);
        }
    }

    /**
     * 円盤の渦の筋の時計。時刻に速さを掛けると出力が変わった瞬間に模様が飛ぶので、速さを積み上げる。
     * 筋は 2π/3 ごとに同じ形に戻るので、その周期で折り返す（0 は筋なしの意味なので返さない）。
     */
    private float swirlClock(JetForgeBlockEntity be, float time, float power) {
        float[] clock = swirlClocks.computeIfAbsent(be, b -> new float[]{time, 0});
        float dt = Mth.clamp(time - clock[0], 0, 1);
        clock[0] = time;
        clock[1] = (clock[1] + dt * (0.4F + 1.6F * power)) % (Mth.TWO_PI / 3);
        return Math.max(clock[1], 1.0E-3F);
    }

    /** 拘束コイルの輪を巡る電流の光。 */
    private void drawCoils(Frame f, JetForgeTimeline tl, VertexConsumer glow, float ignition) {
        float a = 0.12F + 0.78F * Math.max(tl.power(), ignition);
        for (int k = 0; k < 2; k++) {
            float s = JetForgeLayout.COIL_X[k] - JetForgeLayout.CORE_X;
            Vec3 c = f.origin().add(f.axis().scale(s));
            float r = JetForgeLayout.COIL_R;
            int segs = f.far() ? 32 : 72;
            float speed = (k == 0 ? 1 : -1) * (1.5F + 6F * tl.power());
            ringSegments(f, glow, c, f.axis(), r - 0.62F, r + 0.62F, segs, CYAN, a, f.time() * speed, 6);
        }
    }

    // ------------------------------------------------------------------ ジェットとビーム

    /** 絞っていないジェットの太さ（炉心からの距離 s で）。 */
    private static float wideRadius(float s) {
        float x = Math.abs(s);
        return 0.36F + 0.26F * x;
    }

    /** 3つのリングで絞りきったビームの太さ。 */
    private static float narrowRadius(float s) {
        if (s < 4) {
            return Mth.lerp(Mth.clamp((s - 0.8F) / 3.2F, 0, 1), 0.4F, 1.2F);
        }
        if (s < RING_S[0]) {
            return Mth.lerp((s - 4) / (RING_S[0] - 4), 1.2F, 0.9F);
        }
        if (s < RING_S[1]) {
            return Mth.lerp((s - RING_S[0]) / (RING_S[1] - RING_S[0]), 0.9F, 0.55F);
        }
        if (s < RING_S[2]) {
            return Mth.lerp((s - RING_S[1]) / (RING_S[2] - RING_S[1]), 0.55F, 0.28F);
        }
        return 0.28F;
    }

    /** 絞りきったあとの、リング i を抜けたビームの太さ。 */
    private static final float[] PINCHED = {0.9F, 0.55F, 0.28F};

    /** s の位置の絞られ具合（そこより手前のリングが全部入っているほど 1。色と光の配分に使う）。 */
    private static float focusAt(JetForgeTimeline tl, float s) {
        float out = 0.45F * tl.ring(0) * Mth.clamp(s / RING_S[0], 0, 1);
        float all = 1;
        for (int i = 0; i < 3; i++) {
            all = Math.min(all, tl.ring(i));
            float w = JetForgeTimeline.ease((s - RING_S[i] + 1.5F) / 2F);
            out = Mth.lerp(w, out, Math.max(out, all));
        }
        return out;
    }

    /** s の位置のビームの太さ。入ったリングを抜けるたびに細くなる（入っていないリングの先は太いまま広がる）。 */
    private static float radiusAt(JetForgeTimeline tl, float s) {
        float r = wideRadius(s);
        if (s < RING_S[0]) {
            r = Mth.lerp(0.6F * tl.ring(0) * JetForgeTimeline.ease(s / RING_S[0]), r, narrowRadius(s));
        }
        float all = 1;
        for (int i = 0; i < 3; i++) {
            all = Math.min(all, tl.ring(i));
            float w = JetForgeTimeline.ease((s - RING_S[i] + 1.5F) / 2F);
            r = Mth.lerp(w * all, r, Math.min(r, Math.max(PINCHED[i], narrowRadius(s))));
        }
        return r;
    }

    private void drawJets(Frame f, JetForgeTimeline tl, @Nullable VertexConsumer beam, VertexConsumer glow, float flicker, boolean fancy) {
        if (tl.jet() <= 0.001F) {
            return;
        }
        int n = f.dist() < 48 ? 72 : f.far() ? 24 : 40;
        // 加工側: 収束が進むほど遠くまで届き、照射中は素材の位置まで（素材を抜けた弱い光はビーム止めまで）
        float wideReach = 4 + 9 * tl.jet();
        float reach = Mth.lerp(JetForgeTimeline.ease(tl.focus() * 1.15F), wideReach, STOP_S);
        float pulse = tl.phase() == JetForgeBlockEntity.IRRADIATE ? 1 + 0.12F * Mth.sin(f.time() * 6.3F) : 1;
        float[] ss = new float[n + 1];
        float[] rr = new float[n + 1];
        float[] aa = new float[n + 1];
        float[] ee = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = 0.9F + (reach - 0.9F) * i / n;
            float e = focusAt(tl, s);
            ss[i] = s;
            ee[i] = e;
            rr[i] = radiusAt(tl, s) * pulse * (0.6F + 0.4F * tl.jet());
            float fade = 1 - JetForgeTimeline.ease((s - (reach - 2.5F)) / 2.5F);
            // 素材を抜けた先は弱い
            if (s > TARGET_S + 0.4F) {
                fade *= 0.25F;
            }
            aa[i] = fade * tl.jet() * flicker;
        }
        tube(f, beam, glow, ss, rr, aa, ee, 1, fancy);
        // 反対側（排熱側）: 絞らない太いプラズマが杯に当たる
        int m = n / 2;
        float back = Math.min(-SINK_S - 0.3F, 3 + 10 * tl.jet());
        float[] bs = new float[m + 1];
        float[] br = new float[m + 1];
        float[] ba = new float[m + 1];
        float[] be = new float[m + 1];
        for (int i = 0; i <= m; i++) {
            float s = 0.9F + (back - 0.9F) * i / m;
            bs[i] = s;
            float flare = JetForgeTimeline.ease((s - (-SINK_S - 3.5F)) / 3.2F);
            br[i] = Math.min(Math.min(2.7F, 0.38F + 0.27F * s) + 0.5F * flare, 3.1F) * (0.6F + 0.4F * tl.jet());
            ba[i] = tl.jet() * flicker * (1 - 0.45F * flare);
            be[i] = 0;
        }
        tube(f, beam, glow, bs, br, ba, be, -1, fancy);
    }

    /**
     * 軸に沿った光の筒。dir は 1 で加工側、−1 で排熱側。外側の光暈・プラズマ・芯の3層を、軸のまわりの立体の筒で重ねる
     * （頂点ごとに「カメラから見て筒のどのくらい横か」を渡すので、中心が明るく縁が消え、斜めから見ても平たくならない）。
     * 絞られ具合 e で色と太さを変える。
     */
    private void tube(Frame f, @Nullable VertexConsumer beam, VertexConsumer glow, float[] s, float[] r, float[] a, float[] e, int dir,
                      boolean fancy) {
        int n = s.length;
        Vec3[] pts = new Vec3[n];
        for (int i = 0; i < n; i++) {
            pts[i] = f.origin().add(f.axis().scale(s[i] * dir));
        }
        int sides = f.far() ? 8 : 12;
        for (int layer = fancy ? 0 : 1; layer < 3; layer++) {
            float[] w = new float[n];
            float[] al = new float[n];
            int[] c = new int[n];
            for (int i = 0; i < n; i++) {
                w[i] = layerWidth(layer, r[i], e[i]);
                al[i] = a[i] * layerAlpha(layer, e[i]);
                c[i] = layerColor(layer, e[i]);
            }
            pathTube(f, beam, glow, pts, w, al, c, s, layer == 0 ? 2 : layer == 1 ? 0 : 1, sides);
        }
    }

    /**
     * 点の列に沿った光の筒。u は流れの位置（シェーダーが模様を流す）、style はシェーダーの種類。
     * 各輪の頂点には、カメラから見た横方向の位置 v（−1〜1）を渡す。シェーダーがなければ、v から明るさを付けた色だけの筒。
     */
    private static void pathTube(Frame f, @Nullable VertexConsumer beam, VertexConsumer glow, Vec3[] pts, float[] w, float[] a, int[] c,
                                 float[] u, int style, int sides) {
        int n = pts.length;
        Vec3[][] ring = new Vec3[n][sides + 1];
        float[][] vv = new float[n][sides + 1];
        // 輪の向きは1つ前の輪から引き継ぐ（平行移動）。輪ごとに決め直すと、曲がった所で向きが急に回って
        // 隣の輪との間の面がねじれ、ギザギザの星のような三角形が出る
        Vec3 e1 = null;
        for (int i = 0; i < n; i++) {
            Vec3 t = pts[Math.min(n - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            t = t.lengthSqr() < 1e-8 ? f.axis() : t.normalize();
            if (e1 != null) {
                e1 = e1.subtract(t.scale(e1.dot(t)));
            }
            e1 = e1 == null || e1.lengthSqr() < 1e-6 ? basis(t)[0] : e1.normalize();
            Vec3[] b = {e1, t.cross(e1).normalize()};
            Vec3 view = pts[i].subtract(f.cam());
            Vec3 sd = t.cross(view);
            sd = sd.lengthSqr() < 1e-8 ? b[0] : sd.normalize();
            for (int j = 0; j <= sides; j++) {
                float th = j * Mth.TWO_PI / sides;
                Vec3 radial = b[0].scale(Mth.cos(th)).add(b[1].scale(Mth.sin(th)));
                ring[i][j] = pts[i].add(radial.scale(w[i]));
                vv[i][j] = (float) radial.dot(sd);
            }
        }
        float base = 4F * style;
        for (int i = 0; i < n - 1; i++) {
            if (a[i] <= 0.002F && a[i + 1] <= 0.002F) {
                continue;
            }
            for (int j = 0; j < sides; j++) {
                int[][] q = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] k : q) {
                    float v = vv[k[0]][k[1]];
                    if (beam != null) {
                        uv(beam, f, ring[k[0]][k[1]], c[k[0]], a[k[0]] * 0.55F, u[k[0]], base + v);
                    } else {
                        col(glow, f, ring[k[0]][k[1]], c[k[0]], a[k[0]] * 0.45F * (1 - v * v));
                    }
                }
            }
        }
    }

    private static float layerWidth(int layer, float r, float e) {
        return switch (layer) {
            case 0 -> r * (2.4F - 0.6F * e);
            case 1 -> r * (1.05F - 0.25F * e);
            default -> r * (0.28F + 0.22F * e);
        };
    }

    private static int layerColor(int layer, float e) {
        return switch (layer) {
            case 0 -> FxDraw.lerpRgb(0x6A70FF, CYAN, e);
            case 1 -> FxDraw.lerpRgb(PLASMA, 0xBFE6FF, e);
            default -> FxDraw.lerpRgb(WARM, WHITE, e);
        };
    }

    private static float layerAlpha(int layer, float e) {
        return switch (layer) {
            case 0 -> 0.3F + 0.25F * e;
            case 1 -> 0.85F - 0.45F * e;
            default -> 0.35F + 0.85F * e;
        };
    }

    /** 帯の横向き（軸とカメラへの向きの両方に直交）。軸に沿って見ているときは上向き。 */
    private static Vec3 side(Frame f, Vec3 p) {
        Vec3 view = p.subtract(f.cam());
        Vec3 sd = f.axis().cross(view);
        double l = sd.length();
        return l < 1e-4 ? f.up() : sd.scale(1 / l);
    }

    private static void ribbonShader(Frame f, VertexConsumer vc, Vec3 p0, Vec3 p1, float w0, float w1, int c0, int c1, float a0,
                                     float a1, float u0, float u1, int style) {
        Vec3 s0 = side(f, p0).scale(w0);
        Vec3 s1 = side(f, p1).scale(w1);
        float v = 4F * style;
        uv(vc, f, p0.subtract(s0), c0, a0, u0, v - 1);
        uv(vc, f, p0.add(s0), c0, a0, u0, v + 1);
        uv(vc, f, p1.add(s1), c1, a1, u1, v + 1);
        uv(vc, f, p1.subtract(s1), c1, a1, u1, v - 1);
    }

    /** シェーダーがないとき: 中心が明るく縁が消える2枚の帯。 */
    private static void ribbonGlow(Frame f, VertexConsumer vc, Vec3 p0, Vec3 p1, float w0, float w1, int c0, int c1, float a0, float a1) {
        Vec3 s0 = side(f, p0).scale(w0);
        Vec3 s1 = side(f, p1).scale(w1);
        for (int k = -1; k <= 1; k += 2) {
            col(vc, f, p0, c0, a0);
            col(vc, f, p0.add(s0.scale(k)), c0, 0);
            col(vc, f, p1.add(s1.scale(k)), c1, 0);
            col(vc, f, p1, c1, a1);
        }
    }

    // ------------------------------------------------------------------ 収束リング

    private void drawCollimators(Frame f, JetForgeTimeline tl, VertexConsumer glow, float flicker) {
        for (int i = 0; i < 3; i++) {
            float on = tl.ring(i);
            float idle = tl.phase() <= JetForgeBlockEntity.IGNITE ? 0.15F * tl.power() : 0;
            float a = Math.max(on, idle) * flicker;
            if (a <= 0.01F) {
                continue;
            }
            Vec3 c = f.origin().add(f.axis().scale(RING_S[i]));
            float R = JetForgeLayout.LENS_R[i];
            ringSegments(f, glow, c, f.axis(), R - 0.62F, R + 0.55F, f.far() ? 24 : 56, 0xA8E4FF, 0.85F * a, f.time() * (4 + i * 2F), 4);
            // 入った瞬間のひらめき
            float flash = tl.phase() == JetForgeBlockEntity.FOCUS ? Math.max(0, 1 - Math.abs(tl.focus() - i / 3F) * 12) : 0;
            if (flash > 0) {
                disc(f, glow, c, f.axis(), R - 0.6F, R + 2.5F * flash, 48, WHITE, (int) (200 * flash), 0, 0);
            }
            if (!f.far() && on > 0.3F) {
                arcs(f, glow, c, R, narrowRadius(RING_S[i]) * 1.2F, i, on * flicker);
            }
        }
    }

    /** リングの内側からビームへ走る稲妻（3 tick ごとに形が変わる）。 */
    private void arcs(Frame f, VertexConsumer glow, Vec3 c, float R, float rb, int ring, float a) {
        Vec3[] basis = basis(f.axis());
        int seed = (int) (f.time() * 20 / 3);
        RandomSource rnd = RandomSource.create(seed * 31L + ring * 977L);
        for (int k = 0; k < 3; k++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            Vec3 dir = basis[0].scale(Mth.cos(ang)).add(basis[1].scale(Mth.sin(ang)));
            Vec3 from = c.add(dir.scale(R - 0.55F));
            Vec3 to = c.add(dir.scale(rb)).add(f.axis().scale(0.6F + rnd.nextFloat() * 1.2F));
            Vec3 prev = from;
            int segs = 7;
            for (int j = 1; j <= segs; j++) {
                float k2 = j / (float) segs;
                Vec3 p = from.lerp(to, k2);
                if (j < segs) {
                    float jitter = 0.32F * Mth.sin(k2 * Mth.PI);
                    p = p.add(basis[0].scale((rnd.nextFloat() - 0.5F) * jitter * 2))
                            .add(basis[1].scale((rnd.nextFloat() - 0.5F) * jitter * 2))
                            .add(f.axis().scale((rnd.nextFloat() - 0.5F) * jitter));
                }
                ribbonGlow(f, glow, prev, p, 0.16F, 0.16F, 0x9FD8FF, 0x9FD8FF, 0.5F * a, 0.5F * a);
                ribbonGlow(f, glow, prev, p, 0.05F, 0.05F, WHITE, WHITE, a, a);
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------ 警告灯・力場の膜

    private void drawBeacons(Frame f, JetForgeTimeline tl, VertexConsumer glow, Direction back, BlockPos pos, Vec3 corner) {
        boolean warn = tl.phase() == JetForgeBlockEntity.PRIME || tl.stalled();
        float spin = f.time() * (warn ? 5.5F : 2.0F);
        float blink = warn ? 0.55F + 0.45F * Mth.sin(f.time() * 14F) : 0.6F;
        double[][] spots = {{JetForgeLayout.CORE_X, JetForgeLayout.AXIS_Y + JetForgeLayout.COIL_R + 0.75, JetForgeLayout.AXIS_Z},
                {JetForgeLayout.CAGE_X0, JetForgeLayout.AXIS_Y + JetForgeLayout.CAGE_HALF + 0.75, JetForgeLayout.AXIS_Z - JetForgeLayout.CAGE_HALF},
                {JetForgeLayout.CAGE_X0, JetForgeLayout.AXIS_Y + JetForgeLayout.CAGE_HALF + 0.75, JetForgeLayout.AXIS_Z + JetForgeLayout.CAGE_HALF},
                {JetForgeLayout.CAGE_X1, JetForgeLayout.AXIS_Y + JetForgeLayout.CAGE_HALF + 0.75, JetForgeLayout.AXIS_Z - JetForgeLayout.CAGE_HALF},
                {JetForgeLayout.CAGE_X1, JetForgeLayout.AXIS_Y + JetForgeLayout.CAGE_HALF + 0.75, JetForgeLayout.AXIS_Z + JetForgeLayout.CAGE_HALF}};
        int color = tl.stalled() && tl.phase() != JetForgeBlockEntity.PRIME ? 0xFF4A30 : AMBER;
        for (int i = 0; i < spots.length; i++) {
            Vec3 p = JetForgeLayout.at(pos, back, spots[i][0], spots[i][1] - 0.5, spots[i][2]).subtract(corner);
            glowBox(f, glow, p, 0.16F, color, (int) (255 * blink));
            billboard(f, glow, p, 0.9F, color, (int) (150 * blink));
            if (!f.far()) {
                // 回る光の束（向かい合う2本）
                float ang = spin + i * 1.3F;
                for (int k = 0; k < 2; k++) {
                    float a2 = ang + k * Mth.PI;
                    Vec3 d = new Vec3(Mth.cos(a2), -0.08, Mth.sin(a2)).normalize();
                    ribbonGlow(f, glow, p, p.add(d.scale(3.2)), 0.08F, 0.9F, color, color, 0.45F * blink, 0);
                }
            }
        }
    }

    /** 照射室の四方に張る安全の膜（予告で張り、冷却の終わりで消える）。 */
    private void drawField(Frame f, JetForgeTimeline tl, @Nullable VertexConsumer beam, VertexConsumer glow, Direction back, BlockPos pos,
                           Vec3 corner) {
        float a = JetForgeTimeline.ease(tl.prime()) * (1 - JetForgeTimeline.ease((tl.cool() - 0.6F) / 0.4F));
        if (tl.phase() >= JetForgeBlockEntity.FOCUS && tl.phase() <= JetForgeBlockEntity.FINISH) {
            a *= 0.55F;
        }
        if (tl.finish() >= 0) {
            a = Math.min(1.2F, a + 0.5F * Math.max(0, 1 - tl.finish() / 8F));
        }
        if (a <= 0.01F) {
            return;
        }
        double x0 = JetForgeLayout.CAGE_X0 + 0.5, x1 = JetForgeLayout.CAGE_X1 - 0.5;
        double h = JetForgeLayout.CAGE_HALF - 0.45;
        double cy = JetForgeLayout.AXIS_Y, cz = JetForgeLayout.AXIS_Z;
        // 手前・奥の面と、上・下の面
        Vec3[][] quads = {
                {at(pos, back, corner, x0, cy - h, cz - h), at(pos, back, corner, x1, cy - h, cz - h), at(pos, back, corner, x1, cy + h, cz - h),
                        at(pos, back, corner, x0, cy + h, cz - h)},
                {at(pos, back, corner, x0, cy - h, cz + h), at(pos, back, corner, x1, cy - h, cz + h), at(pos, back, corner, x1, cy + h, cz + h),
                        at(pos, back, corner, x0, cy + h, cz + h)},
                {at(pos, back, corner, x0, cy + h, cz - h), at(pos, back, corner, x1, cy + h, cz - h), at(pos, back, corner, x1, cy + h, cz + h),
                        at(pos, back, corner, x0, cy + h, cz + h)},
                {at(pos, back, corner, x0, cy - h, cz - h), at(pos, back, corner, x1, cy - h, cz - h), at(pos, back, corner, x1, cy - h, cz + h),
                        at(pos, back, corner, x0, cy - h, cz + h)}};
        int color = tl.stalled() ? 0xFF8A50 : 0x7FD0FF;
        float len = (float) (x1 - x0);
        Vec3 middle = f.origin().add(f.axis().scale(TARGET_S));
        for (Vec3[] q : quads) {
            // 奥の膜は格子ごとはっきり描く（素材が手前で隠す）。カメラと素材の間の膜は、縁だけ光らせて真ん中を透明にする
            // （真ん中まで描くと、膜の格子が素材の上に乗って素材が透けて見える）
            Vec3 paneCenter = q[0].add(q[2]).scale(0.5);
            boolean near = paneCenter.subtract(middle).dot(f.cam().subtract(middle)) > 0;
            int n = 8;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    float[][] cell = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    for (float[] k : cell) {
                        float su = k[0] / n;
                        float sv = k[1] / n;
                        Vec3 p = q[0].add(q[1].subtract(q[0]).scale(su)).add(q[3].subtract(q[0]).scale(sv));
                        // 縁からの近さ（縁で 1、真ん中で 0）
                        float edge = Math.max(Math.abs(su * 2 - 1), Math.abs(sv * 2 - 1));
                        float w = near ? (float) Math.pow(edge, 6) : 1;
                        if (beam != null) {
                            uv(beam, f, p, color, (near ? 0.5F : 0.5F) * a * w, su * len, 12 + (sv * 2 - 1));
                        } else {
                            col(glow, f, p, color, 0.08F * a * w);
                        }
                    }
                }
            }
        }
    }

    private static Vec3 at(BlockPos pos, Direction back, Vec3 corner, double x, double y, double z) {
        return JetForgeLayout.at(pos, back, x, y, z).subtract(corner);
    }

    // ------------------------------------------------------------------ 排熱側の杯・ビーム止め

    private void drawSinks(Frame f, JetForgeTimeline tl, VertexConsumer glow, float flicker) {
        float heat = Math.max(tl.jet() * flicker, tl.cool() > 0 ? 0.5F * (1 - tl.cool()) : 0);
        if (heat <= 0.01F) {
            return;
        }
        // 杯の奥の板と4枚の壁の内側が、当たったプラズマで焼ける（面に貼りつく光。中心ほど・奥ほど熱い）
        float flutter = 0.85F + 0.15F * Mth.sin(f.time() * 9.0F);
        Vec3[] b = basis(f.axis());
        float half = JetForgeLayout.SINK_HALF - 0.5F;
        heatFace(f, glow, f.origin().add(f.axis().scale(SINK_S + 0.02F)), b[0], b[1], half, half, 2.3F, heat * flutter, 1F);
        for (int k = 0; k < 4; k++) {
            Vec3 out = (k < 2 ? b[0] : b[1]).scale(k % 2 == 0 ? 1 : -1);
            Vec3 along = k < 2 ? b[1] : b[0];
            Vec3 c = f.origin().add(f.axis().scale(SINK_S + 1.0F)).add(out.scale(half - 0.02F));
            heatWall(f, glow, c, along, f.axis(), half, 1.0F, heat * flutter);
        }
        // 素材を抜けた光が止まる板
        float stop = tl.jet() * flicker * (tl.phase() == JetForgeBlockEntity.IRRADIATE ? 0.7F : 0.35F);
        if (stop > 0.01F) {
            heatFace(f, glow, f.origin().add(f.axis().scale(STOP_S - 0.02F)), b[0], b[1], 2.5F, 2.5F, 1.2F, stop, 0.8F);
        }
    }

    /** 面に貼りつく熱の光（中心からの距離で弱まる。端は消えているので、面の縁で切れて見えない）。 */
    private static void heatFace(Frame f, VertexConsumer vc, Vec3 c, Vec3 e1, Vec3 e2, float h1, float h2, float falloff, float a, float white) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (float[] k : q) {
                    float x = -h1 + 2 * h1 * k[0] / n;
                    float y = -h2 + 2 * h2 * k[1] / n;
                    float r2 = (x * x + y * y) / (falloff * falloff);
                    float g = (float) Math.exp(-r2);
                    int rgb = FxDraw.lerpRgb(0xFF5A1E, 0xFFE8B0, Math.min(1, g * white));
                    col(vc, f, c.add(e1.scale(x)).add(e2.scale(y)), rgb, a * g * 0.9F);
                }
            }
        }
    }

    /** 杯の壁の内側の光（奥の板に近いほど熱く、壁の中央ほど明るい）。 */
    private static void heatWall(Frame f, VertexConsumer vc, Vec3 c, Vec3 along, Vec3 axis, float half, float depth, float a) {
        int n = 8;
        int m = 3;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                float[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (float[] k : q) {
                    float x = -half + 2 * half * k[0] / n;
                    float d = -depth + 2 * depth * k[1] / m;
                    float g = (float) Math.exp(-x * x / 4.0) * (0.25F + 0.75F * (1 - (d + depth) / (2 * depth)));
                    col(vc, f, c.add(along.scale(x)).add(axis.scale(d)), FxDraw.lerpRgb(0xFF5A1E, 0xFFC070, g), a * g * 0.6F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 降着円盤へ流れ込む物質・点火

    /** 加工中の流れ込みの強さ（点火で強まり、照射のあいだ続き、仕上げで止まる）。 */
    private static float feedStrength(JetForgeTimeline tl) {
        return switch (tl.phase()) {
            case JetForgeBlockEntity.IGNITE -> JetForgeTimeline.ease(tl.t() / 40F);
            case JetForgeBlockEntity.FOCUS, JetForgeBlockEntity.IRRADIATE -> 1;
            case JetForgeBlockEntity.FINISH -> 1 - JetForgeTimeline.ease(tl.t() / 20F);
            default -> 0;
        };
    }

    /**
     * 拘束コイルの4本の梁の先（噴射口）から、質量ペレットを溶かした物質の流れが降着円盤へらせんを描いて落ちていく。
     * 円盤と同じ向きに回りながら細く熱くなり、流れに乗った明るい塊が次々と円盤の内側へ吸い込まれる。
     */
    private void drawInfall(Frame f, @Nullable VertexConsumer beam, VertexConsumer glow, float a, boolean fancy) {
        Vec3[] b = basis(f.axis());
        int segs = f.far() ? 20 : 40;
        int sides = f.far() ? 6 : 8;
        for (int k = 0; k < 4; k++) {
            float start = k * Mth.HALF_PI;
            Vec3[] pts = new Vec3[segs + 1];
            float[] w = new float[segs + 1];
            float[] al = new float[segs + 1];
            int[] c = new int[segs + 1];
            float[] u = new float[segs + 1];
            for (int i = 0; i <= segs; i++) {
                float t = i / (float) segs;
                float r = 1.05F + 5.35F * (float) Math.pow(1 - t, 1.3);
                // 円盤の回る向き（円盤の面の向き × 中心からの向き）へ1周と少し回る
                float phi = start + t * 1.25F * Mth.TWO_PI;
                Vec3 dir = b[0].scale(Mth.cos(phi)).add(b[1].scale(Mth.sin(phi)));
                // 噴射口から出たばかりは少し軸の方へふくらんでから、円盤の面へ落ちる
                float bulge = 0.6F * Mth.sin(t * Mth.PI) * (k % 2 == 0 ? 1 : -1) * (1 - t);
                pts[i] = f.origin().add(dir.scale(r)).add(f.axis().scale(bulge));
                w[i] = (0.6F - 0.32F * t) * (0.7F + 0.3F * a);
                al[i] = a * Math.min(1, t * 6) * (1 - 0.6F * JetForgeTimeline.ease((t - 0.85F) / 0.15F));
                c[i] = FxDraw.lerpRgb(0xFF8A3C, 0xFFF0D8, t);
                u[i] = t * 9;
            }
            // 荒れたプラズマのガス（細い線に見えないよう、太くやわらかく、縁をちぎる）と、外側のにじむ光
            pathTube(f, beam, glow, pts, w, al, c, u, 0, sides);
            float[] wh = new float[segs + 1];
            float[] ah = new float[segs + 1];
            int[] ch = new int[segs + 1];
            for (int i = 0; i <= segs; i++) {
                wh[i] = w[i] * (fancy ? 2.2F : 1.6F);
                ah[i] = al[i] * 0.4F;
                ch[i] = FxDraw.lerpRgb(0xFF6A30, 0xFFC890, i / (float) segs);
            }
            pathTube(f, beam, glow, pts, wh, ah, ch, u, 2, sides);
            // 噴射口の光と、流れに乗って落ちていく明るい塊
            billboard(f, glow, pts[0], 0.75F * a, 0xFFB070, (int) (200 * a));
            if (!f.far()) {
                for (int j = 0; j < 3; j++) {
                    float t = ((f.time() * 0.55F) + j / 3F + k * 0.13F) % 1F;
                    int idx = Math.min(segs, Math.round(t * segs));
                    billboard(f, glow, pts[idx], 0.7F * (1 - 0.4F * t), 0xFFE8C8, (int) (130 * a * Math.min(1, t * 6)));
                }
            }
        }
    }

    /** ブラックホールができた瞬間の閃光の長さ（tick）。 */
    static final int FLASH_TICKS = 30;

    /**
     * 点火の演出（Pリアクターと同じ長さと段。点火の音と合わせる）。
     * <ol>
     *   <li>目覚め（〜6秒）: 拘束コイルを電流が巡り始め、8つの炉心安定化コイルが0.75秒ごとに1つずつ灯る</li>
     *   <li>注入（〜13.6秒）: 8つの安定化コイルから種へ光が流れ込み、噴射口からの物質も降着円盤へ落ち始める。種が脈打ちながら膨らむ</li>
     *   <li>崩壊（最後の2.4秒）: 光の根元が中心へ寄り、種を囲む3つの輪が絞り込まれ、黒い地平線が生まれかける</li>
     * </ol>
     * 電力が満ちていないうちは、流れ込む光がちらつく。
     */
    private void drawIgnition(Frame f, @Nullable VertexConsumer beam, VertexConsumer glow, Vec3 coreW, float p, boolean charged, Direction back,
                              BlockPos pos, Vec3 corner, MultiBufferSource buffers) {
        float wake = Mth.clamp(p / 0.375F, 0, 1);
        float inject = Mth.clamp((p - 0.375F) / (0.85F - 0.375F), 0, 1);
        float collapse = Mth.clamp((p - 0.85F) / 0.15F, 0, 1);
        float power = 0.25F * wake + 0.75F * inject;
        float flicker = charged ? 1F : 0.55F + 0.45F * Mth.sin(f.time() * 46F) * Mth.sin(f.time() * 14F);
        int beamRgb = FxDraw.lerpRgb(0x64DCFF, 0xFFFFFF, Math.min(1, power + collapse));
        Vec3 c = f.origin();
        int[][] st = JetForgeLayout.stabilizers();
        for (int i = 0; i < st.length; i++) {
            Vec3 from = JetForgeLayout.at(pos, back, st[i][0], JetForgeLayout.AXIS_Y + st[i][1], JetForgeLayout.AXIS_Z + st[i][2]).subtract(corner);
            float lit = Mth.clamp(wake * st.length - i, 0, 1);
            if (lit > 0) {
                billboard(f, glow, from, 0.9F + 0.6F * power, beamRgb, (int) (200 * lit));
                glowBox(f, glow, from, 0.55F, beamRgb, (int) (90 * lit));
            }
            if (inject > 0) {
                // 光の流れ（崩壊では根元も中心へ寄る）と、流れに乗って種へ落ちる光の粒
                Vec3 root = c.add(from.subtract(c).scale(1 - 0.82F * collapse * collapse));
                int n = 10;
                Vec3[] pts = new Vec3[n + 1];
                float[] w = new float[n + 1];
                float[] al = new float[n + 1];
                int[] col = new int[n + 1];
                float[] u = new float[n + 1];
                for (int k = 0; k <= n; k++) {
                    float t = k / (float) n;
                    pts[k] = root.lerp(c, t);
                    w[k] = (0.05F + 0.18F * inject + 0.12F * collapse) * (1 - 0.5F * t);
                    al[k] = (0.25F + 0.75F * inject) * flicker;
                    col[k] = beamRgb;
                    u[k] = t * 6;
                }
                pathTube(f, beam, glow, pts, w, al, col, u, 1, 6);
                for (int k = 0; k < n + 1; k++) {
                    w[k] *= 3.2F;
                    al[k] *= 0.35F;
                }
                pathTube(f, beam, glow, pts, w, al, col, u, 2, 6);
                for (int k = 0; k < 2; k++) {
                    float t = (f.time() * (0.6F + 1.6F * inject + 4F * collapse) + i * 0.137F + k * 0.5F) % 1F;
                    billboard(f, glow, root.lerp(c, t), 0.25F + 0.35F * inject, 0xFFFFFF, (int) ((120 + 120 * inject) * flicker));
                }
            }
        }
        // 中心の種: 何重にも重なった光と、種を囲む3つの輪
        float pulse = 1 + (0.12F + 0.25F * collapse) * Mth.sin(f.time() * 20 * (0.3F + 1.4F * power + 4F * collapse));
        float seed = (0.4F + 1.6F * power) * (1 - 0.55F * collapse) * pulse;
        billboard(f, glow, c, seed * 3.2F, 0x3A70FF, (int) (40 + 70 * power));
        billboard(f, glow, c, seed * 1.8F, 0x64DCFF, (int) (70 + 120 * power));
        billboard(f, glow, c, seed, 0xFFFFFF, (int) (170 + 85 * Math.max(power, collapse)));
        if (inject > 0) {
            float ringR = (0.6F + 1.4F * inject) * (1 - 0.7F * collapse);
            int a = (int) (120 * inject + 100 * collapse);
            float spin = f.time() * (1 + 4 * collapse);
            Vec3[] b = basis(f.axis());
            Vec3[] normals = {f.axis(), b[0].scale(Mth.cos(spin)).add(f.axis().scale(Mth.sin(spin))).normalize(),
                    b[1].scale(Mth.cos(spin * 1.3F)).add(b[0].scale(Mth.sin(spin * 1.3F))).normalize()};
            for (Vec3 nrm : normals) {
                disc(f, glow, c, nrm, ringR, ringR + 0.06F + 0.06F * inject, 48, 0xB8ECFF, a, a, 0);
            }
        }
        if (collapse > 0.55F) {
            // 黒い地平線が生まれかける
            float k = (collapse - 0.55F) / 0.45F;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(f.pose().pose());
            ps.last().normal().set(f.pose().normal());
            ps.translate(c.x, c.y, c.z);
            FxDraw.sphere(ps.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), 0.1F + 0.3F * k * k, 0x000000,
                    LightTexture.FULL_BRIGHT);
        }
        GravitationalLensing.addDistortion(coreW, 0.1F + 0.4F * power * power + 0.8F * collapse, 0.3F + 0.5F * power + 0.3F * collapse, 4.5F);
    }

    /** ブラックホールができた瞬間: まぶしい光が縮み、3つの面に衝撃波の輪が広がる（k は 0〜1）。 */
    private void drawFormationFlash(Frame f, VertexConsumer glow, float k) {
        Vec3 c = f.origin();
        billboard(f, glow, c, 0.5F + 4.5F * (1 - k), 0xFFFFFF, (int) (255 * (1 - k)));
        billboard(f, glow, c, 1.5F + 7F * (1 - k), 0x9ED8FF, (int) (150 * (1 - k)));
        Vec3[] b = basis(f.axis());
        float r = 0.8F + 9F * JetForgeTimeline.ease(k);
        int a = (int) (220 * (1 - k));
        for (Vec3 n : new Vec3[]{f.axis(), b[0], b[1]}) {
            disc(f, glow, c, n, r * 0.9F, r, 64, 0xCFEFFF, a, (int) (a * 0.3F), 0);
        }
    }

    // ------------------------------------------------------------------ 素材

    /** 熱の色（0 は暗い赤、1 は白熱。1 を超えると青白い）。 */
    static int heatColor(float h) {
        if (h < 0.3F) {
            return FxDraw.lerpRgb(0x501408, 0xC8280A, h / 0.3F);
        }
        if (h < 0.55F) {
            return FxDraw.lerpRgb(0xC8280A, 0xFF7A1E, (h - 0.3F) / 0.25F);
        }
        if (h < 0.8F) {
            return FxDraw.lerpRgb(0xFF7A1E, 0xFFD278, (h - 0.55F) / 0.25F);
        }
        if (h <= 1F) {
            return FxDraw.lerpRgb(0xFFD278, 0xFFFAEB, (h - 0.8F) / 0.2F);
        }
        return FxDraw.lerpRgb(0xFFFAEB, 0xD8ECFF, Math.min(1, (h - 1) / 0.3F));
    }

    private void drawImpact(Frame f, JetForgeTimeline tl, VertexConsumer glow, float flicker) {
        if (tl.phase() != JetForgeBlockEntity.IRRADIATE && !(tl.phase() == JetForgeBlockEntity.FOCUS && tl.focus() > 0.8F)) {
            return;
        }
        Vec3 t = f.origin().add(f.axis().scale(TARGET_S - 0.45F));
        float pulse = 0.5F + 0.5F * Mth.sin(f.time() * 6.3F);
        float a = flicker * (tl.phase() == JetForgeBlockEntity.FOCUS ? (tl.focus() - 0.8F) * 5 : 1);
        // 当たった所の光: 大きな光は後ろへずらし（素材の前に平たい板が重ならないように）、芯は立体の小さな光の球
        billboard(f, glow, behind(f, t, 1.0F), 0.9F + 0.5F * pulse + 0.6F * tl.irr(), 0xCFEBFF, (int) (170 * a));
        glowBall(f, glow, t, 0.14F + 0.05F * pulse, WHITE, a);
        glowBall(f, glow, t, 0.3F + 0.08F * pulse, 0xBFE6FF, 0.35F * a);
        if (f.far()) {
            return;
        }
        // 光の輪: 照射が進むほど速く、たくさん
        float period = Mth.lerp(tl.shatter(), 1.2F, 0.5F);
        for (int k = 0; k < 3; k++) {
            float age = ((f.time() / period) + k / 3F) % 1F;
            float strength = Math.max(0.25F, tl.shatter()) * a;
            float r = 0.4F + 3.4F * age;
            int alpha = (int) (200 * strength * (1 - age) * (1 - age));
            if (alpha > 2) {
                disc(f, glow, f.origin().add(f.axis().scale(TARGET_S - 0.2F)), f.axis(), r - 0.18F - 0.3F * age, r, 48,
                        FxDraw.lerpRgb(WHITE, 0x8CC8FF, age), alpha, alpha, 0);
            }
        }
    }

    private void drawMaterial(JetForgeBlockEntity be, Frame f, JetForgeTimeline tl, PoseStack pose, MultiBufferSource buffers,
                              VertexConsumer glow, Direction back, BlockPos pos, Vec3 corner, float partialTick) {
        int count = be.batchSize();
        Vec3[] plane = basis(f.axis());
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            Vec3 offset = count == 1 ? Vec3.ZERO : plane[0].scale(Math.cos(angle) * 0.9).add(plane[1].scale(Math.sin(angle) * 0.9));
            drawOneMaterial(be, f, tl, pose, buffers, glow, back, pos, corner, partialTick, offset);
            if (count > 1 && tl.phase() >= JetForgeBlockEntity.FOCUS && tl.phase() <= JetForgeBlockEntity.IRRADIATE) {
                Vec3 target = f.origin().add(f.axis().scale(TARGET_S)).add(offset);
                ribbonGlow(f, glow, f.origin().add(f.axis().scale(TARGET_S - 3)), target,
                        0.025F, 0.09F, CYAN, WHITE, 0.35F, 0.6F);
            }
        }
    }

    private void drawOneMaterial(JetForgeBlockEntity be, Frame f, JetForgeTimeline tl, PoseStack pose, MultiBufferSource buffers,
                                VertexConsumer glow, Direction back, BlockPos pos, Vec3 corner, float partialTick, Vec3 offset) {
        ItemStack stack = tl.phase() >= JetForgeBlockEntity.FINISH && !be.result().isEmpty() ? be.result() : be.working();
        if (stack.isEmpty()) {
            return;
        }
        Vec3 target = f.origin().add(f.axis().scale(TARGET_S));
        // 回収口の上で、素材が回収口のブロックにめりこまない高さ（素材は 1.35 倍に描くので、半分の高さは約0.7）
        Vec3 bottom = JetForgeLayout.collector(pos, back).subtract(corner).add(0, 0.8, 0);
        Vec3 p = target;
        if (tl.phase() == JetForgeBlockEntity.PRIME) {
            p = bottom.lerp(target, JetForgeTimeline.ease(tl.prime() * 1.25F));
        } else if (tl.phase() == JetForgeBlockEntity.COOL) {
            p = target.lerp(bottom, JetForgeTimeline.ease((tl.cool() - 0.62F) / 0.38F));
        }
        float bob = 0.06F * Mth.sin(f.time() * 2.1F) * (1 - tl.heat() * 0.6F);
        float shake = tl.phase() == JetForgeBlockEntity.IRRADIATE ? 0.035F * tl.shatter() * Mth.sin(f.time() * 61F) : 0;
        p = p.add(0, bob + shake, 0).add(offset);
        float squeeze = tl.phase() == JetForgeBlockEntity.IRRADIATE ? 1 - 0.18F * JetForgeTimeline.ease((tl.irr() - 0.85F) / 0.15F) : 1;
        float pop = tl.finish() >= 0 ? 1 + 0.25F * Math.max(0, 1 - tl.finish() / 6F) : 1;
        float scale = (be.batchSize() == 1 ? 1.35F : 0.8F) * squeeze * pop;
        // カメラの方へ向き、ゆっくり回る（熱いほど速く）
        Vec3 toCam = f.cam().subtract(p);
        float yaw = (float) Math.atan2(toCam.x, toCam.z) + Mth.sin(f.time() * (0.6F + tl.heat())) * 0.5F;
        pose.pushPose();
        pose.translate(p.x, p.y, p.z);
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.ZP.rotation(Mth.sin(f.time() * 0.8F) * 0.15F));
        pose.scale(scale, scale, scale);
        Minecraft mc = Minecraft.getInstance();
        float h = tl.heat();
        // 赤熱の絵がある素材は、絵そのものを熱の段階の絵に替える（段階の間は次の段階の絵を重ねて少しずつ移る）
        boolean textured = renderHeated(mc, stack, h, pose, buffers, be);
        if (!textured) {
            MultiBufferSource solid = type -> buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    solid, be.getLevel(), 0);
        }
        // 熱の光: 同じ形を加算で重ねる（素材の絵の明るい所ほど光る）
        if (h > 0.02F) {
            int hc = heatColor(h);
            float k = Math.min(1, 0.4F + h) * (textured ? 0.45F : 1);
            MultiBufferSource hot = type -> new Tinted(buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS)),
                    (hc >> 16 & 255) * k / 255F, (hc >> 8 & 255) * k / 255F, (hc & 255) * k / 255F, 1);
            // 暗い素材でも赤熱して見えるよう、少しずつ大きくして重ねる（加算なので明るさが積み上がり、縁がにじむ。絵ごと焼いた素材は1回）
            for (int layer = 0; layer < (textured ? 1 : 3); layer++) {
                float sc = 1.02F + 0.035F * layer;
                pose.pushPose();
                pose.scale(sc, sc, 1.2F + 0.15F * layer);
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                        hot, be.getLevel(), 0);
                pose.popPose();
            }
        }
        // 亀裂（素材の面の上に、中心から外へ伸びる光の筋）
        if (tl.crack() > 0 && !f.far() && tl.phase() <= JetForgeBlockEntity.FINISH) {
            PoseStack.Pose last = pose.last();
            Frame local = new Frame(last, Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 4), f.time(), f.dist(), false);
            int n = Mth.ceil(tl.crack() * 7);
            RandomSource rnd = RandomSource.create(be.getBlockPos().asLong() * 13L);
            int crackColor = heatColor(Math.max(1F, h + 0.2F));
            for (int i = 0; i < 7; i++) {
                float ang = rnd.nextFloat() * Mth.TWO_PI;
                float len = 0.2F + rnd.nextFloat() * 0.25F;
                float[] jit = new float[5];
                for (int j = 0; j < 5; j++) {
                    jit[j] = (rnd.nextFloat() - 0.5F) * 0.5F;
                }
                if (i >= n) {
                    continue;
                }
                float grow = Mth.clamp(tl.crack() * 7 - i, 0, 1) * len;
                Vec3 prev = Vec3.ZERO;
                for (int j = 1; j <= 4; j++) {
                    float r = grow * j / 4;
                    float a2 = ang + jit[j];
                    Vec3 q = new Vec3(Mth.cos(a2) * r, Mth.sin(a2) * r, 0);
                    for (float z : new float[]{0.045F, -0.045F}) {
                        ribbonGlow(local, glow, prev.add(0, 0, z), q.add(0, 0, z), 0.022F, 0.012F, crackColor, crackColor, 1, 0.8F);
                    }
                    prev = q;
                }
            }
        }
        pose.popPose();
        // 熱のまわりの光と、飛び散る破片
        if (h > 0.05F) {
            // 熱のまわりのにじむ光（赤熱から白熱へ）。ゆらぐ熱気
            float shimmer = 0.9F + 0.1F * Mth.sin(f.time() * 11F);
            // 素材の後ろ（カメラから見て奥）に置く後光。素材と同じ面に置くと、カメラを向いた素材の絵を斜めに切って透けて見える
            billboard(f, glow, behind(f, p, 0.9F), (1.1F + 1.4F * Math.min(1, h)) * shimmer, heatColor(h), (int) (170 * Math.min(1, 0.3F + h)));
            billboard(f, glow, behind(f, p, 0.6F), 0.6F + 0.5F * Math.min(1, h), heatColor(Math.min(1.2F, h + 0.25F)),
                    (int) (150 * Math.min(1, 0.3F + h)));
        }
        if (!f.far()) {
            shards(f, tl, glow, target, be.getBlockPos().asLong());
        }
    }

    /** 飛び散る破片（照射の後半と、仕上げの瞬間）。 */
    private void shards(Frame f, JetForgeTimeline tl, VertexConsumer glow, Vec3 target, long seed) {
        float rate = tl.shatter();
        boolean burst = tl.finish() >= 0 && tl.finish() < 20;
        if (rate <= 0.01F && !burst) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        Vec3[] b = basis(f.axis());
        int n = burst ? 40 : 22;
        float life = 1.4F;
        for (int i = 0; i < n; i++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            float out = 0.3F + rnd.nextFloat() * 0.7F;
            float fwd = (rnd.nextFloat() - 0.25F) * 0.9F;
            float phase = rnd.nextFloat();
            float spin = rnd.nextFloat() * 6;
            Vec3 dir = b[0].scale(Mth.cos(ang) * out).add(b[1].scale(Mth.sin(ang) * out)).add(f.axis().scale(fwd)).normalize();
            float age;
            float speed;
            if (burst) {
                age = tl.finish() / 20F;
                speed = 9 + rnd.nextFloat() * 6;
            } else {
                if (rnd.nextFloat() > rate) {
                    continue;
                }
                age = ((f.time() / life) + phase) % 1F;
                speed = 2.5F + rnd.nextFloat() * 2.5F;
            }
            float d = 0.35F + speed * age * (1 - 0.35F * age);
            Vec3 p = target.add(dir.scale(d));
            float size = (0.05F + 0.07F * rnd.nextFloat()) * (1 - age);
            int c = heatColor(1.1F - age * 0.9F);
            glowBox(f, glow, p, size * (0.8F + 0.2F * Mth.sin(f.time() * spin)), c, (int) (255 * (1 - age)));
        }
    }

    // ------------------------------------------------------------------ 仕上げ

    private void drawFinish(Frame f, JetForgeTimeline tl, VertexConsumer glow, Vec3 targetW) {
        if (tl.finish() < 0) {
            return;
        }
        float t = tl.finish();
        Vec3 target = f.origin().add(f.axis().scale(TARGET_S));
        // 閃光の球（すぐに膨らんで消える）
        float k = Math.min(1, t / 10F);
        billboard(f, glow, target, 2 + 14 * k, WHITE, (int) (255 * (1 - k) * (1 - k)));
        billboard(f, glow, target, 1 + 5 * k, 0xCFE8FF, (int) (255 * (1 - k)));
        // 衝撃波: 軸に直交する輪と、水平な輪が広がる
        float w = Math.min(1, t / 26F);
        float r = 0.8F + 17 * JetForgeTimeline.ease(w);
        int alpha = (int) (230 * (1 - w));
        if (alpha > 2) {
            disc(f, glow, target, f.axis(), r * 0.86F, r, 64, 0xBFE2FF, alpha, (int) (alpha * 0.3F), 0);
            disc(f, glow, target, f.up(), r * 0.9F, r, 64, 0xFFE6C0, (int) (alpha * 0.8F), (int) (alpha * 0.2F), 0);
            // 歪みの輪（重力レンズ）。空気が押しのけられるように景色が波打つ
            GravitationalLensing.addDistortion(targetW, 0.4F + 0.9F * w, 0.7F * (1 - w), 1.5F + r);
        }
    }

    // ------------------------------------------------------------------ 描画の部品

    private static void col(VertexConsumer vc, Frame f, Vec3 p, int rgb, float a) {
        vc.addVertex(f.pose(), (float) p.x, (float) p.y, (float) p.z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Mth.clamp((int) (255 * a), 0, 255));
    }

    private static void uv(VertexConsumer vc, Frame f, Vec3 p, int rgb, float a, float u, float v) {
        vc.addVertex(f.pose(), (float) p.x, (float) p.y, (float) p.z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Mth.clamp((int) (255 * a), 0, 255))
                .setUv(u, v);
    }

    /** 軸 n に直交する2本の単位ベクトル。 */
    private static Vec3[] basis(Vec3 n) {
        Vec3 a = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 e1 = n.cross(a).normalize();
        return new Vec3[]{e1, n.cross(e1).normalize()};
    }

    /** 面 n の上の輪（内側の半径 r0 から外側の r1。色の濃さは内側 a0・外側 a1、中ほどの a2 は使わない）。 */
    private static void disc(Frame f, VertexConsumer vc, Vec3 c, Vec3 n, float r0, float r1, int segs, int rgb, int a0, int a1, int unused) {
        Vec3[] b = basis(n);
        for (int i = 0; i < segs; i++) {
            float t0 = i * Mth.TWO_PI / segs;
            float t1 = (i + 1) * Mth.TWO_PI / segs;
            Vec3 d0 = b[0].scale(Mth.cos(t0)).add(b[1].scale(Mth.sin(t0)));
            Vec3 d1 = b[0].scale(Mth.cos(t1)).add(b[1].scale(Mth.sin(t1)));
            col(vc, f, c.add(d0.scale(r0)), rgb, a0 / 255F);
            col(vc, f, c.add(d0.scale(r1)), rgb, a1 / 255F);
            col(vc, f, c.add(d1.scale(r1)), rgb, a1 / 255F);
            col(vc, f, c.add(d1.scale(r0)), rgb, a0 / 255F);
        }
    }

    /** 輪を巡る光（幅の真ん中がいちばん明るい。phase で模様が回る。bands は明るい所の数）。 */
    private static void ringSegments(Frame f, VertexConsumer vc, Vec3 c, Vec3 n, float r0, float r1, int segs, int rgb, float a,
                                     float phase, int bands) {
        Vec3[] b = basis(n);
        float rm = (r0 + r1) / 2;
        for (int i = 0; i < segs; i++) {
            float t0 = i * Mth.TWO_PI / segs;
            float t1 = (i + 1) * Mth.TWO_PI / segs;
            float b0 = 0.35F + 0.65F * Math.max(0, Mth.sin(t0 * bands - phase));
            float b1 = 0.35F + 0.65F * Math.max(0, Mth.sin(t1 * bands - phase));
            Vec3 d0 = b[0].scale(Mth.cos(t0)).add(b[1].scale(Mth.sin(t0)));
            Vec3 d1 = b[0].scale(Mth.cos(t1)).add(b[1].scale(Mth.sin(t1)));
            for (float[] band : new float[][]{{r0, rm}, {r1, rm}}) {
                col(vc, f, c.add(d0.scale(band[0])), rgb, 0);
                col(vc, f, c.add(d0.scale(band[1])), rgb, a * b0);
                col(vc, f, c.add(d1.scale(band[1])), rgb, a * b1);
                col(vc, f, c.add(d1.scale(band[0])), rgb, 0);
            }
        }
    }

    /** p をカメラから見て奥へ d だけずらした位置。 */
    private static Vec3 behind(Frame f, Vec3 p, float d) {
        Vec3 v = p.subtract(f.cam());
        double l = v.length();
        return l < 1e-4 ? p : p.add(v.scale(d / l));
    }

    /** 立体の小さな光の球（加算。深さで素材に正しく隠れる）。 */
    private static void glowBall(Frame f, VertexConsumer vc, Vec3 c, float r, int rgb, float a) {
        if (a <= 0.01F) {
            return;
        }
        int lat = 6;
        int lon = 10;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon;
                float p1 = Mth.TWO_PI * (j + 1) / lon;
                float[][] q = {{t0, p0}, {t1, p0}, {t1, p1}, {t0, p1}};
                for (float[] k : q) {
                    Vec3 d = new Vec3(Mth.cos(k[0]) * Mth.cos(k[1]), Mth.sin(k[0]), Mth.cos(k[0]) * Mth.sin(k[1]));
                    col(vc, f, c.add(d.scale(r)), rgb, a * 0.5F);
                }
            }
        }
    }

    /** カメラの方を向いた丸い光（中心が明るく、縁で消える）。 */
    private static void billboard(Frame f, VertexConsumer vc, Vec3 c, float size, int rgb, int alpha) {
        if (alpha <= 2) {
            return;
        }
        Vec3 view = c.subtract(f.cam()).normalize();
        disc(f, vc, c, view, 0, size, 16, rgb, alpha, 0, 0);
    }

    /** 光る小さな箱。 */
    private static void glowBox(Frame f, VertexConsumer vc, Vec3 c, float h, int rgb, int alpha) {
        if (alpha <= 2) {
            return;
        }
        float a = alpha / 255F;
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = c.add((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] q : faces) {
            for (int idx : q) {
                col(vc, f, corners[idx], rgb, a);
            }
        }
    }

    /**
     * 頂点を書くたびに描き込み先を引き直す受け口。描き方が同じあいだは同じ描き込み先が返り、途中で別の描き方（素材のアイテム・
     * もう1つの光）が描かれたあとは、新しい描き込み先に続けて書く。四角形の4点は続けて書くので、途中で切れない。
     */
    private static final class Lazy implements VertexConsumer {
        private final MultiBufferSource buffers;
        private final RenderType type;
        private VertexConsumer current;

        Lazy(MultiBufferSource buffers, RenderType type) {
            this.buffers = buffers;
            this.type = type;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            current = buffers.getBuffer(type);
            current.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            current.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current.setNormal(x, y, z);
            return this;
        }
    }

    /** 赤熱の絵を持つ素材と、その段階の数（tools/jet_forge_assets.py の HEATED_ITEMS・HEAT_STAGES）。 */
    static final java.util.List<String> HEATED_ITEMS = java.util.List.of("collapsar_billet", "collapsar_ingot");
    static final int HEAT_STAGES = 4;

    /** 赤熱の絵のモデル（ModelEvent.RegisterAdditional で読む）。 */
    static net.minecraft.client.resources.model.ModelResourceLocation heatModel(String id, int stage) {
        return net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                io.github.genichimaruo.singulo.Singulo.id("item/" + id + "_heat_" + stage));
    }

    static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (String id : HEATED_ITEMS) {
            for (int s = 1; s <= HEAT_STAGES; s++) {
                event.register(heatModel(id, s));
            }
        }
    }

    /**
     * 熱 h（0 で元の絵、1 で白熱）に合わせて、素材を赤熱の絵で描く。段階 i の絵を描き、その上に段階 i+1 の絵を
     * 半透明で重ねて移っていく。赤熱の絵がない素材なら false（呼び出し側で元の絵を描く）。
     */
    private static boolean renderHeated(Minecraft mc, ItemStack stack, float h, PoseStack pose, MultiBufferSource buffers,
                                        JetForgeBlockEntity be) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!HEATED_ITEMS.contains(id) || !"singulo".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        float s = Mth.clamp(h, 0, 1) * HEAT_STAGES;
        int lo = Math.min(HEAT_STAGES, (int) Math.floor(s));
        float frac = s - lo;
        var models = mc.getModelManager();
        var missing = models.getMissingModel();
        var base = lo == 0 ? null : models.getModel(heatModel(id, lo));
        if (base == missing) {
            return false;
        }
        // 素材は、焼けた絵もそうでない絵も不透明の切り抜きで描く（半透明の描き方では、重ねた光や膜と混ざって透けて見えることがある）
        MultiBufferSource solid = type -> buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        if (base == null) {
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    solid, be.getLevel(), 0);
        } else {
            mc.getItemRenderer().render(stack, ItemDisplayContext.FIXED, false, pose, solid, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, base);
        }
        if (lo < HEAT_STAGES && frac > 0.01F) {
            var next = models.getModel(heatModel(id, lo + 1));
            if (next != missing) {
                MultiBufferSource fade = type -> new Tinted(buffers.getBuffer(RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)),
                        1, 1, 1, frac);
                pose.pushPose();
                pose.scale(1.002F, 1.002F, 1.02F);
                mc.getItemRenderer().render(stack, ItemDisplayContext.FIXED, false, pose, fade, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, next);
                pose.popPose();
            }
        }
        return true;
    }

    /** 色（と透明度）を掛けて渡す頂点の受け口（熱の光で素材の絵を加算で重ねる・次の段階の絵を半透明で重ねるため）。 */
    private static final class Tinted implements VertexConsumer {
        private final VertexConsumer inner;
        private final float r, g, b, a;

        Tinted(VertexConsumer inner, float r, float g, float b, float a) {
            this.inner = inner;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            inner.setColor((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
