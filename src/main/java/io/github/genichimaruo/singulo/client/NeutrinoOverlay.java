package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.item.NeutrinoScannerItem;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * ニュートリノ・スキャナーの表示: 見つけた鉱石（金色）と遺構（水色）の輪郭を、地形を透かして10秒間描く。
 * 終わりに近づくほど薄くなる。
 */
public final class NeutrinoOverlay {
    private static List<BlockPos> ores = new ArrayList<>();
    private static List<BlockPos> ruins = new ArrayList<>();
    private static long until;

    private NeutrinoOverlay() {}

    public static void show(List<BlockPos> o, List<BlockPos> r) {
        ores = new ArrayList<>(o);
        ruins = new ArrayList<>(r);
        Minecraft mc = Minecraft.getInstance();
        until = mc.level == null ? 0 : mc.level.getGameTime() + NeutrinoScannerItem.SHOW_TICKS;
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        // AFTER_LEVEL の PoseStack にはカメラの向きが入っていないので、ホロ投影と同じ段階で描く（地形にぴったり重なる）
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level == null || ores.isEmpty() && ruins.isEmpty()) {
            return;
        }
        long left = until - mc.level.getGameTime();
        if (left <= 0) {
            ores.clear();
            ruins.clear();
            return;
        }
        float alpha = Math.min(1F, left / 40F);
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(XrayLines.TYPE);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos p : ores) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(p).deflate(0.05), 1.0F, 0.82F, 0.35F, alpha);
        }
        for (BlockPos p : ruins) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(p).deflate(0.05), 0.47F, 0.82F, 0.94F, alpha * 0.7F);
        }
        pose.popPose();
        buffers.endBatch(XrayLines.TYPE);
    }

    /**
     * 深さを無視して描く線（地形越しに見える）。RenderType の保護された部品を使うために継承する。
     * NO_DEPTH_TEST は「深さの判定を何もしない」だけで、すでに有効な深さの判定を切らない（ワールドの描画中は有効なので、
     * 地中の鉱石が地形に隠れて見えなかった）。そこで描く間だけ深さの判定を切る段（XRAY）を足す。
     */
    private static final class XrayLines extends RenderType {
        private static final LayeringStateShard XRAY = new LayeringStateShard("singulo_xray",
                com.mojang.blaze3d.systems.RenderSystem::disableDepthTest, com.mojang.blaze3d.systems.RenderSystem::enableDepthTest);
        static final RenderType TYPE = create("singulo_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 4096, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                        .setLayeringState(XRAY)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private XrayLines(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
