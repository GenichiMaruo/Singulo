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
    public static final ModelLayerLocation WHIP_LAYER = new ModelLayerLocation(Singulo.id("whip_android"), "main");
    public static final ModelLayerLocation SABER_LAYER = new ModelLayerLocation(Singulo.id("saber_android"), "main");
    public static final ModelLayerLocation AXE_LAYER = new ModelLayerLocation(Singulo.id("axe_android"), "main");
    public static final ModelLayerLocation BIT_LAYER = new ModelLayerLocation(Singulo.id("bit_android"), "main");
    public static final ModelLayerLocation GUNNER_LAYER = new ModelLayerLocation(Singulo.id("gunner_android"), "main");

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
    /** 鞭型の柄（なければ null）。鞭そのものは描画で伸ばす。 */
    final ModelPart whipHandle;
    /** 光剣型の柄（なければ null）。光の刀身は描画で出す（柄の +Y の向き）。 */
    final ModelPart hilt;
    /** 斧型の握り（なければ null。箱は持たない）。+Y が柄の先（斧頭）の向き。斧そのものは描画で出す。 */
    final ModelPart axeGrip;
    /** ビット型の短剣の握り（なければ null。箱は持たない）。+Y が刃の向き。 */
    final ModelPart daggerGrip;
    /** 射撃型の銃の握り（なければ null。箱は持たない）。+Y が銃身の向き、+Z が握りの下の向き。銃は描画で出す。 */
    final ModelPart gunGrip;
    /** サイドテールと長い房のある側（+1 は体の左、-1 は右）。髪の揺れの向きに使う。 */
    final float hairSide;
    /** 装甲板を積んだときだけ見える、胸の銀の板と左肩の小さな肩当て（さりげなく）。 */
    final ModelPart armorChest;
    final ModelPart armorPad;
    /** バックパックアップグレードを積んだときの、背中のバックパックと肩ひも（背中のスラスターの代わりに付く）。 */
    final ModelPart backpack;
    final ModelPart strapL;
    final ModelPart strapR;
    final ModelPart thrusterL;
    final ModelPart thrusterR;
    /** バッテリーアップグレードの数だけ、腰の後ろ（スカートの上）に付くバッテリーパック。 */
    final ModelPart batteryL;
    final ModelPart batteryR;

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
        this.whipHandle = forearmR.hasChild("whip_handle") ? forearmR.getChild("whip_handle") : null;
        this.hilt = forearmR.hasChild("hilt") ? forearmR.getChild("hilt") : null;
        this.axeGrip = forearmR.hasChild("axe_grip") ? forearmR.getChild("axe_grip") : null;
        this.daggerGrip = forearmR.hasChild("dagger_grip") ? forearmR.getChild("dagger_grip") : null;
        this.gunGrip = forearmR.hasChild("gun_grip") ? forearmR.getChild("gun_grip") : null;
        this.hairSide = ponytail.getInitialPose().x > 0 ? 1 : -1;
        this.armorChest = torso.getChild("armor_chest");
        this.armorPad = armL.getChild("armor_pad");
        this.backpack = torso.getChild("backpack");
        this.strapL = torso.getChild("strap_l");
        this.strapR = torso.getChild("strap_r");
        this.thrusterL = torso.getChild("thruster_l");
        this.thrusterR = torso.getChild("thruster_r");
        this.batteryL = skirtBack.getChild("battery_l");
        this.batteryR = skirtBack.getChild("battery_r");
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
        PartDefinition skirtBack = hips.addOrReplaceChild("skirt_back", cube(72, 16, -4, 0, -0.5F, 8, 6, 1, def(0, 0, -0.2F)),
                PartPose.offsetAndRotation(0, -2, 2.0F, 0.28F, 0, 0));
        // バッテリーパック（スカートの後ろの面に、左右1つずつ。スカートと一緒に揺れる）
        skirtBack.addOrReplaceChild("battery_l", cube(16, 54, -1, -0.2F, 0.35F, 2, 3, 2, def(-0.05F)), PartPose.offset(2.3F, 0, 0));
        skirtBack.addOrReplaceChild("battery_r", CubeListBuilder.create().texOffs(16, 54).mirror()
                .addBox(-1, -0.2F, 0.35F, 2, 3, 2, def(-0.05F)), PartPose.offset(-2.3F, 0, 0));
        hips.addOrReplaceChild("skirt_left", cube(90, 16, -0.5F, 0, -2.5F, 1, 5, 5, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(3.5F, -2, 0, 0, 0, -0.28F));
        hips.addOrReplaceChild("skirt_right", CubeListBuilder.create().texOffs(90, 16).mirror().addBox(-0.5F, 0, -2.5F, 1, 5, 5, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(-3.5F, -2, 0, 0, 0, 0.28F));
        hips.addOrReplaceChild("skirt_tail", cube(102, 16, -0.5F, 0, -1.5F, 1, 8, 3, def(-0.25F, 0, 0)),
                PartPose.offsetAndRotation(-3.3F, -2, 1.2F, 0.25F, 0, 0.22F));

        PartDefinition torso = hips.addOrReplaceChild("torso", cube(0, 16, -3, -8, -1.5F, 6, 8, 3), PartPose.offset(0, -3, 0));
        torso.addOrReplaceChild("chest", cube(18, 16, -3, -7, -2.3F, 6, 3, 1, def(-0.3F, 0, -0.15F)), PartPose.ZERO);
        // 装甲板: 胸の板にかぶせる薄い銀の板（胸の板より少しだけ大きい）
        torso.addOrReplaceChild("armor_chest", cube(110, 40, -3, -7, -2.3F, 6, 3, 1, def(-0.12F, 0.12F, 0.06F)), PartPose.ZERO);
        torso.addOrReplaceChild("neck", cube(114, 0, -1, -1, -1, 2, 2, 2, def(-0.15F)), PartPose.offset(0, -8, 0));
        // バックパック（背中から少し離して背負う）と、背中の小物入れ、肩ひも
        PartDefinition backpack = torso.addOrReplaceChild("backpack", cube(16, 44, -3, -7, 0, 6, 6, 3), PartPose.offset(0, 0, 1.6F));
        backpack.addOrReplaceChild("pocket", cube(36, 44, -2, -4, 2.9F, 4, 3, 1, def(-0.05F)), PartPose.ZERO);
        torso.addOrReplaceChild("strap_l", cube(48, 44, -0.5F, -8.5F, -2, 1, 1, 4, def(-0.1F)), PartPose.offset(2.1F, 0, 0));
        torso.addOrReplaceChild("strap_r", CubeListBuilder.create().texOffs(48, 44).mirror()
                .addBox(-0.5F, -8.5F, -2, 1, 1, 4, def(-0.1F)), PartPose.offset(-2.1F, 0, 0));
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
        // 長い房とサイドテールの側（光刃型などは左、鞭型・ビット型は右。side_lock_l は長い房、side_lock_r は短い房）
        float m = variant.mirror ? -1 : 1;
        head.addOrReplaceChild("side_lock_l", cube(82, 0, -0.5F, 0, -1, 1, 8, 2, def(-0.1F)),
                PartPose.offsetAndRotation(4.4F * m, -5, -2, 0, 0, -0.05F * m));
        head.addOrReplaceChild("side_lock_r", cube(88, 0, -0.5F, 0, -1, 1, 4, 2, def(-0.1F)),
                PartPose.offsetAndRotation(-4.4F * m, -5, -2, 0, 0, 0.05F * m));
        head.addOrReplaceChild("ponytail", cube(94, 0, -1, 0, -1, 2, 7, 2, def(-0.1F)),
                PartPose.offsetAndRotation(4.1F * m, -7, 2, 0.25F, 0, -0.45F * m));
        // アホ毛（横から見てコの字。薄い板だが厚みを持たせ、表裏が重ならないように）
        head.addOrReplaceChild("ahoge", cube(10, 48, -0.5F, -3, -1, 1, 3, 2, def(-0.35F, 0, 0)),
                PartPose.offsetAndRotation(0.5F, -8.2F, -0.5F, 0, 0.5F, 0.15F));
        head.addOrReplaceChild("fin", cube(106, 0, -0.5F, -1.5F, -1.5F, 1, 3, 3, def(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(-4.45F, -5.5F, 0.5F, -0.45F, 0, 0));

        // 腕（左はふつう、右は肩当てと籠手）
        PartDefinition armL = torso.addOrReplaceChild("arm_l", cube(48, 32, -1, -0.5F, -1, 2, 5, 2, def(-0.05F)),
                PartPose.offsetAndRotation(4, -7.5F, 0, 0, 0, -0.08F));
        // 前腕は上腕より少し細く: 同じ太さだと、肘を曲げたとき両方の横の面が同じ平面で重なり、肘の光る線がちらつく
        armL.addOrReplaceChild("forearm_l", cube(64, 32, -1, 0, -1, 2, 5, 2, def(-0.1F)), PartPose.offset(0, 4.5F, 0));
        // 装甲板: 左肩の小さな肩当て（右の肩当てより薄い）
        armL.addOrReplaceChild("armor_pad", cube(110, 46, -1, -1.2F, -2, 3, 1, 4, def(0.02F, -0.1F, -0.1F)),
                PartPose.rotation(0, 0, -0.15F));
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
        if (variant == AndroidRenderer.Variant.SABER) {
            // 光剣の柄（握った手から前へ。+Y が刀身の向き）と、鍔
            PartDefinition hilt = forearmR.addOrReplaceChild("hilt", cube(114, 32, -0.5F, -1.5F, -0.5F, 1, 4, 1, def(-0.05F)),
                    PartPose.offsetAndRotation(0, 4.4F, -0.2F, -Mth.HALF_PI, 0, 0));
            hilt.addOrReplaceChild("guard", cube(118, 32, -1.5F, 2.5F, -0.5F, 3, 1, 1, def(-0.1F)), PartPose.ZERO);
        }
        if (variant == AndroidRenderer.Variant.AXE) {
            // 両手斧の握り（手の中。柄は前上がりに傾けて持つ）
            forearmR.addOrReplaceChild("axe_grip", CubeListBuilder.create(), PartPose.offsetAndRotation(0, 4.4F, -0.2F, -2.3F, 0, 0));
        }
        if (variant == AndroidRenderer.Variant.GUNNER) {
            // 銃の握り（手の中。構えていないときは、銃口を足元へ向けて下げて持つ。人や前の物に向けない）
            forearmR.addOrReplaceChild("gun_grip", CubeListBuilder.create(), PartPose.offsetAndRotation(0, 4.4F, -0.2F, 0.15F, 0, 0));
        }
        if (variant == AndroidRenderer.Variant.BIT) {
            // 短剣の握り（手から前へ）
            forearmR.addOrReplaceChild("dagger_grip", CubeListBuilder.create(), PartPose.offsetAndRotation(0, 4.4F, -0.2F, -Mth.HALF_PI, 0, 0));
        }
        if (variant == AndroidRenderer.Variant.WHIP) {
            // 鞭の柄（握った手から下へ）
            forearmR.addOrReplaceChild("whip_handle", cube(110, 32, -0.5F, 0, -0.5F, 1, 4, 1, def(-0.05F)),
                    PartPose.offsetAndRotation(0, 4.2F, -0.6F, -0.35F, 0, 0));
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

    /** 装甲の部品を、親の変形をたどって描く（エンチャントの光沢を重ねるため）。 */
    void renderArmor(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay) {
        pose.pushPose();
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        torso.translateAndRotate(pose);
        armorChest.render(pose, vc, light, overlay);
        armL.translateAndRotate(pose);
        armorPad.render(pose, vc, light, overlay);
        pose.popPose();
    }

    /** 頭までの変形を pose に積む（頭の装飾）。 */
    void toHead(PoseStack pose) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        torso.translateAndRotate(pose);
        head.translateAndRotate(pose);
    }

    /** 後ろ髪までの変形（リボンの垂れを、髪の角度に合わせて髪の外側に沿わせる）。 */
    void toBackHair(PoseStack pose) {
        toHead(pose);
        backHair.translateAndRotate(pose);
    }

    /** スカートの後ろの面までの変形（腰リボンの垂れを、スカートの角度に合わせて沿わせる）。 */
    void toSkirtBack(PoseStack pose) {
        toHips(pose);
        skirtBack.translateAndRotate(pose);
    }

    /** 胴までの変形（背中の装飾）。 */
    void toTorso(PoseStack pose) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        torso.translateAndRotate(pose);
    }

    /** 背負ったバックパックまでの変形（スラスターのノズルを付けるため）。 */
    void toBackpack(PoseStack pose) {
        toTorso(pose);
        backpack.translateAndRotate(pose);
    }

    /** すねまでの変形（かかとのスラスターを付けるため）。 */
    void toShin(PoseStack pose, boolean left) {
        root.translateAndRotate(pose);
        (left ? legL : legR).translateAndRotate(pose);
        (left ? shinL : shinR).translateAndRotate(pose);
    }

    /** 腰までの変形（腰の装飾）。 */
    void toHips(PoseStack pose) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
    }

    /** 腰の横（スカートの左右の面）までの変形を pose に積む（タンクを描くため）。 */
    void toHipSide(PoseStack pose, boolean left) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        hips.getChild(left ? "skirt_left" : "skirt_right").translateAndRotate(pose);
    }

    /** 左手の先までの変形を pose に積む（ソーラーパネルを出すため）。 */
    void toLeftHand(PoseStack pose) {
        root.translateAndRotate(pose);
        hips.translateAndRotate(pose);
        torso.translateAndRotate(pose);
        armL.translateAndRotate(pose);
        forearmL.translateAndRotate(pose);
        pose.translate(0, 5 / 16F, 0);
    }

    /** 柄の先（鞭の付け根）までの変形を pose に積む。 */
    void toWhip(PoseStack pose) {
        toBlade(pose);
        whipHandle.translateAndRotate(pose);
        pose.translate(0, 4 / 16F, 0);
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
        pose(android, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
        blend(android, age);
    }

    // ------------------------------------------------------------------ 動きのつなぎ

    /** 個体ごとの、動きのつなぎの記録（前の絵の姿勢と、つなぎ始めの姿勢）。 */
    private static final class Blend {
        float[] last;
        float[] from;
        int key = Integer.MIN_VALUE;
        float start;
        float duration;
    }

    private final java.util.Map<RuinAndroid, Blend> blends = new java.util.WeakHashMap<>();
    private ModelPart[] parts;

    /** 姿勢を決める区切り（行動の状態と、状態を変えずに姿勢だけ変わる条件）。これが変わったらつなぐ。 */
    private int poseKey(RuinAndroid android) {
        int flags = (!android.printed() ? 1 : 0) | (android.napping() ? 2 : 0) | (android.showsHeldDecor() ? 4 : 0)
                | (android instanceof io.github.genichimaruo.singulo.android.BitAndroid bit && bit.deployed() > 0 ? 8 : 0)
                | (axeGrip != null && axeOneHanded(android) ? 16 : 0);
        return android.state() << 5 | flags;
    }

    /** 区切り from から to へ移るときのつなぎの長さ（tick）。 */
    private static int blendTicks(int from, int to) {
        int a = from >> 5, b = to >> 5;
        // 構え → 本打ちは、構えの終わりの姿勢から本打ちが始まるよう作ってあるので、つながずに振り抜く
        if (b == a + 1 && (a == BladeAndroid.STATE_DASH_WINDUP || a == BladeAndroid.STATE_SPIN_WINDUP
                || a == io.github.genichimaruo.singulo.android.WhipAndroid.STATE_LASH_WINDUP
                || a == io.github.genichimaruo.singulo.android.WhipAndroid.STATE_SLAM_WINDUP
                || a == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_THRUST_WINDUP
                || a == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_SWEEP_WINDUP
                || a == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CLEAVE_WINDUP
                || a == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_SLAM_WINDUP
                || a == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_LEAP_WINDUP
                || a == io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_ASSEMBLE || a == io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_CHARGE || a == io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_FIRE)) {
            return 0;
        }
        if (a != b && strike(b)) {
            return 2;               // 構えなしの素早い打ち込みは、遅れて見えないよう短く
        }
        if (calm(a) || calm(b)) {
            return 8;               // 座る・休む・作業に入る／抜けるときは、ゆっくり
        }
        if (a == b) {
            return 10;              // 行動はそのままで持ち方などだけ変わるとき（片手持ち・ビットの出し入れ）
        }
        return 5;
    }

    /** 構えなしで出る、素早い打ち込み。 */
    private static boolean strike(int state) {
        return state == BladeAndroid.STATE_DASH || state == BladeAndroid.STATE_SLASH || state == BladeAndroid.STATE_SPIN
                || state == BladeAndroid.STATE_SWING || state == io.github.genichimaruo.singulo.android.WhipAndroid.STATE_LASH
                || state == io.github.genichimaruo.singulo.android.WhipAndroid.STATE_SLAM
                || state == io.github.genichimaruo.singulo.android.WhipAndroid.STATE_CRACK
                || state == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_THRUST
                || state == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_SWEEP
                || state == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_RIPOSTE
                || state == io.github.genichimaruo.singulo.android.SaberAndroid.STATE_CUT
                || state == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CLEAVE
                || state == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_SLAM
                || state == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_LEAP
                || state == io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CHOP
                || state == io.github.genichimaruo.singulo.android.BitAndroid.STATE_STAB
                || state == io.github.genichimaruo.singulo.android.BitAndroid.STATE_BACKSTEP
                || state == io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_HOP;
    }

    /** 座る・休む・充電・作業など、落ち着いた行動。 */
    private static boolean calm(int state) {
        return state == RuinAndroid.STATE_LOW_POWER || state == RuinAndroid.STATE_CHARGING || state == RuinAndroid.STATE_SOLAR
                || state == RuinAndroid.STATE_SOLAR_FOLD || state == RuinAndroid.STATE_RESTING || state == RuinAndroid.STATE_CRAFTING
                || state == RuinAndroid.STATE_MINING || state == RuinAndroid.STATE_SIT || state == RuinAndroid.STATE_SIT_GROUND
                || state == RuinAndroid.STATE_TEND || state == RuinAndroid.STATE_SWEEP || state == RuinAndroid.STATE_FLOAT_REST
                || state == RuinAndroid.STATE_SEABED_REST;
    }

    /** 区切りが変わったら、前の絵の姿勢から新しい姿勢へ、なめらかに寄せる（どの型・どの行動の組み合わせでも）。 */
    private void blend(RuinAndroid android, float age) {
        if (parts == null) {
            parts = root.getAllParts().toArray(ModelPart[]::new);
        }
        Blend b = blends.computeIfAbsent(android, a -> new Blend());
        int key = poseKey(android);
        if (key != b.key) {
            if (b.last != null) {
                b.from = b.last.clone();
                b.start = age;
                b.duration = blendTicks(b.key, key);
            }
            b.key = key;
        }
        if (b.from != null) {
            float u = b.duration <= 0 ? 1 : Mth.clamp((age - b.start) / b.duration, 0, 1);
            if (u >= 1) {
                b.from = null;
            } else {
                float r = 1 - ease(u);
                for (int i = 0; i < parts.length; i++) {
                    ModelPart part = parts[i];
                    int o = i * 6;
                    part.x += (b.from[o] - part.x) * r;
                    part.y += (b.from[o + 1] - part.y) * r;
                    part.z += (b.from[o + 2] - part.z) * r;
                    // 回転は近い向きの側から回す（回転技の後に、何回転も巻き戻さないよう）
                    part.xRot += Mth.wrapDegrees((b.from[o + 3] - part.xRot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * r;
                    part.yRot += Mth.wrapDegrees((b.from[o + 4] - part.yRot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * r;
                    part.zRot += Mth.wrapDegrees((b.from[o + 5] - part.zRot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * r;
                }
            }
        }
        float[] last = b.last != null ? b.last : new float[parts.length * 6];
        for (int i = 0; i < parts.length; i++) {
            ModelPart part = parts[i];
            int o = i * 6;
            last[o] = part.x;
            last[o + 1] = part.y;
            last[o + 2] = part.z;
            last[o + 3] = part.xRot;
            last[o + 4] = part.yRot;
            last[o + 5] = part.zRot;
        }
        b.last = last;
    }

    /**
     * 斧型の待機の持ち方。ふだんは両手で斧の先を下ろして持ち、ときどき（個体ごとにずらして、30秒のうち12秒）
     * 右手だけで斧を前上がりに担ぐ。左手に装飾を持っている間は、いつも片手持ち。
     */
    private static boolean axeOneHanded(RuinAndroid android) {
        return android.showsHeldDecor() || Math.floorMod(android.tickCount + android.getId() * 977, 600) < 240;
    }

    private void pose(RuinAndroid android, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean armored = !android.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty();
        armorChest.visible = armored;
        armorPad.visible = armored;
        boolean pack = android.hasBackpack();
        backpack.visible = pack;
        strapL.visible = pack;
        strapR.visible = pack;
        thrusterL.visible = !pack;
        thrusterR.visible = !pack;
        // ベレー帽をかぶっている間は、アホ毛を帽子の中にしまう（突き抜けないように）
        ahoge.visible = !(android.decor(io.github.genichimaruo.singulo.android.AndroidAccessoryItem.HEAD).getItem()
                instanceof io.github.genichimaruo.singulo.android.AndroidAccessoryItem acc && acc.style().equals("beret"));
        int batteries = android.batteries();
        batteryL.visible = batteries >= 1;
        batteryR.visible = batteries >= 2;
        // 物を作っている間は、手の武器をしまう（両手を使う。射撃型の小型銃は腰に差す: 描画の側）
        boolean armed = !android.weaponStowed();
        for (ModelPart grip : new ModelPart[]{whipHandle, hilt, axeGrip, daggerGrip, gunGrip}) {
            if (grip != null) {
                grip.visible = armed;
            }
        }
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
        ponytail.zRot += (Mth.sin(age * 0.09F) * 0.07F - 0.1F * walk) * hairSide;
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
            case RuinAndroid.STATE_CRAFTING -> crafting(age);
            case RuinAndroid.STATE_MINING -> mining(age);
            case RuinAndroid.STATE_CHARGING -> charging(t, age);
            case RuinAndroid.STATE_SOLAR -> solar(t, age);
            case RuinAndroid.STATE_SOLAR_FOLD -> solarFold(t, age);
            case RuinAndroid.STATE_RESTING, RuinAndroid.STATE_SIT_GROUND -> resting(t, age);
            case RuinAndroid.STATE_FLOAT_REST -> floatingRest(t, age);
            case RuinAndroid.STATE_SEABED_REST -> seabedRest(t, age);
            case RuinAndroid.STATE_SIT -> sittingOnChair(t, age);
            case RuinAndroid.STATE_TEND -> tending(t, age);
            case RuinAndroid.STATE_SWEEP -> sweeping(age);
            case BladeAndroid.STATE_DASH_WINDUP -> dashWindup(t, age);
            case BladeAndroid.STATE_DASH -> dash();
            case BladeAndroid.STATE_SLASH -> slash(t, 4, 1F);
            case BladeAndroid.STATE_SWING -> slash(t, 3, 0.6F);
            case BladeAndroid.STATE_SPIN_WINDUP -> spinWindup(t);
            case BladeAndroid.STATE_SPIN -> spin(t);
            case BladeAndroid.STATE_CAST -> cast(t);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_LASH_WINDUP -> lashWindup(t);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_LASH -> lash(t, 1F);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_CRACK -> crack(t);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_BIND -> bind(t);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_SLAM_WINDUP -> slamWindup(t, age);
            case io.github.genichimaruo.singulo.android.WhipAndroid.STATE_SLAM -> slam(t);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_THRUST_WINDUP -> thrustWindup(t);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_THRUST -> thrust(t);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_SWEEP_WINDUP -> sweepWindup(t);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_SWEEP -> sweep(t, 2F);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_RIPOSTE -> sweep(t, 1.5F);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_CUT -> slash(t, 3, 0.6F);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_GUARD -> guard(t, age);
            case io.github.genichimaruo.singulo.android.SaberAndroid.STATE_WAVE -> wave(t);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CLEAVE_WINDUP -> cleaveWindup(t);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CLEAVE -> cleave(t);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_SLAM_WINDUP -> axeRaise(ease(t / 8), age);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_SLAM -> axeSlam(t);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_LEAP_WINDUP -> leapWindup(t, age);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_LEAP -> leap(t, age);
            case io.github.genichimaruo.singulo.android.AxeAndroid.STATE_CHOP -> {
                slash(t, 3, 0.75F);
                grip(1 - ease((t - 3) / 4), -0.5F);
            }
            case io.github.genichimaruo.singulo.android.BitAndroid.STATE_DEPLOY -> deploy(t);
            case io.github.genichimaruo.singulo.android.BitAndroid.STATE_STAB -> stab(t);
            case io.github.genichimaruo.singulo.android.BitAndroid.STATE_BACKSTEP -> backstep(t);
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_SHOT -> pistolAim(t, android instanceof io.github.genichimaruo.singulo.android.GunnerAndroid gun ? Mth.clamp((gun.shotFlash() - pt) / io.github.genichimaruo.singulo.android.GunnerAndroid.FLASH, 0, 1) : 0);
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_ASSEMBLE -> railHold(ease(t / 8), age, 0);
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_CHARGE -> railHold(1, age, Mth.clamp(t / io.github.genichimaruo.singulo.android.GunnerAndroid.RAIL_CHARGE, 0, 1));
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_FIRE -> railFire(t, age);
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_RAIL_STOW -> railHold(1 - ease((t - 7.5F) / 4.5F), age, 0);   // 部品が背中へ戻り終わってから腕を下ろす（銃が体に刺さらない）
            case io.github.genichimaruo.singulo.android.GunnerAndroid.STATE_HOP -> backstep(t);
            default -> {
                if (axeGrip != null && axeOneHanded(android)) {
                    // 片手持ち: 右手だけで柄を握り、斧を前上がりに担ぐ。左手は歩きに合わせて楽に振る
                    armR.xRot -= 0.15F;
                } else if (axeGrip != null) {
                    // 両手斧の先を右前の床近くへ下ろし、体の前で斜めに両手で持つ。
                    // 柄頭は胸より下に収め、左手は右手から柄頭の側へ3.5ピクセルの所を握る（角度は柄の線に左手が乗るよう逆算した値）
                    armR.xRot = -0.5F;
                    forearmR.xRot = -0.5F;
                    axeGrip.setRotation(-0.626F, 2.449F, 0.33F);
                    armL.setRotation(-0.658F, 0.723F, 0.392F);
                    forearmL.xRot = -0.351F;
                }
                if (android instanceof io.github.genichimaruo.singulo.android.BitAndroid bit && bit.deployed() > 0) {
                    // ビットを浮かべている間: 左手を相手の方へ差し向けて指揮する
                    armL.xRot = Mth.lerp(0.8F, armL.xRot, -1.35F);
                    armL.zRot = -0.25F;
                    forearmL.xRot = -0.1F;
                    head.zRot -= 0.04F;
                }
                if (hilt != null) {
                    // 剣を低く前へ向けて構える
                    armR.xRot -= 0.3F;
                    forearmR.xRot = -0.5F;
                }
                if (whipHandle != null) {
                    // 鞭を持つ右手は少し前に出して構える
                    armR.xRot -= 0.35F;
                    forearmR.xRot = -0.45F;
                }
            }
        }
        gestureOverlay(android, pt);
        if (android.napping()) {
            // 昼寝: 頭を前へ垂らして、ゆっくり息をする
            float breath = Mth.sin(age * 0.06F);
            head.xRot = 0.6F + breath * 0.04F;
            head.zRot = 0.25F;
            head.yRot = 0;
            torso.xRot += 0.08F + breath * 0.02F;
        }
        if (android.showsHeldDecor()) {
            // 手に持つ装飾: 左の前腕を前へ水平に出して持つ
            armL.xRot = Mth.lerp(0.8F, armL.xRot, -0.35F);
            armL.zRot = -0.1F;
            forearmL.xRot = -1.2F;
        }
    }

    /** 挨拶の仕草: 手を振る（左手を上げて左右に）・会釈（頭と上体を軽く下げる）。始めと終わりはなめらかに。 */
    private void gestureOverlay(RuinAndroid android, float pt) {
        int g = android.gesture();
        if (g == RuinAndroid.GESTURE_NONE) {
            return;
        }
        float t = android.gestureTime(pt);
        float k = Mth.clamp(Math.min(t / 5, (RuinAndroid.GESTURE_TICKS - t) / 5), 0, 1);
        k = k * k * (3 - 2 * k);
        if (g == RuinAndroid.GESTURE_WAVE) {
            armL.xRot = Mth.lerp(k, armL.xRot, -0.3F);
            armL.zRot = Mth.lerp(k, armL.zRot, -2.5F + Mth.sin(t * 0.7F) * 0.35F);
            forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.35F);
            head.zRot += 0.08F * k;
        } else if (g == RuinAndroid.GESTURE_NOD) {
            float bow = Math.max(0, Mth.sin(t / RuinAndroid.GESTURE_TICKS * Mth.PI));
            head.xRot += 0.45F * bow;
            torso.xRot += 0.12F * bow;
        } else if (g == RuinAndroid.GESTURE_TALK) {
            // 話す身ぶり: 右手を胸の前へ出して、手のひらを返しながら小さく動かす。頭も少し傾ける
            armR.xRot = Mth.lerp(k, armR.xRot, -0.7F + Mth.sin(t * 0.5F) * 0.15F);
            armR.zRot = Mth.lerp(k, armR.zRot, 0.35F);
            forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.9F + Mth.sin(t * 0.8F) * 0.2F);
            head.zRot += 0.1F * k * Mth.sin(t * 0.3F);
        } else if (android.state() == RuinAndroid.STATE_IDLE) {
            // 射撃型の仕草は、構えていない間だけ（次の構えが始まったら、そちらを優先）
            if (g == io.github.genichimaruo.singulo.android.GunnerAndroid.GESTURE_RELOAD) {
                reload(t, k);
            } else if (g == io.github.genichimaruo.singulo.android.GunnerAndroid.GESTURE_TWIRL) {
                twirl(t, k);
            }
        }
    }

    /**
     * リロード: 小型銃を胸の前へ引き寄せて見下ろし、左手で新しいエネルギーカートリッジを握りの下へ運んで、ぐっと押し込む
     * （カートリッジの出し入れと、握りの窓が満ちていくのは描画の側）。
     */
    private void reload(float t, float k) {
        float slap = Math.max(0, Mth.sin(Mth.clamp((t - io.github.genichimaruo.singulo.android.GunnerAndroid.RELOAD_INSERT + 2) / 4, 0, 1) * Mth.PI));
        armR.xRot = Mth.lerp(k, armR.xRot, -0.5F);
        armR.yRot = Mth.lerp(k, armR.yRot, -0.1F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.05F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -1.1F);
        float reach = k * ease((t - 4) / 5) * (1 - ease((t - 16) / 5));
        armL.xRot = Mth.lerp(reach, armL.xRot, -0.75F + 0.2F * slap);
        armL.yRot = Mth.lerp(reach, armL.yRot, 0.55F);
        armL.zRot = Mth.lerp(reach, armL.zRot, 0.05F);
        forearmL.xRot = Mth.lerp(reach, forearmL.xRot, -0.95F + 0.15F * slap);
        head.xRot += 0.35F * k;
        head.yRot -= 0.2F * k;
    }

    /**
     * 相手を倒したあと: 小型銃を指で2回転させてから、銃口を上へ立てて顔の横に構え、首をかしげる（回すのは描画の側）。
     * アホ毛も小さく跳ねる。
     */
    private void twirl(float t, float k) {
        float pose = ease((t - 13) / 5);
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(pose, -0.7F, -0.15F));
        armR.yRot = Mth.lerp(k, armR.yRot, Mth.lerp(pose, -0.2F, 0.15F));
        armR.zRot = Mth.lerp(k, armR.zRot, Mth.lerp(pose, 0.3F, 0.55F));
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, Mth.lerp(pose, -0.55F, -1.6F));
        float tilt = k * pose;
        head.zRot += 0.22F * tilt;
        head.xRot -= 0.05F * tilt;
        torso.zRot += 0.04F * tilt;
        float hop = t > 13 ? Mth.sin((t - 13) * 0.9F) * Math.max(0, 1 - (t - 13) / 10) : 0;
        ahoge.zRot += 0.35F * hop * k;
        ahoge.xRot -= 0.15F * Math.abs(hop) * k;
    }

    // ------------------------------------------------------------------ 光剣型の動き

    /** 溜め突きの前触れ: 剣を腰だめに引き、左手を相手へ向ける。腰を落とす。 */
    private void thrustWindup(float t) {
        float k = ease(t / 6);
        armR.xRot = Mth.lerp(k, armR.xRot, 0.55F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.2F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -1.45F);
        torso.yRot += 0.5F * k;
        hips.y += 1.6F * k;
        legL.xRot = -0.6F * k;
        shinL.xRot = 0.8F * k;
        legR.xRot = 0.4F * k;
        shinR.xRot = 0.7F * k;
        armL.xRot = -1.35F * k;
        armL.zRot = -0.08F + 0.25F * k;
        forearmL.xRot = -0.1F * k;
        head.yRot -= 0.3F * k;
    }

    /** 突き: 腕をまっすぐ前へ伸ばして深く踏み込む。 */
    private void thrust(float t) {
        float u = easeOut(t / 2);
        float k = 1 - ease((t - 4) / 4);
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, 0.55F, -1.5F));
        armR.zRot = Mth.lerp(k, armR.zRot, 0.05F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, Mth.lerp(u, -1.45F, -0.05F));
        torso.yRot += Mth.lerp(u, 0.5F, -0.35F) * k;
        torso.xRot += 0.4F * u * k;
        hips.y += 2.0F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -1.05F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.7F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.85F);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 0.3F);
        armL.xRot = Mth.lerp(k, armL.xRot, 0.5F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.6F);
        backHair.xRot += 0.5F * u * k;
        ponytail.xRot += 0.6F * u * k;
        skirtBack.xRot += 0.5F * u * k;
        skirtTail.xRot += 0.5F * u * k;
    }

    /** 横薙ぎの前触れ: 剣を右へ高く引き、体をひねる。 */
    private void sweepWindup(float t) {
        float k = ease(t / 6);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.45F);
        armR.yRot = 1.3F * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        torso.yRot += 0.6F * k;
        hips.y += 1.0F * k;
        armL.zRot = -0.08F - 0.6F * k;
        legL.xRot = -0.4F * k;
        legR.xRot = 0.35F * k;
    }

    /** 横薙ぎ（と斬り返し）: 腕を水平に大きく振り抜く。 */
    private void sweep(float t, float fast) {
        float u = easeOut(t / fast);
        float k = 1 - ease((t - fast - 2) / 4);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.45F);
        armR.yRot = Mth.lerp(u, 1.3F, -1.15F) * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.1F);
        torso.yRot += Mth.lerp(u, 0.6F, -0.75F) * k;
        hips.yRot += Mth.lerp(u, 0.2F, -0.25F) * k;
        hips.y += 1.2F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.55F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.5F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.5F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.8F);
        ponytail.zRot -= 0.6F * u * k * hairSide;
        skirtTail.zRot += 0.5F * u * k;
        skirtLeft.zRot -= 0.3F * u * k;
    }

    /** 構え: 剣を体の前に立て、左手を添える。腰を落として小さく揺れる。 */
    private void guard(float t, float age) {
        float k = ease(t / 4);
        armR.xRot = Mth.lerp(k, armR.xRot, -0.6F);
        armR.zRot = Mth.lerp(k, armR.zRot, -0.35F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.97F);
        armL.xRot = Mth.lerp(k, armL.xRot, -0.95F);
        armL.zRot = Mth.lerp(k, armL.zRot, 0.45F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.7F);
        hips.y += 1.3F * k + Mth.sin(age * 0.3F) * 0.15F * k;
        legL.xRot = -0.35F * k;
        shinL.xRot = 0.6F * k;
        legR.xRot = 0.3F * k;
        shinR.xRot = 0.6F * k;
        torso.xRot += 0.1F * k;
    }

    /** 斬撃波: 剣を頭上へ振りかぶり、斜めに振り下ろして光を飛ばす。 */
    private void wave(float t) {
        if (t < 6) {
            float k = ease(t / 5);
            armR.xRot = Mth.lerp(k, armR.xRot, -2.8F);
            armR.zRot = Mth.lerp(k, armR.zRot, 0.4F);
            forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.3F);
            torso.xRot -= 0.15F * k;
            armL.xRot = -0.6F * k;
            return;
        }
        float u = easeOut((t - 6) / 2);
        float k = 1 - ease((t - 10) / 4);
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -2.8F, -0.6F));
        armR.zRot = Mth.lerp(k, armR.zRot, Mth.lerp(u, 0.4F, -0.3F));
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        torso.xRot += 0.35F * u * k;
        torso.yRot -= 0.3F * u * k;
        hips.y += 1.5F * u * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.7F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.7F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.5F);
        backHair.xRot += 0.3F * u * k;
    }

    // ------------------------------------------------------------------ 斧型の動き

    /** 斧の握りの角度（柄の向き）を target へ寄せる。 */
    private void grip(float k, float target) {
        if (axeGrip != null) {
            axeGrip.xRot = Mth.lerp(k, axeGrip.xRot, target);
        }
    }

    /** 薙ぎ払いの前触れ: 斧を右後ろへ大きく引き、両手で握って腰を落とす。 */
    private void cleaveWindup(float t) {
        float k = ease(t / 8);
        grip(k, -0.15F);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.3F);
        armR.yRot = 1.45F * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.35F);
        armL.xRot = Mth.lerp(k, armL.xRot, -1.2F);
        armL.yRot = 0.9F * k;
        armL.zRot = Mth.lerp(k, armL.zRot, 0.5F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.5F);
        torso.yRot += 0.8F * k;
        hips.y += 1.6F * k;
        legL.xRot = -0.55F * k;
        shinL.xRot = 0.6F * k;
        legR.xRot = 0.45F * k;
        shinR.xRot = 0.5F * k;
        head.yRot -= 0.5F * k;
    }

    /** 薙ぎ払い: 両手で水平に大きく振り抜き、体ごと左へ回る。 */
    private void cleave(float t) {
        float u = easeOut(t / 2.5F);
        float k = 1 - ease((t - 5) / 4);
        grip(k, -0.15F);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.35F);
        armR.yRot = Mth.lerp(u, 1.45F, -1.2F) * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        armL.xRot = Mth.lerp(k, armL.xRot, -1.25F);
        armL.yRot = Mth.lerp(u, 0.9F, -1.3F) * k;
        armL.zRot = Mth.lerp(k, armL.zRot, 0.3F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.3F);
        torso.yRot += Mth.lerp(u, 0.8F, -0.9F) * k;
        hips.yRot += Mth.lerp(u, 0.25F, -0.35F) * k;
        hips.y += 1.6F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.65F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.6F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.55F);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 0.4F);
        ponytail.zRot -= 0.8F * u * k * hairSide;
        backHair.xRot += 0.3F * u * k;
        skirtTail.zRot += 0.6F * u * k;
        skirtLeft.zRot -= 0.35F * u * k;
    }

    /** 斧を両手で頭上へ振り上げる（k: 0〜1）。体を少し反らして伸び上がる。 */
    private void axeRaise(float k, float age) {
        float tremble = Mth.sin(age * 1.9F) * 0.02F * k;
        armR.xRot = Mth.lerp(k, armR.xRot, -2.95F + tremble);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.3F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.35F);
        armL.xRot = Mth.lerp(k, armL.xRot, -2.95F - tremble);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.3F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.35F);
        torso.xRot -= 0.2F * k;
        head.xRot -= 0.15F * k;
        legL.xRot = -0.3F * k;
        legR.xRot = 0.3F * k;
    }

    /** 叩きつけ: 両手の斧を床まで振り下ろし、深くかがみ込む。 */
    private void axeSlam(float t) {
        float u = easeOut(t / 2.5F);
        float k = 1 - ease((t - 8) / 4);
        grip(k, Mth.lerp(u, -2.3F, -0.45F));
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -2.95F, -0.75F));
        armR.zRot = Mth.lerp(k, armR.zRot, Mth.lerp(u, 0.3F, -0.25F));
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        armL.xRot = Mth.lerp(k, armL.xRot, Mth.lerp(u, -2.95F, -0.75F));
        armL.zRot = Mth.lerp(k, armL.zRot, Mth.lerp(u, -0.3F, 0.35F));
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.2F);
        torso.xRot += 0.65F * u * k;
        hips.y += 2.8F * u * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -1.0F * u);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 1.2F * u);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.45F * u);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 1.1F * u);
        backHair.xRot -= 0.3F * u * k;
        ponytail.xRot -= 0.6F * u * k;
        skirtBack.xRot += 0.4F * u * k;
        skirtFront.xRot -= 0.3F * u * k;
    }

    /** 踏み込み斬りの前触れ: 低くかがみ、斧を右下へ引いて構える。小さく震える。 */
    private void leapWindup(float t, float age) {
        float k = ease(t / 6);
        grip(k, 0.3F);
        hips.y += 2.6F * k;
        torso.xRot += 0.45F * k;
        torso.zRot += Mth.sin(age * 2.2F) * 0.02F * k;
        head.xRot -= 0.4F * k;
        legL.xRot = -0.8F * k;
        shinL.xRot = 1.2F * k;
        legR.xRot = 0.3F * k;
        shinR.xRot = 1.1F * k;
        armR.xRot = 0.9F * k;
        armR.zRot = 0.12F + 0.3F * k;
        forearmR.xRot = -0.6F * k;
        armL.xRot = 0.5F * k;
        armL.zRot = -0.08F - 0.3F * k;
        skirtBack.xRot += 0.3F * k;
    }

    /** 踏み込み斬り（宙）: 斧を頭上へ振り上げ、ひざを曲げて飛び込む。 */
    private void leap(float t, float age) {
        axeRaise(ease(t / 3), age);
        legL.xRot = -0.9F;
        shinL.xRot = 1.1F;
        legR.xRot = 0.2F;
        shinR.xRot = 1.3F;
        backHair.xRot += 0.6F;
        ponytail.xRot += 0.7F;
        skirtBack.xRot += 0.5F;
        skirtFront.xRot -= 0.3F;
    }

    // ------------------------------------------------------------------ ビット型の動き

    /** ビットの展開: 左手を前へ差し出して指を開き、背中のビットを送り出す。顔を上げる。 */
    private void deploy(float t) {
        float k = ease(t / 5) * (1 - ease((t - 10) / 3));
        armL.xRot = Mth.lerp(k, armL.xRot, -1.65F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.45F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, 0);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.45F);
        torso.xRot -= 0.12F * k;
        head.xRot -= 0.2F * k;
        backHair.xRot += 0.25F * k;
        ponytail.xRot += 0.3F * k;
    }

    /** 短剣の二連撃: 踏み込んで突き、手首を返して斬り上げる。 */
    private void stab(float t) {
        if (t < 6) {
            thrust(t * 0.9F + 0.3F);
            return;
        }
        float u = easeOut((t - 6) / 2.5F);
        float k = 1 - ease((t - 9) / 3);
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -1.4F, -2.2F));
        armR.yRot = Mth.lerp(u, -0.6F, 0.7F) * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.15F);
        torso.yRot += Mth.lerp(u, -0.35F, 0.4F) * k;
        torso.xRot += 0.15F * k;
        hips.y += 1.2F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.7F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.6F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.6F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.6F);
        ponytail.zRot += 0.5F * u * k * hairSide;
    }

    /** 後ろ跳び: 体を少し反らし、両腕を広げて着地に備える。脚はそろえて曲げる。 */
    private void backstep(float t) {
        float k = ease(t / 3) * (1 - ease((t - 8) / 4));
        torso.xRot -= 0.25F * k;
        head.xRot += 0.2F * k;
        armL.zRot = Mth.lerp(k, armL.zRot, -0.9F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.9F);
        armL.xRot = Mth.lerp(k, armL.xRot, -0.3F);
        armR.xRot = Mth.lerp(k, armR.xRot, -0.3F);
        legL.xRot = -0.5F * k;
        legR.xRot = -0.3F * k;
        shinL.xRot = 0.9F * k;
        shinR.xRot = 0.8F * k;
        skirtFront.xRot -= 0.5F * k;
        backHair.xRot -= 0.3F * k;
        ponytail.xRot -= 0.4F * k;
    }

    // ------------------------------------------------------------------ 射撃型の動き

    /**
     * 小型銃の3連射: 実銃のように両手で構える。両腕を前へ伸ばして銃を目の高さ・体の中心線に置き、左手で右手を下から包む
     * （角度は、銃身が正面・握りが真下になり、左手が右手に重なるよう逆算した値）。上体を少し前へ倒し、足を前後に開いて腰を落とす。
     * 頭の上下の向きに合わせて両腕ごと狙いを上下させ、撃つたびに反動で手元が小さく跳ね上がる（kick: 撃った直後ほど 1）。
     */
    private void pistolAim(float t, float kick) {
        float k = ease(t / 5);                          // 下ろすときは、行動のつなぎで滑らかに戻る
        float pitch = head.xRot;
        armR.xRot = Mth.lerp(k, armR.xRot, -1.642F + pitch - 0.1F * kick);
        armR.yRot = Mth.lerp(k, armR.yRot, -0.362F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.155F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.273F - 0.12F * kick);
        if (gunGrip != null) {
            gunGrip.xRot = Mth.lerp(k, gunGrip.xRot, 0.321F);
            gunGrip.yRot = Mth.lerp(k, gunGrip.yRot, 0.027F);
            gunGrip.zRot = Mth.lerp(k, gunGrip.zRot, 0.391F);
        }
        armL.xRot = Mth.lerp(k, armL.xRot, -1.381F + pitch - 0.1F * kick);
        armL.yRot = Mth.lerp(k, armL.yRot, 0.426F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.086F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.59F - 0.12F * kick);
        torso.xRot += 0.07F * k;
        hips.y += 0.4F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.3F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.25F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.22F);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 0.15F);
    }

    /**
     * レールガンを肩に当てて両手で構える（k: 0〜1）。右手は握り、左手は銃身の下に添える（角度は手が銃身に乗るよう逆算した値）。
     * 構えると銃身が体の左へ少し傾くので、上体を右へひねって、銃身を正面へ向ける。charge: 溜めの進み（体が少し震える）。
     */
    private void railHold(float k, float age, float charge) {
        float tremble = Mth.sin(age * 2.3F) * 0.012F * charge;
        armR.xRot = Mth.lerp(k, armR.xRot, 0.051F);
        armR.yRot = Mth.lerp(k, armR.yRot, -0.372F);
        armR.zRot = Mth.lerp(k, armR.zRot, -0.397F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -1.955F + tremble);
        if (gunGrip != null) {
            gunGrip.xRot = Mth.lerp(k, gunGrip.xRot, 0.494F);
            gunGrip.yRot = Mth.lerp(k, gunGrip.yRot, -0.408F);
            gunGrip.zRot = Mth.lerp(k, gunGrip.zRot, -0.006F);
        }
        armL.xRot = Mth.lerp(k, armL.xRot, -0.94F);
        armL.yRot = Mth.lerp(k, armL.yRot, 0.372F);
        armL.zRot = Mth.lerp(k, armL.zRot, 0.568F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.203F + tremble);
        torso.yRot += 0.219F * k;
        head.yRot -= 0.219F * k;
        hips.y += 0.6F * k;
        hairFlare(charge, age);
        legL.xRot = Mth.lerp(k, legL.xRot, -0.35F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.3F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.3F);
    }

    /** レールガンを溜めている間、髪がふわっと浮いて広がる（c: 0〜1）。毛先は小さく揺れる。 */
    private void hairFlare(float c, float age) {
        if (c <= 0) {
            return;
        }
        float sway = Mth.sin(age * 0.35F) * 0.05F * c;
        backHair.xRot += 0.3F * c + sway;
        sideLockL.zRot -= (0.3F * c + sway) * hairSide;
        sideLockR.zRot += (0.3F * c - sway) * hairSide;
        sideLockL.xRot -= 0.1F * c;
        sideLockR.xRot -= 0.1F * c;
        ponytail.zRot -= (0.35F * c + sway) * hairSide;
        ponytail.xRot += 0.15F * c;
        ahoge.xRot -= 0.2F * c;
    }

    /** レールガンの発射: 構えたまま、反動で上体と銃が跳ね上がり、少しのけぞってから戻る。 */
    private void railFire(float t, float age) {
        railHold(1, age, 0);
        hairFlare(1 - ease(t / 8), age);           // 溜めで広がった髪は、撃ったあと落ち着く
        float kick = (1 - ease(t / 6)) * ease(t * 2);
        torso.xRot -= 0.3F * kick;
        forearmR.xRot -= 0.25F * kick;
        forearmL.xRot -= 0.25F * kick;
        head.xRot -= 0.15F * kick;
        backHair.xRot += 0.4F * kick;
        ponytail.xRot += 0.5F * kick;
        skirtBack.xRot += 0.3F * kick;
    }

    // ------------------------------------------------------------------ 鞭型の動き

    /** 鞭打ちの前触れ: 右腕を頭の上・後ろへ高く上げ、体を少し反らす。左腕は前へ。 */
    private void lashWindup(float t) {
        float k = ease(t / 6);
        armR.xRot = Mth.lerp(k, armR.xRot, -2.75F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.35F);
        forearmR.xRot = -0.5F * k;
        torso.xRot -= 0.18F * k;
        torso.yRot += 0.35F * k;
        armL.xRot = -0.8F * k;
        armL.zRot = -0.08F - 0.3F * k;
        legL.xRot = -0.35F * k;
        legR.xRot = 0.3F * k;
        ponytail.xRot -= 0.2F * k;
        head.xRot -= 0.1F * k;
    }

    /** 打ち込み: 腕を一気に前へ振り下ろす（2 tick）。体を前へ倒し、髪とスカートが遅れてついてくる。 */
    private void lash(float t, float amount) {
        float u = easeOut(t / 2);
        float k = (1 - ease((t - 3) / 5)) * amount;
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -2.75F, -1.15F));
        armR.zRot = Mth.lerp(k, armR.zRot, 0.15F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.1F);
        torso.xRot += 0.3F * u * k;
        torso.yRot += Mth.lerp(u, 0.35F, -0.25F) * k;
        hips.y += 0.8F * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.55F);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 0.5F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.45F);
        armL.xRot = Mth.lerp(k, armL.xRot, 0.3F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.5F);
        backHair.xRot += 0.35F * u * k;
        ponytail.xRot += 0.5F * u * k;
        skirtBack.xRot += 0.3F * u * k;
    }

    /** 近くへの横払い: 手首を返して横に鋭く払う。 */
    private void crack(float t) {
        float u = easeOut(t / 2);
        float k = 1 - ease((t - 3) / 4);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.35F);
        armR.yRot = Mth.lerp(u, 0.9F, -0.8F) * k;
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        torso.yRot += Mth.lerp(u, 0.35F, -0.4F) * k;
        hips.y += 0.5F * k;
    }

    /** 絡め取り: 投げてから、腕を後ろへ引き、体を反らして引き寄せる。 */
    private void bind(float t) {
        if (t < io.github.genichimaruo.singulo.android.WhipAndroid.BIND_THROW) {
            lash(t * 0.6F, 1F);
            return;
        }
        float k = ease((t - io.github.genichimaruo.singulo.android.WhipAndroid.BIND_THROW) / 4) * (1 - ease((t - io.github.genichimaruo.singulo.android.WhipAndroid.BIND + 4) / 4));
        float tug = Mth.sin(t * 0.9F) * 0.08F;
        armR.xRot = Mth.lerp(k, -1.15F, 0.45F + tug);
        armR.zRot = 0.2F;
        forearmR.xRot = -0.9F * k;
        armL.xRot = Mth.lerp(k, 0.2F, -0.7F);
        forearmL.xRot = -0.8F * k;
        armL.zRot = -0.3F;
        torso.xRot -= 0.25F * k;
        hips.y += 1.4F * k;
        legL.xRot = -0.6F * k;
        shinL.xRot = 0.5F * k;
        legR.xRot = 0.5F * k;
        shinR.xRot = 0.6F * k;
        ponytail.xRot -= 0.2F * k;
        skirtFront.xRot -= 0.2F * k;
    }

    /** 叩きつけの前触れ: 右腕を頭上に掲げて鞭を回す（腕が小さく円を描く）。体は伸び上がる。 */
    private void slamWindup(float t, float age) {
        float k = ease(t / 5);
        float spin = age * 1.4F;
        armR.xRot = Mth.lerp(k, armR.xRot, -2.9F + Mth.sin(spin) * 0.15F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.25F + Mth.cos(spin) * 0.15F);
        forearmR.xRot = -0.2F * k;
        torso.xRot -= 0.12F * k;
        hips.y -= 0.6F * k;
        armL.zRot = -0.08F - 0.5F * k;
        armL.xRot = -0.3F * k;
        head.xRot -= 0.25F * k;
        skirtLeft.zRot -= 0.15F * k;
        skirtRight.zRot += 0.15F * k;
    }

    /** 叩きつけ: 深くかがみ込みながら、腕を床まで振り下ろす。 */
    private void slam(float t) {
        float u = easeOut(t / 2);
        float k = 1 - ease((t - 8) / 5);
        armR.xRot = Mth.lerp(k, armR.xRot, Mth.lerp(u, -2.9F, -0.55F));
        armR.zRot = Mth.lerp(k, armR.zRot, 0.1F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        torso.xRot += 0.6F * u * k;
        hips.y += 2.6F * u * k;
        legL.xRot = Mth.lerp(k, legL.xRot, -0.9F * u);
        shinL.xRot = Mth.lerp(k, shinL.xRot, 1.2F * u);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.4F * u);
        shinR.xRot = Mth.lerp(k, shinR.xRot, 1.1F * u);
        armL.xRot = Mth.lerp(k, armL.xRot, 0.4F);
        armL.zRot = Mth.lerp(k, armL.zRot, -0.7F);
        backHair.xRot -= 0.3F * u * k;
        ponytail.xRot -= 0.6F * u * k;
        skirtBack.xRot += 0.4F * u * k;
        skirtFront.xRot -= 0.3F * u * k;
    }

    /** 作業台で作る: 少し前かがみで、両手を前に出し、交互にとんとんと手を動かす。 */
    private void crafting(float age) {
        float beat = age * 0.45F;
        // 上体を少し前へ。頭は軽くうつむく程度（作業台を見下ろして首が折れないように、見る向きは使わない）
        torso.xRot += 0.12F;
        head.xRot = 0.2F;
        armL.xRot = -0.9F + Mth.sin(beat) * 0.25F;
        armR.xRot = -0.9F - Mth.sin(beat) * 0.25F;
        armL.zRot = -0.18F;
        armR.zRot = 0.18F;
        forearmL.xRot = -0.6F - Math.max(0, Mth.sin(beat)) * 0.4F;
        forearmR.xRot = -0.6F - Math.max(0, -Mth.sin(beat)) * 0.4F;
    }

    /** 採掘: 左手のツルハシを振りかぶって、振り下ろす。 */
    private void mining(float age) {
        float beat = age * 0.32F;
        float s = Mth.sin(beat);
        float down = s > 0 ? s * s : s * 0.6F;          // 振り下ろしは速く、振りかぶりはゆっくり
        torso.xRot += 0.1F;
        head.xRot = 0.25F;
        armL.xRot = -1.5F + down * 1.0F;
        armL.zRot = -0.12F;
        forearmL.xRot = -0.45F;
        armR.xRot = -0.25F;
        armR.zRot = 0.18F;
        forearmR.xRot = -0.3F;
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
    /**
     * ソーラー充電: 左手を体の左外へ開きながら空へ掲げる（少し後ろへ反らして、手とパネルが顔にかからないように）。
     * 顔はパネルの方を見上げる。
     */
    private void solar(float t, float age) {
        float k = ease(t / 8);
        armL.xRot = Mth.lerp(k, armL.xRot, -3.3F);
        // 頭上に掲げた腕は、z の回転の向きが下ろしたときと逆になる（正で体の左外へ開く）
        armL.zRot = Mth.lerp(k, armL.zRot, 0.48F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, 0.1F);
        head.xRot -= 0.3F * k;
        head.yRot += 0.25F * k;
        head.zRot -= 0.06F * k;
        torso.xRot -= 0.06F * k + Mth.sin(age * 0.06F) * 0.01F;
        armR.zRot = Mth.lerp(k, armR.zRot, 0.18F);
    }

    /** ソーラーをたたむ: パネルがたたまれるあいだは腕を掲げたまま、そのあと腕を下ろす。 */
    private void solarFold(float t, float age) {
        float down = ease((t - 9) / 10);
        float keep = 1 - down;
        solar(8 * keep, age);
    }

    /**
     * 座って充電（女の子座り）: ひざを前でそろえて床につき、すねを左右の外へ折り返して、その間に腰を下ろす。
     * 両手はひざの間の床に軽くつく。少しうつむいて首をかしげる。
     */
    private void resting(float t, float age) {
        float k = ease(t / 12);
        hips.y += 10.5F * k;
        legL.y += 10.5F * k;
        legR.y += 10.5F * k;
        // 太ももは前へ水平に、少し内向き（ひざが寄る）
        legL.xRot = -1.5F * k;
        legR.xRot = -1.5F * k;
        legL.yRot = 0.2F * k;
        legR.yRot = -0.2F * k;
        // すねは後ろへ折り返し、外へ開く
        shinL.xRot = 2.9F * k;
        shinR.xRot = 2.9F * k;
        shinL.zRot = 0.6F * k;
        shinR.zRot = -0.6F * k;
        torso.xRot += 0.15F * k;
        head.xRot = Mth.lerp(k, head.xRot, 0.25F + Mth.sin(age * 0.05F) * 0.03F);
        head.zRot += 0.14F * k;
        // 両手はひざの間の床へ
        armL.xRot = Mth.lerp(k, armL.xRot, -0.45F);
        armL.zRot = Mth.lerp(k, armL.zRot, 0.22F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.2F);
        armR.xRot = Mth.lerp(k, armR.xRot, -0.45F);
        armR.zRot = Mth.lerp(k, armR.zRot, -0.22F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
        skirtFront.xRot -= 1.0F * k;
        skirtLeft.zRot -= 0.6F * k;
        skirtRight.zRot += 0.6F * k;
        skirtBack.xRot += 0.35F * k;
        skirtTail.xRot += 0.4F * k;
        skirtTail.zRot += 0.3F * k;
    }

    /**
     * 水上であお向けに浮かんで休む（体を寝かせるのは描画のほう）: 両腕を横へ大きく広げ、脚はそろえて少しだけ開き、頭は少し後ろへ。
     * 手足が波にゆられて、ゆっくり漂う。
     */
    private void floatingRest(float t, float age) {
        float k = ease(t / 14);
        float drift = Mth.sin(age * 0.06F);
        armL.zRot = Mth.lerp(k, armL.zRot, -1.25F - 0.08F * drift);
        armR.zRot = Mth.lerp(k, armR.zRot, 1.25F + 0.08F * drift);
        armL.xRot = Mth.lerp(k, armL.xRot, 0.1F);
        armR.xRot = Mth.lerp(k, armR.xRot, 0.1F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.25F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.25F);
        legL.xRot = Mth.lerp(k, legL.xRot, 0.05F);
        legR.xRot = Mth.lerp(k, legR.xRot, 0.05F);
        legL.zRot = -(0.12F + 0.03F * drift) * k;
        legR.zRot = (0.12F + 0.03F * drift) * k;
        shinL.xRot = 0.15F * k;
        shinR.xRot = 0.15F * k;
        head.xRot = Mth.lerp(k, head.xRot, -0.35F + 0.04F * drift);
        skirtFront.xRot -= 0.15F * k;
    }

    /**
     * 水の底でひざを抱えて座って休む: 腰を底まで下げ、ひざを胸の前に立て、両腕でひざを抱える。頭はひざにのせるように少し下げ、
     * 水にゆられて、体がゆっくり前後に揺れる。
     */
    private void seabedRest(float t, float age) {
        float k = ease(t / 14);
        float sway = Mth.sin(age * 0.05F);
        hips.y += 10.5F * k;
        legL.y += 10.5F * k;
        legR.y += 10.5F * k;
        // 太ももは前へ高く上げ（ひざを立てる）、すねは下へ折り返す
        legL.xRot = -2.2F * k;
        legR.xRot = -2.2F * k;
        legL.yRot = 0.08F * k;
        legR.yRot = -0.08F * k;
        shinL.xRot = 2.3F * k;
        shinR.xRot = 2.3F * k;
        torso.xRot += (0.35F + 0.04F * sway) * k;
        // 両腕は前へのばして、すねのまわりを抱える
        armL.xRot = Mth.lerp(k, armL.xRot, -1.1F);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.1F);
        armL.zRot = Mth.lerp(k, armL.zRot, 0.35F);
        armR.zRot = Mth.lerp(k, armR.zRot, -0.35F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.5F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.5F);
        head.xRot = Mth.lerp(k, head.xRot, 0.3F + 0.03F * sway);
        skirtFront.xRot -= 1.2F * k;
        skirtLeft.zRot -= 0.5F * k;
        skirtRight.zRot += 0.5F * k;
        skirtBack.xRot += 0.3F * k;
    }

    /** 椅子・階段に座る: 腰を座面まで下げ、太ももを前へ水平に、すねは下へ垂らす。両手はひざの上。 */
    private void sittingOnChair(float t, float age) {
        float k = ease(t / 10);
        hips.y += 11.5F * k;
        legL.y += 11.5F * k;
        legR.y += 11.5F * k;
        legL.xRot = -1.5F * k;
        legR.xRot = -1.5F * k;
        legL.yRot = 0.08F * k;
        legR.yRot = -0.08F * k;
        shinL.xRot = 1.45F * k + Mth.sin(age * 0.05F) * 0.05F * k;      // 足をすこしぶらぶら
        shinR.xRot = 1.45F * k - Mth.sin(age * 0.05F) * 0.05F * k;
        torso.xRot -= 0.05F * k;
        armL.xRot = Mth.lerp(k, armL.xRot, -0.55F);
        armL.zRot = Mth.lerp(k, armL.zRot, 0.12F);
        forearmL.xRot = Mth.lerp(k, forearmL.xRot, -0.35F);
        armR.xRot = Mth.lerp(k, armR.xRot, -0.55F);
        armR.zRot = Mth.lerp(k, armR.zRot, -0.12F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.35F);
        skirtFront.xRot -= 1.2F * k;
        skirtLeft.zRot -= 0.3F * k;
        skirtRight.zRot += 0.3F * k;
        skirtBack.xRot += 0.2F * k;
    }

    /** 植木鉢・作物に手をかざす: 少しかがんで、右手を前へ下向きに出し、ゆっくり左右に動かす。 */
    private void tending(float t, float age) {
        float k = ease(t / 8);
        torso.xRot += 0.35F * k;
        hips.y += 1.2F * k;
        legL.xRot = -0.35F * k;
        legR.xRot = -0.35F * k;
        shinL.xRot = 0.6F * k;
        shinR.xRot = 0.6F * k;
        head.xRot = Mth.lerp(k, head.xRot, 0.5F);
        armR.xRot = Mth.lerp(k, armR.xRot, -1.1F);
        armR.zRot = Mth.lerp(k, armR.zRot, 0.1F + Mth.sin(age * 0.12F) * 0.15F);
        forearmR.xRot = Mth.lerp(k, forearmR.xRot, -0.2F);
    }

    /** 床を掃く: ほうきを持つように両手を体の前で下げ、左右に掃く。 */
    private void sweeping(float age) {
        float s = Mth.sin(age * 0.35F);
        torso.xRot += 0.2F;
        torso.yRot += s * 0.25F;
        head.xRot = 0.35F;
        armL.xRot = -0.6F;
        armL.zRot = -0.25F + s * 0.25F;
        forearmL.xRot = -0.3F;
        armR.xRot = -0.45F;
        armR.zRot = 0.35F + s * 0.25F;
        forearmR.xRot = -0.25F;
    }

    private void charging(float t, float age) {
        float k = ease(t / 10);
        // 脚は腰とは別の部品なので、腰と一緒に下げる（下げないと、立ったときの高さから脚が前へ突き出る）
        hips.y += 8.5F * k;
        legL.y += 8.5F * k;
        legR.y += 8.5F * k;
        legL.xRot = -1.5F * k;
        legR.xRot = -1.5F * k;
        shinL.xRot = 2.95F * k;
        shinR.xRot = 2.95F * k;
        legL.zRot = -0.08F * k;
        legR.zRot = 0.08F * k;
        torso.xRot += 0.1F * k;
        head.xRot = 0.15F * k + Mth.sin(age * 0.06F) * 0.03F;
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
        ponytail.zRot -= 0.5F * u * k * hairSide;
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
        ponytail.zRot -= 1.1F * fling * hairSide;
        backHair.xRot += 0.6F * fling;
        sideLockL.zRot -= 0.5F * fling * hairSide;
        sideLockR.zRot += 0.5F * fling * hairSide;
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
