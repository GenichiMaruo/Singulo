package io.github.genichimaruo.singulo.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.IArmPoseTransformer;

/**
 * 傘を差す腕の構え: 肘を曲げて手を胸の高さまで上げ、少し体の内側へ寄せる（ほかの人から見て、傘の柄を手で握って見えるように）。
 * HumanoidModel.ArmPose の拡張（Forge / NeoForge 1.20.1 の create API）。
 */
public final class UmbrellaArmPose {
    /** 腕を前へ上げる角度（ラジアン。負が前）と、体の内側へ寄せる角度。 */
    static final float RAISE = -0.95F, INWARD = 0.12F;

    public static final HumanoidModel.ArmPose UMBRELLA = HumanoidModel.ArmPose.create("SINGULO_UMBRELLA", false,
            (IArmPoseTransformer) (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = RAISE;
                part.yRot = arm == HumanoidArm.RIGHT ? -INWARD : INWARD;
                part.zRot = 0;
            });

    private UmbrellaArmPose() {}
}
