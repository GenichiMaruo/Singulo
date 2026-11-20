package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.PortablePrinterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * 地面に置いた携帯型アンドロイド印刷機（0.21.0）。寝かせたカバン（下の殻・ふた・ふたの前の取っ手）で、印刷のときは
 * ふたが真ん中の継ぎ目から後ろへパカっと開き（{@link #LID_OPEN} 度）、中から印刷面がせり上がる。閉じるときは逆。
 * 前（取っ手の側）は使った人の方を向く。印刷面の上面には、組立ステーションの上面と同じく動く絵（ブロックの絵
 * block/portable_printer_bed の真ん中の 12×8。エンティティの絵は動かせないので、ブロックの絵の束から描く）を重ねる。
 * 絵は tools/portable_printer.py。
 */
public class PortablePrinterRenderer extends EntityRenderer<PortablePrinterEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Singulo.id("portable_printer"), "main");
    private static final ResourceLocation TEXTURE = Singulo.id("textures/entity/portable_printer.png");
    /** Mk2 の絵（継ぎ目と印の色が違う）。 */
    private static final ResourceLocation TEXTURE_2 = Singulo.id("textures/entity/portable_printer_2.png");
    private static final ResourceLocation BED = Singulo.id("block/portable_printer_bed");
    /** ふたの開く角度（度）。 */
    private static final float LID_OPEN = 110;
    /** 印刷面の高さ（モデルの座標。下向きが正、地面は 24）: 閉じているとき（殻の中）と、開いたとき（上面が地面から4ドット）。 */
    private static final float BED_CLOSED = 22.5F, BED_OPEN = 20;

    private final ModelPart root;
    private final ModelPart lid;
    private final ModelPart bed;

    public PortablePrinterRenderer(EntityRendererProvider.Context context) {
        super(context);
        root = context.bakeLayer(LAYER);
        lid = root.getChild("lid");
        bed = root.getChild("bed");
        shadowRadius = 0.4F;
    }

    /**
     * 絵（64×64）の割り付け: 殻 (0,0) 14×2×10、ふた (0,12) 14×2×10、取っ手 (48,0) 4×1×1、印刷面 (0,24) 12×1×8。
     * 殻の上面とふたの下面が内側（開いたときに見える）。
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        parts.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-7, 22, -5, 14, 2, 10), PartPose.ZERO);
        parts.addOrReplaceChild("lid", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-7, -2, -10, 14, 2, 10)
                .texOffs(48, 0).addBox(-2, -1.5F, -11, 4, 1, 1), PartPose.offset(0, 22, 5));
        parts.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 24).addBox(-6, 0, -4, 12, 1, 8),
                PartPose.offset(0, BED_CLOSED, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PortablePrinterEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = entity.openness(partialTick);
        lid.xRot = -open * LID_OPEN * Mth.DEG_TO_RAD;
        bed.y = Mth.lerp(open, BED_CLOSED, BED_OPEN);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - entity.getYRot()));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        root.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        if (open > 0.02F) {
            bedTop(pose, buffers, light);
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** 印刷面の上面（モデルの座標で x −6〜6・z −4〜4、高さは印刷面の上端のわずか上）に、動く絵の真ん中の 12×8 を貼る。 */
    private void bedTop(PoseStack pose, MultiBufferSource buffers, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(BED);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose last = pose.last();
        float y = (bed.y - 0.02F) / 16F;
        float x0 = -6 / 16F, x1 = 6 / 16F, z0 = -4 / 16F, z1 = 4 / 16F;
        float u0 = sprite.getU(2 / 16F), u1 = sprite.getU(14 / 16F), v0 = sprite.getV(4 / 16F), v1 = sprite.getV(12 / 16F);
        vertex(vc, last, x0, y, z0, u0, v0, light);
        vertex(vc, last, x0, y, z1, u0, v1, light);
        vertex(vc, last, x1, y, z1, u1, v1, light);
        vertex(vc, last, x1, y, z0, u1, v0, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), 0, -1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(PortablePrinterEntity entity) {
        return io.github.genichimaruo.singulo.android.PortableAndroidPrinterItem.units(entity.shownItem()) > 1 ? TEXTURE_2 : TEXTURE;
    }
}
