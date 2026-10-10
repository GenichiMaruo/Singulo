package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * ホロ投影機の投影（クライアントだけ）。足りない位置には半分の大きさの見本ブロックと水色の枠、
 * 違うブロックがある位置と空けるべき位置には赤い枠を描く。全部そろうか、離れるか、コントローラが壊れると消える。
 */
public final class HologramRenderer {
    @Nullable
    private static BlockPos controller;
    @Nullable
    private static Blueprints.Kind kind;
    @Nullable
    private static Map<BlockPos, BlockState> layout;

    private HologramRenderer() {}

    /** 投影を切り替える。同じコントローラなら消し、resize なら大きさを変えて出し直す。 */
    public static void toggle(BlockPos pos, Blueprints.Kind k, Direction back, int size, boolean resize) {
        if (pos.equals(controller) && !resize) {
            clear();
            return;
        }
        controller = pos.immutable();
        kind = k;
        layout = Blueprints.layout(k, pos, back, size);
    }

    public static void clear() {
        controller = null;
        kind = null;
        layout = null;
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || layout == null || controller == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(controller)) > 96 * 96
                || kind == null || !level.getBlockState(controller).is(Blueprints.controllerBlock(kind))) {
            clear();
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int wrong = 0;
        float pulse = 0.55F + 0.25F * (float) Math.sin((level.getGameTime() + event.getPartialTick()) * 0.15);
        for (Map.Entry<BlockPos, BlockState> e : layout.entrySet()) {
            BlockPos p = e.getKey();
            BlockState want = e.getValue();
            BlockState have = level.getBlockState(p);
            if (Blueprints.matches(want, have)) {
                continue;
            }
            wrong++;
            pose.pushPose();
            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            if (want.isAir() || !have.isAir()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98), 1.0F, 0.3F, 0.3F, 1.0F);
            } else {
                LevelRenderer.renderLineBox(pose, lines, new AABB(0.05, 0.05, 0.05, 0.95, 0.95, 0.95), 0.47F, 0.82F, 0.94F, pulse);
            }
            if (!want.isAir()) {
                pose.translate(0.25, 0.25, 0.25);
                pose.scale(0.5F, 0.5F, 0.5F);
                mc.getBlockRenderer().renderSingleBlock(want, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        buffers.endBatch();
        if (wrong == 0) {
            mc.player.displayClientMessage(Component.translatable("holo.singulo.complete"), true);
            clear();
        }
    }
}
