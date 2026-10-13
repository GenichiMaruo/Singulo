package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BladeAndroid;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 遺構のアンドロイドの体（3体で共通。武器だけ型ごとに違う）。プレイヤーより少し小さい女の子の形。
 *
 * <p>頭は大きめ（幅8・高さ8）、髪は頭を包む殻（前髪・横・後ろ）と、背中まで伸びる後ろ髪、長さの違う左右のひと房（左が長い）、
 * 左上のサイドテール、アホ毛。右のこめかみには共通の目印のセンサーの羽。胴は細く、胸に陶磁の板、腰にスカート
 * （前後左右の4枚と、右だけ長い垂れ）。背中に小さなスラスター2つ。右腕は肩当てと太い籠手（光刃型は刃が付く）。
 *
 * <p>テクスチャは 128×64。配置は tools/gen_data.py の ANDROID_PARTS と同じ。
 */
public class AndroidModel extends HierarchicalModel<RuinAndroid> {
    public static final ModelLayerLocation BLADE_LAYER = new ModelLayerLocation(Singulo.id("blade_android"), "main");

    final ModelPart root;
    final ModelPart hips;
    final ModelPart torso;
    final ModelPart head;
    final ModelPart backHair;
    final ModelPart sideLockL;
    final ModelPart sideLockR;
    final ModelPart ponytail;
    final ModelPart ahoge;
    final ModelPart skirtFront;
    final ModelPart skirtBack;
    final ModelPart skirtLeft;
    final ModelPart skirtRight;
    final ModelPart skirtTail;
    final ModelPart armL;
    final ModelPart forearmL;
    final ModelPart armR;
    final ModelPart forearmR;
    final ModelPart legL;
    final ModelPart shinL;
    final ModelPart legR;
    final ModelPart shinR;
    /** 光刃型の刃（なければ null）。半透明で光るので、本体とは別に描く。 */
    final ModelPart blade;

    public AndroidModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.torso = hips.getChild("torso");
        this.head = torso.getChild("head");
        this.backHair = head.getChild("back_hair");
        this.sideLockL = head.getChild("side_lock_l");
        this.sideLockR = head.getChild("side_lock_r");
        this.ponytail = head.getChild("ponytail");
        this.ahoge = head.getChild("ahoge");
        this.skirtFront = hips.getChild("skirt_front");
        this.skirtBack = hips.getChild("skirt_back");
        this.skirtLeft = hips.getChild("skirt_left");
        this.skirtRight = hips.getChild("skirt_right");
        this.skirtTail = hips.getChild("skirt_tail");
        this.armL = torso.getChild("arm_l");
        this.forearmL = armL.getChild("forearm_l");
        this.armR = torso.getChild("arm_r");
        this.forearmR = armR.getChild("forearm_r");
        this.legL = root.getChild("leg_l");
        this.shinL = legL.getChild("shin_l");
        this.legR = root.getChild("leg_r");
        this.shinR = legR.getChild("shin_r");
        this.blade = forearmR.hasChild("blade") ? forearmR.getChild("blade") : null;
    }

    private static CubeListBuilder cube(int u, int v, float x, float y, float z, int w, int h, int d, CubeDeformation def) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, def);
    }

    private static CubeListBuilder cube(int u, int v, float x, float y, float z, int w, int h, int d) {
        return cube(u, v, x, y, z, w, h, d, CubeDeformation.NONE);
    }

    private static CubeDeformation def(float g) {
        return new CubeDeformation(g);
    }

    private static CubeDeformation def(float x, float y, float z) {
        return new CubeDeformation(x, y, z);
    }

    public static LayerDefinition createLayer(AndroidRenderer.Variant variant) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", cube(32, 16, -3.5F, -3, -2, 7, 3, 4), PartPose.offset(0, 12, 0));
        // スカート（前・後ろ・左右と、右だけ長い垂れ）
        hips.addOrReplaceChild("skirt_front", cube(54, 16, -4, 0, -0.5F, 8, 5, 1, def(0, 0, -0.2F)),
                PartPose.offsetAndRotation(0, -2, -2.0F, -0.22F, 0, 0));
        hips.addOrReplaceChild("skirt_back", cube(72, 16, -4, 0, -0.5F, 8, 6, 1, def(0, 0, -0.2F)),
                PartPose.offsetAndRotation(0, -2, 2.0F, 0.28F, 0, 0));
        hips.addOrReplaceChild("skirt_left", cube(90, 16, -0.5F, 0, -2.5F, 1, 5, 5, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(3.5F, -2, 0, 0, 0, -0.28F));
        hips.addOrReplaceChild("skirt_right", CubeListBuilder.create().texOffs(90, 16).mirror().addBox(-0.5F, 0, -2.5F, 1, 5, 5, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(-3.5F, -2, 0, 0, 0, 0.28F));
        hips.addOrReplaceChild("skirt_tail", cube(102, 16, -0.5F, 0, -1.5F, 1, 8, 3, def(-0.25F, 0, 0)),
                PartPose.offsetAndRotation(-3.3F, -2, 1.2F, 0.25F, 0, 0.22F));

        PartDefinition torso = hips.addOrReplaceChild("torso", cube(0, 16, -3, -8, -1.5F, 6, 8, 3), PartPose.offset(0, -3, 0));
        torso.addOrReplaceChild("chest", cube(18, 16, -3, -7, -2.3F, 6, 3, 1, def(-0.3F, 0, -0.15F)), PartPose.ZERO);
        torso.addOrReplaceChild("neck", cube(114, 0, -1, -1, -1, 2, 2, 2, def(-0.15F)), PartPose.offset(0, -8, 0));
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 1.6F : -1.6F;
            CubeListBuilder b = CubeListBuilder.create().texOffs(0, 48);
            if (side == 1) {
                b.mirror();
            }
            torso.addOrReplaceChild(side == 0 ? "thruster_l" : "thruster_r", b.addBox(-1, 0, 0, 2, 3, 2, def(-0.1F)),
                    PartPose.offsetAndRotation(x, -6.5F, 1.3F, 0.3F, 0, side == 0 ? -0.12F : 0.12F));
        }

        // 頭と髪
        PartDefinition head = torso.addOrReplaceChild("head", cube(0, 0, -4, -8, -3.5F, 8, 8, 7), PartPose.offset(0, -8, 0));
        head.addOrReplaceChild("hair", cube(30, 0, -4, -8, -3.5F, 8, 8, 7, def(0.45F)), PartPose.ZERO);
        head.addOrReplaceChild("back_hair", cube(60, 0, -4.5F, 0, -1, 9, 9, 2, def(0, 0, -0.3F)),
                PartPose.offsetAndRotation(0, -6.5F, 3.4F, 0.12F, 0, 0));
        head.addOrReplaceChild("side_lock_l", cube(82, 0, -0.5F, 0, -1, 1, 8, 2, def(-0.1F)),
                PartPose.offsetAndRotation(4.4F, -5, -2, 0, 0, -0.05F));
        head.addOrReplaceChild("side_lock_r", cube(88, 0, -0.5F, 0, -1, 1, 4, 2, def(-0.1F)),
                PartPose.offsetAndRotation(-4.4F, -5, -2, 0, 0, 0.05F));
        head.addOrReplaceChild("ponytail", cube(94, 0, -1, 0, -1, 2, 7, 2, def(-0.1F)),
                PartPose.offsetAndRotation(4.1F, -7, 2, 0.25F, 0, -0.45F));
        // アホ毛（横から見てコの字。薄い板だが厚みを持たせ、表裏が重ならないように）
        head.addOrReplaceChild("ahoge", cube(10, 48, -0.5F, -3, -1, 1, 3, 2, def(-0.35F, 0, 0)),
                PartPose.offsetAndRotation(0.5F, -8.2F, -0.5F, 0, 0.5F, 0.15F));
        head.addOrReplaceChild("fin", cube(106, 0, -0.5F, -1.5F, -1.5F, 1, 3, 3, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(-4.45F, -5.5F, 0.5F, -0.45F, 0, 0));

        // 腕（左はふつう、右は肩当てと籠手）
        PartDefinition armL = torso.addOrReplaceChild("arm_l", cube(48, 32, -1, -0.5F, -1, 2, 5, 2, def(-0.05F)),
                PartPose.offsetAndRotation(4, -7.5F, 0, 0, 0, -0.08F));
        armL.addOrReplaceChild("forearm_l", cube(64, 32, -1, 0, -1, 2, 5, 2, def(-0.05F)), PartPose.offset(0, 4.5F, 0));
        PartDefinition armR = torso.addOrReplaceChild("arm_r", cube(56, 32, -1, -0.5F, -1, 2, 5, 2, def(-0.05F)),
                PartPose.offsetAndRotation(-4, -7.5F, 0, 0, 0, 0.12F));
        armR.addOrReplaceChild("pad_r", cube(84, 32, -2, -1.5F, -2, 3, 2, 4, def(0.05F)), PartPose.rotation(0, 0, 0.15F));
        PartDefinition forearmR = armR.addOrReplaceChild("forearm_r", cube(72, 32, -1.5F, 0, -1.5F, 3, 5, 3, def(-0.1F)),
                PartPose.offset(0, 4.5F, 0));
        if (variant == AndroidRenderer.Variant.BLADE) {
            PartDefinition blade = forearmR.addOrReplaceChild("blade", cube(98, 32, -0.5F, 0, -1, 1, 13, 2, def(-0.3F, 0, -0.15F)),
                    PartPose.offset(-1.55F, -1, 0)); // 籠手の面と重ならないよう、わずかに食い込ませる
            // 先端は刃の中へ少し食い込ませ、細くする（刃の面と同じ所に面が重なると、ちらつくので）
            blade.addOrReplaceChild("blade_tip", cube(104, 32, -0.5F, 12.5F, -0.5F, 1, 2, 1, def(-0.32F, 0, -0.1F)), PartPose.ZERO);
        }

        // 脚（太ももは左右で違う絵。左は黒いニーハイ、右は素肌にガーター）
        PartDefinition legL = root.addOrReplaceChild("leg_l", cube(0, 32, -1.5F, 0, -1.5F, 3, 6, 3, def(-0.1F, 0, -0.1F)),
                PartPose.offset(1.6F, 12, 0));
        legL.addOrReplaceChild("shin_l", cube(24, 32, -1.5F, 0, -1.5F, 3, 6, 3, def(-0.05F)), PartPose.offset(0, 6, 0));
        PartDefinition legR = root.addOrReplaceChild("leg_r", cube(12, 32, -1.5F, 0, -1.5F, 3, 6, 3, def(-0.1F, 0, -0.1F)),
                PartPose.offset(-1.6F, 12, 0));
        legR.addOrReplaceChild("shin_r", cube(36, 32, -1.5F, 0, -1.5F, 3, 6, 3, def(-0.05F)), PartPose.offset(0, 6, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** 刃までの親の変形を pose に積む（刃を別に描くため）。 */
    void toBlade(PoseStack pose) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        torso.translateAndRotate(pose);
        armR.translateAndRotate(pose);
        forearmR.translateAndRotate(pose);
    }

    // ------------------------------------------------------------------ 動き

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    @Override
    public void setupAnim(RuinAndroid android, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float pt = age - android.tickCount;
        int state = android.state();
        float t = android.stateTime(pt);
        boolean printing = !android.printed();
        float walk = printing ? 0 : Math.min(1, limbSwingAmount);
        float w = limbSwing * 0.6662F;

        // 歩き・立ち姿
        legL.xRot = Mth.cos(w) * 0.9F * walk;
        legR.xRot = Mth.cos(w + Mth.PI) * 0.9F * walk;
        shinL.xRot = Math.max(0, Mth.sin(w + 0.6F)) * 0.8F * walk;
        shinR.xRot = Math.max(0, Mth.sin(w + Mth.PI + 0.6F)) * 0.8F * walk;
        armL.xRot = Mth.cos(w + Mth.PI) * 0.55F * walk;
        armR.xRot = Mth.cos(w) * 0.25F * walk;
        forearmL.xRot = -0.15F - 0.2F * walk;
        forearmR.xRot = -0.3F;
        hips.y += Math.abs(Mth.sin(w)) * -0.4F * walk;
        torso.xRot = 0.04F * walk + Mth.sin(age * 0.08F) * 0.02F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.zRot = 0.06F + Mth.sin(age * 0.05F) * 0.03F;
        float breeze = Mth.sin(age * 0.2F);
        skirtFront.xRot += -0.22F * walk - 0.03F * breeze;
        skirtBack.xRot += 0.3F * walk + 0.03F * breeze;
        skirtLeft.zRot += -0.08F * walk;
        skirtRight.zRot += 0.08F * walk;
        skirtTail.xRot += 0.35F * walk + 0.04F * breeze;
        ponytail.zRot += Mth.sin(age * 0.09F) * 0.07F - 0.1F * walk;
        ponytail.xRot += 0.4F * walk + Mth.sin(age * 0.07F) * 0.05F;
        backHair.xRot += 0.25F * walk;
        sideLockL.xRot += -0.1F * walk;
        sideLockR.xRot += -0.1F * walk;
        ahoge.zRot += Mth.sin(age * 0.15F) * 0.08F;

        if (printing) {
            head.xRot = 0.25F;
            armL.zRot = -0.2F;
            armR.zRot = 0.25F;
            return;
        }
        switch (state) {
            case RuinAndroid.STATE_LOW_POWER -> lowPower();
            case RuinAndroid.STATE_CHARGING -> charging(t, age);
            case BladeAndroid.STATE_DASH_WINDUP -> dashWindup(t, age);
            case BladeAndroid.STATE_DASH -> dash();
            case BladeAndroid.STATE_SLASH -> slash(t, 4, 1F);
            case BladeAndroid.STATE_SWING -> slash(t, 3, 0.6F);
            case BladeAndroid.STATE_SPIN_WINDUP -> spinWindup(t);
            case BladeAndroid.STATE_SPIN -> spin(t);
            case BladeAndroid.STATE_CAST -> cast(t);
            default -> {
            }
        }
    }

    private void lowPower() {
        torso.xRot += 0.3F;
        head.xRot += 0.55F;
        armL.zRot = -0.02F;
        armR.zRot = 0.05F;
        forearmL.xRot = 0;
        forearmR.xRot = 0;
        ponytail.xRot -= 0.15F;
    }

    /** 正座して、うつむいて充電する。 */
    private void charging(float t, float age) {
        float k = ease(t / 10);
        hips.y += 8.5F * k;
        legL.xRot = -1.5F * k;
        legR.xRot = -1.5F * k;
        shinL.xRot = 2.95F * k;
        shinR.xRot = 2.95F * k;
        legL.zRot = -0.08F * k;
        legR.zRot = 0.08F * k;
        torso.xRot += 0.1F * k;
        head.xRot = 0.4F * k + Mth.sin(age * 0.06F) * 0.03F;
        armL.xRot = -0.55F * k;
        forearmL.xRot = -0.65F * k;
        armL.zRot = 0.15F * k;
        armR.xRot = -0.45F * k;
        forearmR.xRot = -0.55F * k;
        armR.zRot = -0.1F * k;
        skirtFront.xRot -= 1.0F * k;
        skirtLeft.zRot -= 0.5F * k;
        skirtRight.zRot += 0.5F * k;
        skirtBack.xRot += 0.3F * k;
    }

    /** 瞬歩斬りの前触れ: 身をかがめ、刃を後ろへ引く。小さく震える。 */
    private void dashWindup(float t, float age) {
        float k = ease(t / 6);
        hips.y += 2.2F * k;
        torso.xRot += 0.45F * k;
        torso.zRot += Mth.sin(age * 2.2F) * 0.02F * k;
        head.xRot -= 0.35F * k;
        legL.xRot = -0.7F * k;
        shinL.xRot = 1.1F * k;
        legR.xRot = 0.35F * k;
        shinR.xRot = 1.0F * k;
        armR.xRot = 1.1F * k;
        armR.zRot = 0.12F + 0.4F * k;
        forearmR.xRot = -0.3F - 0.5F * k;
        armL.xRot = -0.9F * k;
        armL.zRot = -0.08F - 0.2F * k;
        forearmL.xRot = -0.15F - 0.5F * k;
        skirtBack.xRot += 0.35F * k;
        skirtTail.xRot += 0.3F * k;
        ponytail.xRot += 0.2F * k;
    }

    /** 突進: 深く前へ傾き、刃を後ろにたなびかせる。髪とスカートは後ろへ流れる。 */
    private void dash() {
        hips.y += 1.5F;
        torso.xRot += 0.75F;
        head.xRot -= 0.6F;
        legL.xRot = -1.0F;
        shinL.xRot = 0.5F;
        legR.xRot = 0.9F;
        shinR.xRot = 0.9F;
        armR.xRot = 1.4F;
        armR.zRot = 0.6F;
        forearmR.xRot = 0;
        armL.xRot = -0.4F;
        armL.zRot = -0.5F;
        backHair.xRot += 0.9F;
        ponytail.xRot += 1.1F;
        sideLockL.xRot += 0.6F;
        sideLockR.xRot += 0.6F;
        skirtBack.xRot += 0.8F;
        skirtTail.xRot += 0.9F;
        skirtFront.xRot += 0.1F;
        skirtLeft.zRot -= 0.2F;
        skirtRight.zRot += 0.2F;
    }

    /** 腕ごと振り抜く横薙ぎ（右後ろから左前へ）。fast tick で振り切り、残りで戻す。 */
    private void slash(float t, float fast, float amount) {
        float u = easeOut(t / fast);
        float back = 1 - ease((t - fast) / 4);
        float k = back * amount;
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -1.9F, -1.15F));
        armR.yRot = Mth.lerp(u, 1.25F, -0.95F) * k;
        armR.zRot = Mth.lerp(k, armR.zRot, 0.2F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.15F);
        torso.yRot += Mth.lerp(u, 0.5F, -0.6F) * k;
        torso.xRot += 0.15F * k;
        hips.yRot += Mth.lerp(u, 0.15F, -0.2F) * k;
        hips.y += 1.0F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.6F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.6F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.5F);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 0.3F);
        armL.xRot = Mth.lerp(k, armL.xRot, -0.3F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.6F);
        ponytail.zRot -= 0.5F * u * k;
        backHair.xRot += 0.3F * k;
        skirtTail.zRot += 0.4F * u * k;
        skirtLeft.zRot -= 0.25F * u * k;
    }

    private void spinWindup(float t) {
        float k = ease(t / 5);
        hips.y += 1.2F * k;
        torso.yRot += 0.6F * k;
        armR.zRot = 0.12F + 1.25F * k;
        forearmR.xRot = -0.3F * (1 - k);
        armL.zRot = -0.08F - 0.7F * k;
        legL.xRot = -0.3F * k;
        legR.xRot = 0.3F * k;
        shinL.xRot = 0.4F * k;
        shinR.xRot = 0.4F * k;
    }

    /** その場で一回転。刃を水平に広げ、スカートと髪が遠心力で広がる。 */
    private void spin(float t) {
        float u = ease(t / 7);
        float k = 1 - ease((t - 7) / 3);
        root.yRot = -Mth.TWO_PI * u;
        hips.y += 1.2F * k;
        armR.zRot = 0.12F + 1.33F * k;
        forearmR.xRot = 0;
        armL.zRot = -0.08F - 0.9F * k;
        float fling = Mth.sin(Mth.PI * Mth.clamp(t / 8, 0, 1)) * k;
        skirtFront.xRot -= 0.6F * fling;
        skirtBack.xRot += 0.6F * fling;
        skirtLeft.zRot -= 0.7F * fling;
        skirtRight.zRot += 0.7F * fling;
        skirtTail.zRot += 0.9F * fling;
        ponytail.zRot -= 1.1F * fling;
        backHair.xRot += 0.6F * fling;
        sideLockL.zRot -= 0.5F * fling;
        sideLockR.zRot += 0.5F * fling;
    }

    /** 光柱: 片膝をついて左手を床に当てる。撃つ瞬間に顔を上げる。 */
    private void cast(float t) {
        float k = ease(t / 5) * (1 - ease((t - 14) / 4));
        hips.y += 5.0F * k;
        legL.xRot = -1.45F * k;
        shinL.xRot = 1.45F * k;
        legR.xRot = -0.1F * k;
        shinR.xRot = 1.57F * k;
        torso.xRot += 0.4F * k;
        armL.xRot = -0.35F * k;
        armL.zRot = -0.08F - 0.25F * k;
        forearmL.xRot = -0.25F * k;
        armR.xRot = 0.5F * k;
        armR.zRot = 0.12F + 0.35F * k;
        float look = t < 8 ? 0.35F : -0.15F;
        head.xRot += look * k;
        skirtFront.xRot -= 0.5F * k;
        skirtBack.xRot += 0.2F * k;
    }
}
