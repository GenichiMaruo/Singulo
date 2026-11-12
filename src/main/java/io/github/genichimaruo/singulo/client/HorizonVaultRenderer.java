package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.storage.HorizonVaultBlock;
import io.github.genichimaruo.singulo.storage.HorizonVaultBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 事象の地平線倉庫の中: ガラスの室の真ん中に浮かぶ小さなブラックホール（入っている量の桁で大きくなる）と、その周りを回る橙の降着円盤、
 * 円盤の上を回りながら吸い込まれていく、しまっている物の見本。正面の台に個数（K・M・G で短く）を出す。空なら、かすかな光の点だけ。
 */
public class HorizonVaultRenderer implements BlockEntityRenderer<HorizonVaultBlockEntity> {
    /** 室の中心（ブロック）。 */
    private static final float CY = 9.5F / 16;
    private final Font font;

    public HorizonVaultRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(HorizonVaultBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = (be.getLevel().getGameTime() % 24000 + partialTick) / 20F;
        long count = be.count();
        float fill = count <= 0 ? 0 : (float) Math.min(1, Math.log10(1 + count) / Math.log10(1 + HorizonVaultBlockEntity.VISUAL_FULL));
        float r = 0.045F + 0.075F * fill;
        pose.pushPose();
        pose.translate(0.5, CY, 0.5);
        if (count <= 0) {
            pose.mulPose(Axis.YP.rotationDegrees(time * 30));
            FxDraw.ring(pose.last(), FxDraw.glow(buffers), 0, 0.04F, 0xFFB060, 120, 0);
            pose.popPose();
            return;
        }
        // 影の球（黒）と、少し傾いて回る降着円盤（加算の光）
        FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, LightTexture.FULL_BRIGHT);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(18));
        pose.mulPose(Axis.YP.rotationDegrees(time * 90));
        var glow = FxDraw.glow(buffers);
        FxDraw.ring(pose.last(), glow, r * 1.25F, r * 1.6F, 0xFFF0D0, 230, 160);
        FxDraw.ring(pose.last(), glow, r * 1.6F, r * 2.0F, 0xFF9A40, 170, 0);
        pose.popPose();
        // いつもこちらを向く: 影の縁の光子の輪（細く明るい）と、まわりの紫のゆがみの暈
        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FxDraw.ring(pose.last(), glow, r * 1.02F, r * 1.14F, 0xFFFFFF, 220, 90);
        FxDraw.ring(pose.last(), glow, r * 1.14F, r * 2.0F, 0x9060FF, 70, 0);
        pose.popPose();
        // 円盤の上を回りながら、少しずつ内へ寄っていく見本
        ItemStack type = be.type();
        if (!type.isEmpty()) {
            float angle = time * 70;
            float spiral = 1 - Mth.positiveModulo(time / 6F, 1F) * 0.45F;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(18));
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0.2F * spiral, 0.02F * Mth.sin(time * 3), 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * 120));
            float s = 0.14F * (0.6F + 0.4F * spiral);
            pose.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(type, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
            pose.popPose();
        }
        pose.popPose();
        gauge(fill, pose, buffers);
        label(be, count, pose, buffers);
    }

    /**
     * 埋まり具合: 四隅の柱の外側の2面に、下から紫の光の帯がのびる（室の高さ 6〜13 ドットのうち、量の桁の割合まで）。
     */
    private static void gauge(float fill, PoseStack pose, MultiBufferSource buffers) {
        if (fill <= 0) {
            return;
        }
        var glow = FxDraw.glow(buffers);
        float y0 = 6 / 16F, y1 = y0 + 8 / 16F * fill, e = 0.004F, w = 0.6F / 16, t = 0.4F / 16;
        // 柱の外側の角の位置（x, z）と、その角から外へ向く面
        float lo = 2 / 16F, hi = 14 / 16F;
        float[][] corners = {{lo, lo}, {hi, lo}, {lo, hi}, {hi, hi}};
        for (float[] c : corners) {
            float sx = c[0] == lo ? -1 : 1, sz = c[1] == lo ? -1 : 1;
            // 柱の真ん中（角から1ドット内側）の、x の外側の面と z の外側の面に、細い帯
            float px = c[0] - sx / 16F, pz = c[1] - sz / 16F;
            float fx = c[0] + sx * e, fz = c[1] + sz * e;
            FxDraw.box(pose, glow, Math.min(fx, fx + sx * t), y0, pz - w / 2, Math.max(fx, fx + sx * t), y1, pz + w / 2,
                    LightTexture.FULL_BRIGHT, 0xA070FF, 200);
            FxDraw.box(pose, glow, px - w / 2, y0, Math.min(fz, fz + sz * t), px + w / 2, y1, Math.max(fz, fz + sz * t),
                    LightTexture.FULL_BRIGHT, 0xA070FF, 200);
        }
    }

    /** 正面の台の個数。 */
    private void label(HorizonVaultBlockEntity be, long count, PoseStack pose, MultiBufferSource buffers) {
        // 上から見て点対称（4面とも同じ台）なので、4面すべてに出す
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            labelOn(be, count, facing, pose, buffers);
        }
    }

    private void labelOn(HorizonVaultBlockEntity be, long count, Direction facing, PoseStack pose, MultiBufferSource buffers) {
        String text = compact(count);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        // 個数の台（x 3〜13、y 1〜3、正面は z 0）の前に
        pose.translate(0, -0.5 + 2 / 16F, -0.5 - 0.003);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        // 右に、しまっている物の小さな絵（平たく。個数は左へ寄せる）
        ItemStack type = be.type();
        if (!type.isEmpty()) {
            pose.pushPose();
            pose.translate(3.8F / 16, 0, 0);
            pose.scale(1.4F / 16, 1.4F / 16, 0.002F);
            Minecraft.getInstance().getItemRenderer().renderStatic(type, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
        float s = 1 / 110F;
        pose.scale(s, -s, s);
        float shift = type.isEmpty() ? 0 : -0.9F / 16 / s;
        font.drawInBatch(text, -font.width(text) / 2F + shift, -4, 0xFFE8C070, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** 個数を短く（1,234 → 1.2K、5,600,000 → 5.6M）。 */
    static String compact(long n) {
        if (n < 10_000) {
            return String.valueOf(n);
        }
        String[] units = {"K", "M", "G", "T", "P", "E"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v >= 100 ? String.format("%.0f", v) : String.format("%.1f", v)) + units[u];
    }

    @Override
    public boolean shouldRenderOffScreen(HorizonVaultBlockEntity be) {
        return false;
    }
}
