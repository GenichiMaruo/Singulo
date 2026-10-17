package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * アンドロイド作業台: 預かり箱に物がたまっている間、台の上にその物を浮かべて回す（右クリックで受け取れる、の目印）。
 */
public class WorkbenchRenderer implements BlockEntityRenderer<AndroidWorkbenchBlockEntity> {
    private final BlockEntityRendererProvider.Context context;

    public WorkbenchRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(AndroidWorkbenchBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack shown = be.shown();
        if (shown.isEmpty() || be.getLevel() == null) {
            return;
        }
        float age = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.3 + 0.05 * Mth.sin(age * 0.1F), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(age * 2));
        pose.scale(0.5F, 0.5F, 0.5F);
        // 台のブロックの中は真っ暗なので、台の上のマスの明るさで描く
        int above = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        context.getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, above, overlay, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
