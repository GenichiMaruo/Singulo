package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.android.KatanaAndroid;

/** The case and the hand use the same folded cloth and pickup point. Coordinates are torso-local model pixels. */
final class KatanaCareKit {
    private static final float WIDTH_SCALE = 1.25F, HEIGHT_SCALE = 1.20F;
    private static final float Y = -0.98F, HINGE_Y = -0.86F;
    static final float CLOTH_Y = Y + (HINGE_Y - 0.16F) * HEIGHT_SCALE;
    private static final float CLOTH_OFFSET_X = 1.66F, CLOTH_DEPTH = 1.36F;
    private static final int LACQUER = 0x352832, LID = 0x4C3741, LIP = 0x844052;
    private static final int LINING = 0x291A23, BRASS = 0xB3935E, DARK_BRASS = 0x705639;

    private KatanaCareKit() {}

    private static float mountX(boolean backpack) {
        return backpack ? 3.40F : 1.10F;
    }

    private static float mountZ(boolean backpack) {
        return backpack ? 5.50F : 1.52F;
    }

    static float clothX(boolean backpack) {
        return mountX(backpack) + CLOTH_OFFSET_X;
    }

    static float clothZ(boolean backpack) {
        return mountZ(backpack) + CLOTH_DEPTH;
    }

    static void render(AndroidModel model, PoseStack pose, VertexConsumer body, int light, KatanaAndroid android, float partialTick) {
        boolean backpack = android.hasBackpack();
        int state = android.state();
        float time = android.stateTime(partialTick);
        float interruptedAt = android.maintenanceExitTime(partialTick);
        boolean returningCloth = KatanaMotion.clothInHand(KatanaAndroid.STATE_MAINTENANCE, interruptedAt);
        pose.pushPose();
        model.toTorso(pose);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        pose.translate(mountX(backpack), Y, mountZ(backpack));

        pose.pushPose();
        pose.scale(WIDTH_SCALE, HEIGHT_SCALE, 1);
        FxDraw.box(pose, body, -2.04F, 0.69F, -0.10F, 2.04F, 0.99F, 1.38F, light, LACQUER);
        FxDraw.box(pose, body, -2.04F, HINGE_Y, -0.10F, 2.04F, 0.69F, 0.12F, light, LINING);
        FxDraw.box(pose, body, -2.04F, HINGE_Y, 0.12F, -1.82F, 0.69F, 1.38F, light, LACQUER);
        FxDraw.box(pose, body, 1.82F, HINGE_Y, 0.12F, 2.04F, 0.69F, 1.38F, light, LACQUER);
        FxDraw.box(pose, body, -1.82F, HINGE_Y, 1.15F, 1.82F, 0.69F, 1.38F, light, LACQUER);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 1.83F;
            FxDraw.box(pose, body, x - 0.23F, 0.65F, 0.90F, x + 0.23F, 1.04F, 1.43F, light, DARK_BRASS);
        }
        FxDraw.box(pose, body, -0.32F, -0.63F, 1.38F, 0.32F, 0.18F, 1.48F, light, DARK_BRASS);
        pose.popPose();

        pose.pushPose();
        pose.translate(0, HINGE_Y * HEIGHT_SCALE, 0);
        float open = state == KatanaAndroid.STATE_MAINTENANCE ? lidOpening(time) : 0;
        if (interruptedAt >= 0) {
            float progress = android.maintenanceExitProgress(partialTick);
            open = lidOpening(interruptedAt) * (1 - progress);
            if (returningCloth) open += (1 - open) * KatanaMotion.ease(progress, 0, 0.65F) * (1 - KatanaMotion.ease(progress, 0.8F, 1));
        }
        pose.mulPose(Axis.XP.rotationDegrees(52 * open));
        pose.scale(WIDTH_SCALE, HEIGHT_SCALE, 1);
        FxDraw.box(pose, body, -2.12F, -0.40F, -0.14F, 2.12F, -0.08F, 1.40F, light, LID);
        FxDraw.box(pose, body, -2.12F, -0.08F, -0.14F, 2.12F, 0, 1.40F, light, LIP);
        FxDraw.box(pose, body, -0.20F, -0.18F, 1.39F, 0.20F, 0.62F, 1.54F, light, BRASS);
        pose.popPose();

        if (!KatanaMotion.clothInHand(android, partialTick)) {
            pose.translate(CLOTH_OFFSET_X, CLOTH_Y - Y, CLOTH_DEPTH);
            cloth(pose, body, light);
        }
        pose.popPose();
    }

    /** A folded ivory cloth with one narrow woven red hem. The origin is the fold held by the left hand. */
    static void cloth(PoseStack pose, VertexConsumer body, int light) {
        FxDraw.box(pose, body, -0.62F, -0.11F, -0.35F, 0.62F, 0.10F, 0.22F, light, 0xF6E9D5);
        FxDraw.box(pose, body, -0.57F, 0.10F, 0.06F, 0.62F, 0.62F, 0.22F, light, 0xEADBC7);
        FxDraw.box(pose, body, -0.57F, 0.62F, 0.06F, 0.62F, 0.74F, 0.22F, light, 0x98535F);
        FxDraw.box(pose, body, -0.57F, 0.74F, 0.06F, 0.62F, 0.84F, 0.22F, light, 0xEADBC7);
    }

    private static float lidOpening(float time) {
        return KatanaMotion.ease(time, 4, 11) * (1 - KatanaMotion.ease(time, 14, 20))
                + KatanaMotion.ease(time, 78, 85) * (1 - KatanaMotion.ease(time, 87, 94));
    }
}
