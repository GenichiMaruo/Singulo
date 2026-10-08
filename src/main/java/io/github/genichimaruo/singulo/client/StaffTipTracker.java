package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 一人称で手に持ったグラビトン・マニピュレーターの、杖の先の球が画面のどこに描かれたかを覚える。
 * 手のアイテムは歩くと揺れ、振ると動くので、描くときの変換（手の揺れ・振り・持ち方をすべて含む）から球の中心を求める。
 * 手はワールドのあとに描かれるので、重力レンズは1フレーム前の位置を使う。
 */
public final class StaffTipTracker {
    /** モデルの中での球の中心と半径（1 = 1ブロック。モデルは中心が原点になるよう 0.5 ずらして描かれる）。 */
    private static final float TIP_Y = 13F / 16F - 0.5F;
    private static final float TIP_RADIUS = 3F / 16F;

    /** 腕ごと（0 = 右腕, 1 = 左腕）に、最後に描かれた位置（画面 -1〜1）と大きさ（縦の UV での半径）、そのフレーム番号。 */
    private static final float[] NDC_X = new float[2];
    private static final float[] NDC_Y = new float[2];
    private static final float[] RADIUS_UV = new float[2];
    private static final long[] FRAME = {-10, -10};
    private static long currentFrame;

    private StaffTipTracker() {}

    /** モデルの読み込みのあと、杖のモデルを包んで、描く位置を覚えられるようにする。 */
    static void wrapModel(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = ModelResourceLocation.inventory(Singulo.id("graviton_manipulator"));
        BakedModel original = event.getModels().get(key);
        if (original != null) {
            event.getModels().put(key, new Tracking(original));
        }
    }

    /** 重力レンズを掛けるたびに数える（何フレーム前の位置かを知るため）。 */
    static void nextFrame() {
        currentFrame++;
    }

    /**
     * 直前のフレームで、その腕に一人称の杖が描かれていれば、その画面位置と大きさ。なければ null
     * （持ち替えた直後などでまだ描かれていなければ、歪みも出さない）。
     */
    static float[] recent(boolean rightArm) {
        int i = rightArm ? 0 : 1;
        return currentFrame - FRAME[i] <= 2 ? new float[]{NDC_X[i], NDC_Y[i], RADIUS_UV[i]} : null;
    }

    private static void record(PoseStack pose, boolean leftArm) {
        // 手は「カメラの向きの逆」から始まる PoseStack と、カメラの向きを持つ ModelView 行列の両方を通して描かれる
        Matrix4f m = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Vector4f c = m.transform(new Vector4f(0, TIP_Y, 0, 1));
        Vector4f e = m.transform(new Vector4f(TIP_RADIUS, TIP_Y, 0, 1));
        float viewRadius = (float) Math.sqrt((e.x - c.x) * (e.x - c.x) + (e.y - c.y) * (e.y - c.y) + (e.z - c.z) * (e.z - c.z));
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Vector4f clip = proj.transform(new Vector4f(c));
        if (clip.w <= 0.01F) {
            return;
        }
        int i = leftArm ? 1 : 0;
        NDC_X[i] = clip.x / clip.w;
        NDC_Y[i] = clip.y / clip.w;
        RADIUS_UV[i] = proj.m11() * viewRadius / clip.w * 0.5F;
        FRAME[i] = currentFrame;
    }

    private static final class Tracking extends BakedModelWrapper<BakedModel> {
        Tracking(BakedModel original) {
            super(original);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            BakedModel result = super.applyTransform(context, pose, leftHand);
            if (context.firstPerson()) {
                record(pose, context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            }
            return result;
        }
    }
}
