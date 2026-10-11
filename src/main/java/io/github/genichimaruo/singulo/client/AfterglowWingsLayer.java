package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.item.AfterglowWingsItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * 残光の翼の見た目（プレイヤーと防具立てに着せたとき）。羽根を重ねた白い天使の翼で、先だけ淡い水色の残光が灯る。
 * 最後の記録者の翼（象牙の細長い羽根に金の先）とはちがい、ふっくらと丸い白い羽根。
 * <p>
 * 鳥の翼のつくりにならう。翼は上腕（肩→肘）・前腕（肘→手首）・手（手首→先）の3つの骨でできていて、
 * 畳むときは「上腕は後ろへ・前腕は前へ・手はまた後ろへ」と Z 字に折れる。人の背中では体の後ろ（鳥の尾の側）が下にあたるので、
 * 畳むほど肘は下がり、手首は上へ持ち上がって翼の肩（いちばん高い角）になり、手から先の初列風切が下へ垂れる。
 * 風切羽は骨といっしょには回らず、畳むほどそろって後ろ（下）を向いて重なっていく。
 * <ul>
 *   <li>地上: 軽く開く（畳み具合 {@link #FOLD_GROUND}）。手首が肩より上に立ち、初列風切が扇のように少し開いて垂れる</li>
 *   <li>宙（滑空していない）: もう少し開く。滑空中: まっすぐ伸ばして大きく広げる</li>
 *   <li>滑空中にスニークすると（羽ばたきの加速）、大きな鳥のようにゆっくり大きく打つ</li>
 * </ul>
 * 重なりは鳥と同じく、背中側から雨覆 → 三列風切 → 次列風切 → 初列風切の順。羽根は1枚ずつ瓦のように少し傾き、
 * 表（背中側）は白く、裏は少し陰らせる。
 */
public class AfterglowWingsLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    static final int WHITE = 0xFFFFFF;
    static final int SOFT = 0xF4F8FF;
    static final int GLOW = 0xC6F2FF;
    /** 骨の長さ（上腕・前腕・手）。 */
    static final float ARM = 0.5F, FORE = 0.55F, HAND = 0.45F;
    /** 翼全体の大きさの倍率。 */
    static final float WING_SCALE = 0.95F;
    /** 畳み具合（0: 広げきる 〜 1: 畳みきる）。地上・宙。 */
    static final float FOLD_GROUND = 0.55F, FOLD_AIR = 0.3F;
    /** 骨の向き（翼の面の中で、外が 0°・上が 90°）: 広げたとき・畳みきったとき。上腕・前腕・手。 */
    static final float[] BONE_OPEN = {12, 6, -2}, BONE_FOLDED = {-40, 105, -100};
    /** 強い羽ばたきの速さ（ラジアン/tick）と振れ幅（度、片側）。 */
    static final float FLAP_SPEED = 0.14F, FLAP_SWING = 44;
    /** 広げ方・強い羽ばたきの今の値（なめらかに変えるため、体ごとに覚えておく）。 */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();

    public AfterglowWingsLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float netHeadYaw, float headPitch) {
        if (!AfterglowWingsItem.wearing(entity) || entity.isInvisible()) {
            return;
        }
        boolean gliding = entity.isFallFlying();
        boolean flapping = gliding && entity.isShiftKeyDown();
        float[] st = STATE.computeIfAbsent(entity, k -> new float[] {FOLD_GROUND, 0});
        st[0] = Mth.lerp(0.1F, st[0], gliding ? 0 : entity.onGround() ? FOLD_GROUND : FOLD_AIR);
        st[1] = Mth.lerp(0.15F, st[1], flapping ? 1 : 0);                             // 強い羽ばたき
        float flap = st[1];
        // 強い羽ばたきは大きな鳥のように、ゆっくり大きく（1回およそ2.2秒、上下に約90°）。打ち下ろしは速く、打ち上げはゆっくり。
        // 打ち上げでは手首を曲げて翼を少し畳み、先は遅れてついてくる
        float th = age * FLAP_SPEED;
        float stroke = Mth.sin(th + 0.4F * Mth.sin(th));
        float rising = Mth.cos(th + 0.4F * Mth.sin(th));
        float beat = Mth.sin(age * 0.08F) * 3 * (1 - flap) + flap * (8 + FLAP_SWING * stroke);
        float fold = st[0] + flap * 0.3F * Math.max(0, rising);
        float handLag = -flap * 18 * rising;

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0, 0.16F, 0.16F);                       // 肩甲骨のあたり（モデルは下が +Y、背中が +Z）
        pose.scale(1, -1, 1);                                  // 上を +Y にして描く
        // 半透明の描き方だと、後ろにある水やガラスと混ぜて描かれて透けてしまう。羽根はほぼ不透明なので、不透明（切り抜き）で描く
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FxDraw.WHITE));
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.1F, 0, 0);
            pose.scale(side, 1, 1);                           // 左の翼は右の翼を鏡に映したもの
            pose.mulPose(Axis.YP.rotationDegrees(-(12 + 18 * fold)));      // 翼の面を少し後ろへ（畳むほど背中に沿う）
            pose.mulPose(Axis.ZP.rotationDegrees(beat));
            pose.scale(WING_SCALE, WING_SCALE, WING_SCALE);
            drawWing(pose, vc, age, fold, handLag);
            pose.popPose();
        }
        pose.popPose();
    }

    /** 右の翼を、肩を原点に描く（翼の面は X が外・Y が上、+Z が背中側）。骨の関節の位置を出してから、羽根を並べる。 */
    private static void drawWing(PoseStack pose, VertexConsumer vc, float age, float fold, float handLag) {
        float[] a = new float[3];
        for (int b = 0; b < 3; b++) {
            a[b] = Mth.lerp(fold, BONE_OPEN[b], BONE_FOLDED[b]);
        }
        a[2] += handLag;
        float[] len = {ARM, FORE, HAND};
        float[][] p = new float[4][2];
        for (int b = 0; b < 3; b++) {
            p[b + 1][0] = p[b][0] + len[b] * Mth.cos(a[b] * Mth.DEG_TO_RAD);
            p[b + 1][1] = p[b][1] + len[b] * Mth.sin(a[b] * Mth.DEG_TO_RAD);
        }
        int[] k = {0};
        // 初列風切（手）: 広げると先へ向かって開き、畳むと下へ垂れて少しだけ扇に開く。いちばん奥（体の側）
        for (int i = 0; i < 9; i++) {
            float u = i / 8F;
            float dir = Mth.lerp(fold, a[2] + Mth.lerp(u, -70, -6), -95 + 22 * u);
            feather(pose, vc, at(p, 2, u), -0.1F - 0.004F * i, dir, 0.62F + 0.36F * u, 0.18F, k, age);
        }
        // 次列風切（前腕）
        for (int i = 0; i < 8; i++) {
            float u = i / 7F;
            float dir = Mth.lerp(fold, a[1] - 92 + 16 * u, -98 + 8 * u);
            feather(pose, vc, at(p, 1, u), -0.07F, dir, 0.62F - 0.04F * u, 0.17F, k, age);
        }
        // 三列風切（上腕）
        for (int i = 0; i < 4; i++) {
            float u = 0.15F + 0.25F * i;
            float dir = Mth.lerp(fold, a[0] - 98 + 4 * u, -100);
            feather(pose, vc, at(p, 0, u), -0.05F, dir, 0.5F + 0.12F * u, 0.17F, k, age);
        }
        // 雨覆（2列、上腕・前腕・手の付け根）
        for (int row = 0; row < 2; row++) {
            for (int b = 0; b < 3; b++) {
                int n = b == 2 ? 3 : 4 + b;
                for (int i = 0; i < n; i++) {
                    float u = (i + 0.5F) / n * (b == 2 ? 0.7F : 1);
                    float dir = Mth.lerp(fold, a[b] - 94 + 8 * u, -96);
                    float[] q = at(p, b, u);
                    q[1] -= 0.03F * row;
                    feather(pose, vc, q, 0.025F * row, dir, row == 0 ? 0.34F : 0.22F, row == 0 ? 0.16F : 0.14F, k, age);
                }
            }
        }
        // 前縁（骨に沿った、ふっくらした白い縁）。いちばん手前
        float[] width = {0.13F, 0.11F, 0.09F};
        for (int b = 0; b < 3; b++) {
            pose.pushPose();
            pose.translate(p[b][0], p[b][1], 0.04F);
            pose.mulPose(Axis.ZP.rotationDegrees(a[b]));
            pose.translate(-0.03F, 0.02F, 0);
            shape(pose, vc, len[b] + 0.06F, width[b], 0, WHITE, 250, WHITE, 250, SOFT, 245);
            pose.popPose();
        }
    }

    /** 骨 b の上の、付け根から u（0〜1）の位置。 */
    private static float[] at(float[][] p, int b, float u) {
        return new float[] {Mth.lerp(u, p[b][0], p[b + 1][0]), Mth.lerp(u, p[b][1], p[b + 1][1])};
    }

    /** 羽根1枚: 白くふっくらした羽根に、先だけ淡い水色の残光。中に細い羽軸。瓦のように少し傾けて重ねる。 */
    private static void feather(PoseStack pose, VertexConsumer vc, float[] at, float z, float angle, float len, float width,
                                int[] k, float age) {
        int i = k[0]++;
        float glow = 0.75F + 0.25F * Mth.sin(age * 0.08F + i * 0.7F);
        pose.pushPose();
        pose.translate(at[0], at[1], z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.mulPose(Axis.XP.rotationDegrees(i % 2 == 0 ? 12 : 6));
        shape(pose, vc, len, width, 0, WHITE, 250, SOFT, 245, GLOW, (int) (230 * glow));
        shape(pose, vc, len * 0.85F, 0.018F, 0.004F, 0xE2EAF6, 220, 0xE2EAF6, 180, GLOW, 120);
        pose.popPose();
    }

    /** 羽根の形（+X 方向、両面）: 付け根は細く → 30〜80% で幅いっぱい → 丸くすぼまる先。裏は少し陰り、少し後ろにずらす。 */
    private static void shape(PoseStack pose, VertexConsumer vc, float len, float width, float z,
                              int rootRgb, int rootA, int midRgb, int midA, int tipRgb, int tipA) {
        PoseStack.Pose last = pose.last();
        float h = width / 2;
        float[] xs = {0, len * 0.3F, len * 0.8F, len * 0.94F, len};
        float[] hs = {h * 0.3F, h, h, h * 0.6F, h * 0.1F};
        int[] cs = {rootRgb, midRgb, midRgb, FxDraw.lerpRgb(midRgb, tipRgb, 0.7F), tipRgb};
        int[] as = {rootA, midA, midA, (midA + tipA) / 2, tipA};
        int L = FxDraw.FULL_BRIGHT;
        int[] under = new int[cs.length];
        for (int i = 0; i < cs.length; i++) {
            under[i] = FxDraw.lerpRgb(cs[i], 0xB4C0D6, 0.22F);     // 裏（胸側から見える面）は少し陰る
        }
        for (int face = 0; face < 2; face++) {
            float n = face == 0 ? 1 : -1;
            float zz = face == 0 ? z : z - 0.004F;
            int[] col = face == 0 ? cs : under;
            for (int i = 0; i < 4; i++) {
                if (face == 0) {
                    LastArchivistRenderer.vertex(vc, last, xs[i], -hs[i], zz, col[i], as[i], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i + 1], -hs[i + 1], zz, col[i + 1], as[i + 1], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i + 1], hs[i + 1], zz, col[i + 1], as[i + 1], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i], hs[i], zz, col[i], as[i], L, 0, 0, n);
                } else {
                    LastArchivistRenderer.vertex(vc, last, xs[i], hs[i], zz, col[i], as[i], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i + 1], hs[i + 1], zz, col[i + 1], as[i + 1], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i + 1], -hs[i + 1], zz, col[i + 1], as[i + 1], L, 0, 0, n);
                    LastArchivistRenderer.vertex(vc, last, xs[i], -hs[i], zz, col[i], as[i], L, 0, 0, n);
                }
            }
        }
    }
}
