package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.KatanaAndroid;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two rigid arm segments guide each grip along the moving scabbard. ModelPart coordinates are in pixels. */
final class KatanaRig {
    private static final float PIXELS_PER_BLOCK = 16 / AndroidRenderer.SCALE;
    private final AndroidModel model;
    private final ModelPart hipMouth;
    private final ModelPart backMouth;

    KatanaRig(AndroidModel model, ModelPart hipMouth, ModelPart backMouth) {
        this.model = model;
        this.hipMouth = hipMouth;
        this.backMouth = backMouth;
    }

    ModelPart mouth(boolean crimson) {
        return crimson ? backMouth : hipMouth;
    }

    void update(KatanaAndroid android, float partialTick) {
        hipMouth.resetPose();
        backMouth.resetPose();
        if (android.hasBackpack()) {
            backMouth.z += 1.35F;
        }
        int state = android.state();
        float t = android.stateTime(partialTick);
        if (state == KatanaAndroid.STATE_MAINTENANCE) {
            maintenanceBlade(t);
            maintenanceCloth(android.hasBackpack(), t, 0);
            return;
        }
        float interrupted = android.maintenanceExitTime(partialTick);
        if (KatanaMotion.clothInHand(KatanaAndroid.STATE_MAINTENANCE, interrupted)) {
            float bladeTime = KatanaMotion.interruptedBladeTime(android, partialTick);
            if (bladeTime >= 0) maintenanceBlade(bladeTime);
            else if (state == KatanaAndroid.STATE_DRAW) guide(false, state, t, engage(t / 2));
            maintenanceCloth(android.hasBackpack(), interrupted, android.maintenanceExitProgress(partialTick));
            return;
        }
        float finisher = android.iaiVictoryTime(partialTick);
        if (finisher >= 0) {
            // 右の打刀で次の相手に応じても、左手は勝利の礼からそのまま紅刀を納める。
            model.iaiVictoryBlade(finisher);
            guide(true, KatanaAndroid.STATE_IAI_VICTORY, finisher, 1 - engage((finisher - KatanaAndroid.IAI_VICTORY_SEAT)
                    / (KatanaAndroid.IAI_VICTORY - KatanaAndroid.IAI_VICTORY_SEAT)));
            return;
        }
        float salute = android.victoryTime(partialTick);
        if (salute >= 0) {
            float contact = engage(salute / KatanaAndroid.VICTORY_GRIP)
                    * (1 - engage((salute - KatanaAndroid.VICTORY_SEAT) / (KatanaAndroid.VICTORY - KatanaAndroid.VICTORY_SEAT)));
            guide(true, KatanaAndroid.STATE_VICTORY, salute, contact);
            return;
        }
        switch (state) {
            case KatanaAndroid.STATE_DRAW -> guide(false, state, t, engage(t / 2));
            case KatanaAndroid.STATE_SHEATHE -> guide(false, state, t, 1 - engage((t - KatanaAndroid.SHEATHE_SEAT)
                    / (KatanaAndroid.SHEATHE - KatanaAndroid.SHEATHE_SEAT)));
            case KatanaAndroid.STATE_IAI_STANCE -> guide(true, state, t, engage(t / 6));
            case KatanaAndroid.STATE_IAI_FLASH -> guide(true, state, t, 1);
            case KatanaAndroid.STATE_IAI_ZANSHIN -> guide(true, state, t, 1 - engage((t - KatanaAndroid.IAI_SHEATHE_SEAT)
                    / (KatanaAndroid.IAI_ZANSHIN - KatanaAndroid.IAI_SHEATHE_SEAT)));
        }
    }

    /** 右手は鯉口を離れてから、刃の広い面を前に向けて布の下へ滑らせる。 */
    private void maintenanceBlade(float t) {
        if (t >= KatanaAndroid.MAINTENANCE_CLEAR && t <= KatanaAndroid.MAINTENANCE_GUIDE) {
            Vector3f grip = polishingGrip(t).sub(model.armR.x, model.armR.y, model.armR.z);
            fitArm(model.armR, model.forearmR, model.katanaGrip, grip, new Vector3f(-1, 0.6F, 0), polishingFrame(), 1);
        }
        float hold = KatanaMotion.ease(t, 8, KatanaAndroid.MAINTENANCE_DRAW)
                * (1 - KatanaMotion.ease(t, KatanaAndroid.MAINTENANCE_SEAT, KatanaAndroid.MAINTENANCE_STOW));
        guide(false, KatanaAndroid.STATE_MAINTENANCE, t, hold);
    }

    private static Vector3f polishingGrip(float t) {
        float s = KatanaMotion.wipePosition(t);
        return new Vector3f(-4.6F - 4.9F * s, -3.3F + 0.65F * s, -4.95F);
    }

    private static Quaternionf polishingFrame() {
        return new Quaternionf().rotationZ(-1.73F).rotateX(-0.05F).rotateY(1.26F);
    }

    private void maintenanceCloth(boolean backpack, float t, float exit) {
        Vector3f box = new Vector3f(KatanaCareKit.clothX(backpack), KatanaCareKit.CLOTH_Y, KatanaCareKit.clothZ(backpack));
        float unfold = KatanaMotion.clothUnfold(t);
        // 箱から前へは肘を外に開き、脇を回る。布が胸や背負い物を通り抜けないための中継点。
        Vector3f hand = new Vector3f(box).lerp(new Vector3f(7.1F, -3.0F, -2.3F), unfold);
        hand.x += 1.5F * Mth.sin(Mth.PI * unfold);
        float polish = KatanaMotion.handBlend(KatanaAndroid.STATE_MAINTENANCE, t, false, false);
        float s = KatanaMotion.wipePosition(t);
        float lift = Mth.sin(Mth.PI * KatanaMotion.ease(t, 47, 53));
        // +X is the near face of the steel in this grip. The small lift resets the cloth between strokes.
        Vector3f onBlade = new Vector3f(0.3F + 1.2F * lift, (AndroidModel.KATANA_GRIP_TO_GUARD + KatanaMotion.stroke(false) * s) * PIXELS_PER_BLOCK,
                -KatanaMotion.curveOffset(s) * PIXELS_PER_BLOCK).rotate(polishingFrame()).add(polishingGrip(t));
        hand.lerp(onBlade, polish);
        float returning = KatanaMotion.ease(exit, 0, 0.8F);
        hand.lerp(box, returning);
        hand.x += 3.6F * Mth.sin(Mth.PI * returning);
        Quaternionf fold = new Quaternionf().rotationZ(-0.20F * polish * (1 - returning));
        float hold = KatanaMotion.ease(t, 0, KatanaAndroid.MAINTENANCE_CLOTH)
                * (1 - KatanaMotion.ease(t, KatanaAndroid.MAINTENANCE_STOW, KatanaAndroid.MAINTENANCE))
                * (1 - KatanaMotion.ease(exit, 0.8F, 1));
        hand.sub(model.armL.x, model.armL.y, model.armL.z);
        fitArm(model.armL, model.forearmL, model.katanaGripL, hand, new Vector3f(1, 0.35F, 0), fold, hold);
    }

    private void guide(boolean crimson, int state, float t, float contact) {
        float hand = KatanaMotion.handBlend(state, t, crimson, false);
        float weight = contact * (1 - hand);
        if (weight <= 0) {
            return;
        }
        float exposed = KatanaMotion.exposure(state, t, crimson, false);
        float draw = exposed * (1 - hand);
        ModelPart mouth = mouth(crimson);
        if (crimson) {
            // The back harness swivels out over the shoulder as the scabbard slides away from the hand.
            mouth.xRot += 24 * Mth.DEG_TO_RAD * draw;
            if (state == KatanaAndroid.STATE_VICTORY) {
                mouth.zRot += 34 * Mth.DEG_TO_RAD * draw;
            }
        } else {
            // The left hand lifts the mouth from the belt and levels the sheath as it draws it back.
            mouth.setPos(mouth.x - 0.4F * weight, mouth.y - 1.2F * weight, mouth.z - 1.1F * weight);
            mouth.xRot += (2 * weight + 20 * draw) * Mth.DEG_TO_RAD;
            mouth.yRot += (4 * weight + 9 * draw) * Mth.DEG_TO_RAD;
        }
        Quaternionf frame = rotation(mouth);
        // The salute takes the hilt farther past the shoulder. A full-speed draw pulls the scabbard farther back.
        float retreat = (state == KatanaAndroid.STATE_VICTORY ? 0.57F : crimson ? 0.74F : 0.50F) * PIXELS_PER_BLOCK;
        Vector3f travel = new Vector3f(0, retreat * draw, 0).rotate(frame);
        mouth.setPos(mouth.x + travel.x, mouth.y + travel.y, mouth.z + travel.z);

        float stroke = KatanaMotion.stroke(crimson) * PIXELS_PER_BLOCK;
        float gripDepth = AndroidModel.KATANA_GRIP_TO_GUARD * PIXELS_PER_BLOCK;
        // At clearance the scabbard returns to its mount. The freed hand continues from its extended position.
        Vector3f wrist = new Vector3f(0, -gripDepth - stroke * exposed + retreat * exposed * hand,
                KatanaMotion.curveOffset(exposed) * PIXELS_PER_BLOCK).rotate(frame).add(mouth.x, mouth.y, mouth.z);
        if (!crimson) {
            // The waist mount belongs to the hips, whereas both shoulders belong to the moving torso.
            ModelPart torso = model.torso;
            Quaternionf toTorso = rotation(torso).conjugate();
            wrist.sub(torso.x, torso.y, torso.z).rotate(toTorso);
            if (state != KatanaAndroid.STATE_MAINTENANCE) {
                Vector3f supportingHand = new Vector3f(0, 0.025F * PIXELS_PER_BLOCK, 0).rotate(frame)
                        .add(mouth.x - torso.x, mouth.y - torso.y, mouth.z - torso.z).rotate(toTorso)
                        .sub(model.armL.x, model.armL.y, model.armL.z);
                fitArm(model.armL, model.forearmL, model.katanaGripL, supportingHand, new Vector3f(1, 0.2F, 0),
                        new Quaternionf(toTorso).mul(frame), weight);
            }
            frame.premul(toTorso);
        }
        ModelPart arm = crimson ? model.armL : model.armR;
        ModelPart forearm = crimson ? model.forearmL : model.forearmR;
        ModelPart grip = crimson ? model.katanaGripL : model.katanaGrip;
        wrist.sub(arm.x, arm.y, arm.z);
        // Choose the free side of the head for the raised elbow, and the front of the body for the cross-body reach.
        Vector3f pole = crimson ? new Vector3f(1, 0, -0.25F) : new Vector3f(-0.45F, 0.65F, -0.55F);
        fitArm(arm, forearm, grip, wrist, pole, frame, weight);
    }

    private static void fitArm(ModelPart arm, ModelPart forearm, ModelPart grip, Vector3f target, Vector3f pole,
                               Quaternionf bladeFrame, float weight) {
        float upper = forearm.y, lower = grip.y;
        Vector3f direction = new Vector3f(target).normalize();
        float distance = Mth.clamp(target.length(), Math.abs(upper - lower) + 0.001F, upper + lower - 0.001F);
        float along = (upper * upper - lower * lower + distance * distance) / (2 * distance);
        float bend = Mth.sqrt(Math.max(0, upper * upper - along * along));
        Vector3f elbow = pole.fma(-pole.dot(direction), direction).normalize().mul(bend).fma(along, direction);
        Vector3f lowerDir = direction.mul(distance).sub(elbow).normalize();
        Quaternionf shoulder = fromDown(elbow.normalize());
        Quaternionf fore = fromDown(lowerDir.rotate(new Quaternionf(shoulder).conjugate()));
        Quaternionf wrist = new Quaternionf(shoulder).mul(fore).conjugate().mul(bladeFrame);
        apply(arm, shoulder, weight);
        apply(forearm, fore, weight);
        apply(grip, wrist, weight);
    }

    private static Quaternionf fromDown(Vector3f direction) {
        return new Quaternionf().rotationTo(0, 1, 0, direction.x, direction.y, direction.z);
    }

    private static Quaternionf rotation(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    private static void apply(ModelPart part, Quaternionf rotation, float weight) {
        Quaternionf pose = weight == 1 ? rotation : rotation(part).slerp(rotation, weight);
        // JOML 1.10.5's quaternion ZYX extractor returns an incorrect X angle at large yaw.
        Matrix3f m = new Matrix3f().rotation(pose);
        part.setRotation((float) Math.atan2(m.m12, m.m22), (float) Math.atan2(-m.m02, Math.hypot(m.m00, m.m01)),
                (float) Math.atan2(m.m01, m.m00));
    }

    private static float engage(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
