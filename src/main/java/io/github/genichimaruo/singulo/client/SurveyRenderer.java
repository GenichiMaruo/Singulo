package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.android.work.AreaCardItem;
import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 測量メガネを掛けているとき、手に持ったカードの記録した場所を枠で見せる。
 * <ul>
 * <li>範囲カード: 記録した範囲（水色）。1つ目の角だけなら、その角から見ているブロックまでの、できる範囲の見込み（薄い水色）。</li>
 * <li>作業位置カード: 記録したブロック（緑）。</li>
 * </ul>
 */
final class SurveyRenderer {
    private SurveyRenderer() {}

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !SurveyGlassesItem.wearing(player)) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (AreaCardItem.isCard(stack) && AreaCardItem.sameDimension(stack, mc.level)) {
                BoundingBox box = AreaCardItem.box(stack);
                BlockPos first = AreaCardItem.first(stack);
                if (box != null) {
                    LevelRenderer.renderLineBox(pose, lines, aabb(box), 0.45F, 0.85F, 1.0F, 1.0F);
                } else if (first != null) {
                    LevelRenderer.renderLineBox(pose, lines, new AABB(first).inflate(0.004), 0.45F, 0.85F, 1.0F, 1.0F);
                    // 見ているブロックを2つ目の角にしたときの範囲
                    if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                        LevelRenderer.renderLineBox(pose, lines, aabb(BoundingBox.fromCorners(first, hit.getBlockPos())), 0.45F, 0.85F, 1.0F, 0.45F);
                    }
                }
            } else if (WorkLocationCardItem.isCard(stack) && WorkLocationCardItem.sameDimension(stack, mc.level)) {
                BlockPos p = WorkLocationCardItem.pos(stack);
                if (p != null) {
                    LevelRenderer.renderLineBox(pose, lines, new AABB(p).inflate(0.004), 0.4F, 0.95F, 0.55F, 1.0F);
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** ブロックの範囲を、枠を描く箱にする（少しだけ外へ広げて、ブロックの面と重ならないように）。 */
    private static AABB aabb(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1).inflate(0.004);
    }
}
